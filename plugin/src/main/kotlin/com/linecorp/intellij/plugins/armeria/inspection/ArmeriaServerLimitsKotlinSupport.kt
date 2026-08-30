package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiElement
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaServerLimitsKotlinSupport {
    fun highlight(call: KtCallExpression): PsiElement? {
        if (ArmeriaProductionChecklist.isTestSource(call.containingFile)) {
            return null
        }
        if (!isServerBuilder(call)) {
            return null
        }
        val present = collectBuilderMethodNames(call)
        if (ArmeriaProductionChecklist.missingServerLimits(present).isEmpty()) {
            return null
        }
        return ArmeriaKotlinInspectionCallChains.highlightCallName(call)
    }

    fun missingLimits(call: KtCallExpression): List<String> {
        if (ArmeriaProductionChecklist.isTestSource(call.containingFile)) {
            return emptyList()
        }
        if (!isServerBuilder(call)) {
            return emptyList()
        }
        return ArmeriaProductionChecklist.missingServerLimits(collectBuilderMethodNames(call))
    }

    private fun isServerBuilder(call: KtCallExpression): Boolean =
        ArmeriaKotlinInspectionCallChains.isCallOnClass(
            call = call,
            methodName = "builder",
            fqcn = ArmeriaRouteSupport.ARMERIA_SERVER_CLASS,
            simpleName = "Server",
        )

    private fun collectBuilderMethodNames(call: KtCallExpression): Set<String> {
        val names = mutableSetOf<String>()
        addCallNames(call, names)
        val property = ArmeriaKotlinInspectionCallChains.assignedProperty(call)
        val scope =
            if (property != null && isMemberProperty(property)) {
                property.getParentOfType<KtClassOrObject>(true) ?: call.containingFile
            } else {
                ArmeriaKotlinInspectionCallChains.containingCallable(call) ?: call.containingFile
            }
        if (property != null) {
            for (usage in ArmeriaKotlinInspectionCallChains.callsOnProperty(property, scope)) {
                addCallNames(usage, names)
            }
        }
        return names
    }

    private fun addCallNames(
        call: KtCallExpression,
        names: MutableSet<String>,
    ) {
        for (candidate in ArmeriaKotlinInspectionCallChains.callAndScopeBodyCalls(call)) {
            ArmeriaKotlinInspectionCallChains.callName(candidate)?.let { names += it }
        }
    }

    private fun isMemberProperty(property: KtProperty): Boolean =
        property.getParentOfType<KtClassOrObject>(true) != null && property.parent !is KtBlockExpression
}
