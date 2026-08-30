package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiElement
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientSupport
import org.jetbrains.kotlin.psi.KtCallExpression

internal object ArmeriaClientResilienceKotlinSupport {
    fun highlight(call: KtCallExpression): PsiElement? {
        if (!isWebClientBuilder(call)) {
            return null
        }
        if (ArmeriaProductionChecklist.isTestSource(call.containingFile)) {
            return null
        }
        if (hasResilienceDecorator(call)) {
            return null
        }
        return ArmeriaKotlinInspectionCallChains.highlightCallName(call)
    }

    private fun isWebClientBuilder(call: KtCallExpression): Boolean =
        ArmeriaKotlinInspectionCallChains.isCallOnClass(
            call = call,
            methodName = "builder",
            fqcn = ArmeriaClientSupport.WEB_CLIENT_CLASS,
            simpleName = "WebClient",
        )

    private fun hasResilienceDecorator(call: KtCallExpression): Boolean {
        if (decoratorNamesFromChain(call).any(ArmeriaProductionChecklist::isResilienceDecorator)) {
            return true
        }
        val property = ArmeriaKotlinInspectionCallChains.assignedProperty(call) ?: return false
        val scope = call.containingFile
        return ArmeriaKotlinInspectionCallChains.callsOnProperty(property, scope).any { usage ->
            decoratorNamesFromChain(usage).any(ArmeriaProductionChecklist::isResilienceDecorator) ||
                (
                    ArmeriaKotlinInspectionCallChains.callName(usage) == "decorator" &&
                        decoratorName(usage)?.let { ArmeriaProductionChecklist.isResilienceDecorator(it) } == true
                )
        }
    }

    private fun decoratorNamesFromChain(call: KtCallExpression): Sequence<String> =
        sequence {
            if (ArmeriaKotlinInspectionCallChains.callName(call) == "decorator") {
                decoratorName(call)?.let { yield(it) }
            }
            for (chained in ArmeriaKotlinInspectionCallChains.forwardChainCalls(call)) {
                val name = ArmeriaKotlinInspectionCallChains.callName(chained)
                if (name == "decorator") {
                    decoratorName(chained)?.let { yield(it) }
                }
                if (name in ArmeriaProductionChecklist.SCOPE_FUNCTION_NAMES) {
                    for (bodyCall in ArmeriaKotlinInspectionCallChains.lambdaBodyCalls(chained)) {
                        if (ArmeriaKotlinInspectionCallChains.callName(bodyCall) == "decorator") {
                            decoratorName(bodyCall)?.let { yield(it) }
                        }
                    }
                }
            }
        }

    private fun decoratorName(call: KtCallExpression): String? {
        val argument = call.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null
        return ArmeriaKotlinInspectionCallChains.decoratorClassSimpleName(argument)
    }
}
