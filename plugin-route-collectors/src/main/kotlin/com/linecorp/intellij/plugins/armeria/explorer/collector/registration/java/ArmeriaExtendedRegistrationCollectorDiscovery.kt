package com.linecorp.intellij.plugins.armeria.explorer.collector.registration.java

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaServerRegistrationSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaServerRegistrationSupport.DiscoveryRegistration
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaServerRegistrationSupport.DiscoveryRegistry
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute

/**
 * Collects server-side service-registry registrations attached to `ServerBuilder` via
 * `serverListener(...)` — `ZooKeeperUpdatingListener`, `EurekaUpdatingListener`,
 * and `ConsulUpdatingListener` (`builder(...)` / `of(...)` factories).
 *
 * Each registry is gated on its optional Armeria integration being on the project classpath
 * (`armeria-zookeeper`, `armeria-eureka`, `armeria-consul`); registry URIs such as `zk://`
 * are kept out of `path` so they never surface as HTTP routes.
 */
internal object ArmeriaExtendedRegistrationCollectorDiscovery {
    private const val MAX_INITIALIZER_HOPS = 4

    fun collect(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val methodName = expression.methodExpression.referenceName ?: return
        if (methodName != ArmeriaServerRegistrationSupport.SERVER_LISTENER_METHOD) {
            return
        }
        if (!ArmeriaBuilderCallHeuristics.looksLikeJavaBuilderCall(expression)) {
            return
        }
        val listenerArgument = expression.argumentList.expressions.firstOrNull() ?: return
        val registration = extractRegistration(listenerArgument) ?: return
        if (!isRegistryOnClasspath(expression, registration.registry)) {
            return
        }
        val key = ArmeriaJavaRegistrationChainSupport.registrationKey(expression) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        routes += ArmeriaServerRegistrationSupport.discoveryRoute(expression, registration)
    }

    private fun isRegistryOnClasspath(
        element: PsiExpression,
        registry: DiscoveryRegistry,
    ): Boolean =
        JavaPsiFacade
            .getInstance(element.project)
            .findClass(registry.listenerQualifiedName, element.resolveScope) != null

    private fun extractRegistration(
        expression: PsiExpression?,
        hops: Int = 0,
    ): DiscoveryRegistration? {
        if (expression == null || hops > MAX_INITIALIZER_HOPS) {
            return null
        }
        return when (expression) {
            is PsiMethodCallExpression -> {
                val factoryCall = findListenerFactoryCall(expression) ?: return null
                registrationFromFactoryCall(factoryCall)
            }
            is PsiNewExpression -> {
                val registry =
                    registryFromClassName(expression.classReference?.qualifiedName ?: expression.classReference?.referenceName)
                        ?: return null
                registrationFromArguments(
                    registry,
                    expression.argumentList
                        ?.expressions
                        ?.toList()
                        .orEmpty(),
                )
            }
            is PsiParenthesizedExpression -> extractRegistration(expression.expression, hops + 1)
            is PsiReferenceExpression -> {
                val initializer = (expression.resolve() as? PsiVariable)?.initializer ?: return null
                extractRegistration(initializer, hops + 1)
            }
            else -> null
        }
    }

    /**
     * Walks a listener-builder call chain backwards (e.g. `X.builder(...).sessionTimeout(...).build()`)
     * until the `X.builder(...)` / `X.of(...)` factory call on a known updating-listener class.
     */
    private fun findListenerFactoryCall(start: PsiMethodCallExpression): PsiMethodCallExpression? {
        var current: PsiMethodCallExpression? = start
        while (current != null) {
            if (current.methodExpression.referenceName in ArmeriaServerRegistrationSupport.LISTENER_FACTORY_METHODS &&
                listenerRegistry(current) != null
            ) {
                return current
            }
            current = ArmeriaJavaRegistrationChainSupport.previousMethodCallInChain(current)
        }
        return null
    }

