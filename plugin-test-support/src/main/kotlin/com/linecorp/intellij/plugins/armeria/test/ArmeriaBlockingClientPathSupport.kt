package com.linecorp.intellij.plugins.armeria.test

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReferenceExpression

internal object ArmeriaBlockingClientPathSupport {
    fun extractJavaRequestPath(argument: PsiExpression?): String? {
        argument ?: return null
        return when (argument) {
            is PsiLiteralExpression -> {
                argument.value as? String
            }

            is PsiReferenceExpression -> {
                resolveJavaStringConstant(argument)
            }

            else -> {
                JavaPsiFacade
                    .getInstance(argument.project)
                    .constantEvaluationHelper
                    .computeConstantExpression(argument) as? String
            }
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
}
