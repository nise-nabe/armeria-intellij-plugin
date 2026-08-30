package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport

internal object ArmeriaServerLimitsSupport {
    fun highlight(call: PsiMethodCallExpression): PsiElement? {
        if (!isServerBuilder(call)) {
            return null
        }
        val present = collectBuilderMethodNames(call)
        if (ArmeriaProductionChecklist.missingServerLimits(present).isEmpty()) {
            return null
        }
        return call.methodExpression.referenceNameElement ?: call
    }

    fun missingLimits(call: PsiMethodCallExpression): List<String> {
        if (!isServerBuilder(call)) {
            return emptyList()
        }
        return ArmeriaProductionChecklist.missingServerLimits(collectBuilderMethodNames(call))
    }

    private fun isServerBuilder(call: PsiMethodCallExpression): Boolean =
        ArmeriaJavaInspectionCallChains.isCallOnClass(
            call = call,
            methodName = "builder",
            fqcn = ArmeriaRouteSupport.ARMERIA_SERVER_CLASS,
            simpleName = "Server",
        )

    private fun collectBuilderMethodNames(call: PsiMethodCallExpression): Set<String> {
        val names = ArmeriaJavaInspectionCallChains.forwardChainMethodNames(call).toMutableSet()
        val variable = ArmeriaJavaInspectionCallChains.assignedVariable(call)
        val scope =
            when {
                variable != null && ArmeriaJavaInspectionCallChains.isField(variable) ->
                    ArmeriaJavaInspectionCallChains.containingClass(variable) ?: call.containingFile
                else ->
                    PsiTreeUtil.getParentOfType(call, PsiMethod::class.java) ?: call.containingFile
            }
        if (variable != null) {
            for (usage in ArmeriaJavaInspectionCallChains.methodCallsOnVariable(variable, scope)) {
                ArmeriaJavaInspectionCallChains.methodName(usage)?.let { names += it }
                names += ArmeriaJavaInspectionCallChains.forwardChainMethodNames(usage)
            }
        }
        return names
    }
}
