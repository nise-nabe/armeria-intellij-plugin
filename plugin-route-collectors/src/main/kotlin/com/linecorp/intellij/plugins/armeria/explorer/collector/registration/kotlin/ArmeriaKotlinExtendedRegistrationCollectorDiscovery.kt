package com.linecorp.intellij.plugins.armeria.explorer.collector.registration.kotlin

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaServerRegistrationSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaServerRegistrationSupport.DiscoveryRegistration
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaServerRegistrationSupport.DiscoveryRegistry
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Kotlin counterpart of [ArmeriaExtendedRegistrationCollectorDiscovery] — collects
 * `serverListener(...)` registrations for ZooKeeper / Eureka / Consul updating listeners.
 */
internal object ArmeriaKotlinExtendedRegistrationCollectorDiscovery {
    private const val MAX_INITIALIZER_HOPS = 4

    private val REGISTRY_URI_ARGUMENT_NAMES =
        setOf("zkConnectionString", "eurekaUri", "consulUri", "uri", "connectionString")
    private val SERVICE_NAME_ARGUMENT_NAMES = setOf("znodePath", "appName", "serviceName", "name")
    private val SPEC_ARGUMENT_NAMES = setOf("spec", "registrationSpec")

    fun collect(
        call: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val methodName = ArmeriaKotlinRegistrationChainSupport.resolveCallName(call) ?: return
        if (methodName != ArmeriaServerRegistrationSupport.SERVER_LISTENER_METHOD) {
            return
        }
        if (!ArmeriaBuilderCallHeuristics.looksLikeKotlinBuilderCall(call)) {
            return
        }
        val listenerArgument = call.valueArguments.firstOrNull()?.getArgumentExpression() ?: return
        val registration = extractRegistration(listenerArgument) ?: return
        if (!isRegistryOnClasspath(call, registration.registry)) {
            return
        }
        val key = ArmeriaKotlinRegistrationChainSupport.registrationKey(call) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        routes += ArmeriaServerRegistrationSupport.discoveryRoute(call, registration)
    }

    private fun isRegistryOnClasspath(
        call: KtCallExpression,
        registry: DiscoveryRegistry,
    ): Boolean =
        JavaPsiFacade
            .getInstance(call.project)
            .findClass(registry.listenerQualifiedName, GlobalSearchScope.allScope(call.project)) != null

    private fun extractRegistration(
        expression: KtExpression?,
        hops: Int = 0,
    ): DiscoveryRegistration? {
        if (expression == null || hops > MAX_INITIALIZER_HOPS) {
            return null
        }
        return when (expression) {
            is KtParenthesizedExpression -> extractRegistration(expression.expression, hops + 1)
            is KtNameReferenceExpression -> {
                val resolved = expression.references.firstOrNull()?.resolve()
                val initializer =
                    when (resolved) {
                        is KtProperty -> resolved.initializer
                        is PsiVariable -> resolved.initializer as? KtExpression
                        else -> null
                    } ?: return null
                extractRegistration(initializer, hops + 1)
            }
            is KtDotQualifiedExpression, is KtCallExpression -> registrationFromCallChain(expression)
            else -> null
        }
    }

    /**
     * Flattens a `receiver.call(...).call(...)` chain into (call, receiver) pairs and finds the
     * `builder(...)` / `of(...)` factory on a known updating-listener class (or a direct
     * `XxxUpdatingListener(...)` constructor-style call).
     */
    private fun registrationFromCallChain(expression: KtExpression): DiscoveryRegistration? {
        val chain = callChain(expression)
        for ((index, pair) in chain.withIndex()) {
            val (call, receiver) = pair
            val callName = ArmeriaKotlinRegistrationChainSupport.resolveCallName(call) ?: continue
            if (callName in ArmeriaServerRegistrationSupport.LISTENER_FACTORY_METHODS) {
                val registry = listenerRegistry(call, receiver) ?: continue
                return registrationFromArguments(registry, call.valueArguments, chain.take(index))
            }
            registryFromClassName(callName)?.let { registry ->
                return registrationFromArguments(registry, call.valueArguments, emptyList())
            }
        }
        return null
    }

