package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType

internal object ArmeriaKotlinTimeoutSupport {
    fun collectBuilderTimeoutHints(element: PsiElement): List<String> {
        val registrationCall = element as? KtCallExpression ?: return emptyList()
        val hints = mutableListOf<String>()
        collectTimeoutCallsFromReceiver(registrationCall, hints)
        return hints.distinct()
    }

    private fun collectTimeoutCallsFromReceiver(
        expression: KtExpression,
        hints: MutableList<String>,
    ) {
        var current: KtExpression? = kotlinChainReceiver(expression)
        while (current != null) {
            val call = asKotlinCallExpression(current)
            if (call != null && resolvesToArmeriaServerBuilder(call)) {
                when (resolveKotlinCallName(call)) {
                    "requestTimeout" -> hints += formatKotlinTimeoutCall("route.explorer.timeout.request", call)
                    "responseTimeout" -> hints += formatKotlinTimeoutCall("route.explorer.timeout.response", call)
                    "idleTimeout" -> hints += formatKotlinTimeoutCall("route.explorer.timeout.idle", call)
                }
            }
            current = kotlinChainReceiver(current)
        }
    }

    private fun kotlinChainReceiver(expression: KtExpression): KtExpression? {
        val unwrapped = unwrapKotlinExpression(expression)
        return when (unwrapped) {
            is KtDotQualifiedExpression -> unwrapped.receiverExpression
            is KtCallExpression -> {
                val parent = unwrapped.parent
                if (parent is KtDotQualifiedExpression && parent.selectorExpression == unwrapped) {
                    parent.receiverExpression
                } else {
                    when (val callee = unwrapped.calleeExpression) {
                        is KtDotQualifiedExpression -> callee.receiverExpression
                        else -> null
                    }
                }
            }
            else -> null
        }
    }

    private fun unwrapKotlinExpression(expression: KtExpression): KtExpression =
        when (expression) {
            is KtParenthesizedExpression -> expression.expression?.let(::unwrapKotlinExpression) ?: expression
            else -> expression
        }

    private fun asKotlinCallExpression(expression: KtExpression): KtCallExpression? =
        when (val unwrapped = unwrapKotlinExpression(expression)) {
            is KtCallExpression -> unwrapped
            is KtDotQualifiedExpression -> unwrapped.selectorExpression as? KtCallExpression
            else -> null
        }

    private fun resolveKotlinCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }

    private fun resolvesToArmeriaServerBuilder(call: KtCallExpression): Boolean {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val dotQualified = call.parent as? KtDotQualifiedExpression
        if (dotQualified != null) {
            for (reference in dotQualified.references) {
                if (isArmeriaServerBuilderMethod(reference.resolve())) {
                    return true
                }
            }
        }
        val callee = call.calleeExpression ?: return false
        val references =
            when (callee) {
                is KtNameReferenceExpression -> callee.references.toList()
                is KtDotQualifiedExpression -> callee.references.toList()
                else -> emptyList()
            }
        if (references.any { isArmeriaServerBuilderMethod(it.resolve()) }) {
            return true
        }
        val receiver =
            when (callee) {
                is KtDotQualifiedExpression -> callee.receiverExpression
                else -> dotQualified?.receiverExpression
            }
        return receiver != null && isKotlinServerBuilderReceiver(receiver)
    }

    private fun isArmeriaServerBuilderMethod(resolved: PsiElement?): Boolean =
        resolved is PsiMethod &&
            resolved.containingClass?.qualifiedName?.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX) == true

    private fun isKotlinServerBuilderReceiver(receiver: KtExpression): Boolean {
        val receiverExpression = unwrapKotlinExpression(receiver)
        if (ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(receiverExpression.text)) {
            return true
        }
        if (receiverExpression is KtNameReferenceExpression) {
            when (val resolved = receiverExpression.references.firstOrNull()?.resolve()) {
                is PsiVariable -> {
                    if (ArmeriaRouteSupport.isServerBuilderType(resolved.type.canonicalText)) {
                        return true
                    }
                }
                is KtProperty -> {
                    val typeText = resolveKotlinTypeReferenceText(resolved.typeReference)
                    if (typeText != null && ArmeriaRouteSupport.isServerBuilderType(typeText)) {
                        return true
                    }
                    val initializerText = resolved.initializer?.text
                    if (initializerText != null &&
                        ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(initializerText)
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun resolveKotlinTypeReferenceText(typeReference: KtTypeReference?): String? {
        if (typeReference == null) {
            return null
        }
        val userType = typeReference.typeElement as? KtUserType
        return userType?.referenceExpression?.references?.firstOrNull()?.resolve()?.let { resolved ->
            when (resolved) {
                is org.jetbrains.kotlin.psi.KtClass -> resolved.fqName?.asString()
                else -> typeReference.text
            }
        } ?: typeReference.text
    }

    private fun formatKotlinTimeoutCall(
        bundleKey: String,
        call: KtCallExpression,
    ): String {
        val argument = call.valueArguments.firstOrNull()?.getArgumentExpression()
        val value = argument?.text ?: "…"
        return message(bundleKey, value)
    }
}
