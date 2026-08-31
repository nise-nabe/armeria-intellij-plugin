package com.linecorp.intellij.plugins.armeria.explorer.collector.registration

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaListenPortSupport {
    internal const val HTTP_METHOD = "http"
    internal const val HTTPS_METHOD = "https"
    internal const val PORT_METHOD = "port"
    internal const val SESSION_PROTOCOL_CLASS = "com.linecorp.armeria.common.SessionProtocol"
    internal const val SESSION_PROTOCOL_SIMPLE_NAME = "SessionProtocol"

    internal val METHOD_NAMES: Set<String> = setOf(HTTP_METHOD, HTTPS_METHOD, PORT_METHOD)

    private const val MAX_INITIALIZER_HOPS = 8
    private val HTTP_TOKENS = setOf("HTTP", "H1C", "H2C", "H1C_OR_H2C")
    private val HTTPS_TOKENS = setOf("HTTPS", "H1", "H2", "H1_OR_H2")

    internal fun protocolLabel(
        methodName: String,
        extraArgsPresent: Boolean,
        resolvedProtocolNames: List<String>,
    ): String? =
        when (methodName) {
            HTTPS_METHOD -> canonicalHttps()
            HTTP_METHOD -> canonicalHttp()
            PORT_METHOD -> {
                if (!extraArgsPresent) {
                    canonicalHttp()
                } else {
                    val collapsed = collapseSessionProtocolNames(resolvedProtocolNames)
                    if (collapsed.isEmpty()) {
                        null
                    } else {
                        collapsed.joinToString("+")
                    }
                }
            }
            else -> null
        }

    fun displayProtocols(protocolNames: List<String>): String =
        collapseSessionProtocolNames(protocolNames).ifEmpty { listOf(canonicalHttp()) }.joinToString("+")

    private fun displayPath(port: Int): String = ":$port"

    internal fun listenPortRoute(
        element: PsiElement,
        port: Int,
        protocolLabel: String,
    ): ArmeriaRoute =
        ArmeriaRoute.create(
            element = element,
            protocol = protocolLabel,
            httpMethod = "",
            path = displayPath(port),
            target = message("route.explorer.target.listenPort"),
            routeMatch = RouteMatch.LISTEN_PORT,
            excludeFromDuplicateIndex = true,
        )

    internal fun extractJavaPort(expression: PsiExpression?): Int? {
        val number = extractJavaNumber(expression, hops = 0, visited = mutableSetOf()) ?: return null
        return number.toInt().takeIf(::isValidPort)
    }

    internal fun isValidPort(port: Int): Boolean = port in 1..65535

    internal fun parseIntLiteral(text: String): Int? {
        var cleaned = text.replace("_", "").trim()
        cleaned = cleaned.trimEnd { it == 'u' || it == 'U' || it == 'l' || it == 'L' }
        if (cleaned.isEmpty()) {
            return null
        }
        return when {
            cleaned.startsWith("0x", ignoreCase = true) -> cleaned.substring(2).toIntOrNull(16)
            cleaned.startsWith("0b", ignoreCase = true) -> cleaned.substring(2).toIntOrNull(2)
            else -> cleaned.toIntOrNull()
        }
    }

    internal fun resolveJavaSessionProtocol(expression: PsiExpression): String? {
        val unwrapped = unwrapJavaExpression(expression) ?: return null
        when (unwrapped) {
            is PsiLiteralExpression -> {
                val literal = unwrapped.value as? String ?: return null
                return literal.uppercase()
            }
            is PsiReferenceExpression -> {
                val resolved = unwrapped.resolve()
                if (resolved is PsiField && isSessionProtocolClass(resolved.containingClass)) {
                    return resolved.name
                }
                val name = unwrapped.referenceName ?: return null
                if (qualifierIsSessionProtocol(unwrapped.qualifierExpression)) {
                    return name
                }
                return null
            }
            else -> return null
        }
    }

    internal fun isSessionProtocolClass(psiClass: PsiClass?): Boolean {
        psiClass ?: return false
        return psiClass.qualifiedName == SESSION_PROTOCOL_CLASS ||
            psiClass.name == SESSION_PROTOCOL_SIMPLE_NAME
    }

    private fun qualifierIsSessionProtocol(qualifier: PsiExpression?): Boolean {
        val unwrapped = unwrapJavaExpression(qualifier) ?: return false
        if (unwrapped is PsiReferenceExpression) {
            val resolved = unwrapped.resolve()
            if (resolved is PsiClass && isSessionProtocolClass(resolved)) {
                return true
            }
            if (unwrapped.referenceName == SESSION_PROTOCOL_SIMPLE_NAME) {
                return true
            }
        }
        return false
    }

    private fun collapseSessionProtocolNames(protocolNames: List<String>): List<String> {
        var hasProxy = false
        var hasHttp = false
        var hasHttps = false
        for (raw in protocolNames) {
            val token = raw.substringAfterLast('.').uppercase()
            if (token.isEmpty()) {
                continue
            }
            when {
                token.startsWith("PROXY") -> {
                    hasProxy = true
                    if (token.contains("HTTPS")) {
                        hasHttps = true
                    } else if (token.contains("HTTP")) {
                        hasHttp = true
                    }
                }
                token in HTTPS_TOKENS -> hasHttps = true
                token in HTTP_TOKENS -> hasHttp = true
            }
        }
        return buildList {
            if (hasProxy) {
                add(canonicalProxy())
            }
            if (hasHttp) {
                add(canonicalHttp())
            }
            if (hasHttps) {
                add(canonicalHttps())
            }
        }
    }

    private fun canonicalHttp(): String = message("route.explorer.listenPort.http")

    private fun canonicalHttps(): String = message("route.explorer.listenPort.https")

    private fun canonicalProxy(): String = message("route.explorer.listenPort.proxy")

    private fun unwrapJavaExpression(expression: PsiExpression?): PsiExpression? {
        var current = expression ?: return null
        var hops = 0
        while (current is PsiParenthesizedExpression && hops < MAX_INITIALIZER_HOPS) {
            current = current.expression ?: return null
            hops++
        }
        return current
    }

    private fun extractJavaNumber(
        expression: PsiExpression?,
        hops: Int,
        visited: MutableSet<PsiElement>,
    ): Number? {
        if (expression == null || hops > MAX_INITIALIZER_HOPS) {
            return null
        }
        val unwrapped = unwrapJavaExpression(expression) ?: return null
        when (unwrapped) {
            is PsiLiteralExpression -> (unwrapped.value as? Number)?.let { return it }
            is PsiReferenceExpression -> {
                val resolved = unwrapped.resolve() as? PsiVariable ?: return null
                if (!visited.add(resolved)) {
                    return null
                }
                when (val value = resolved.computeConstantValue()) {
                    is Number -> return value
                }
                return extractJavaNumber(resolved.initializer, hops + 1, visited)
            }
            else -> {
                val constant =
                    JavaPsiFacade
                        .getInstance(unwrapped.project)
                        .constantEvaluationHelper
                        .computeConstantExpression(unwrapped)
                if (constant is Number) {
                    return constant
                }
            }
        }
        return null
    }
}
