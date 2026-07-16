package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal object ArmeriaClientEndpointGroupSupport {
    private val ENDPOINT_GROUP_SIMPLE_NAMES = setOf(
        "EndpointGroup",
        "StaticEndpointGroup",
        "DnsAddressEndpointGroup",
        "DnsServiceEndpointGroup",
        "HealthCheckedEndpointGroup",
    )

    fun labelJavaEndpointGroup(expression: PsiExpression?): String? {
        expression ?: return null
        val call = expression as? PsiMethodCallExpression
        if (call != null) {
            return labelJavaEndpointGroupCall(call.methodExpression.qualifierExpression?.text, call.argumentList.expressions.toList())
        }
        val reference = expression as? PsiReferenceExpression ?: return null
        val resolved = reference.resolve()
        return when (resolved) {
            is PsiVariable -> labelJavaEndpointGroup(resolved.initializer)
            else -> reference.text.takeIf { looksLikeEndpointGroupText(it) }
        }
    }

    fun labelKotlinEndpointGroup(expression: KtExpression?): String? {
        expression ?: return null
        val call = expression as? KtCallExpression
        if (call != null) {
            val receiver = when (val callee = call.calleeExpression) {
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
        return expression.text.takeIf { looksLikeEndpointGroupText(it) }
    }

    fun extractJavaEndpointGroupUri(expression: PsiExpression?): String? {
        val label = labelJavaEndpointGroup(expression) ?: return null
        return extractUriFromLabel(label)
    }

    fun extractKotlinEndpointGroupUri(expression: KtExpression?): String? {
        val label = labelKotlinEndpointGroup(expression) ?: return null
        return extractUriFromLabel(label)
    }

    private fun labelJavaEndpointGroupCall(receiver: String?, arguments: List<PsiExpression>): String? {
        val simpleName = receiver?.substringAfterLast('.')?.takeIf { looksLikeEndpointGroupText(it) } ?: return null
        val detail = arguments.firstNotNullOfOrNull { ArmeriaClientCollector.extractString(it) }
        return if (detail != null) "$simpleName ($detail)" else simpleName
    }

    private fun labelKotlinEndpointGroupCall(receiver: String?, arguments: List<KtExpression>): String? {
        val simpleName = receiver?.substringAfterLast('.')?.takeIf { looksLikeEndpointGroupText(it) } ?: return null
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

    private fun looksLikeEndpointGroupText(text: String): Boolean {
        val simpleName = text.substringAfterLast('.')
        return simpleName in ENDPOINT_GROUP_SIMPLE_NAMES || simpleName.endsWith("EndpointGroup")
    }

    private fun extractUriFromLabel(label: String): String {
        val openParen = label.indexOf('(')
        val closeParen = label.lastIndexOf(')')
        if (openParen >= 0 && closeParen > openParen) {
            return label.substring(openParen + 1, closeParen).trim()
        }
        return label
    }
}
