package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaEndpointGroupCloseKotlinSupport {
    fun highlight(call: KtCallExpression): PsiElement? {
        if (!isDynamicEndpointGroupConstruction(call)) {
            return null
        }
        if (isReceiverOfUseOrClose(call)) {
            return null
        }
        val property = ArmeriaKotlinInspectionCallChains.assignedProperty(call) ?: return null
        val scope = closeSearchScope(property)
        if (isClosed(property, scope)) {
            return null
        }
        return ArmeriaKotlinInspectionCallChains.highlightCallName(call)
    }

    private fun isDynamicEndpointGroupConstruction(call: KtCallExpression): Boolean {
        val name = ArmeriaKotlinInspectionCallChains.callName(call) ?: return false
        if (name != "of" && name != "build") {
            return false
        }
        val resolved = ArmeriaKotlinInspectionCallChains.resolvedContainingClass(call)
        if (resolved != null && ArmeriaProductionChecklist.isDynamicEndpointGroup(resolved.substringAfterLast('.'))) {
            return true
        }
        val qualifierName = constructionTypeSimpleName(call) ?: return false
        return ArmeriaProductionChecklist.isDynamicEndpointGroup(qualifierName)
    }

    private fun constructionTypeSimpleName(call: KtCallExpression): String? {
        if (ArmeriaKotlinInspectionCallChains.callName(call) == "of") {
            return ArmeriaKotlinInspectionCallChains.qualifierSimpleName(call)
        }
        val receiver = ArmeriaKotlinInspectionCallChains.chainReceiver(call)
        val builderCall = receiver?.let { ArmeriaKotlinInspectionCallChains.asCall(it) }
        if (builderCall != null && ArmeriaKotlinInspectionCallChains.callName(builderCall) == "builder") {
            return ArmeriaKotlinInspectionCallChains.qualifierSimpleName(builderCall)
        }
        return ArmeriaKotlinInspectionCallChains.qualifierSimpleName(call)
    }

    private fun isReceiverOfUseOrClose(call: KtCallExpression): Boolean {
        val parent = ArmeriaKotlinInspectionCallChains.nextChainedCall(call) ?: return false
        val name = ArmeriaKotlinInspectionCallChains.callName(parent)
        return name == "close" || name == "closeAsync" || name == "use"
    }

    private fun closeSearchScope(property: KtProperty): PsiElement {
        val owner = property.getParentOfType<KtClassOrObject>(true)
        return if (owner != null && property.parent !is KtBlockExpression) {
            owner
        } else {
            property.containingFile
        }
    }

    private fun isClosed(
        property: KtProperty,
        scope: PsiElement,
    ): Boolean =
        PsiTreeUtil.findChildrenOfType(scope, KtCallExpression::class.java).any { call ->
            val name = ArmeriaKotlinInspectionCallChains.callName(call)
            if (name != "close" && name != "closeAsync" && name != "use") {
                return@any false
            }
            ArmeriaKotlinInspectionCallChains.qualifierResolvesTo(call, property)
        }
}
