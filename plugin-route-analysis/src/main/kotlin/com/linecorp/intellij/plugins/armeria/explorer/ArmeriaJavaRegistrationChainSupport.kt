package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression

internal object ArmeriaJavaRegistrationChainSupport {

    fun registrationKey(expression: PsiMethodCallExpression): String? {
        val virtualFile = expression.containingFile?.virtualFile ?: return null
        val methodName = expression.methodExpression.referenceName ?: return null
        return ArmeriaRouteSupport.registrationKey(
            virtualFile.path,
            expression.textRange,
            methodName,
        )
    }

    fun extractString(expression: PsiExpression?): String? =
        ArmeriaRouteSupport.extractJavaStringConstant(expression)

    fun toChainStep(call: PsiMethodCallExpression): RegistrationChainStep {
        return RegistrationChainStep(
            methodName = call.methodExpression.referenceName.orEmpty(),
            firstStringArg = extractString(call.argumentList.expressions.firstOrNull()),
            rawMethodArgs = call.argumentList.expressions.map { it.text },
        )
    }

    fun methodCallsBetweenInStatement(
        start: PsiMethodCallExpression,
        stopExclusive: PsiMethodCallExpression?,
    ): List<PsiMethodCallExpression> {
        val calls = mutableListOf(start)
        var current = start
        while (true) {
            val next = findImmediateNextChainedCall(current) ?: break
            if (next === stopExclusive) {
                break
            }
            calls += next
            current = next
        }
        return calls
    }

    fun previousMethodCallInChain(call: PsiMethodCallExpression): PsiMethodCallExpression? {
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

    fun findForwardChainedCall(
        start: PsiMethodCallExpression,
        predicate: (PsiMethodCallExpression) -> Boolean,
    ): PsiMethodCallExpression? {
        var current: PsiMethodCallExpression? = start
        while (current != null) {
            if (predicate(current)) {
                return current
            }
            current = findNextChainedMethodCall(current)
        }
        return null
    }

    fun findImmediateNextChainedCall(call: PsiMethodCallExpression): PsiMethodCallExpression? {
        var element: PsiElement? = call
        while (element != null) {
            val parent = element.parent ?: return null
            when (parent) {
                is PsiReferenceExpression -> {
                    val grandParent = parent.parent
                    if (grandParent is PsiMethodCallExpression &&
                        grandParent.methodExpression == parent &&
                        grandParent !== call
                    ) {
                        return grandParent
                    }
                }
                is PsiMethodCallExpression -> {
                    if (parent.methodExpression.qualifierExpression == element && parent !== call) {
                        return parent
                    }
                }
            }
            element = parent
        }
        return null
    }

    fun findNextChainedMethodCall(call: PsiMethodCallExpression): PsiMethodCallExpression? {
        var element: PsiElement? = call
        while (element != null) {
            val parent = element.parent ?: return null
            when (parent) {
                is PsiMethodCallExpression -> {
                    if (parent.methodExpression.qualifierExpression == element ||
                        (parent.methodExpression.qualifierExpression as? PsiReferenceExpression)?.qualifier == element
                    ) {
                        return parent
                    }
                }
                is PsiReferenceExpression -> {
                    val grandParent = parent.parent
                    if (grandParent is PsiMethodCallExpression &&
                        (grandParent.methodExpression.qualifierExpression == parent ||
                            (grandParent.methodExpression.qualifierExpression as? PsiReferenceExpression)?.qualifier == parent)
                    ) {
                        return grandParent
                    }
                }
            }
            element = parent
        }
        return null
    }
}
