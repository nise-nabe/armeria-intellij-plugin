package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
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
        val existing = ArmeriaMissingBlockingKotlinSupport.findUseBlockingTaskExecutorCall(call)
        val replaced =
            if (existing != null) {
                rewriteExisting(existing, factory)
            } else {
                insertAfterBuilder(call, factory)
            } ?: return
        CodeStyleManager.getInstance(project).reformat(replaced)
    }

    private fun rewriteExisting(
        existing: KtCallExpression,
        factory: KtPsiFactory,
    ): PsiElement? {
        val argumentList = existing.valueArgumentList ?: return null
        val trueExpression = factory.createExpression("true")
        val arguments = argumentList.arguments
        if (arguments.isEmpty()) {
            return argumentList.addArgument(factory.createArgument(trueExpression))
        }
        val expression = arguments[0].getArgumentExpression() ?: return arguments[0].replace(factory.createArgument(trueExpression))
        return expression.replace(trueExpression)
    }

    private fun insertAfterBuilder(
        call: KtCallExpression,
        factory: KtPsiFactory,
    ): PsiElement {
        val anchor = expressionToExtend(call)
        return anchor.replace(factory.createExpression("${anchor.text}.useBlockingTaskExecutor(true)"))
    }

    /**
     * `GraphqlService.builder()` may be a [KtCallExpression] whose callee is a
     * [KtDotQualifiedExpression], or a [KtDotQualifiedExpression] whose selector is
     * `builder()`. Extend the full receiver expression so the insert stays well-formed.
     */
    private fun expressionToExtend(call: KtCallExpression): KtExpression {
        val parent = call.parent
        if (parent is KtDotQualifiedExpression && parent.selectorExpression == call) {
            return parent
        }
        return call
    }
}
