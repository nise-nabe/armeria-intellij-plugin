package com.linecorp.intellij.plugins.armeria.run

import com.intellij.psi.PsiElement
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
    private val SERVER_BUILDER_CALL = Regex("""\bServer\.builder\(\)""")
    private val SCOPE_METHODS = setOf("apply", "run", "also", "let")

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
        val parent = call.parent as? KtDotQualifiedExpression
        var receiver: KtExpression? = parent?.receiverExpression
        var hops = 0
        while (receiver != null && hops < 32) {
            if (SERVER_BUILDER_CALL.containsMatchIn(receiver.text)) {
                return true
            }
            receiver =
                when (receiver) {
                    is KtDotQualifiedExpression -> receiver.receiverExpression
                    is KtNameReferenceExpression -> {
                        if (propertyInitializerLooksLikeServerBuilder(receiver)) {
                            return true
                        }
                        null
                    }
                    else -> null
                }
            hops++
        }
        return hasServerBuilderScopeReceiver(call)
    }

    private fun hasServerBuilderScopeReceiver(call: KtCallExpression): Boolean {
        var current: PsiElement? = call.parent
        var hops = 0
        while (current != null && hops < 32) {
            if (current is KtCallExpression) {
                val name = callName(current)
                if (name in SCOPE_METHODS) {
                    val qualified = current.parent as? KtDotQualifiedExpression
                    val receiver = qualified?.receiverExpression
                    if (receiverLooksLikeServerBuilder(receiver)) {
                        return true
                    }
                }
            }
            current = current.parent
            hops++
        }
        return false
    }

    private fun receiverLooksLikeServerBuilder(receiver: KtExpression?): Boolean {
        if (receiver == null) {
            return false
        }
        if (SERVER_BUILDER_CALL.containsMatchIn(receiver.text)) {
            return true
        }
        return (receiver as? KtNameReferenceExpression)?.let(::propertyInitializerLooksLikeServerBuilder) == true
    }

    private fun propertyInitializerLooksLikeServerBuilder(receiver: KtNameReferenceExpression): Boolean {
        val resolved = receiver.references.firstOrNull()?.resolve()
        if (resolved !is KtProperty) {
            return false
        }
        val initializer = resolved.initializer ?: return false
        return SERVER_BUILDER_CALL.containsMatchIn(initializer.text)
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
