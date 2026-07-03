package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable

internal object ArmeriaDecoratorSupport {
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

    fun collectProgrammaticDecorators(element: PsiMethodCallExpression): List<String> {
        return collectJavaDecoratorsOnBuilderChain(element)
    }

    fun labelDecorator(raw: String): String {
        val normalized = raw.removeSuffix("::class.java").removeSuffix(".class")
        val simpleName = normalized.substringAfterLast('.')
        return KNOWN_DECORATOR_LABELS.entries.firstOrNull { (key, _) -> simpleName == key || simpleName.endsWith(key) }?.value
            ?: simpleName
    }

    private fun collectJavaDecoratorsOnBuilderChain(registrationCall: PsiMethodCallExpression): List<String> {
        val decorators = linkedSetOf<String>()
        collectJavaDecoratorsFromQualifier(registrationCall.methodExpression.qualifierExpression, decorators)
        return decorators.toList()
    }

    private fun collectJavaDecoratorsFromQualifier(
        qualifier: PsiExpression?,
        decorators: LinkedHashSet<String>,
    ) {
        var current: PsiExpression? = qualifier
        while (current != null) {
            when (current) {
                is PsiMethodCallExpression -> {
                    if (isArmeriaDecoratorCall(current)) {
                        extractJavaDecoratorLabel(current)?.let { decorators += it }
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

    private fun extractJavaDecoratorLabel(expression: PsiMethodCallExpression): String? {
        val arguments = expression.argumentList.expressions
        val decoratorArgument = when {
            arguments.size >= 2 -> arguments[1]
            arguments.isNotEmpty() -> arguments[0]
            else -> return null
        }
        val target = when (decoratorArgument) {
            is PsiClassObjectAccessExpression -> decoratorArgument.text
            else -> ArmeriaRouteTargetExtractor.extractTarget(decoratorArgument)
        }
        return labelDecorator(target)
    }
}
