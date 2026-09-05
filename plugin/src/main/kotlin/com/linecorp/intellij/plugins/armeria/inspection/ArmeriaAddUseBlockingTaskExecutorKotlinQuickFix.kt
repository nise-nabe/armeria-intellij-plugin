package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

internal class ArmeriaAddUseBlockingTaskExecutorKotlinQuickFix(
    builderCall: KtCallExpression,
) : LocalQuickFixOnPsiElement(builderCall) {
    override fun getFamilyName(): String = message("inspection.missing.blocking.quickfix.executor.family")

    override fun getText(): String = message("inspection.missing.blocking.quickfix.executor")

    override fun invoke(
        project: Project,
        file: PsiFile,
        startElement: PsiElement,
        endElement: PsiElement,
    ) {
        val call = startElement as? KtCallExpression ?: return
        if (ArmeriaMissingBlockingKotlinSupport.hasBlockingTaskExecutor(call)) {
            return
        }
        val factory = KtPsiFactory(project)
        val replacement = factory.createExpression("${call.text}.useBlockingTaskExecutor(true)")
        val replaced = call.replace(replacement)
        CodeStyleManager.getInstance(project).reformat(replaced)
    }
}
