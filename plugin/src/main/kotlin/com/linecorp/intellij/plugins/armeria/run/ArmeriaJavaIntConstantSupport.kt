package com.linecorp.intellij.plugins.armeria.run

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable

internal object ArmeriaJavaIntConstantSupport {
    fun extractPort(expression: PsiExpression?): Int? {
        val number = extractNumber(expression) ?: return null
        return number.toInt().takeIf { it in 1..65535 }
    }

    private fun extractNumber(expression: PsiExpression?): Number? {
        expression ?: return null
        when (expression) {
            is PsiLiteralExpression -> (expression.value as? Number)?.let { return it }
            is PsiReferenceExpression -> {
                val resolved = expression.resolve() as? PsiVariable
                when (val value = resolved?.computeConstantValue()) {
                    is Number -> return value
                }
                resolved?.initializer?.let { initializer -> extractNumber(initializer)?.let { return it } }
            }
            else -> {
                val constant =
                    JavaPsiFacade
                        .getInstance(expression.project)
                        .constantEvaluationHelper
                        .computeConstantExpression(expression)
                if (constant is Number) {
                    return constant
                }
            }
        }
        return expression.text.toIntOrNull()
    }
}
