package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.JavaPsiFacade

internal object ArmeriaDecoratorSupport {
    internal data class DecoratorCandidate(val label: String, val pathPattern: String?)

    private val KNOWN_DECORATOR_LABELS = mapOf(
        "LoggingService" to "Logging",
        "CorsService" to "CORS",
        "AuthService" to "Auth",
        "MetricCollectingService" to "Metrics",
        "EncodingService" to "Encoding",
        "DecodingService" to "Decoding",
        "CacheControlDecorator" to "Cache-Control",
        "WebSocketService" to "WebSocket",
    )

    fun collectProgrammaticDecorators(element: PsiMethodCallExpression, registrationPath: String): List<String> {
        val candidates = linkedSetOf<DecoratorCandidate>()
        collectJavaDecoratorsOnBuilderChain(element, candidates)
        return filterDecoratorCandidates(candidates, registrationPath)
    }

    fun labelDecorator(raw: String): String {
        val normalized = raw.removeSuffix("::class.java").removeSuffix("::class").removeSuffix(".class")
        val simpleName = normalized.substringAfterLast('.')
        return KNOWN_DECORATOR_LABELS.entries.firstOrNull { (key, _) -> simpleName == key || simpleName.endsWith(key) }?.value
            ?: simpleName
    }

    internal fun filterDecoratorCandidates(
        candidates: Collection<DecoratorCandidate>,
        registrationPath: String,
    ): List<String> {
        return candidates.mapNotNull { candidate ->
            val pathPattern = candidate.pathPattern
            if (pathPattern == null ||
                ArmeriaRouteSupport.decoratorPathPatternAppliesToRoute(pathPattern, registrationPath)
            ) {
                candidate.label
            } else {
                null
            }
        }.distinct()
    }

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
                    current = if (resolved is PsiVariable) {
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
        val pathPattern = if (arguments.size >= 2) {
            extractJavaPathPattern(arguments[0])
        } else {
            null
        }
        val decoratorArgument = when {
            arguments.size >= 2 -> arguments[1]
            arguments.isNotEmpty() -> arguments[0]
            else -> return null
        }
        val target = when (decoratorArgument) {
            is PsiClassObjectAccessExpression -> decoratorArgument.text
            else -> ArmeriaRouteTargetExtractor.extractTarget(decoratorArgument)
        }
        return DecoratorCandidate(labelDecorator(target), pathPattern)
    }

    private fun extractJavaPathPattern(expression: PsiExpression): String? {
        return when (expression) {
            is PsiLiteralExpression -> expression.value as? String
            is PsiReferenceExpression -> {
                when (val resolved = expression.resolve()) {
                    is PsiVariable -> ArmeriaRouteSupport.evaluateJavaStringConstant(resolved)
                    else -> computeJavaPathPatternConstant(expression)
                }
            }
            else -> computeJavaPathPatternConstant(expression)
        }
    }

    private fun computeJavaPathPatternConstant(expression: PsiExpression): String? {
        val constantValue = JavaPsiFacade.getInstance(expression.project)
            .constantEvaluationHelper
            .computeConstantExpression(expression) as? String
        return constantValue ?: expression.text.trim().trim('"').takeIf { it.isNotEmpty() }
    }
}
