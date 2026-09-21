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
                    parameterNames(expression),
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
     * When the innermost qualifier is a variable (e.g. `lb.build()` where `lb` was assigned a builder
     * chain), hops into that variable's initializer.
     */
    private fun findListenerFactoryCall(start: PsiMethodCallExpression): PsiMethodCallExpression? {
        var current: PsiMethodCallExpression? = start
        var variableHops = 0
        while (current != null && variableHops <= MAX_INITIALIZER_HOPS) {
            if (current.methodExpression.referenceName in ArmeriaServerRegistrationSupport.LISTENER_FACTORY_METHODS &&
                listenerRegistry(current) != null
            ) {
                return current
            }
            val previous = ArmeriaJavaRegistrationChainSupport.previousMethodCallInChain(current)
            if (previous != null) {
                current = previous
            } else {
                current = qualifierVariableInitializer(current)?.also { variableHops++ }
            }
        }
        return null
    }

    private fun qualifierVariableInitializer(call: PsiMethodCallExpression): PsiMethodCallExpression? {
        val qualifier = call.methodExpression.qualifierExpression as? PsiReferenceExpression ?: return null
        return (qualifier.resolve() as? PsiVariable)?.initializer as? PsiMethodCallExpression
    }

    private fun listenerRegistry(factoryCall: PsiMethodCallExpression): DiscoveryRegistry? {
        val resolvedClass = factoryCall.resolveMethod()?.containingClass?.qualifiedName
        if (resolvedClass != null) {
            // A resolved method on a non-registry class (e.g. a user's own
            // `example.ZooKeeperUpdatingListener`) is a negative — do not fall back to text matching.
            return DiscoveryRegistry.fromQualifiedName(resolvedClass)
        }
        val qualifierText = factoryCall.methodExpression.qualifierExpression?.text ?: return null
        return registryFromClassName(qualifierText)
    }

    private fun registryFromClassName(name: String?): DiscoveryRegistry? {
        name ?: return null
        return DiscoveryRegistry.fromQualifiedName(name) ?: DiscoveryRegistry.fromSimpleName(name.substringAfterLast('.'))
    }

    private fun registrationFromFactoryCall(call: PsiMethodCallExpression): DiscoveryRegistration? {
        val registry = listenerRegistry(call) ?: return null
        val registration =
            registrationFromArguments(registry, call.argumentList.expressions.toList(), parameterNames(call))
        // Builder-chain setters such as EurekaUpdatingListenerBuilder.appName("...").
        chainedServiceName(call)?.let { return registration.copy(serviceName = it) }
        return registration
    }

    /**
     * Maps arguments to roles via the resolved factory's parameter names
     * (`zkConnectionStr` / `eurekaUri` / `consulUri` → registry URI, `znodePath` /
     * `appName` / `serviceName` → service name, `spec` → registration spec). Falls back
     * to the positional convention (0 = URI, 1 = service name, 2 = spec) only when the
     * factory cannot be resolved — so non-URI overloads such as
     * `of(SessionProtocol, EndpointGroup)` never surface a misleading URI or name.
     */
    private fun registrationFromArguments(
        registry: DiscoveryRegistry,
        arguments: List<PsiExpression>,
        parameterNames: List<String>?,
    ): DiscoveryRegistration {
        var registryUri = ""
        var serviceName = ""
        var specName: String? = null
        arguments.forEachIndexed { index, argument ->
            val parameterName = parameterNames?.getOrNull(index)
            when {
                parameterName != null ->
                    when (parameterName) {
                        in ArmeriaServerRegistrationSupport.REGISTRY_URI_PARAMETER_NAMES ->
                            registryUri = extractTextLike(argument).orEmpty()
                        in ArmeriaServerRegistrationSupport.SERVICE_NAME_PARAMETER_NAMES ->
                            serviceName = extractTextLike(argument).orEmpty()
                        in ArmeriaServerRegistrationSupport.SPEC_PARAMETER_NAMES ->
                            specName = extractTextLike(argument)
                    }
                parameterNames == null ->
                    when (index) {
                        0 -> registryUri = extractTextLike(argument).orEmpty()
                        1 -> serviceName = extractTextLike(argument).orEmpty()
                        2 -> specName = extractTextLike(argument)
                    }
            }
        }
        // ZooKeeperRegistrationSpec.curator("name") overrides the znode path as the service name.
        if (registry == DiscoveryRegistry.ZOOKEEPER && specName != null) {
            serviceName = specName
        }
        return DiscoveryRegistration(registry = registry, serviceName = serviceName, registryUri = registryUri)
    }

    private fun parameterNames(call: PsiMethodCallExpression): List<String>? =
        call
            .resolveMethod()
            ?.parameterList
            ?.parameters
            ?.map { it.name }

    private fun parameterNames(expression: PsiNewExpression): List<String>? =
        expression
            .resolveMethod()
            ?.parameterList
            ?.parameters
            ?.map { it.name }

    private fun chainedServiceName(factoryCall: PsiMethodCallExpression): String? {
        var serviceName: String? = null
        var current: PsiMethodCallExpression? =
            ArmeriaJavaRegistrationChainSupport.findImmediateNextChainedCall(factoryCall)
        while (current != null) {
            if (current.methodExpression.referenceName in ArmeriaServerRegistrationSupport.SERVICE_NAME_BUILDER_METHODS) {
                serviceName = extractTextLike(current.argumentList.expressions.firstOrNull())
            }
            current = ArmeriaJavaRegistrationChainSupport.findImmediateNextChainedCall(current)
        }
        return serviceName
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
            } ?: return null
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
