package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil

internal object ArmeriaEndpointGroupCloseSupport {
    fun highlight(call: PsiMethodCallExpression): PsiElement? {
        if (!isDynamicEndpointGroupConstruction(call)) {
            return null
        }
        if (isReceiverOfUseOrClose(call)) {
            return null
        }
        val variable = ArmeriaJavaInspectionCallChains.assignedVariable(call) ?: return null
        if (ArmeriaJavaInspectionCallChains.isResourceVariable(variable)) {
            return null
        }
        val scope = closeSearchScope(variable)
        if (isClosed(variable, scope)) {
            return null
        }
        return call.methodExpression.referenceNameElement ?: call
    }

    private fun isDynamicEndpointGroupConstruction(call: PsiMethodCallExpression): Boolean {
        val name = ArmeriaJavaInspectionCallChains.methodName(call) ?: return false
        if (name != "of" && name != "build") {
            return false
        }
        val resolved = ArmeriaJavaInspectionCallChains.resolvedContainingClass(call)
        if (resolved != null && ArmeriaProductionChecklist.isDynamicEndpointGroup(resolved.substringAfterLast('.'))) {
            return true
        }
        val qualifierName = constructionTypeSimpleName(call) ?: return false
        return ArmeriaProductionChecklist.isDynamicEndpointGroup(qualifierName)
    }

    private fun constructionTypeSimpleName(call: PsiMethodCallExpression): String? {
        if (ArmeriaJavaInspectionCallChains.methodName(call) == "of") {
            return ArmeriaJavaInspectionCallChains.qualifierSimpleName(call)
        }
        val qualifier = ArmeriaJavaInspectionCallChains.unwrapOrNull(call.methodExpression.qualifierExpression)
        val builderCall = qualifier as? PsiMethodCallExpression
        if (builderCall != null && ArmeriaJavaInspectionCallChains.methodName(builderCall) == "builder") {
            return ArmeriaJavaInspectionCallChains.qualifierSimpleName(builderCall)
        }
        val typeName = ArmeriaJavaInspectionCallChains.qualifierTypeName(call) ?: return null
        return typeName.substringAfterLast('.')
    }

    private fun isReceiverOfUseOrClose(call: PsiMethodCallExpression): Boolean {
        val parent = ArmeriaJavaInspectionCallChains.enclosingQualifierCall(call) ?: return false
        val name = ArmeriaJavaInspectionCallChains.methodName(parent)
        return name == "close" || name == "closeAsync" || name == "use"
    }

    private fun closeSearchScope(variable: PsiVariable): PsiElement =
        if (ArmeriaJavaInspectionCallChains.isField(variable)) {
            ArmeriaJavaInspectionCallChains.containingClass(variable) ?: variable.containingFile
        } else {
            variable.containingFile
        }

    private fun isClosed(
        variable: PsiVariable,
        scope: PsiElement,
    ): Boolean =
        PsiTreeUtil.findChildrenOfType(scope, PsiMethodCallExpression::class.java).any { call ->
            val name = ArmeriaJavaInspectionCallChains.methodName(call)
            if (name != "close" && name != "closeAsync" && name != "use") {
                return@any false
            }
            qualifierResolvesToVariable(call, variable)
        }

    private fun qualifierResolvesToVariable(
        call: PsiMethodCallExpression,
        variable: PsiVariable,
    ): Boolean {
        val qualifier = ArmeriaJavaInspectionCallChains.unwrapOrNull(call.methodExpression.qualifierExpression)
        return when (qualifier) {
            is PsiReferenceExpression -> qualifier.resolve() == variable
            is PsiMethodCallExpression -> ArmeriaJavaInspectionCallChains.qualifierResolvesTo(qualifier, variable)
            else -> false
        }
    }
}
