package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiResourceVariable
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil

internal object ArmeriaJavaInspectionCallChains {
    fun methodName(call: PsiMethodCallExpression): String? = call.methodExpression.referenceName

    fun unwrap(expression: PsiExpression): PsiExpression {
        var current = expression
        while (true) {
            current =
                when (current) {
                    is PsiParenthesizedExpression -> current.expression ?: return current
                    is PsiTypeCastExpression -> current.operand ?: return current
                    else -> return current
                }
        }
    }

    fun unwrapOrNull(expression: PsiExpression?): PsiExpression? = expression?.let(::unwrap)

    fun enclosingQualifierCall(expression: PsiExpression): PsiMethodCallExpression? {
        var element: PsiElement? = expression.parent
        while (element != null) {
            if (element is PsiParenthesizedExpression || element is PsiTypeCastExpression) {
                element = element.parent
                continue
            }
            if (element is PsiMethodCallExpression) {
                val qualifier = unwrapOrNull(element.methodExpression.qualifierExpression)
                if (qualifier == unwrap(expression)) {
                    return element
                }
            }
            element = element.parent
        }
        return null
    }

    fun outermostCall(call: PsiMethodCallExpression): PsiMethodCallExpression {
        var current = call
        while (true) {
            current = enclosingQualifierCall(current) ?: return current
        }
    }

    fun forwardChainMethodNames(call: PsiMethodCallExpression): Set<String> {
        val names = mutableSetOf<String>()
        var cursor: PsiMethodCallExpression = call
        while (true) {
            val parent = enclosingQualifierCall(cursor) ?: break
            methodName(parent)?.let { names += it }
            cursor = parent
        }
        return names
    }

    fun assignedVariable(call: PsiMethodCallExpression): PsiVariable? {
        val outermost = outermostCall(call)
        var parent: PsiElement? = outermost.parent
        while (parent is PsiParenthesizedExpression || parent is PsiTypeCastExpression) {
            parent = parent.parent
        }
        return when (parent) {
            is PsiVariable -> parent
            is PsiAssignmentExpression ->
                (parent.lExpression as? PsiReferenceExpression)?.resolve() as? PsiVariable
            else -> null
        }
    }

    fun methodCallsOnVariable(
        variable: PsiVariable,
        scope: PsiElement,
    ): Sequence<PsiMethodCallExpression> =
        PsiTreeUtil
            .findChildrenOfType(scope, PsiMethodCallExpression::class.java)
            .asSequence()
            .filter { call -> qualifierResolvesTo(call, variable) }

    fun qualifierResolvesTo(
        call: PsiMethodCallExpression,
        variable: PsiVariable,
    ): Boolean {
        var current: PsiExpression? = unwrapOrNull(call.methodExpression.qualifierExpression)
        while (current != null) {
            when (current) {
                is PsiReferenceExpression -> return current.resolve() == variable
                is PsiMethodCallExpression ->
                    current = unwrapOrNull(current.methodExpression.qualifierExpression)
                else -> return false
            }
        }
        return false
    }

    fun qualifierSimpleName(call: PsiMethodCallExpression): String? {
        val qualifier = unwrapOrNull(call.methodExpression.qualifierExpression) ?: return null
        return when (qualifier) {
            is PsiReferenceExpression -> qualifier.referenceName
            is PsiMethodCallExpression -> qualifier.methodExpression.referenceName
            else -> qualifier.text.substringAfterLast('.').substringBefore('(')
        }
    }

    fun resolvedContainingClass(call: PsiMethodCallExpression): String? = call.resolveMethod()?.containingClass?.qualifiedName

    fun qualifierTypeName(call: PsiMethodCallExpression): String? =
        unwrapOrNull(call.methodExpression.qualifierExpression)?.type?.canonicalText

    fun isCallOnClass(
        call: PsiMethodCallExpression,
        methodName: String,
        fqcn: String,
        simpleName: String,
    ): Boolean {
        if (this.methodName(call) != methodName) {
            return false
        }
        val resolved = resolvedContainingClass(call)
        if (resolved == fqcn) {
            return true
        }
        val typeName = qualifierTypeName(call)
        if (typeName == fqcn) {
            return true
        }
        val qualifier = unwrapOrNull(call.methodExpression.qualifierExpression) as? PsiReferenceExpression ?: return false
        return qualifier.qualifiedName == fqcn || qualifier.referenceName == simpleName
    }

    fun decoratorClassSimpleName(expression: PsiExpression): String {
        var current: PsiExpression = unwrap(expression)
        while (current is PsiMethodCallExpression) {
            current = unwrapOrNull(current.methodExpression.qualifierExpression) ?: break
        }
        return when (current) {
            is PsiReferenceExpression -> current.referenceName.orEmpty()
            is PsiMethodCallExpression -> current.methodExpression.referenceName.orEmpty()
            else -> current.text.substringAfterLast('.').substringBefore('(')
        }
    }

    fun containingClass(element: PsiElement): PsiClass? = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)

    fun isField(variable: PsiVariable): Boolean = variable is PsiField

    fun isResourceVariable(variable: PsiVariable): Boolean = variable is PsiResourceVariable
}
