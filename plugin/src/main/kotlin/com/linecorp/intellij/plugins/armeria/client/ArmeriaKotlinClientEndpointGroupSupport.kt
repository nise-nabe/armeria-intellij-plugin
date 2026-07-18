package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal object ArmeriaKotlinClientEndpointGroupSupport {
    fun labelKotlinEndpointGroup(expression: KtExpression?): String? {
        expression ?: return null
        val call =
            when (expression) {
                is KtCallExpression -> expression
                is KtDotQualifiedExpression -> expression.selectorExpression as? KtCallExpression
                else -> null
            }
        if (call != null) {
            val receiver =
                when (val callee = call.calleeExpression) {
                    is KtDotQualifiedExpression -> callee.receiverExpression.text
                    else -> (call.parent as? KtDotQualifiedExpression)?.receiverExpression?.text
                }
            val arguments = call.valueArguments.mapNotNull { it.getArgumentExpression() }
            return labelKotlinEndpointGroupCall(receiver, arguments)
        }
        if (expression is KtNameReferenceExpression) {
            val resolved = expression.references.firstOrNull()?.resolve()
            if (resolved is KtProperty) {
                return labelKotlinEndpointGroup(resolved.initializer)
            }
        }
        return expression.text.takeIf { ArmeriaClientEndpointGroupSupport.looksLikeEndpointGroupText(it) }
    }

    fun extractKotlinEndpointGroupUri(expression: KtExpression?): String? {
        val label = labelKotlinEndpointGroup(expression) ?: return null
        return ArmeriaClientEndpointGroupSupport.extractUriFromLabel(label)
    }

    private fun labelKotlinEndpointGroupCall(
        receiver: String?,
        arguments: List<KtExpression>,
    ): String? {
        val simpleName =
            receiver
                ?.substringAfterLast('.')
                ?.takeIf { ArmeriaClientEndpointGroupSupport.looksLikeEndpointGroupText(it) }
                ?: return null
        val detail = arguments.firstNotNullOfOrNull { extractKotlinDetail(it) }
        return if (detail != null) "$simpleName ($detail)" else simpleName
    }

    private fun extractKotlinDetail(expression: KtExpression): String? {
        return when (expression) {
            is KtStringTemplateExpression -> {
                if (expression.entries.size == 1) {
                    expression.entries[0].text.trim('"')
                } else {
                    expression.text.trim('"')
                }
            }
            is KtNameReferenceExpression -> {
                val resolved = expression.references.firstOrNull()?.resolve()
                when (resolved) {
                    is KtProperty -> extractKotlinDetail(resolved.initializer ?: return null)
                    is PsiVariable -> ArmeriaRouteSupport.evaluateJavaStringConstant(resolved)
                    else -> expression.text.trim('"').takeIf { it.isNotEmpty() }
                }
            }
            else -> expression.text.trim('"').takeIf { it.isNotEmpty() }
        }
    }
}
