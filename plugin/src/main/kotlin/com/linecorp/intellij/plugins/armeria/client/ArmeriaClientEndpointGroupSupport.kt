package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaClientEndpointGroupSupport {
    private val ENDPOINT_GROUP_SIMPLE_NAMES =
        setOf(
            "EndpointGroup",
            "StaticEndpointGroup",
            "DnsAddressEndpointGroup",
            "DnsServiceEndpointGroup",
            "DnsTextEndpointGroup",
            "HealthCheckedEndpointGroup",
            "ZooKeeperEndpointGroup",
            "EurekaEndpointGroup",
            "ConsulEndpointGroup",
            "PropertiesEndpointGroup",
        )

    private val ENDPOINT_GROUP_KIND_BUNDLE_KEYS =
        mapOf(
            "DnsAddressEndpointGroup" to "client.explorer.endpointGroup.dns",
            "DnsServiceEndpointGroup" to "client.explorer.endpointGroup.dns",
            "DnsTextEndpointGroup" to "client.explorer.endpointGroup.dns",
            "ZooKeeperEndpointGroup" to "client.explorer.endpointGroup.zookeeper",
            "EurekaEndpointGroup" to "client.explorer.endpointGroup.eureka",
            "ConsulEndpointGroup" to "client.explorer.endpointGroup.consul",
            "HealthCheckedEndpointGroup" to "client.explorer.endpointGroup.healthChecked",
            "PropertiesEndpointGroup" to "client.explorer.endpointGroup.properties",
            "StaticEndpointGroup" to "client.explorer.endpointGroup.static",
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
        var current = label.trim()
        if (current.isEmpty()) {
            return label
        }
        while (true) {
            val openParen = current.lastIndexOf('(')
            if (openParen < 0) {
                return current.ifBlank { label }
            }
            val closeParen = current.indexOf(')', startIndex = openParen + 1)
            if (closeParen < 0) {
                return current.ifBlank { label }
            }
            val inner = current.substring(openParen + 1, closeParen).trim()
            if (inner.isEmpty() || inner == current) {
                return current
            }
            current = inner
        }
    }

    internal fun kindLabel(simpleName: String): String {
        ENDPOINT_GROUP_KIND_BUNDLE_KEYS[simpleName]?.let { return message(it) }
        if (simpleName.startsWith("Dns") && simpleName.endsWith("EndpointGroup")) {
            return message("client.explorer.endpointGroup.dns")
        }
        return simpleName
    }

    private fun labelJavaEndpointGroupCall(
        receiver: String?,
        arguments: List<PsiExpression>,
    ): String? {
        val simpleName = receiver?.substringAfterLast('.')?.takeIf { looksLikeEndpointGroupText(it) } ?: return null
        val nested = arguments.firstNotNullOfOrNull { labelJavaEndpointGroup(it) }
        val detail = nested ?: arguments.firstNotNullOfOrNull { ArmeriaClientCollector.extractString(it) }
        val kind = kindLabel(simpleName)
        return if (detail != null) "$kind ($detail)" else kind
    }
}