    private fun listenerRegistry(factoryCall: PsiMethodCallExpression): DiscoveryRegistry? {
        val resolvedClass = factoryCall.resolveMethod()?.containingClass?.qualifiedName
        DiscoveryRegistry.fromQualifiedName(resolvedClass)?.let { return it }
        val qualifierText = factoryCall.methodExpression.qualifierExpression?.text ?: return null
        return registryFromClassName(qualifierText)
    }

    private fun registryFromClassName(name: String?): DiscoveryRegistry? {
        name ?: return null
        return DiscoveryRegistry.fromQualifiedName(name) ?: DiscoveryRegistry.fromSimpleName(name.substringAfterLast('.'))
    }

    private fun registrationFromFactoryCall(call: PsiMethodCallExpression): DiscoveryRegistration? {
        val registry = listenerRegistry(call) ?: return null
        val arguments = call.argumentList.expressions.toList()
        val registryUri = extractTextLike(arguments.getOrNull(0)).orEmpty()
        var serviceName = extractTextLike(arguments.getOrNull(1)).orEmpty()
        // Builder-chain setters such as EurekaUpdatingListenerBuilder.appName("...").
        chainedServiceName(call)?.let { serviceName = it }
        // ZooKeeperRegistrationSpec.curator("name") overrides the znode path as the service name.
        extractTextLike(arguments.getOrNull(2))?.let { specName ->
            if (registry == DiscoveryRegistry.ZOOKEEPER) {
                serviceName = specName
            }
        }
        return DiscoveryRegistration(registry = registry, serviceName = serviceName, registryUri = registryUri)
    }

    private fun registrationFromArguments(
        registry: DiscoveryRegistry,
        arguments: List<PsiExpression>,
    ): DiscoveryRegistration {
        val registryUri = extractTextLike(arguments.getOrNull(0)).orEmpty()
        var serviceName = extractTextLike(arguments.getOrNull(1)).orEmpty()
        // ZooKeeperRegistrationSpec.curator("name") overrides the znode path as the service name.
        extractTextLike(arguments.getOrNull(2))?.let { specName ->
            if (registry == DiscoveryRegistry.ZOOKEEPER) {
                serviceName = specName
            }
        }
        return DiscoveryRegistration(registry = registry, serviceName = serviceName, registryUri = registryUri)
    }

    private fun chainedServiceName(factoryCall: PsiMethodCallExpression): String? {
        var current: PsiMethodCallExpression? =
            ArmeriaJavaRegistrationChainSupport.findImmediateNextChainedCall(factoryCall)
        while (current != null) {
            if (current.methodExpression.referenceName in ArmeriaServerRegistrationSupport.SERVICE_NAME_BUILDER_METHODS) {
                return extractTextLike(current.argumentList.expressions.firstOrNull())
            }
            current = ArmeriaJavaRegistrationChainSupport.findImmediateNextChainedCall(current)
        }
        return null
    }

    /**
     * Resolves a constant string; unwraps single-argument factories such as `URI.create("...")` /
     * `ZooKeeperRegistrationSpec.curator("...")`; falls back to the expression text, matching the
     * unresolved-value convention used by path extraction.
     */
    private fun extractTextLike(
        expression: PsiExpression?,
        hops: Int = 0,
    ): String? {
        if (expression == null || hops > MAX_INITIALIZER_HOPS) {
            return null
        }
        extractConstantString(expression)?.let { return it }
        val nested =
            when (expression) {
                is PsiMethodCallExpression -> expression.argumentList.expressions.singleOrNull()
                is PsiNewExpression -> expression.argumentList?.expressions?.singleOrNull()
                is PsiParenthesizedExpression -> expression.expression
                is PsiReferenceExpression -> (expression.resolve() as? PsiVariable)?.initializer
                else -> null
            } ?: return expression.text.takeIf { it.isNotBlank() }
        return extractTextLike(nested, hops + 1)
    }

    private fun extractConstantString(expression: PsiExpression): String? {
        if (expression is PsiLiteralExpression) {
            (expression.value as? String)?.let { return it }
        }
        return JavaPsiFacade
            .getInstance(expression.project)
            .constantEvaluationHelper
            .computeConstantExpression(expression) as? String
    }
}
