package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

class ArmeriaMissingBlockingKotlinInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.missing.blocking.kotlin.display.name")

    override fun getStaticDescription(): String = message("inspection.missing.blocking.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                if (!ArmeriaMissingBlockingKotlinSupport.shouldInspect(function)) {
                    return
                }
                val fixes = ArmeriaMissingBlockingKotlinSupport.quickFixes(function)
                for (finding in ArmeriaMissingBlockingKotlinSupport.findings(function)) {
                    holder.registerProblem(
                        finding.highlight,
                        message("inspection.missing.blocking.problem", finding.methodName),
                        ProblemHighlightType.WEAK_WARNING,
                        *fixes,
                    )
                }
            }

            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                registerDataFetcherLambdaFindings(holder, expression)
                if (!ArmeriaMissingBlockingKotlinSupport.isGraphqlServiceBuilderCall(expression)) {
                    return
                }
                if (ArmeriaMissingBlockingKotlinSupport.hasBlockingTaskExecutor(expression)) {
                    return
                }
                if (!ArmeriaMissingBlockingKotlinSupport.hasBlockingDataFetcher(expression)) {
                    return
                }
                holder.registerProblem(
                    ArmeriaMissingBlockingKotlinSupport.highlight(expression),
                    message("inspection.missing.blocking.graphql.executor"),
                    ProblemHighlightType.WEAK_WARNING,
                )
            }

            private fun registerDataFetcherLambdaFindings(
                holder: ProblemsHolder,
                call: KtCallExpression,
            ) {
                val lambdas = ArmeriaMissingBlockingKotlinSupport.dataFetcherLambdas(call)
                if (lambdas.isEmpty()) {
                    return
                }
                if (ArmeriaMissingBlockingKotlinSupport.hasBlockingTaskExecutor(call)) {
                    return
                }
                for (lambda in lambdas) {
                    val body = lambda.bodyExpression ?: continue
                    for (finding in ArmeriaMissingBlockingKotlinSupport.findingsIn(body, lambda)) {
                        holder.registerProblem(
                            finding.highlight,
                            message("inspection.missing.blocking.problem", finding.methodName),
                            ProblemHighlightType.WEAK_WARNING,
                        )
                    }
                }
            }
        }
}
