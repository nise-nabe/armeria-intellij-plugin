package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiVariable
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtValueArgument

object ArmeriaKotlinExpressionSupport {
    fun containingKotlinExpressionScope(call: KtCallExpression): PsiElement {
        var current: PsiElement = call
        while (true) {
            val parent = current.parent ?: break
            if (parent is KtBlockExpression || parent is KtLambdaExpression) {
                return parent
            }
            current = parent
        }
        return call
    }

    fun containingKotlinStatementExpression(call: KtCallExpression): PsiElement {
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

    fun resolveCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }

    fun findArgumentExpression(
        arguments: List<KtValueArgument>,
        parameterName: String,
        positionalIndex: Int,
    ): KtExpression? {
        arguments
            .firstOrNull { argument ->
                argument.getArgumentName()?.asName?.identifier == parameterName
            }?.getArgumentExpression()
            ?.let { return it }
        return arguments.getOrNull(positionalIndex)?.getArgumentExpression()
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

    /**
     * String literal or resolvable compile-time constant.
     * Unresolved names and non-string initializers return null — never PSI `.text`.
     */
    fun extractKotlinStringConstant(
        expression: KtExpression?,
        visitedProperties: MutableSet<KtProperty> = mutableSetOf(),
    ): String? {
        val unwrapped = unwrapKotlinExpression(expression) ?: return null
        return when (unwrapped) {
            is KtStringTemplateExpression -> kotlinStringTemplateWithoutInterpolation(unwrapped)
            is KtDotQualifiedExpression -> extractKotlinStringConstantFromReference(unwrapped, visitedProperties)
            is KtNameReferenceExpression -> extractKotlinStringConstantFromReference(unwrapped, visitedProperties)
            else -> null
        }
    }

    private fun kotlinStringTemplateWithoutInterpolation(template: KtStringTemplateExpression): String? {
        if (template.hasInterpolation()) {
            return null
        }
        return if (template.entries.size == 1) {
            template.entries[0].text.trim('"')
        } else {
            template.text.trim('"').takeIf { it.isNotEmpty() }
        }
    }

    private fun extractKotlinStringConstantFromReference(
        expression: KtExpression,
        visitedProperties: MutableSet<KtProperty>,
    ): String? {
        val resolved = expression.references.firstOrNull()?.resolve()
        when (resolved) {
            is KtProperty -> {
                if (!visitedProperties.add(resolved)) {
                    return null
                }
                extractKotlinStringConstant(resolved.initializer, visitedProperties)?.let { return it }
            }
            is PsiVariable -> ArmeriaRouteSupport.evaluateJavaStringConstant(resolved)?.let { return it }
        }
        if (expression is KtDotQualifiedExpression) {
            val selector = expression.selectorExpression as? KtNameReferenceExpression ?: return null
            val receiver = expression.receiverExpression as? KtNameReferenceExpression ?: return null
            val containingClass =
                receiver.references.firstOrNull()?.resolve() as? com.intellij.psi.PsiClass
                    ?: return null
            val field = containingClass.findFieldByName(selector.getReferencedName(), true)
            if (field != null) {
                ArmeriaRouteSupport.evaluateJavaStringConstant(field)?.let { return it }
            }
        }
        return null
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
            val containingClass =
                receiver.references.firstOrNull()?.resolve() as? com.intellij.psi.PsiClass
                    ?: return null
            val field = containingClass.findFieldByName(selector.getReferencedName(), true)
            if (field != null) {
                ArmeriaRouteSupport.evaluateJavaStringConstant(field)?.let { return it }
            }
        }
        return expression.text.trim('"').takeIf { it.isNotEmpty() }
    }

    fun unwrapKotlinExpression(expression: KtExpression?): KtExpression? {
        var current = expression ?: return null
        while (true) {
            current =
                when (current) {
                    is KtParenthesizedExpression -> current.expression ?: return null
                    is KtBinaryExpressionWithTypeRHS -> current.left
                    is KtUnaryExpression ->
                        if (current.operationToken == KtTokens.EXCLEXCL) {
                            current.baseExpression ?: return current
                        } else {
                            return current
                        }
                    else -> return current
                }
        }
    }
}
