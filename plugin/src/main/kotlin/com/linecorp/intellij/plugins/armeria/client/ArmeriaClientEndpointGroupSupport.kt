package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable

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
            return labelJavaEndpointGroupCall(
                call.methodExpression.qualifierExpression?.text,
                call.argumentList.expressions.toList(),
            )
        }
        val reference = expression as? PsiReferenceExpression ?: return null
        val resolved = reference.resolve()
        return when (resolved) {
            is PsiVariable -> labelJavaEndpointGroup(resolved.initializer)
            else -> reference.text.takeIf { looksLikeEndpointGroupText(it) }
        }
    }

    fun extractJavaEndpointGroupUri(expression: PsiExpression?): String? {
        val label = labelJavaEndpointGroup(expression) ?: return null
        return extractUriFromLabel(label)
    }

    internal fun looksLikeEndpointGroupText(text: String): Boolean {
        val simpleName = text.substringAfterLast('.')
        return simpleName in ENDPOINT_GROUP_SIMPLE_NAMES || simpleName.endsWith("EndpointGroup")
    }

    internal fun extractUriFromLabel(label: String): String {
        val openParen = label.indexOf('(')
        val closeParen = label.lastIndexOf(')')
        if (openParen >= 0 && closeParen > openParen) {
            return label.substring(openParen + 1, closeParen).trim()
        }
        return label
    }

    private fun labelJavaEndpointGroupCall(receiver: String?, arguments: List<PsiExpression>): String? {
        val simpleName = receiver?.substringAfterLast('.')?.takeIf { looksLikeEndpointGroupText(it) } ?: return null
        val detail = arguments.firstNotNullOfOrNull { ArmeriaClientCollector.extractString(it) }
        return if (detail != null) "$simpleName ($detail)" else simpleName
    }
}
