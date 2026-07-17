package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty

internal object ArmeriaKotlinClientDecoratorSupport {
    fun collectKotlinClientDecorators(factoryCall: KtCallExpression): List<String> {
        val decorators = linkedSetOf<String>()
        collectKotlinDecoratorsOnForwardChain(factoryCall, decorators)
        collectKotlinDecoratorsFromReceiver(factoryCall, decorators)
        return decorators.toList()
    }

    private fun collectKotlinDecoratorsOnForwardChain(
        factoryCall: KtCallExpression,
        decorators: LinkedHashSet<String>,
    ) {
        var current: KtCallExpression = factoryCall
        while (true) {
            val next = findNextChainedCall(current) ?: break
            if (next === current) {
                break
            }
            if (isKotlinClientDecoratorCall(next)) {
                extractKotlinDecoratorLabel(next)?.let { decorators += it }
            }
            current = next
        }
    }

    private fun collectKotlinDecoratorsFromReceiver(
        expression: KtExpression,
        decorators: LinkedHashSet<String>,
    ) {
        var current: KtExpression? = kotlinChainReceiver(expression)
        while (current != null) {
            val decoratorCall = asKotlinCallExpression(current)
            if (decoratorCall != null && isKotlinClientDecoratorCall(decoratorCall)) {
                extractKotlinDecoratorLabel(decoratorCall)?.let { decorators += it }
            }
            val next = kotlinChainReceiver(current)
            current = if (next === current) null else next
        }
    }

    private fun findNextChainedCall(call: KtCallExpression): KtCallExpression? {
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

    private fun asKotlinCallExpression(expression: KtExpression): KtCallExpression? {
        return when (expression) {
            is KtCallExpression -> expression
            is KtDotQualifiedExpression -> expression.selectorExpression as? KtCallExpression
            else -> null
        }
    }

    private fun isKotlinClientDecoratorCall(call: KtCallExpression): Boolean {
        if (resolveKotlinCallName(call) != "decorator") {
            return false
        }
        val references = call.calleeExpression?.references?.toList().orEmpty()
        for (reference in references) {
            val resolved = reference.resolve()
            val containingClass = when (resolved) {
                is PsiMethod -> resolved.containingClass?.qualifiedName
                else -> null
            }
            if (containingClass?.startsWith(ArmeriaClientSupport.ARMERIA_CLIENT_PACKAGE_PREFIX) == true) {
                return true
            }
        }
        val receiverText = kotlinChainReceiver(call)?.text ?: return false
        return ArmeriaClientSupport.looksLikeClientBuilderReceiverText(receiverText)
    }

    private fun extractKotlinDecoratorLabel(call: KtCallExpression): String? {
        val decoratorArgument = call.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null
        val target = extractKotlinDecoratorTarget(decoratorArgument)
        return ArmeriaClientDecoratorSupport.labelClientDecorator(target)
    }

    private fun extractKotlinDecoratorTarget(expression: KtExpression): String {
        return when (expression) {
            is KtCallExpression -> {
                when (val callee = expression.calleeExpression) {
                    is KtDotQualifiedExpression -> callee.receiverExpression.text
                    else -> expression.text
                }
            }
            is KtDotQualifiedExpression -> expression.receiverExpression.text
            is KtNameReferenceExpression -> {
                val resolved = expression.references.firstOrNull()?.resolve()
                when (resolved) {
                    is KtProperty -> resolved.initializer?.text ?: expression.text
                    is PsiVariable -> resolved.initializer?.text ?: expression.text
                    else -> expression.text
                }
            }
            else -> expression.text
        }
    }

    private fun resolveKotlinCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }

    private fun kotlinChainReceiver(expression: KtExpression): KtExpression? {
        val receiver = when (expression) {
            is KtDotQualifiedExpression -> expression.receiverExpression
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
            else -> null
        } ?: return null
        return unwrapKotlinExpression(receiver)
    }

    private fun unwrapKotlinExpression(expression: KtExpression): KtExpression {
        var current = expression
        while (current is KtParenthesizedExpression) {
            current = current.expression ?: return expression
        }
        return current
    }
}
