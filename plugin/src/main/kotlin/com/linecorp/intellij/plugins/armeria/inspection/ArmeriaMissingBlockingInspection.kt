package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaMissingBlockingInspection : AbstractBaseJavaLocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.missing.blocking.display.name")

    override fun getStaticDescription(): String = message("inspection.missing.blocking.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                if (!ArmeriaMissingBlockingSupport.shouldInspect(method)) {
                    return
                }
                val fixes = ArmeriaMissingBlockingSupport.quickFixes(method)
                for (finding in ArmeriaMissingBlockingSupport.findings(method)) {
                    holder.registerProblem(
                        finding.highlight,
                        message("inspection.missing.blocking.problem", finding.methodName),
                        ProblemHighlightType.WEAK_WARNING,
                        *fixes,
                    )
                }
            }

            override fun visitLambdaExpression(expression: PsiLambdaExpression) {
                if (!ArmeriaGraphqlBlockingSupport.isGraphqlDataFetcherLambda(expression)) {
                    return
                }
                if (ArmeriaGraphqlBlockingSupport.hasBlockingTaskExecutor(expression)) {
                    return
                }
                val body = expression.body ?: return
                for (finding in ArmeriaMissingBlockingSupport.findingsIn(body, expression)) {
                    holder.registerProblem(
                        finding.highlight,
                        message("inspection.missing.blocking.problem", finding.methodName),
                        ProblemHighlightType.WEAK_WARNING,
                    )
                }
            }

            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                if (!ArmeriaGraphqlBlockingSupport.isGraphqlServiceBuilderCall(expression)) {
                    return
                }
                if (ArmeriaGraphqlBlockingSupport.hasBlockingTaskExecutor(expression)) {
                    return
                }
                if (!ArmeriaGraphqlBlockingSupport.hasBlockingDataFetcher(expression)) {
                    return
                }
                holder.registerProblem(
                    expression.methodExpression.referenceNameElement ?: expression,
                    message("inspection.missing.blocking.graphql.executor"),
                    ProblemHighlightType.WEAK_WARNING,
                )
            }
        }
}
