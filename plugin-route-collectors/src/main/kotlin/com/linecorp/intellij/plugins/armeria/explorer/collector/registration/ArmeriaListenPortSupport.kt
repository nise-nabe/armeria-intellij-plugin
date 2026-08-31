package com.linecorp.intellij.plugins.armeria.explorer.collector.registration

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaListenPortSupport {
    const val HTTP_METHOD = "http"
    const val HTTPS_METHOD = "https"
    const val PORT_METHOD = "port"

    val METHOD_NAMES: Set<String> = setOf(HTTP_METHOD, HTTPS_METHOD, PORT_METHOD)

    private val HTTP_TOKENS = setOf("HTTP", "H1C", "H2C", "H1C_OR_H2C")
    private val HTTPS_TOKENS = setOf("HTTPS", "H1", "H2", "H1_OR_H2")
    private val TOKEN_CLEANUP = Regex("""[^A-Za-z0-9_]+""")

    fun protocolLabel(
        methodName: String,
        extraArgTexts: List<String>,
    ): String = protocolLabels(methodName, extraArgTexts).joinToString("+")

    fun protocolLabels(
        methodName: String,
        extraArgTexts: List<String>,
    ): List<String> {
        val canonical =
            when (methodName) {
                HTTPS_METHOD -> listOf(canonicalHttps())
                HTTP_METHOD -> listOf(canonicalHttp())
                else -> collapseSessionProtocols(extraArgTexts).ifEmpty { listOf(canonicalHttp()) }
            }
        return canonical
    }

    fun displayPath(port: Int): String = ":$port"

    fun extractJavaPort(expression: PsiExpression?): Int? {
        val number = extractJavaNumber(expression, hops = 0) ?: return null
        return number.toInt().takeIf(::isValidPort)
    }

    fun isValidPort(port: Int): Boolean = port in 1..65535

    private fun collapseSessionProtocols(extraArgTexts: List<String>): List<String> {
        var hasProxy = false
        var hasHttp = false
        var hasHttps = false
        for (text in extraArgTexts) {
            val token = sessionProtocolToken(text) ?: continue
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

    private fun sessionProtocolToken(text: String): String? {
        val raw = text.substringAfterLast('.').uppercase()
        val cleaned = TOKEN_CLEANUP.replace(raw, "")
        return cleaned.takeIf { it.isNotEmpty() && it != "SESSIONPROTOCOL" }
    }

    private fun canonicalHttp(): String = message("route.explorer.listenPort.http")

    private fun canonicalHttps(): String = message("route.explorer.listenPort.https")

    private fun canonicalProxy(): String = message("route.explorer.listenPort.proxy")

    private fun extractJavaNumber(
        expression: PsiExpression?,
        hops: Int,
    ): Number? {
        if (expression == null || hops > 8) {
            return null
        }
        when (expression) {
            is PsiLiteralExpression -> (expression.value as? Number)?.let { return it }
            is PsiReferenceExpression -> {
                val resolved = expression.resolve() as? PsiVariable
                when (val value = resolved?.computeConstantValue()) {
                    is Number -> return value
                }
                resolved?.initializer?.let { initializer ->
                    extractJavaNumber(initializer, hops + 1)?.let { return it }
                }
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
