package com.linecorp.intellij.plugins.armeria.explorer.collector.registration
import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.java.ArmeriaJavaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.kotlin.ArmeriaKotlinBuilderCallHeuristics
import org.jetbrains.kotlin.psi.KtCallExpression

internal object ArmeriaBuilderCallHeuristics {
    fun looksLikeJavaBuilderCall(expression: PsiMethodCallExpression): Boolean =
        ArmeriaJavaBuilderCallHeuristics.looksLikeJavaBuilderCall(expression)

    fun looksLikeKotlinBuilderCall(call: KtCallExpression): Boolean = ArmeriaKotlinBuilderCallHeuristics.looksLikeKotlinBuilderCall(call)

    fun looksLikeArmeriaFluentRouteBuild(expression: PsiMethodCallExpression): Boolean =
        ArmeriaJavaBuilderCallHeuristics.looksLikeArmeriaFluentRouteBuild(expression)

    fun looksLikeArmeriaFluentRouteBuild(call: KtCallExpression): Boolean =
        ArmeriaKotlinBuilderCallHeuristics.looksLikeArmeriaFluentRouteBuild(call)

    fun isClearlyNonArmeriaJavaRegistrationCall(expression: PsiMethodCallExpression): Boolean =
        ArmeriaJavaBuilderCallHeuristics.isClearlyNonArmeriaJavaRegistrationCall(expression)

    fun isClearlyNonArmeriaKotlinRegistrationCall(call: KtCallExpression): Boolean =
        ArmeriaKotlinBuilderCallHeuristics.isClearlyNonArmeriaKotlinRegistrationCall(call)
}
