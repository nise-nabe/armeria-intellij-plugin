package com.linecorp.intellij.plugins.armeria.explorer.collector.annotation
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaTimeoutSupport {
    fun collectExecutionHints(method: PsiMethod): List<String> {
        val methodHint = executionHint(method)
        if (methodHint != null) {
            return listOf(methodHint)
        }
        val containingClass = method.containingClass ?: return emptyList()
        return executionHint(containingClass)?.let(::listOf) ?: emptyList()
    }

    fun collectBuilderTimeoutHints(registrationCall: PsiMethodCallExpression): List<String> {
        val hints = mutableListOf<String>()
        collectTimeoutCallsFromQualifier(registrationCall.methodExpression.qualifierExpression, hints)
        return hints.distinct()
    }

    private fun executionHint(element: PsiClass): String? =
        when {
            element.hasAnnotation(ArmeriaRouteSupport.BLOCKING_ANNOTATION) ->
                message("route.explorer.execution.blocking")
            element.hasAnnotation(ArmeriaRouteSupport.NON_BLOCKING_ANNOTATION) ->
                message("route.explorer.execution.nonBlocking")
            else -> null
        }

    private fun executionHint(method: PsiMethod): String? =
        when {
            method.hasAnnotation(ArmeriaRouteSupport.BLOCKING_ANNOTATION) ->
                message("route.explorer.execution.blocking")
            method.hasAnnotation(ArmeriaRouteSupport.NON_BLOCKING_ANNOTATION) ->
                message("route.explorer.execution.nonBlocking")
            else -> null
        }

    private fun collectTimeoutCallsFromQualifier(
        qualifier: PsiExpression?,
        hints: MutableList<String>,
    ) {
        var current: PsiExpression? = qualifier
        while (current != null) {
            current = unwrapQualifierExpression(current)
            when (current) {
                is PsiMethodCallExpression -> {
                    when (current.methodExpression.referenceName) {
                        "requestTimeout" ->
                            if (resolvesToArmeriaServerBuilder(current)) {
                                hints += formatTimeoutCall("route.explorer.timeout.request", current)
                            }
                        "responseTimeout" ->
                            if (resolvesToArmeriaServerBuilder(current)) {
                                hints += formatTimeoutCall("route.explorer.timeout.response", current)
                            }
                        "idleTimeout" ->
                            if (resolvesToArmeriaServerBuilder(current)) {
                                hints += formatTimeoutCall("route.explorer.timeout.idle", current)
                            }
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

    private fun resolvesToArmeriaServerBuilder(expression: PsiMethodCallExpression): Boolean {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
        if (resolvedClass?.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX) == true) {
            return true
        }
        val qualifierText = expression.methodExpression.qualifierExpression?.text ?: return false
        return ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(qualifierText)
    }

    private fun formatTimeoutCall(
        bundleKey: String,
        call: PsiMethodCallExpression,
    ): String {
        val argument = call.argumentList.expressions.firstOrNull()
        val value =
            when (argument) {
                null -> "…"
                else ->
                    JavaPsiFacade
                        .getInstance(call.project)
                        .constantEvaluationHelper
                        .computeConstantExpression(argument)
                        ?.toString()
                        ?: argument.text
            }
        return message(bundleKey, value)
    }

    private fun unwrapQualifierExpression(expression: PsiExpression): PsiExpression {
        var current = expression
        while (true) {
            current =
                when (current) {
                    is PsiParenthesizedExpression -> current.expression ?: return expression
                    is PsiTypeCastExpression -> current.operand ?: return expression
                    else -> return current
                }
        }
    }
}
