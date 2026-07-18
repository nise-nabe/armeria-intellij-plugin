package com.linecorp.intellij.plugins.armeria.explorer

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

internal object ArmeriaKotlinRegistrationChainSupport {
    fun registrationKey(call: KtCallExpression): String? {
        val virtualFile = call.containingKtFile.virtualFile ?: return null
        val methodName = resolveCallName(call) ?: return null
        return ArmeriaRouteSupport.registrationKey(
            virtualFile.path,
            call.textRange,
            methodName,
        )
    }

    fun resolveCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }

    fun toChainStep(call: KtCallExpression): RegistrationChainStep =
        RegistrationChainStep(
            methodName = resolveCallName(call).orEmpty(),
            firstStringArg = ArmeriaKotlinExpressionSupport.extractKotlinString(call.valueArguments.firstOrNull()?.getArgumentExpression()),
            rawMethodArgs = call.valueArguments.mapNotNull { it.getArgumentExpression()?.text },
        )

    fun methodCallsBetweenInStatement(
        start: KtCallExpression,
        stopExclusive: KtCallExpression?,
    ): List<KtCallExpression> {
        val calls = mutableListOf(start)
        var current = start
        while (true) {
            val next = findNextChainedCall(current) ?: break
            if (next === stopExclusive) {
                break
            }
            calls += next
            current = next
        }
        return calls
    }

    fun parentCallExpression(call: KtCallExpression): KtCallExpression? {
        val parent = call.parent
        return when (parent) {
            is KtDotQualifiedExpression -> {
                when (val receiver = parent.receiverExpression) {
                    is KtCallExpression -> receiver
                    is KtDotQualifiedExpression -> receiver.selectorExpression as? KtCallExpression
                    else -> null
                }
            }
            else -> null
        }
    }

    fun findForwardChainedCall(
        start: KtCallExpression,
        predicate: (KtCallExpression) -> Boolean,
    ): KtCallExpression? {
        var current: KtCallExpression? = start
        while (current != null) {
            if (predicate(current)) {
                return current
            }
            current = findNextChainedCall(current)
        }
        return null
    }

    fun findNextChainedCall(call: KtCallExpression): KtCallExpression? {
        val parent = call.parent
        if (parent is KtDotQualifiedExpression && parent.receiverExpression == call) {
            return parent.selectorExpression as? KtCallExpression
        }
        if (parent is KtDotQualifiedExpression) {
            val grandParent = parent.parent as? KtDotQualifiedExpression
            if (grandParent != null && grandParent.receiverExpression == parent) {
                return grandParent.selectorExpression as? KtCallExpression
            }
        }
        return null
    }
}
