package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

class ArmeriaClientDecoratorOrderKotlinInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.decorator.order.kotlin.display.name")

    override fun getStaticDescription(): String = message("inspection.decorator.order.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!isDecoratorCall(expression)) {
                    return
                }
                val chain = decoratorCallsInChain(expression)
                val kinds = chain.map { it to decoratorKind(it) }
                maybeRegister(
                    holder = holder,
                    visited = expression,
                    kinds = kinds,
                    targetKind = ArmeriaClientDecoratorKind.LOGGING,
                    otherKind = ArmeriaClientDecoratorKind.RETRYING,
                    messageKey = "inspection.decorator.order.logging.after.retry",
                )
                maybeRegister(
                    holder = holder,
                    visited = expression,
                    kinds = kinds,
                    targetKind = ArmeriaClientDecoratorKind.CIRCUIT_BREAKER,
                    otherKind = ArmeriaClientDecoratorKind.RETRYING,
                    messageKey = "inspection.decorator.order.circuit.after.retry",
                )
            }
        }

    private fun maybeRegister(
        holder: ProblemsHolder,
        visited: KtCallExpression,
        kinds: List<Pair<KtCallExpression, ArmeriaClientDecoratorKind?>>,
        targetKind: ArmeriaClientDecoratorKind,
        otherKind: ArmeriaClientDecoratorKind,
        messageKey: String,
    ) {
        val targetIndex = kinds.indexOfLast { it.second == targetKind }
        val otherIndex = kinds.indexOfLast { it.second == otherKind }
        if (targetIndex < 0 || otherIndex < 0 || targetIndex <= otherIndex) {
            return
        }
        if (kinds[targetIndex].first != visited) {
            return
        }
        holder.registerProblem(
            highlight(visited),
            message(messageKey),
            ProblemHighlightType.WEAK_WARNING,
        )
    }

    private fun highlight(call: KtCallExpression): PsiElement =
        call.valueArguments
            .firstOrNull()
            ?.getArgumentExpression()
            ?: call.calleeExpression
            ?: call

    private fun isDecoratorCall(call: KtCallExpression): Boolean {
        if (ArmeriaKotlinExpressionSupport.resolveCallName(call) != "decorator") {
            return false
        }
        val references =
            call.calleeExpression
                ?.references
                ?.toList()
                .orEmpty()
        for (reference in references) {
            val resolved = reference.resolve()
            val containingClass = (resolved as? PsiMethod)?.containingClass?.qualifiedName
            if (containingClass?.startsWith(ArmeriaClientSupport.ARMERIA_CLIENT_PACKAGE_PREFIX) == true) {
                return true
            }
        }
        val receiverText = chainReceiver(call)?.text ?: return false
        return ArmeriaClientSupport.looksLikeClientBuilderReceiverText(receiverText)
    }

    private fun decoratorKind(call: KtCallExpression): ArmeriaClientDecoratorKind? {
        val argument = call.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null
        return ArmeriaClientDecoratorKind.fromSimpleName(decoratorClassSimpleName(argument))
    }

    private fun decoratorClassSimpleName(expression: KtExpression): String {
        var current: KtExpression =
            ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: expression
        while (true) {
            when (current) {
                is KtCallExpression -> {
                    val receiver = chainReceiver(current)
                    if (receiver != null) {
                        current = receiver
                    } else {
                        return ArmeriaKotlinExpressionSupport.resolveCallName(current).orEmpty()
                    }
                }
                is KtDotQualifiedExpression -> {
                    when (val selector = current.selectorExpression) {
                        is KtCallExpression -> current = selector
                        is KtNameReferenceExpression -> return selector.getReferencedName()
                        else -> current = current.receiverExpression
                    }
                }
                is KtNameReferenceExpression -> return current.getReferencedName()
                else -> return current.text.substringAfterLast('.').substringBefore('(')
            }
        }
    }

    private fun decoratorCallsInChain(call: KtCallExpression): List<KtCallExpression> {
        val preceding = mutableListOf<KtCallExpression>()
        var current: KtExpression? = chainReceiver(call)
        while (current != null) {
            val decoratorCall = asCall(current)
            if (decoratorCall != null && isDecoratorCall(decoratorCall)) {
                preceding += decoratorCall
            }
            val next = chainReceiver(current)
            current = if (next === current) null else next
        }
        val following = mutableListOf<KtCallExpression>()
        var cursor: KtCallExpression = call
        while (true) {
            val next = nextChainedCall(cursor) ?: break
            if (isDecoratorCall(next)) {
                following += next
            }
            cursor = next
        }
        return preceding.asReversed() + call + following
    }

    private fun asCall(expression: KtExpression): KtCallExpression? =
        when (expression) {
            is KtCallExpression -> expression
            is KtDotQualifiedExpression -> expression.selectorExpression as? KtCallExpression
            else -> null
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

    private fun chainReceiver(expression: KtExpression): KtExpression? {
        val receiver =
            when (expression) {
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
        return ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(receiver) ?: receiver
    }
}
