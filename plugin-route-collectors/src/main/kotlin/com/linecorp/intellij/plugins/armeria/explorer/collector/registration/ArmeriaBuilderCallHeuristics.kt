package com.linecorp.intellij.plugins.armeria.explorer.collector.registration
import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.java.ArmeriaJavaBuilderCallHeuristics

object ArmeriaBuilderCallHeuristics {
    fun looksLikeJavaBuilderCall(expression: PsiMethodCallExpression): Boolean =
        ArmeriaJavaBuilderCallHeuristics.looksLikeJavaBuilderCall(expression)

    fun looksLikeArmeriaFluentRouteBuild(expression: PsiMethodCallExpression): Boolean =
        ArmeriaJavaBuilderCallHeuristics.looksLikeArmeriaFluentRouteBuild(expression)

    fun isClearlyNonArmeriaJavaRegistrationCall(expression: PsiMethodCallExpression): Boolean =
        ArmeriaJavaBuilderCallHeuristics.isClearlyNonArmeriaJavaRegistrationCall(expression)
}
