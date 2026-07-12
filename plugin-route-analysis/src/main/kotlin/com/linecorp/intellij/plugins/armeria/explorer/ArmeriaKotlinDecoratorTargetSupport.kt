package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument

internal object ArmeriaKotlinDecoratorTargetSupport {
    fun extractKotlinDecoratorCandidate(call: KtCallExpression): ArmeriaDecoratorSupport.DecoratorCandidate? {
        val arguments = call.valueArguments
        val pathPattern = if (arguments.size >= 2) {
            extractKotlinPathPattern(arguments[0].getArgumentExpression())
        } else {
            null
        }
        val decoratorArgument = when {
            arguments.size >= 2 -> arguments[1].getArgumentExpression()
            arguments.isNotEmpty() -> arguments[0].getArgumentExpression()
            else -> return null
        } ?: return null
        return ArmeriaDecoratorSupport.DecoratorCandidate(
            ArmeriaDecoratorSupport.labelDecorator(extractKotlinDecoratorTarget(decoratorArgument)),
            pathPattern,
        )
    }

    fun extractKotlinDecoratorTarget(expression: KtExpression): String {
        val unwrapped = unwrapKotlinDecoratorExpression(expression) ?: return expression.text
        if (unwrapped is KtClassLiteralExpression) {
            val classReceiver = unwrapped.receiverExpression
            if (classReceiver is KtNameReferenceExpression) {
                ArmeriaKotlinDecoratorChainSupport.resolveQualifiedClassName(classReceiver.references.firstOrNull()?.resolve())
                    ?.let { return it }
            }
            return unwrapped.text.removeSuffix("::class")
        }
        if (unwrapped is KtDotQualifiedExpression) {
            val receiver = unwrapped.receiverExpression
            if (receiver is KtClassLiteralExpression) {
                val classReceiver = receiver.receiverExpression
                if (classReceiver is KtNameReferenceExpression) {
                    ArmeriaKotlinDecoratorChainSupport.resolveQualifiedClassName(classReceiver.references.firstOrNull()?.resolve())
                        ?.let { return it }
                }
                return receiver.text.removeSuffix("::class")
            }
            val selector = unwrapped.selectorExpression
            if (selector is KtCallExpression) {
                return extractKotlinDecoratorTargetFromCall(selector, expression)
            }
            ArmeriaKotlinDecoratorChainSupport.resolveQualifiedClassName(receiver.references.firstOrNull()?.resolve())
                ?.let { return it }
        }
        if (unwrapped is KtCallExpression) {
            return extractKotlinDecoratorTargetFromCall(unwrapped, expression)
        }
        if (unwrapped is KtNameReferenceExpression) {
            when (val resolved = unwrapped.references.firstOrNull()?.resolve()) {
                is KtProperty -> {
                    resolved.initializer?.let { return extractKotlinDecoratorTarget(it) }
                    ArmeriaKotlinDecoratorChainSupport.resolveKotlinTypeReferenceText(resolved.typeReference)?.let { return it }
                }
                is PsiVariable -> {
                    resolved.initializer?.let { initializer ->
                        if (initializer is KtExpression) {
                            return extractKotlinDecoratorTarget(initializer)
                        }
                        return ArmeriaRouteTargetExtractor.extractTarget(initializer)
                    }
                    return resolved.type.presentableText
                }
            }
            ArmeriaKotlinDecoratorChainSupport.resolveQualifiedClassName(unwrapped.references.firstOrNull()?.resolve())
                ?.let { return it }
        }
        return expression.text
    }

    private fun extractKotlinDecoratorTargetFromCall(call: KtCallExpression, fallback: KtExpression): String {
        val callee = call.calleeExpression
        val methodName = when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee?.text
        }
        if (methodName == "build") {
            ArmeriaKotlinDecoratorChainSupport.kotlinChainReceiver(call)?.let { receiver ->
                return extractKotlinDecoratorTarget(receiver)
            }
        }
        if (methodName == "builder" || methodName == "newDecorator") {
            ArmeriaKotlinDecoratorChainSupport.kotlinChainReceiver(call)?.let { receiver ->
                val fromReceiver = extractKotlinDecoratorTarget(receiver)
                if (fromReceiver != methodName && fromReceiver != receiver.text) {
                    return fromReceiver
                }
            }
            ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
            val references = when (callee) {
                is KtDotQualifiedExpression -> callee.references.toList()
                is KtNameReferenceExpression -> callee.references.toList()
                else -> emptyList()
            }
            references.firstOrNull()?.resolve()?.let { resolved ->
                ArmeriaKotlinDecoratorChainSupport.resolveQualifiedClassName(resolved)?.let { return it }
            }
        }
        ArmeriaKotlinDecoratorChainSupport.kotlinChainReceiver(call)?.let { receiver ->
            val fromReceiver = extractKotlinDecoratorTarget(receiver)
            if (fromReceiver != methodName && fromReceiver != "build" && fromReceiver != receiver.text) {
                return fromReceiver
            }
        }
        return methodName ?: fallback.text
    }

    private fun unwrapKotlinDecoratorExpression(expression: KtExpression?): KtExpression? {
        var current = expression ?: return null
        while (true) {
            current = when (current) {
                is KtParenthesizedExpression -> current.expression ?: return null
                else -> return current
            }
        }
    }

    private fun extractKotlinPathPattern(expression: KtExpression?): String? {
        val unwrapped = expression ?: return null
        return when (unwrapped) {
            is KtStringTemplateExpression -> {
                if (unwrapped.entries.size == 1) {
                    unwrapped.entries[0].text.trim('"')
                } else {
                    unwrapped.text.trim('"')
                }
            }
            is KtDotQualifiedExpression -> extractKotlinPathPatternFromReference(unwrapped)
            is KtNameReferenceExpression -> extractKotlinPathPatternFromReference(unwrapped)
            else -> unwrapped.text.trim().trim('"').takeIf { it.isNotEmpty() }
        }
    }

    private fun extractKotlinPathPatternFromReference(expression: KtExpression): String? {
        val resolved = expression.references.firstOrNull()?.resolve()
        when (resolved) {
            is KtProperty -> extractKotlinPathPattern(resolved.initializer)?.let { return it }
            is PsiVariable -> ArmeriaRouteSupport.evaluateJavaStringConstant(resolved)?.let { return it }
        }
        if (expression is KtDotQualifiedExpression) {
            val selector = expression.selectorExpression as? KtNameReferenceExpression ?: return null
            val receiver = expression.receiverExpression as? KtNameReferenceExpression ?: return null
            val containingClass = receiver.references.firstOrNull()?.resolve() as? com.intellij.psi.PsiClass
                ?: return null
            val field = containingClass.findFieldByName(selector.getReferencedName(), true)
            if (field != null) {
                ArmeriaRouteSupport.evaluateJavaStringConstant(field)?.let { return it }
            }
        }
        return expression.text.trim().trim('"').takeIf { it.isNotEmpty() }
    }
}
