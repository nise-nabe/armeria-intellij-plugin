package com.linecorp.intellij.plugins.armeria.test

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReferenceExpression
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal object ArmeriaBlockingClientPathSupport {
    fun extractJavaRequestPath(argument: PsiExpression?): String? {
        argument ?: return null
        return when (argument) {
            is PsiLiteralExpression -> argument.value as? String
            is PsiReferenceExpression -> resolveJavaStringConstant(argument)
            else ->
                JavaPsiFacade
                    .getInstance(argument.project)
                    .constantEvaluationHelper
                    .computeConstantExpression(argument) as? String
        }
    }

    private fun resolveJavaStringConstant(reference: PsiReferenceExpression): String? {
        JavaPsiFacade
            .getInstance(reference.project)
            .constantEvaluationHelper
            .computeConstantExpression(reference)
            ?.let { return it as? String }
        val field = reference.resolve() as? PsiField ?: return null
        return staticFinalStringLiteral(field)
    }

    private fun staticFinalStringLiteral(field: PsiField): String? {
        if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) {
            return null
        }
        return (field.initializer as? PsiLiteralExpression)?.value as? String
    }

    fun extractKotlinRequestPath(argument: KtExpression?): String? {
        argument ?: return null
        return when (argument) {
            is KtStringTemplateExpression -> {
                if (argument.entries.size != 1) {
                    return null
                }
                argument.entries
                    .single()
                    .text
                    .removeSurrounding("\"")
            }
            is KtNameReferenceExpression -> resolveKotlinStringConstant(argument)
            is KtDotQualifiedExpression -> {
                val reference = argument.selectorExpression as? KtNameReferenceExpression ?: return null
                resolveKotlinStringConstant(reference)
            }
            else -> null
        }
    }

    private fun resolveKotlinStringConstant(reference: KtNameReferenceExpression): String? {
        when (val resolved = reference.reference?.resolve()) {
            is PsiField -> return staticFinalStringLiteral(resolved)
            is KtProperty -> {
                if (!resolved.hasModifier(KtTokens.CONST_KEYWORD)) {
                    return null
                }
                val initializer = resolved.initializer as? KtStringTemplateExpression ?: return null
                if (initializer.entries.size != 1) {
                    return null
                }
                return initializer.entries
                    .single()
                    .text
                    .removeSurrounding("\"")
            }
            else -> return null
        }
    }
}
