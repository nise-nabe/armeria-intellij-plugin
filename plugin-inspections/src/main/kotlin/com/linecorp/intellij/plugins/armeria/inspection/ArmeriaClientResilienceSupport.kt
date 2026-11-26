package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientSupport

internal object ArmeriaClientResilienceSupport {
    fun highlight(call: PsiMethodCallExpression): PsiElement? {
        if (!isWebClientBuilder(call)) {
            return null
        }
        if (ArmeriaProductionChecklist.isTestSource(call.containingFile)) {
            return null
        }
        if (hasResilienceDecorator(call)) {
            return null
        }
        return call.methodExpression.referenceNameElement ?: call
    }

    private fun isWebClientBuilder(call: PsiMethodCallExpression): Boolean =
        ArmeriaJavaInspectionCallChains.isCallOnClass(
            call = call,
            methodName = "builder",
            fqcn = ArmeriaClientSupport.WEB_CLIENT_CLASS,
            simpleName = "WebClient",
        )

    private fun hasResilienceDecorator(call: PsiMethodCallExpression): Boolean {
        if (decoratorNamesInForwardChain(call).any(ArmeriaProductionChecklist::isResilienceDecorator)) {
            return true
        }
        val variable = ArmeriaJavaInspectionCallChains.assignedVariable(call) ?: return false
        val scope = call.containingFile
        return ArmeriaJavaInspectionCallChains.methodCallsOnVariable(variable, scope).any { usage ->
            if (ArmeriaJavaInspectionCallChains.methodName(usage) == "decorator") {
                decoratorName(usage)?.let { ArmeriaProductionChecklist.isResilienceDecorator(it) } == true
            } else {
                decoratorNamesInForwardChain(usage).any(ArmeriaProductionChecklist::isResilienceDecorator)
            }
        }
    }

    private fun decoratorNamesInForwardChain(call: PsiMethodCallExpression): Sequence<String> {
        val names = mutableListOf<String>()
        var cursor: PsiMethodCallExpression = call
        while (true) {
            val parent = ArmeriaJavaInspectionCallChains.enclosingQualifierCall(cursor) ?: break
            if (ArmeriaJavaInspectionCallChains.methodName(parent) == "decorator") {
                decoratorName(parent)?.let { names += it }
            }
            cursor = parent
        }
        return names.asSequence()
    }

    private fun decoratorName(call: PsiMethodCallExpression): String? {
        val argument = call.argumentList.expressions.firstOrNull() ?: return null
        return ArmeriaJavaInspectionCallChains.decoratorClassSimpleName(argument)
    }
}
