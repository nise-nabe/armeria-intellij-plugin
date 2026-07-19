package com.linecorp.intellij.plugins.armeria.explorer.collector.decorator
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaKotlinDecoratorScopeSupport {
    private val IMPLICIT_RECEIVER_SCOPE_METHODS = setOf("apply", "run")
    private val EXPLICIT_PARAMETER_SCOPE_METHODS = setOf("also", "let")
    private val BUILDER_SCOPE_METHODS = IMPLICIT_RECEIVER_SCOPE_METHODS + EXPLICIT_PARAMETER_SCOPE_METHODS

    fun collectKotlinDecoratorsFromPrecedingScopeBlocks(
        registrationCall: KtCallExpression,
        candidates: LinkedHashSet<ArmeriaDecoratorSupport.DecoratorCandidate>,
    ) {
        var current: KtExpression? = ArmeriaKotlinDecoratorChainSupport.kotlinChainReceiver(registrationCall)
        while (current != null) {
            val scopeCall = ArmeriaKotlinDecoratorChainSupport.asKotlinCallExpression(current)
            if (scopeCall != null && ArmeriaKotlinDecoratorChainSupport.resolveKotlinCallName(scopeCall) in BUILDER_SCOPE_METHODS) {
                val scopeReceiver = extractScopeReceiver(scopeCall)
                if (scopeReceiver != null &&
                    (
                        ArmeriaKotlinDecoratorChainSupport.isKotlinServerBuilderReceiver(scopeReceiver) ||
                            ArmeriaKotlinDecoratorChainSupport.receiverChainContainsServerBuilder(scopeReceiver)
                    )
                ) {
                    collectAllDecoratorsInScopeLambda(scopeCall, candidates)
                }
            }
            current = ArmeriaKotlinDecoratorChainSupport.kotlinChainReceiver(current)
        }
    }

    fun collectAllDecoratorsInScopeLambda(
        scopeCall: KtCallExpression,
        candidates: LinkedHashSet<ArmeriaDecoratorSupport.DecoratorCandidate>,
    ) {
        val lambda =
            scopeCall.valueArguments
                .firstOrNull()
                ?.getArgumentExpression() as? KtLambdaExpression ?: return
        lambda.bodyExpression?.statements?.forEach { statement ->
            statement.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitCallExpression(expression: KtCallExpression) {
                        if (ArmeriaKotlinDecoratorChainSupport.isKotlinArmeriaDecoratorCall(expression)) {
                            ArmeriaKotlinDecoratorTargetSupport.extractKotlinDecoratorCandidate(expression)?.let { candidates += it }
                        }
                        super.visitCallExpression(expression)
                    }
                },
            )
        }
    }

    fun enclosingBuilderScopeCall(registrationCall: KtCallExpression): KtCallExpression? {
        val lambda = registrationCall.getParentOfType<KtLambdaExpression>(strict = true) ?: return null
        val scopeCall = (lambda.parent as? KtValueArgument)?.parent as? KtCallExpression ?: return null
        val scopeMethod = ArmeriaKotlinDecoratorChainSupport.resolveKotlinCallName(scopeCall) ?: return null
        return scopeCall.takeIf { scopeMethod in BUILDER_SCOPE_METHODS }
    }

    fun hasScopeBuilderReceiver(call: KtCallExpression): Boolean {
        val lambda = call.getParentOfType<KtLambdaExpression>(strict = true) ?: return false
        val lambdaArgument = lambda.parent as? KtValueArgument ?: return false
        val scopeCall = lambdaArgument.parent as? KtCallExpression ?: return false
        val scopeMethod = ArmeriaKotlinDecoratorChainSupport.resolveKotlinCallName(scopeCall) ?: return false
        if (scopeMethod !in BUILDER_SCOPE_METHODS) {
            return false
        }
        val scopeReceiver = extractScopeReceiver(scopeCall) ?: return false
        if (!ArmeriaKotlinDecoratorChainSupport.isKotlinServerBuilderReceiver(scopeReceiver) &&
            !ArmeriaKotlinDecoratorChainSupport.receiverChainContainsServerBuilder(scopeReceiver)
        ) {
            return false
        }
        return when (scopeMethod) {
            in IMPLICIT_RECEIVER_SCOPE_METHODS -> true
            in EXPLICIT_PARAMETER_SCOPE_METHODS -> isCallOnScopeLambdaParameter(call, lambda)
            else -> false
        }
    }

    fun collectKotlinDecoratorsInScopeLambda(
        registrationCall: KtCallExpression,
        candidates: LinkedHashSet<ArmeriaDecoratorSupport.DecoratorCandidate>,
    ) {
        val lambda = registrationCall.getParentOfType<KtLambdaExpression>(strict = true) ?: return
        val lambdaArgument = lambda.parent as? KtValueArgument ?: return
        val scopeCall = lambdaArgument.parent as? KtCallExpression ?: return
        val scopeMethod = ArmeriaKotlinDecoratorChainSupport.resolveKotlinCallName(scopeCall) ?: return
        if (scopeMethod !in BUILDER_SCOPE_METHODS) {
            return
        }
        val registrationOffset = registrationCall.textRange.startOffset
        lambda.bodyExpression?.statements?.forEach { statement ->
            if (statement.textRange.startOffset >= registrationOffset) {
                return
            }
            statement.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitCallExpression(expression: KtCallExpression) {
                        if (expression.textRange.startOffset >= registrationOffset) {
                            return
                        }
                        if (ArmeriaKotlinDecoratorChainSupport.isKotlinArmeriaDecoratorCall(expression)) {
                            ArmeriaKotlinDecoratorTargetSupport.extractKotlinDecoratorCandidate(expression)?.let { candidates += it }
                        }
                        super.visitCallExpression(expression)
                    }
                },
            )
            if (PsiTreeUtil.isAncestor(statement, registrationCall, false)) {
                return
            }
        }
    }

    private fun isCallOnScopeLambdaParameter(
        call: KtCallExpression,
        scopeLambda: KtLambdaExpression,
    ): Boolean {
        val dotQualified =
            when (val callee = call.calleeExpression) {
                is KtDotQualifiedExpression -> callee
                else -> call.parent as? KtDotQualifiedExpression
            } ?: return false

        val receiver = dotQualified.receiverExpression
        if (ArmeriaKotlinDecoratorChainSupport.isKotlinServerBuilderReceiver(receiver)) {
            return true
        }
        val receiverName = (receiver as? KtNameReferenceExpression)?.getReferencedName() ?: return false
        if (scopeLambda.valueParameters.any { it.name == receiverName }) {
            return true
        }
        return scopeLambda.valueParameters.isEmpty() && receiverName == "it"
    }

    private fun extractScopeReceiver(scopeCall: KtCallExpression): KtExpression? =
        when (val callee = scopeCall.calleeExpression) {
            is KtDotQualifiedExpression -> callee.receiverExpression
            else -> (scopeCall.parent as? KtDotQualifiedExpression)?.receiverExpression
        }
}
