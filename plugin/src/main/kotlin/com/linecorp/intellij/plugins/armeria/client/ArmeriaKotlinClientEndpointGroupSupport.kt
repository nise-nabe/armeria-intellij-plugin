package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal object ArmeriaKotlinClientEndpointGroupSupport {
    fun labelKotlinEndpointGroup(expression: KtExpression?): String? = labelKotlinEndpointGroup(expression, mutableSetOf())

    private fun labelKotlinEndpointGroup(
        expression: KtExpression?,
        visited: MutableSet<PsiElement>,
    ): String? {
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
            return labelKotlinEndpointGroupCall(
                receiver,
                arguments,
                ArmeriaKotlinClientXdsSupport.resolveKotlinFactoryClass(call),
                visited,
            )
        }
        val reference =
            when (expression) {
                is KtNameReferenceExpression -> expression
                is KtDotQualifiedExpression -> expression.selectorExpression as? KtNameReferenceExpression
                else -> null
            }
        if (reference != null) {
            when (val resolved = reference.references.firstOrNull()?.resolve()) {
                is KtProperty ->
                    if (visited.add(resolved)) {
                        return labelKotlinEndpointGroup(resolved.initializer, visited)
                    }
                is PsiVariable ->
                    if (visited.add(resolved)) {
                        return ArmeriaClientEndpointGroupSupport.labelJavaEndpointGroup(
                            resolved.initializer,
                            visited,
                        )
                    }
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
        resolvedClassName: String?,
        visited: MutableSet<PsiElement>,
    ): String? {
        val simpleName =
            receiver
                ?.substringAfterLast('.')
                ?.takeIf { ArmeriaClientEndpointGroupSupport.looksLikeEndpointGroupText(it) }
                ?: return null
        val nested = arguments.firstNotNullOfOrNull { labelKotlinEndpointGroup(it, visited) }
        val detail = nested ?: arguments.firstNotNullOfOrNull { extractKotlinDetail(it, visited) }
        val kind = ArmeriaClientEndpointGroupSupport.kindLabel(simpleName, resolvedClassName)
        return if (detail != null) "$kind ($detail)" else kind
    }

    private fun extractKotlinDetail(
        expression: KtExpression,
        visited: MutableSet<PsiElement>,
    ): String? {
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
                    is KtProperty ->
                        if (visited.add(resolved)) {
                            extractKotlinDetail(resolved.initializer ?: return null, visited)
                        } else {
                            null
                        }
                    is PsiVariable -> ArmeriaRouteSupport.evaluateJavaStringConstant(resolved)
                    else -> expression.text.trim('"').takeIf { it.isNotEmpty() }
                }
            }
            else -> expression.text.trim('"').takeIf { it.isNotEmpty() }
        }
    }
}
