package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

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

    fun collectDecoratorsInScope(element: PsiElement): List<String> {
        val javaMethod = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        if (javaMethod != null) {
            return collectJavaDecorators(javaMethod)
        }
        return emptyList()
    }

    fun collectKotlinDecoratorsInScope(element: org.jetbrains.kotlin.psi.KtElement): List<String> {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return emptyList()
        return function.bodyExpression?.collectDescendantsOfType<KtCallExpression>()
            ?.mapNotNull { call ->
                val name = call.calleeExpression?.text?.substringAfterLast('.') ?: return@mapNotNull null
                if (name != "decorator") {
                    return@mapNotNull null
                }
                call.valueArguments.firstOrNull()?.getArgumentExpression()?.text?.let(::labelDecorator)
            }.orEmpty()
    }

    fun labelDecorator(raw: String): String {
        val simpleName = raw.substringAfterLast('.').removeSuffix("::class.java").removeSuffix(".class")
        return KNOWN_DECORATOR_LABELS.entries.firstOrNull { simpleName.contains(it.key) }?.value
            ?: simpleName
    }

    private fun collectJavaDecorators(scope: PsiElement): List<String> {
        val decorators = linkedSetOf<String>()
        scope.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                if (expression.methodExpression.referenceName == "decorator") {
                    val argument = expression.argumentList.expressions.firstOrNull() ?: return
                    val target = ArmeriaRouteTargetExtractor.extractTarget(argument)
                    decorators += labelDecorator(target)
                }
                super.visitMethodCallExpression(expression)
            }
        })
        return decorators.toList()
    }
}
