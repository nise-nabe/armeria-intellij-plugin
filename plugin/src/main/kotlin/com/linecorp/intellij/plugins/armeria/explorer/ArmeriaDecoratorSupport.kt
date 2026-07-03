package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaDecoratorSupport {
    private val KNOWN_DECORATOR_LABELS = mapOf(
        "LoggingService" to "Logging",
        "CorsService" to "CORS",
        "AuthService" to "Auth",
        "MetricCollectingService" to "Metrics",
        "EncodingService" to "Encoding",
        "DecodingService" to "Decoding",
        "CacheControlDecorator" to "Cache-Control",
        "WebSocketService" to "WebSocket",
    )

    fun collectProgrammaticDecorators(element: PsiElement): List<String> {
        return when (element) {
            is PsiMethodCallExpression -> collectJavaDecoratorsOnBuilderChain(element)
            is KtCallExpression -> collectKotlinDecoratorsOnBuilderChain(element)
            else -> emptyList()
        }
    }

    fun labelDecorator(raw: String): String {
        val normalized = raw.removeSuffix("::class.java").removeSuffix(".class")
        val simpleName = normalized.substringAfterLast('.')
        return KNOWN_DECORATOR_LABELS.entries.firstOrNull { (key, _) -> simpleName == key || simpleName.endsWith(key) }?.value
            ?: simpleName
    }

    private fun collectJavaDecoratorsOnBuilderChain(registrationCall: PsiMethodCallExpression): List<String> {
        val decorators = linkedSetOf<String>()
        collectJavaDecoratorsFromQualifier(registrationCall.methodExpression.qualifierExpression, decorators)
        return decorators.toList()
    }

    private fun collectJavaDecoratorsFromQualifier(
        qualifier: PsiExpression?,
        decorators: LinkedHashSet<String>,
    ) {
        var current: PsiExpression? = qualifier
        while (current != null) {
            when (current) {
                is PsiMethodCallExpression -> {
                    if (isArmeriaDecoratorCall(current)) {
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

    private fun isArmeriaDecoratorCall(expression: PsiMethodCallExpression): Boolean {
        if (expression.methodExpression.referenceName != "decorator") {
            return false
        }
        return resolvesToArmeriaServerBuilder(expression)
    }

    private fun resolvesToArmeriaServerBuilder(expression: PsiMethodCallExpression): Boolean {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
        if (resolvedClass?.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX) == true) {
            return true
        }
        val qualifierText = expression.methodExpression.qualifierExpression?.text ?: return false
        return ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(qualifierText)
    }

    private fun extractJavaDecoratorLabel(expression: PsiMethodCallExpression): String? {
        val arguments = expression.argumentList.expressions
        val decoratorArgument = when {
            arguments.size >= 2 -> arguments[1]
            arguments.isNotEmpty() -> arguments[0]
            else -> return null
        }
        val target = when (decoratorArgument) {
            is PsiClassObjectAccessExpression -> decoratorArgument.text
            else -> ArmeriaRouteTargetExtractor.extractTarget(decoratorArgument)
        }
        return labelDecorator(target)
    }

    private fun collectKotlinDecoratorsOnBuilderChain(registrationCall: KtCallExpression): List<String> {
        val decorators = linkedSetOf<String>()
        collectKotlinDecoratorsFromReceiver(registrationCall, decorators)
        collectKotlinDecoratorsInApplyScope(registrationCall, decorators)
        return decorators.toList()
    }

    private fun collectKotlinDecoratorsFromReceiver(
        expression: KtExpression,
        decorators: LinkedHashSet<String>,
    ) {
        var current: KtExpression? = kotlinChainReceiver(expression)
        while (current != null) {
            val decoratorCall = asKotlinCallExpression(current)
            if (decoratorCall != null && isKotlinArmeriaDecoratorCall(decoratorCall)) {
                extractKotlinDecoratorLabel(decoratorCall)?.let { decorators += it }
            }
            current = kotlinChainReceiver(current)
        }
    }

    private fun asKotlinCallExpression(expression: KtExpression): KtCallExpression? {
        return when (expression) {
            is KtCallExpression -> expression
            is KtDotQualifiedExpression -> expression.selectorExpression as? KtCallExpression
            else -> null
        }
    }

    private fun kotlinChainReceiver(expression: KtExpression): KtExpression? {
        val parent = expression.parent
        if (parent is KtDotQualifiedExpression && parent.selectorExpression == expression) {
            return parent.receiverExpression
        }
        if (expression is KtCallExpression) {
            val callee = expression.calleeExpression
            if (callee is KtDotQualifiedExpression) {
                return callee.receiverExpression
            }
        }
        return null
    }

    private fun isKotlinArmeriaDecoratorCall(call: KtCallExpression): Boolean {
        if (resolveKotlinCallName(call) != "decorator") {
            return false
        }
        val dotQualifiedParent = call.parent as? KtDotQualifiedExpression
        if (dotQualifiedParent != null && isKotlinServerBuilderReceiver(dotQualifiedParent.receiverExpression)) {
            return true
        }
        if (hasServerBuilderImplicitReceiver(call)) {
            return true
        }
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val callee = call.calleeExpression
        val references = when (callee) {
            is KtNameReferenceExpression -> callee.references.toList()
            is KtDotQualifiedExpression -> callee.references.toList()
            else -> emptyList()
        }
        if (references.any { reference ->
                val resolved = reference.resolve()
                resolved is PsiMethod &&
                    resolved.containingClass?.qualifiedName?.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX) == true
            }) {
            return true
        }
        val receiver = when (callee) {
            is KtDotQualifiedExpression -> callee.receiverExpression
            else -> dotQualifiedParent?.receiverExpression
        }
        return receiver != null && isKotlinServerBuilderReceiver(receiver)
    }

    private fun isKotlinServerBuilderReceiver(receiver: KtExpression): Boolean {
        if (ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(receiver.text)) {
            return true
        }
        if (receiver is KtNameReferenceExpression) {
            when (val resolved = receiver.references.firstOrNull()?.resolve()) {
                is PsiVariable -> {
                    if (ArmeriaRouteSupport.isServerBuilderType(resolved.type.canonicalText)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun extractKotlinDecoratorLabel(call: KtCallExpression): String? {
        val arguments = call.valueArguments
        val decoratorArgument = when {
            arguments.size >= 2 -> arguments[1].getArgumentExpression()
            arguments.isNotEmpty() -> arguments[0].getArgumentExpression()
            else -> return null
        } ?: return null
        return labelDecorator(renderKotlinDecoratorArgument(decoratorArgument))
    }

    private fun renderKotlinDecoratorArgument(expression: KtExpression): String {
        return expression.text
    }

    private fun hasServerBuilderImplicitReceiver(call: KtCallExpression): Boolean {
        val lambda = call.getParentOfType<KtLambdaExpression>(strict = true) ?: return false
        val lambdaArgument = lambda.parent as? KtValueArgument ?: return false
        val scopeCall = lambdaArgument.parent as? KtCallExpression ?: return false
        if (resolveKotlinCallName(scopeCall) !in APPLY_SCOPE_METHODS) {
            return false
        }
        val scopeReceiver = when (val callee = scopeCall.calleeExpression) {
            is KtDotQualifiedExpression -> callee.receiverExpression
            else -> (scopeCall.parent as? KtDotQualifiedExpression)?.receiverExpression
        } ?: return false
        return isKotlinServerBuilderReceiver(scopeReceiver)
    }

    private fun collectKotlinDecoratorsInApplyScope(
        registrationCall: KtCallExpression,
        decorators: LinkedHashSet<String>,
    ) {
        val lambda = registrationCall.getParentOfType<KtLambdaExpression>(strict = true) ?: return
        val lambdaArgument = lambda.parent as? KtValueArgument ?: return
        val scopeCall = lambdaArgument.parent as? KtCallExpression ?: return
        val scopeMethod = resolveKotlinCallName(scopeCall) ?: return
        if (scopeMethod !in APPLY_SCOPE_METHODS) {
            return
        }
        val registrationOffset = registrationCall.textRange.startOffset
        lambda.bodyExpression?.statements?.forEach { statement ->
            if (statement.textRange.startOffset >= registrationOffset) {
                return
            }
            statement.accept(object : KtTreeVisitorVoid() {
                override fun visitCallExpression(expression: KtCallExpression) {
                    if (isKotlinArmeriaDecoratorCall(expression)) {
                        extractKotlinDecoratorLabel(expression)?.let { decorators += it }
                    }
                    super.visitCallExpression(expression)
                }
            })
        }
    }

    private fun resolveKotlinCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }

    private val APPLY_SCOPE_METHODS = setOf("apply", "run")
}
