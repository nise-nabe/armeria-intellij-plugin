package com.linecorp.intellij.plugins.armeria.run

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Resolves `Server.builder().http/.https/.port` listen ports from Kotlin sources.
 * Loaded only when the Kotlin plugin is available.
 */
internal object ArmeriaKotlinServerListenPortSupport {
    private val LISTEN_METHODS = setOf("http", "https", "port")
    private val HTTPS_TOKEN = Regex("""\bHTTPS\b""")
    private val HTTP_TOKEN = Regex("""\bHTTP\b|\bH1C\b|\bH2C\b|\bH1\b""")

    fun extractFromFile(file: PsiFile): ArmeriaListenEndpoint? {
        val calls = PsiTreeUtil.findChildrenOfType(file, KtCallExpression::class.java)
        val candidates = mutableListOf<ArmeriaServerListenPortSupport.ListenCandidate>()
        for (call in calls) {
            val methodName = callName(call) ?: continue
            if (methodName !in LISTEN_METHODS) {
                continue
            }
            if (!looksLikeServerBuilder(call)) {
                continue
            }
            val port = extractPort(call.valueArguments.firstOrNull()?.getArgumentExpression()) ?: continue
            val https =
                when (methodName) {
                    "https" -> true
                    "http" -> false
                    else -> extraArgsSuggestHttps(call)
                }
            candidates +=
                ArmeriaServerListenPortSupport.ListenCandidate(
                    port = port,
                    https = https,
                    kind = kindFor(methodName),
                )
        }
        return ArmeriaServerListenPortSupport.pick(candidates)
    }

    private fun callName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtNameReferenceExpression -> callee.getReferencedName()
            is KtDotQualifiedExpression -> (callee.selectorExpression as? KtNameReferenceExpression)?.getReferencedName()
            else -> callee.text
        }
    }

    private fun looksLikeServerBuilder(call: KtCallExpression): Boolean {
        val parent = call.parent as? KtDotQualifiedExpression ?: return false
        var receiver: KtExpression? = parent.receiverExpression
        var hops = 0
        while (receiver != null && hops < 32) {
            val text = receiver.text
            if (text == "Server.builder()" ||
                text.endsWith(".Server.builder()") ||
                text.contains("Server.builder()")
            ) {
                return true
            }
            receiver =
                when (receiver) {
                    is KtDotQualifiedExpression -> receiver.receiverExpression
                    else -> null
                }
            hops++
        }
        return false
    }

    private fun extraArgsSuggestHttps(call: KtCallExpression): Boolean {
        val extras = call.valueArguments.drop(1)
        if (extras.isEmpty()) {
            return false
        }
        val blob = extras.joinToString(" ") { it.getArgumentExpression()?.text.orEmpty() }
        return HTTPS_TOKEN.containsMatchIn(blob) && !HTTP_TOKEN.containsMatchIn(blob)
    }

    private fun extractPort(expression: KtExpression?): Int? {
        val unwrapped = unwrap(expression) ?: return null
        when (unwrapped) {
            is KtConstantExpression -> unwrapped.text.toIntOrNull()?.let { return it.takeIf(::isValidPort) }
            is KtNameReferenceExpression -> {
                val resolved = unwrapped.references.firstOrNull()?.resolve()
                if (resolved is KtProperty) {
                    extractPort(resolved.initializer)?.let { return it }
                }
            }
        }
        return unwrapped.text.toIntOrNull()?.takeIf(::isValidPort)
    }

    private fun unwrap(expression: KtExpression?): KtExpression? {
        var current = expression ?: return null
        var hops = 0
        while (hops < 8) {
            val parenthesized = current as? KtParenthesizedExpression
            current = parenthesized?.expression ?: return current
            hops++
        }
        return current
    }

    private fun kindFor(methodName: String): ArmeriaServerListenPortSupport.ListenKind =
        when (methodName) {
            "http" -> ArmeriaServerListenPortSupport.ListenKind.HTTP
            "https" -> ArmeriaServerListenPortSupport.ListenKind.HTTPS
            else -> ArmeriaServerListenPortSupport.ListenKind.PORT
        }

    private fun isValidPort(port: Int): Boolean = port in 1..65535
}
