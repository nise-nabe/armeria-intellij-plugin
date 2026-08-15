package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientSupport

internal data class ArmeriaDecoratorOrderFinding(
    val highlight: PsiElement,
    val messageKey: String,
)

internal object ArmeriaClientDecoratorOrderSupport {
    fun findings(call: PsiMethodCallExpression): List<ArmeriaDecoratorOrderFinding> {
        if (!isDecoratorCall(call)) {
            return emptyList()
        }
        val chain = decoratorCallsInChain(call)
        val kinds = chain.map { it to decoratorKind(it) }
        val findings = mutableListOf<ArmeriaDecoratorOrderFinding>()
        addFinding(
            visited = call,
            kinds = kinds,
            targetKind = ArmeriaClientDecoratorKind.LOGGING,
            otherKind = ArmeriaClientDecoratorKind.RETRYING,
            messageKey = "inspection.decorator.order.logging.after.retry",
            findings = findings,
        )
        addFinding(
            visited = call,
            kinds = kinds,
            targetKind = ArmeriaClientDecoratorKind.CIRCUIT_BREAKER,
            otherKind = ArmeriaClientDecoratorKind.RETRYING,
            messageKey = "inspection.decorator.order.circuit.after.retry",
            findings = findings,
        )
        return findings
    }

    private fun addFinding(
        visited: PsiMethodCallExpression,
        kinds: List<Pair<PsiMethodCallExpression, ArmeriaClientDecoratorKind?>>,
        targetKind: ArmeriaClientDecoratorKind,
        otherKind: ArmeriaClientDecoratorKind,
        messageKey: String,
        findings: MutableList<ArmeriaDecoratorOrderFinding>,
    ) {
        val targetIndex = kinds.indexOfLast { it.second == targetKind }
        val otherIndex = kinds.indexOfLast { it.second == otherKind }
        if (targetIndex < 0 || otherIndex < 0 || targetIndex <= otherIndex) {
            return
        }
        if (kinds[targetIndex].first != visited) {
            return
        }
        findings +=
            ArmeriaDecoratorOrderFinding(
                highlight = highlight(visited),
                messageKey = messageKey,
            )
    }

    private fun highlight(call: PsiMethodCallExpression): PsiElement =
        call.argumentList.expressions.firstOrNull()
            ?: call.methodExpression.referenceNameElement
            ?: call

    private fun isDecoratorCall(call: PsiMethodCallExpression): Boolean {
        if (call.methodExpression.referenceName != "decorator") {
            return false
        }
        val resolvedClass = call.resolveMethod()?.containingClass?.qualifiedName
        if (resolvedClass != null) {
            return resolvedClass.startsWith(ArmeriaClientSupport.ARMERIA_CLIENT_PACKAGE_PREFIX)
        }
        val qualifierText = call.methodExpression.qualifierExpression?.text ?: return false
        return ArmeriaClientSupport.looksLikeClientBuilderReceiverText(qualifierText)
    }

    private fun decoratorKind(call: PsiMethodCallExpression): ArmeriaClientDecoratorKind? {
        val argument = call.argumentList.expressions.firstOrNull() ?: return null
        return ArmeriaClientDecoratorKind.fromSimpleName(decoratorClassSimpleName(argument))
    }

    private fun decoratorClassSimpleName(expression: PsiExpression): String {
        var current: PsiExpression = expression
        while (current is PsiMethodCallExpression) {
            current = current.methodExpression.qualifierExpression ?: break
        }
        return when (current) {
            is PsiReferenceExpression -> current.referenceName.orEmpty()
            is PsiMethodCallExpression -> current.methodExpression.referenceName.orEmpty()
            else -> current.text.substringAfterLast('.').substringBefore('(')
        }
    }

    private fun decoratorCallsInChain(call: PsiMethodCallExpression): List<PsiMethodCallExpression> {
        val preceding = mutableListOf<PsiMethodCallExpression>()
        var current: PsiExpression? = call.methodExpression.qualifierExpression
        while (current != null) {
            when (current) {
                is PsiMethodCallExpression -> {
                    if (isDecoratorCall(current)) {
                        preceding += current
                    }
                    current = current.methodExpression.qualifierExpression
                }
                is PsiReferenceExpression -> {
                    val resolved = current.resolve()
                    current = if (resolved is PsiVariable) resolved.initializer else null
                }
                else -> break
            }
        }
        val following = mutableListOf<PsiMethodCallExpression>()
        var cursor: PsiExpression = call
        while (true) {
            val parent = enclosingQualifierCall(cursor) ?: break
            if (isDecoratorCall(parent)) {
                following += parent
            }
            cursor = parent
        }
        return preceding.asReversed() + call + following
    }

    private fun enclosingQualifierCall(expression: PsiExpression): PsiMethodCallExpression? {
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
}