    private fun callChain(expression: KtExpression): List<Pair<KtCallExpression, KtExpression?>> {
        val result = mutableListOf<Pair<KtCallExpression, KtExpression?>>()
        var current: KtExpression? = expression
        while (current != null) {
            when (current) {
                is KtDotQualifiedExpression -> {
                    (current.selectorExpression as? KtCallExpression)?.let { selector ->
                        result += selector to current.receiverExpression
                    }
                    current = current.receiverExpression
                }
                is KtCallExpression -> {
                    result += current to null
                    current = null
                }
                is KtParenthesizedExpression -> current = current.expression
                else -> current = null
            }
        }
        return result
    }

    private fun listenerRegistry(
        call: KtCallExpression,
        receiver: KtExpression?,
    ): DiscoveryRegistry? {
        val resolved = (call.calleeExpression as? KtNameReferenceExpression)?.references?.firstOrNull()?.resolve()
        DiscoveryRegistry.fromQualifiedName((resolved as? PsiMethod)?.containingClass?.qualifiedName)?.let {
            return it
        }
        return registryFromClassName(receiver?.text)
    }

    private fun registryFromClassName(name: String?): DiscoveryRegistry? {
        name ?: return null
        return DiscoveryRegistry.fromQualifiedName(name) ?: DiscoveryRegistry.fromSimpleName(name.substringAfterLast('.'))
    }

    private fun registrationFromArguments(
        registry: DiscoveryRegistry,
        arguments: List<KtValueArgument>,
        forwardCalls: List<Pair<KtCallExpression, KtExpression?>>,
    ): DiscoveryRegistration {
        val registryUri = extractTextLike(argumentAt(arguments, 0, REGISTRY_URI_ARGUMENT_NAMES)).orEmpty()
        var serviceName = extractTextLike(argumentAt(arguments, 1, SERVICE_NAME_ARGUMENT_NAMES)).orEmpty()
        // Builder-chain setters such as EurekaUpdatingListenerBuilder.appName("...").
        forwardCalls
            .firstOrNull { (forwardCall, _) ->
                ArmeriaKotlinRegistrationChainSupport.resolveCallName(forwardCall) in
                    ArmeriaServerRegistrationSupport.SERVICE_NAME_BUILDER_METHODS
            }?.let { (forwardCall, _) ->
                extractTextLike(forwardCall.valueArguments.firstOrNull()?.getArgumentExpression())?.let { serviceName = it }
            }
        // ZooKeeperRegistrationSpec.curator("name") overrides the znode path as the service name.
        extractTextLike(argumentAt(arguments, 2, SPEC_ARGUMENT_NAMES))?.let { specName ->
            if (registry == DiscoveryRegistry.ZOOKEEPER) {
                serviceName = specName
            }
        }
        return DiscoveryRegistration(registry = registry, serviceName = serviceName, registryUri = registryUri)
    }

    private fun argumentAt(
        arguments: List<KtValueArgument>,
        position: Int,
        names: Set<String>,
    ): KtExpression? {
        val named = arguments.firstOrNull { it.getArgumentName()?.asName?.asString() in names }
        if (named != null) {
            return named.getArgumentExpression()
        }
        val positional = arguments.filterNot { it.isNamed() }
        return positional.getOrNull(position)?.getArgumentExpression()
    }

    /**
     * Kotlin counterpart of the Java `extractTextLike` — resolves constants first, then unwraps
     * single-argument factories (`URI.create("...")`, `ZooKeeperRegistrationSpec.curator("...")`),
     * variable initializers, and falls back to expression text.
     */
    private fun extractTextLike(
        expression: KtExpression?,
        hops: Int = 0,
    ): String? {
        if (expression == null || hops > MAX_INITIALIZER_HOPS) {
            return null
        }
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: expression
        ArmeriaKotlinExpressionSupport.extractKotlinString(unwrapped)?.let { return it }
        val nested =
            when (unwrapped) {
                is KtCallExpression -> unwrapped.valueArguments.singleOrNull()?.getArgumentExpression()
                is KtDotQualifiedExpression ->
                    (unwrapped.selectorExpression as? KtCallExpression)
                        ?.valueArguments
                        ?.singleOrNull()
                        ?.getArgumentExpression()
                else -> null
            } ?: return unwrapped.text.takeIf { it.isNotBlank() }
        return extractTextLike(nested, hops + 1)
    }
}
