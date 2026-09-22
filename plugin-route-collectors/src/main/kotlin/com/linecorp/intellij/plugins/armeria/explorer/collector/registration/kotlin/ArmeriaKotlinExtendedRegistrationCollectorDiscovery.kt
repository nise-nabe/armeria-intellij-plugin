package com.linecorp.intellij.plugins.armeria.explorer.collector.registration.kotlin

import com.intellij.openapi.module.ModuleUtilCore
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
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression

/**
 * Kotlin counterpart of [ArmeriaExtendedRegistrationCollectorDiscovery] — collects
 * `serverListener(...)` registrations for ZooKeeper / Eureka / Consul updating listeners.
 */
internal object ArmeriaKotlinExtendedRegistrationCollectorDiscovery {
    private const val MAX_INITIALIZER_HOPS = 4

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
    ): Boolean {
        val module = ModuleUtilCore.findModuleForPsiElement(call) ?: return false
        return JavaPsiFacade
            .getInstance(call.project)
            .findClass(
                registry.listenerQualifiedName,
                GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module),
            ) != null
    }

    private fun extractRegistration(
        expression: KtExpression?,
        hops: Int = 0,
    ): DiscoveryRegistration? {
        if (expression == null || hops > MAX_INITIALIZER_HOPS) {
            return null
        }
        return when (expression) {
            is KtNameReferenceExpression -> {
                val initializer = propertyInitializer(expression) ?: return null
                extractRegistration(initializer, hops + 1)
            }
            is KtQualifiedExpression -> {
                registrationFromCallChain(expression, hops)
                    ?: propertyInitializer(expression)?.let { extractRegistration(it, hops + 1) }
            }
            is KtCallExpression -> registrationFromCallChain(expression, hops)
            else ->
                ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression)?.let {
                    if (it === expression) null else extractRegistration(it, hops + 1)
                }
        }
    }

    private fun propertyInitializer(expression: KtExpression): KtExpression? {
        val referenceExpression =
            when (expression) {
                is KtQualifiedExpression -> expression.selectorExpression
                else -> expression
            } ?: return null
        val resolved = referenceExpression.references.firstOrNull()?.resolve()
        return when (resolved) {
            is KtProperty -> resolved.initializer
            is PsiVariable -> resolved.initializer as? KtExpression
            else -> null
        }
    }

    /**
     * Flattens a `receiver.call(...).call(...)` chain into (call, receiver) pairs and finds the
     * `builder(...)` / `of(...)` factory on a known updating-listener class (or a direct
     * `XxxUpdatingListener(...)` constructor-style call).
     */
    private fun registrationFromCallChain(
        expression: KtExpression,
        hops: Int,
    ): DiscoveryRegistration? {
        val chain = callChain(expression)
        for ((index, pair) in chain.withIndex()) {
            val (call, receiver) = pair
            val callName = ArmeriaKotlinRegistrationChainSupport.resolveCallName(call) ?: continue
            if (callName in ArmeriaServerRegistrationSupport.LISTENER_FACTORY_METHODS) {
                val registry = listenerRegistry(call, receiver) ?: continue
                return registrationFromArguments(registry, call, chain.take(index))
            }
            registryFromClassName(callName)?.let { registry ->
                return registrationFromArguments(registry, call, emptyList())
            }
        }
        // `lb.build()` where `lb` holds a builder chain — hop into the variable's initializer.
        val innermostReceiver = chain.lastOrNull()?.second as? KtNameReferenceExpression ?: return null
        val initializer = propertyInitializer(innermostReceiver) ?: return null
        return extractRegistration(initializer, hops + 1)
    }

    private fun callChain(expression: KtExpression): List<Pair<KtCallExpression, KtExpression?>> {
        val result = mutableListOf<Pair<KtCallExpression, KtExpression?>>()
        var current: KtExpression? = expression
        while (current != null) {
            when (val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(current)) {
                is KtQualifiedExpression -> {
                    (unwrapped.selectorExpression as? KtCallExpression)?.let { selector ->
                        result += selector to unwrapped.receiverExpression
                    }
                    current = unwrapped.receiverExpression
                }
                is KtCallExpression -> {
                    result += unwrapped to null
                    current = null
                }
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
        if (resolved != null) {
            // A resolved callee on a non-registry class (e.g. a user's own
            // `example.ZooKeeperUpdatingListener`) is a negative — do not fall back to text matching.
            return DiscoveryRegistry.fromQualifiedName((resolved as? PsiMethod)?.containingClass?.qualifiedName)
        }
        return registryFromClassName(receiver?.text)
    }

    private fun registryFromClassName(name: String?): DiscoveryRegistry? {
        name ?: return null
        return DiscoveryRegistry.fromQualifiedName(name) ?: DiscoveryRegistry.fromSimpleName(name.substringAfterLast('.'))
    }

    /**
     * Maps arguments to roles via the resolved factory's parameter names (explicit named
     * arguments win, then the resolved parameter name, then — only when the factory cannot
     * be resolved — the positional convention 0 = URI, 1 = service name, 2 = spec). Mirrors
     * the Java collector so non-URI overloads such as `of(SessionProtocol, EndpointGroup)`
     * never surface a misleading URI or name.
     */
    private fun registrationFromArguments(
        registry: DiscoveryRegistry,
        call: KtCallExpression,
        forwardCalls: List<Pair<KtCallExpression, KtExpression?>>,
    ): DiscoveryRegistration {
        val parameterNames =
            (
                (call.calleeExpression as? KtNameReferenceExpression)
                    ?.references
                    ?.firstOrNull()
                    ?.resolve() as? PsiMethod
            )?.parameterList
                ?.parameters
                ?.map { it.name }
        var registryUri = ""
        var serviceName = ""
        var specName: String? = null
        var positionalIndex = 0
        for (argument in call.valueArguments) {
            val argumentExpression = argument.getArgumentExpression() ?: continue
            val parameterName =
                argument.getArgumentName()?.asName?.asString()
                    ?: parameterNames?.getOrNull(positionalIndex).also { positionalIndex++ }
            when {
                parameterName != null ->
                    when (parameterName) {
                        in ArmeriaServerRegistrationSupport.REGISTRY_URI_PARAMETER_NAMES ->
                            registryUri = extractTextLike(argumentExpression).orEmpty()
                        in ArmeriaServerRegistrationSupport.SERVICE_NAME_PARAMETER_NAMES ->
                            serviceName = extractTextLike(argumentExpression).orEmpty()
                        in ArmeriaServerRegistrationSupport.SPEC_PARAMETER_NAMES ->
                            specName = extractTextLike(argumentExpression)
                    }
                parameterNames == null ->
                    when (positionalIndex - 1) {
                        0 -> registryUri = extractTextLike(argumentExpression).orEmpty()
                        1 -> serviceName = extractTextLike(argumentExpression).orEmpty()
                        2 -> specName = extractTextLike(argumentExpression)
                    }
            }
        }
        // Builder-chain setters such as EurekaUpdatingListenerBuilder.appName("...") — the chain is
        // outermost-first so the first match is the last applied setter (last-wins semantics).
        forwardCalls
            .firstOrNull { (forwardCall, _) ->
                ArmeriaKotlinRegistrationChainSupport.resolveCallName(forwardCall) in
                    ArmeriaServerRegistrationSupport.SERVICE_NAME_BUILDER_METHODS
            }?.let { (forwardCall, _) ->
                extractTextLike(forwardCall.valueArguments.firstOrNull()?.getArgumentExpression())?.let { serviceName = it }
            }
        // ZooKeeperRegistrationSpec.curator("name") overrides the znode path as the service name.
        if (registry == DiscoveryRegistry.ZOOKEEPER && specName != null) {
            serviceName = specName
        }
        return DiscoveryRegistration(registry = registry, serviceName = serviceName, registryUri = registryUri)
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
        ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(unwrapped)?.let { return it }
        val nested =
            when (unwrapped) {
                is KtCallExpression -> unwrapped.valueArguments.singleOrNull()?.getArgumentExpression()
                is KtQualifiedExpression ->
                    (unwrapped.selectorExpression as? KtCallExpression)
                        ?.valueArguments
                        ?.singleOrNull()
                        ?.getArgumentExpression()
                else -> null
            } ?: return null
        return extractTextLike(nested, hops + 1)
    }
}
