package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaClientDecoratorSupport {
    private val KNOWN_CLIENT_DECORATOR_BUNDLE_KEYS = mapOf(
        "LoggingClient" to "client.explorer.decorator.logging",
        "BraveClient" to "client.explorer.decorator.brave",
        "RetryingClient" to "client.explorer.decorator.retrying",
        "CircuitBreakerClient" to "client.explorer.decorator.circuitBreaker",
    )

    fun collectJavaClientDecorators(factoryCall: PsiMethodCallExpression): List<String> {
        val decorators = linkedSetOf<String>()
        collectJavaDecoratorsOnForwardChain(factoryCall, decorators)
        collectJavaDecoratorsFromQualifier(factoryCall.methodExpression.qualifierExpression, decorators)
        return decorators.toList()
    }

    fun labelClientDecorator(raw: String): String {
        val normalized = raw.removeSuffix("::class.java").removeSuffix("::class").removeSuffix(".class")
        val simpleName = normalized.substringAfterLast('.').removeSuffix("()")
        val bundleKey = KNOWN_CLIENT_DECORATOR_BUNDLE_KEYS[simpleName]
        return if (bundleKey != null) message(bundleKey) else simpleName
    }

    private fun collectJavaDecoratorsOnForwardChain(
        factoryCall: PsiMethodCallExpression,
        decorators: LinkedHashSet<String>,
    ) {
        var current: PsiExpression = factoryCall
        while (true) {
            val parent = findEnclosingQualifierCall(current) ?: break
            if (isJavaClientDecoratorCall(parent)) {
                extractJavaDecoratorLabel(parent)?.let { decorators += it }
            }
            current = parent
        }
    }

    private fun findEnclosingQualifierCall(expression: PsiExpression): PsiMethodCallExpression? {
        var element: PsiElement? = expression.parent
        while (element != null) {
            if (element is PsiMethodCallExpression &&
                element.methodExpression.qualifierExpression == expression
            ) {
                return element
            }
            element = element.parent
        }
        return null
    }

    private fun collectJavaDecoratorsFromQualifier(
        qualifier: PsiExpression?,
        decorators: LinkedHashSet<String>,
    ) {
        var current: PsiExpression? = qualifier
        while (current != null) {
            when (current) {
                is PsiMethodCallExpression -> {
                    if (isJavaClientDecoratorCall(current)) {
                        extractJavaDecoratorLabel(current)?.let { decorators += it }
                    }
                    current = current.methodExpression.qualifierExpression
                }
                is PsiReferenceExpression -> {
                    val resolved = current.resolve()
                    current = if (resolved is PsiVariable) {
                        resolved.initializer
                    } else {
                        null
                    }
                }
                else -> break
            }
        }
    }

    private fun isJavaClientDecoratorCall(expression: PsiMethodCallExpression): Boolean {
        if (expression.methodExpression.referenceName != "decorator") {
            return false
        }
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
        if (resolvedClass != null) {
            return resolvedClass.startsWith(ArmeriaClientSupport.ARMERIA_CLIENT_PACKAGE_PREFIX)
        }
        val qualifierText = expression.methodExpression.qualifierExpression?.text ?: return false
        return ArmeriaClientSupport.looksLikeClientBuilderReceiverText(qualifierText)
    }

    private fun extractJavaDecoratorLabel(expression: PsiMethodCallExpression): String? {
        val decoratorArgument = expression.argumentList.expressions.firstOrNull() ?: return null
        val target = when (decoratorArgument) {
            is PsiClassObjectAccessExpression -> decoratorArgument.text
            is PsiLiteralExpression -> decoratorArgument.value?.toString().orEmpty()
            is PsiMethodCallExpression -> {
                decoratorArgument.methodExpression.qualifierExpression?.text
                    ?: decoratorArgument.methodExpression.referenceName.orEmpty()
            }
            else -> decoratorArgument.text.trim()
        }
        return labelClientDecorator(target)
    }
}
