package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.linecorp.intellij.plugins.armeria.message

internal class ArmeriaAddUseBlockingTaskExecutorQuickFix(
    builderCall: PsiMethodCallExpression,
) : LocalQuickFixOnPsiElement(builderCall) {
    override fun getFamilyName(): String = message("inspection.missing.blocking.quickfix.executor.family")

    override fun getText(): String = message("inspection.missing.blocking.quickfix.executor")

    override fun invoke(
        project: Project,
        file: PsiFile,
        startElement: PsiElement,
        endElement: PsiElement,
    ) {
        val call = startElement as? PsiMethodCallExpression ?: return
        if (ArmeriaGraphqlBlockingSupport.hasBlockingTaskExecutor(call)) {
            return
        }
        val factory = JavaPsiFacade.getElementFactory(project)
        val existing = ArmeriaGraphqlBlockingSupport.findUseBlockingTaskExecutorCall(call)
        val replaced =
            if (existing != null) {
                rewriteExisting(existing, factory)
            } else {
                insertAfterBuilder(call, factory)
            } ?: return
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced)
        CodeStyleManager.getInstance(project).reformat(replaced)
    }

    private fun rewriteExisting(
        existing: PsiMethodCallExpression,
        factory: PsiElementFactory,
    ): PsiElement? {
        val trueLiteral = factory.createExpressionFromText("true", existing)
        val arguments = existing.argumentList.expressions
        return if (arguments.isEmpty()) {
            existing.argumentList.add(trueLiteral)
        } else {
            arguments[0].replace(trueLiteral)
        }
    }

    private fun insertAfterBuilder(
        call: PsiMethodCallExpression,
        factory: PsiElementFactory,
    ): PsiElement =
        call.replace(
            factory.createExpressionFromText(
                "${call.text}.useBlockingTaskExecutor(true)",
                call,
            ),
        )
}
