package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

internal class ArmeriaAddBlockingAnnotationQuickFix(
    owner: PsiModifierListOwner,
    private val classLevel: Boolean,
) : LocalQuickFixOnPsiElement(owner) {
    override fun getFamilyName(): String = message("inspection.missing.blocking.quickfix.family")

    override fun getText(): String =
        if (classLevel) {
            message("inspection.missing.blocking.quickfix.class")
        } else {
            message("inspection.missing.blocking.quickfix.method")
        }

    override fun invoke(
        project: Project,
        file: PsiFile,
        startElement: PsiElement,
        endElement: PsiElement,
    ) {
        val owner = startElement as? PsiModifierListOwner ?: return
        val modifierList = owner.modifierList ?: return
        if (modifierList.hasAnnotation(ArmeriaRouteSupport.BLOCKING_ANNOTATION)) {
            return
        }
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(
            modifierList.addAnnotation(ArmeriaRouteSupport.BLOCKING_ANNOTATION),
        )
    }

    companion object {
        fun forMethod(method: PsiModifierListOwner): ArmeriaAddBlockingAnnotationQuickFix =
            ArmeriaAddBlockingAnnotationQuickFix(method, classLevel = false)

        fun forClass(psiClass: PsiModifierListOwner): ArmeriaAddBlockingAnnotationQuickFix =
            ArmeriaAddBlockingAnnotationQuickFix(psiClass, classLevel = true)
    }
}
