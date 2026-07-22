package com.linecorp.intellij.plugins.armeria.explorer.model
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaRouteMetadata {
    fun moduleName(element: PsiElement): String =
        ModuleUtilCore.findModuleForPsiElement(element)?.name
            ?: message("route.explorer.module.unassigned")

    fun sourceHint(element: PsiElement): String = sourceHintAtOffset(element, element.textRange.startOffset)

    fun sourceHintAtOffset(
        element: PsiElement,
        offset: Int,
    ): String {
        val containingFile = element.containingFile ?: return ""
        val virtualFile = containingFile.virtualFile ?: return ""
        val document = PsiDocumentManager.getInstance(element.project).getDocument(containingFile) ?: return virtualFile.presentableUrl
        val safeOffset = offset.coerceIn(0, document.textLength)
        val line = document.getLineNumber(safeOffset) + 1
        return "${virtualFile.presentableUrl}:$line"
    }

    fun registeredInHint(element: PsiElement): String {
        val anchor =
            when (element) {
                is PsiMethod -> element
                is PsiMethodCallExpression -> PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
                else -> PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
            } ?: return ""
        val containingClass = anchor.containingClass
        val className = containingClass?.qualifiedName ?: containingClass?.name ?: message("route.explorer.registeredIn.anonymousClass")
        return message("route.explorer.registeredIn.method", className, anchor.name)
    }
}
