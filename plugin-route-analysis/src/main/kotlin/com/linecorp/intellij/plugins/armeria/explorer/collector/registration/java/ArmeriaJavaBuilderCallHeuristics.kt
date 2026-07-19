package com.linecorp.intellij.plugins.armeria.explorer.collector.registration.java
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.linecorp.intellij.plugins.armeria.explorer.model.ServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport

internal object ArmeriaJavaBuilderCallHeuristics {
    fun looksLikeJavaBuilderCall(expression: PsiMethodCallExpression): Boolean {
        if (resolvesToArmeriaServerBuilder(expression)) {
            return true
        }
        val qualifierText = expression.methodExpression.qualifierExpression?.text ?: return false
        return ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(qualifierText) ||
            ArmeriaRouteSupport.looksLikeRouteDecoratorReceiverText(qualifierText)
    }

    fun looksLikeArmeriaFluentRouteBuild(expression: PsiMethodCallExpression): Boolean {
        if (looksLikeJavaBuilderCall(expression)) {
            return true
        }
        var current = javaPreviousMethodCallInChain(expression)
        while (current != null) {
            if (current.methodExpression.referenceName == "route") {
                return looksLikeJavaBuilderCall(current)
            }
            current = javaPreviousMethodCallInChain(current)
        }
        return false
    }

    fun isClearlyNonArmeriaJavaRegistrationCall(expression: PsiMethodCallExpression): Boolean {
        val methodName = expression.methodExpression.referenceName ?: return false
        if (methodName !in ServiceRegistrationMethod.METHOD_NAMES && methodName != "build") {
            return false
        }
        if (looksLikeJavaBuilderCall(expression)) {
            return false
        }
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName ?: return false
        return !resolvedClass.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX)
    }

    private fun resolvesToArmeriaServerBuilder(expression: PsiMethodCallExpression): Boolean {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
        return resolvedClass?.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX) == true
    }

    private fun javaPreviousMethodCallInChain(call: PsiMethodCallExpression): PsiMethodCallExpression? {
        var expression: PsiElement? = call.methodExpression.qualifierExpression
        while (expression != null) {
            when (expression) {
                is PsiMethodCallExpression -> return expression
                is PsiReferenceExpression -> expression = expression.qualifier
                else -> return null
            }
        }
        return null
    }
}
