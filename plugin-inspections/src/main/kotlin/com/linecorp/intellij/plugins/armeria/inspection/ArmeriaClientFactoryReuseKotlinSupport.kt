package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientSupport
import org.jetbrains.kotlin.psi.KtCallExpression

internal object ArmeriaClientFactoryReuseKotlinSupport {
    fun highlight(call: KtCallExpression): PsiElement? {
        if (!isClientFactoryBuilder(call)) {
            return null
        }
        if (!forwardsToBuild(call)) {
            return null
        }
        val callable = ArmeriaKotlinInspectionCallChains.containingCallable(call) ?: return null
        if (!callableBuildsClient(callable, call)) {
            return null
        }
        return ArmeriaKotlinInspectionCallChains.highlightCallName(call)
    }

    private fun isClientFactoryBuilder(call: KtCallExpression): Boolean =
        ArmeriaKotlinInspectionCallChains.isCallOnClass(
            call = call,
            methodName = "builder",
            fqcn = ArmeriaProductionChecklist.CLIENT_FACTORY_CLASS,
            simpleName = "ClientFactory",
        )

    private fun forwardsToBuild(call: KtCallExpression): Boolean {
        if (ArmeriaKotlinInspectionCallChains.forwardChainCalls(call).any {
                ArmeriaKotlinInspectionCallChains.callName(it) == "build"
            }
        ) {
            return true
        }
        val property = ArmeriaKotlinInspectionCallChains.assignedProperty(call) ?: return false
        val callable = ArmeriaKotlinInspectionCallChains.containingCallable(call) ?: return false
        return ArmeriaKotlinInspectionCallChains.callsOnProperty(property, callable).any { usage ->
            ArmeriaKotlinInspectionCallChains.callName(usage) == "build" ||
                ArmeriaKotlinInspectionCallChains.forwardChainCalls(usage).any {
                    ArmeriaKotlinInspectionCallChains.callName(it) == "build"
                }
        }
    }

    private fun callableBuildsClient(
        callable: PsiElement,
        factoryBuilder: KtCallExpression,
    ): Boolean =
        PsiTreeUtil.findChildrenOfType(callable, KtCallExpression::class.java).any { call ->
            call != factoryBuilder && isClientBuildFactory(call)
        }

    private fun isClientBuildFactory(call: KtCallExpression): Boolean {
        val name = ArmeriaKotlinInspectionCallChains.callName(call) ?: return false
        if (name !in ArmeriaClientSupport.FACTORY_METHOD_NAMES) {
            return false
        }
        val resolved = ArmeriaKotlinInspectionCallChains.resolvedContainingClass(call)
        if (ArmeriaProductionChecklist.isClientFactoryClass(resolved)) {
            return false
        }
        if (ArmeriaProductionChecklist.isArmeriaClientClass(resolved)) {
            return true
        }
        val qualifierName = ArmeriaKotlinInspectionCallChains.qualifierSimpleName(call)
        return qualifierName != null && ArmeriaClientSupport.protocolForSimpleName(qualifierName) != null
    }
}
