package com.linecorp.intellij.plugins.armeria.explorer.collector.decorator
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteTargetExtractor
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaDecoratorSupport {
    internal data class DecoratorCandidate(
        val label: String,
        val pathPattern: String?,
    )

    private data class DecoratorInfo(
        val labelKey: String,
        val observabilityKey: String? = null,
    )

    private val KNOWN_DECORATORS =
        mapOf(
            "LoggingService" to DecoratorInfo("route.explorer.decorator.logging", "route.explorer.observability.logging"),
            "CorsService" to DecoratorInfo("route.explorer.decorator.cors"),
            "AuthService" to DecoratorInfo("route.explorer.decorator.auth", "route.explorer.observability.auth"),
            "MetricCollectingService" to DecoratorInfo("route.explorer.decorator.metrics", "route.explorer.observability.metrics"),
            "EncodingService" to DecoratorInfo("route.explorer.decorator.encoding"),
            "DecodingService" to DecoratorInfo("route.explorer.decorator.decoding"),
            "CacheControlDecorator" to DecoratorInfo("route.explorer.decorator.cacheControl"),
            "WebSocketService" to DecoratorInfo("route.explorer.decorator.webSocket"),
            "BraveService" to DecoratorInfo("route.explorer.decorator.brave", "route.explorer.observability.tracing"),
            "ThrottlingService" to DecoratorInfo("route.explorer.decorator.throttling", "route.explorer.observability.throttling"),
            "PrometheusMetricCollectingService" to
                DecoratorInfo(
                    "route.explorer.decorator.prometheus",
                    "route.explorer.observability.prometheus",
                ),
            "Bucket4jService" to DecoratorInfo("route.explorer.decorator.bucket4j", "route.explorer.observability.rateLimit"),
        )

    private val KNOWN_DECORATOR_BUNDLE_KEYS = KNOWN_DECORATORS.mapValues { it.value.labelKey }

    fun observabilitySignals(decoratorLabels: List<String>): List<String> {
        val signals = linkedSetOf<String>()
        for (label in decoratorLabels) {
            val observabilityKey = observabilityKeyForLabel(label) ?: continue
            signals += message(observabilityKey)
        }
        return signals.toList()
    }

    private fun observabilityKeyForLabel(label: String): String? {
        val normalized = label.removeSuffix("::class.java").removeSuffix("::class").removeSuffix(".class")
        val simpleName = normalized.substringAfterLast('.').removeSuffix("()")
        KNOWN_DECORATORS[simpleName]?.observabilityKey?.let { return it }
        for ((_, info) in KNOWN_DECORATORS) {
            val observabilityKey = info.observabilityKey ?: continue
            if (label == message(info.labelKey)) {
                return observabilityKey
            }
        }
        return null
    }

    fun collectProgrammaticDecorators(
        element: PsiMethodCallExpression,
        registrationPath: String,
    ): List<String> {
        val candidates = linkedSetOf<DecoratorCandidate>()
        collectJavaDecoratorsOnBuilderChain(element, candidates)
        return filterDecoratorCandidates(candidates, registrationPath)
    }

    fun labelDecorator(raw: String): String {
        val normalized = raw.removeSuffix("::class.java").removeSuffix("::class").removeSuffix(".class")
        val simpleName = normalized.substringAfterLast('.').removeSuffix("()")
        val bundleKey = KNOWN_DECORATOR_BUNDLE_KEYS[simpleName]
        return if (bundleKey != null) message(bundleKey) else simpleName
    }

    internal fun filterDecoratorCandidates(
        candidates: Collection<DecoratorCandidate>,
        registrationPath: String,
    ): List<String> =
        candidates
            .mapNotNull { candidate ->
                val pathPattern = candidate.pathPattern
                if (pathPattern == null ||
                    ArmeriaRouteSupport.decoratorPathPatternAppliesToRoute(pathPattern, registrationPath)
                ) {
                    candidate.label
                } else {
                    null
                }
            }.distinct()

    private fun collectJavaDecoratorsOnBuilderChain(
        registrationCall: PsiMethodCallExpression,
        candidates: LinkedHashSet<DecoratorCandidate>,
    ) {
        collectJavaDecoratorsFromQualifier(registrationCall.methodExpression.qualifierExpression, candidates)
    }

    private fun collectJavaDecoratorsFromQualifier(
        qualifier: PsiExpression?,
        candidates: LinkedHashSet<DecoratorCandidate>,
    ) {
        var current: PsiExpression? = qualifier
        while (current != null) {
            when (current) {
                is PsiMethodCallExpression -> {
                    if (isArmeriaDecoratorCall(current)) {
                        extractJavaDecoratorCandidate(current)?.let { candidates += it }
                    }
                    current = current.methodExpression.qualifierExpression
                }
                is PsiReferenceExpression -> {
                    val resolved = current.resolve()
                    current =
                        if (resolved is PsiVariable) {
                            resolved.initializer
                        } else {
                            null
                        }
                }
                else -> break
            }
        }
    }

    private fun isArmeriaDecoratorCall(expression: PsiMethodCallExpression): Boolean {
        if (expression.methodExpression.referenceName != "decorator") {
            return false
        }
        return resolvesToArmeriaServerBuilder(expression)
    }

    private fun resolvesToArmeriaServerBuilder(expression: PsiMethodCallExpression): Boolean {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
        if (resolvedClass?.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX) == true) {
            return true
        }
        val qualifierText = expression.methodExpression.qualifierExpression?.text ?: return false
        return ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(qualifierText)
    }

    private fun extractJavaDecoratorCandidate(expression: PsiMethodCallExpression): DecoratorCandidate? {
        val arguments = expression.argumentList.expressions
        val pathPattern =
            if (arguments.size >= 2) {
                extractJavaPathPattern(arguments[0])
            } else {
                null
            }
        val decoratorArgument =
            when {
                arguments.size >= 2 -> arguments[1]
                arguments.isNotEmpty() -> arguments[0]
                else -> return null
            }
        val target =
            when (decoratorArgument) {
                is PsiClassObjectAccessExpression -> decoratorArgument.text
                else -> ArmeriaRouteTargetExtractor.extractTarget(decoratorArgument)
            }
        return DecoratorCandidate(labelDecorator(target), pathPattern)
    }

    private fun extractJavaPathPattern(expression: PsiExpression): String? =
        when (expression) {
            is PsiLiteralExpression -> expression.value as? String
            is PsiReferenceExpression -> {
                when (val resolved = expression.resolve()) {
                    is PsiVariable -> ArmeriaRouteSupport.evaluateJavaStringConstant(resolved)
                    else -> computeJavaPathPatternConstant(expression)
                }
            }
            else -> computeJavaPathPatternConstant(expression)
        }

    private fun computeJavaPathPatternConstant(expression: PsiExpression): String? {
        val constantValue =
            JavaPsiFacade
                .getInstance(expression.project)
                .constantEvaluationHelper
                .computeConstantExpression(expression) as? String
        return constantValue ?: expression.text
            .trim()
            .trim('"')
            .takeIf { it.isNotEmpty() }
    }
}
