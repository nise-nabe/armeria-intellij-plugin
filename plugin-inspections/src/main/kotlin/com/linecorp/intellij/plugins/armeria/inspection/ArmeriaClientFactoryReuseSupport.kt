package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientSupport

internal object ArmeriaClientFactoryReuseSupport {
    fun highlight(call: PsiMethodCallExpression): PsiElement? {
        if (!isClientFactoryBuilder(call)) {
            return null
        }
        if (!forwardsToBuild(call)) {
            return null
        }
        val method = PsiTreeUtil.getParentOfType(call, PsiMethod::class.java) ?: return null
        if (!methodBuildsClient(method, call)) {
            return null
        }
        return call.methodExpression.referenceNameElement ?: call
    }

    private fun isClientFactoryBuilder(call: PsiMethodCallExpression): Boolean =
        ArmeriaJavaInspectionCallChains.isCallOnClass(
            call = call,
            methodName = "builder",
            fqcn = ArmeriaProductionChecklist.CLIENT_FACTORY_CLASS,
            simpleName = "ClientFactory",
        )

    private fun forwardsToBuild(call: PsiMethodCallExpression): Boolean {
        if ("build" in ArmeriaJavaInspectionCallChains.forwardChainMethodNames(call)) {
            return true
        }
        val variable = ArmeriaJavaInspectionCallChains.assignedVariable(call) ?: return false
        val method = PsiTreeUtil.getParentOfType(call, PsiMethod::class.java) ?: return false
        return ArmeriaJavaInspectionCallChains.methodCallsOnVariable(variable, method).any { usage ->
            ArmeriaJavaInspectionCallChains.methodName(usage) == "build" ||
                "build" in ArmeriaJavaInspectionCallChains.forwardChainMethodNames(usage)
        }
    }

    private fun methodBuildsClient(
        method: PsiMethod,
        factoryBuilder: PsiMethodCallExpression,
    ): Boolean =
        PsiTreeUtil.findChildrenOfType(method, PsiMethodCallExpression::class.java).any { call ->
            call != factoryBuilder && isClientBuildFactory(call)
        }

    private fun isClientBuildFactory(call: PsiMethodCallExpression): Boolean {
        val name = ArmeriaJavaInspectionCallChains.methodName(call) ?: return false
        if (name !in ArmeriaClientSupport.FACTORY_METHOD_NAMES) {
            return false
        }
        val resolved = ArmeriaJavaInspectionCallChains.resolvedContainingClass(call)
        if (ArmeriaProductionChecklist.isClientFactoryClass(resolved)) {
            return false
        }
        if (ArmeriaProductionChecklist.isArmeriaClientClass(resolved)) {
            return true
        }
        val qualifierName = ArmeriaJavaInspectionCallChains.qualifierSimpleName(call)
        return qualifierName != null && ArmeriaClientSupport.protocolForSimpleName(qualifierName) != null
    }
}
