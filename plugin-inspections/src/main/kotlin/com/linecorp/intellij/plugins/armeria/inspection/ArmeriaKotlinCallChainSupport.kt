package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiElement
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression

internal object ArmeriaKotlinCallChainSupport {
    fun asCall(expression: KtExpression): KtCallExpression? =
        when (expression) {
            is KtCallExpression -> expression
            is KtDotQualifiedExpression -> expression.selectorExpression as? KtCallExpression
            else -> null
        }

    fun chainRoot(call: KtCallExpression): PsiElement {
        var current: PsiElement = call
        while (current.parent is KtDotQualifiedExpression) {
            current = current.parent
        }
        return current
    }

    fun outermostChainCall(call: KtCallExpression): KtCallExpression {
        var current = call
        val visited = mutableSetOf<PsiElement>()
        while (visited.add(current)) {
            current = nextChainedCall(current) ?: break
        }
        return current
    }

    fun chainCalls(
        builder: KtCallExpression,
        outermost: KtCallExpression,
    ): List<KtCallExpression> {
        val calls = mutableListOf<KtCallExpression>()
        var current: KtCallExpression? = builder
        val visited = mutableSetOf<PsiElement>()
        while (current != null && visited.add(current)) {
            calls += current
            if (current == outermost) {
                break
            }
            current = nextChainedCall(current)
        }
        return calls
    }

    private fun nextChainedCall(call: KtCallExpression): KtCallExpression? {
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

    fun chainReceiver(expression: KtExpression): KtExpression? {
        val receiver =
            when (expression) {
                is KtDotQualifiedExpression -> {
                    expression.receiverExpression
                }

                is KtCallExpression -> {
                    val parent = expression.parent
                    if (parent is KtDotQualifiedExpression && parent.selectorExpression == expression) {
                        parent.receiverExpression
                    } else {
                        when (val callee = expression.calleeExpression) {
                            is KtDotQualifiedExpression -> callee.receiverExpression
                            else -> null
                        }
                    }
                }

                else -> {
                    null
                }
            } ?: return null
        return ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(receiver) ?: receiver
    }
}
