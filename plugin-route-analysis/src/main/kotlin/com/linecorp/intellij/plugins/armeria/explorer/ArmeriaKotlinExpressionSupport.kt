package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiVariable
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal object ArmeriaKotlinExpressionSupport {

    fun containingKotlinExpressionScope(call: KtCallExpression): PsiElement {
        var current: PsiElement = call
        while (true) {
            val parent = current.parent ?: break
            if (parent is KtBlockExpression || parent is KtLambdaExpression) {
                return current
            }
            current = parent
        }
        return call
    }

    fun extractKotlinString(expression: KtExpression?): String? {
        val unwrapped = unwrapKotlinExpression(expression) ?: return null
        return when (unwrapped) {
            is KtStringTemplateExpression -> {
                if (unwrapped.entries.size == 1) {
                    unwrapped.entries[0].text.trim('"')
                } else {
                    unwrapped.text.trim('"')
                }
            }
            is KtDotQualifiedExpression -> extractKotlinStringFromReference(unwrapped)
            is KtNameReferenceExpression -> extractKotlinStringFromReference(unwrapped)
            else -> unwrapped.text.trim('"').takeIf { it.isNotEmpty() }
        }
    }

    private fun extractKotlinStringFromReference(expression: KtExpression): String? {
        val resolved = expression.references.firstOrNull()?.resolve()
        when (resolved) {
            is KtProperty -> extractKotlinString(resolved.initializer)?.let { return it }
            is PsiVariable -> ArmeriaRouteSupport.evaluateJavaStringConstant(resolved)?.let { return it }
        }
        if (expression is KtDotQualifiedExpression) {
            val selector = expression.selectorExpression as? KtNameReferenceExpression ?: return null
            val receiver = expression.receiverExpression as? KtNameReferenceExpression ?: return null
            val containingClass = receiver.references.firstOrNull()?.resolve() as? com.intellij.psi.PsiClass
                ?: return null
            val field = containingClass.findFieldByName(selector.getReferencedName(), true)
            if (field != null) {
                ArmeriaRouteSupport.evaluateJavaStringConstant(field)?.let { return it }
            }
        }
        return expression.text.trim('"').takeIf { it.isNotEmpty() }
    }

    private fun unwrapKotlinExpression(expression: KtExpression?): KtExpression? {
        var current = expression ?: return null
        while (true) {
            current = when (current) {
                is KtParenthesizedExpression -> current.expression ?: return null
                else -> return current
            }
        }
    }
}
