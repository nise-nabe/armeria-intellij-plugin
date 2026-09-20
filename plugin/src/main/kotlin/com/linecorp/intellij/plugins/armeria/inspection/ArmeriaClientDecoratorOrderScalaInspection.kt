package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientSupport
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScParenthesisedExpr
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariableDefinition

class ArmeriaClientDecoratorOrderScalaInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.decorator.order.scala.display.name")

    override fun getStaticDescription(): String = message("inspection.decorator.order.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : ScalaElementVisitor() {
            override fun visitMethodCallExpression(call: ScMethodCall) {
                super.visitMethodCallExpression(call)
                if (!isDecoratorCall(call)) {
                    return
                }
                val kinds = decoratorCallsInChain(call).map { it to decoratorKind(it) }
                maybeRegister(
                    holder = holder,
                    visited = call,
                    kinds = kinds,
                    targetKind = ArmeriaClientDecoratorKind.LOGGING,
                    otherKind = ArmeriaClientDecoratorKind.RETRYING,
                    messageKey = "inspection.decorator.order.logging.after.retry",
                )
                maybeRegister(
                    holder = holder,
                    visited = call,
                    kinds = kinds,
                    targetKind = ArmeriaClientDecoratorKind.CIRCUIT_BREAKER,
                    otherKind = ArmeriaClientDecoratorKind.RETRYING,
                    messageKey = "inspection.decorator.order.circuit.after.retry",
                )
            }
        }

    private fun maybeRegister(
        holder: ProblemsHolder,
        visited: ScMethodCall,
        kinds: List<Pair<ScMethodCall, ArmeriaClientDecoratorKind?>>,
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

    private fun highlight(call: ScMethodCall): PsiElement =
        call
            .args()
            ?.exprs()
            ?.headOption()
            ?.orNull() ?: call.invokedExpr

    private fun isDecoratorCall(call: ScMethodCall): Boolean {
        val reference = call.invokedExpr as? ScReferenceExpression ?: return false
        if (reference.refName() != "decorator") {
            return false
        }
        val resolvedClass = (reference.resolve() as? PsiMethod)?.containingClass?.qualifiedName
        if (resolvedClass != null) {
            return resolvedClass.startsWith(ArmeriaClientSupport.ARMERIA_CLIENT_PACKAGE_PREFIX)
        }
        val qualifierText = reference.qualifier().orNull()?.text ?: return false
        return ArmeriaClientSupport.looksLikeClientBuilderReceiverText(qualifierText)
    }

    private fun decoratorKind(call: ScMethodCall): ArmeriaClientDecoratorKind? {
        val argument =
            call
                .args()
                ?.exprs()
                ?.headOption()
                ?.orNull() ?: return null
        return ArmeriaClientDecoratorKind.fromSimpleName(decoratorClassSimpleName(argument))
    }

    private fun decoratorClassSimpleName(expression: ScExpression): String {
        var current = expression
        while (true) {
            current =
                when (current) {
                    is ScMethodCall -> {
                        val reference = current.invokedExpr as? ScReferenceExpression
                        val qualifier = reference?.qualifier()?.orNull()
                        if (qualifier is ScExpression) {
                            qualifier
                        } else {
                            return reference?.refName().orEmpty()
                        }
                    }
                    // A class reference's own refName is its last segment, which is the
                    // simple name whether or not the reference is package-qualified.
                    is ScReferenceExpression -> return current.refName()
                    else -> return current.text.substringAfterLast('.').substringBefore('(')
                }
        }
    }

    private fun decoratorCallsInChain(call: ScMethodCall): List<ScMethodCall> {
        val preceding = mutableListOf<ScMethodCall>()
        val visited = mutableSetOf<PsiElement>()
        var current: ScExpression? = invokedQualifier(call)
        while (current != null && visited.add(current)) {
            when (current) {
                is ScMethodCall -> {
                    if (isDecoratorCall(current)) {
                        preceding += current
                    }
                    current = invokedQualifier(current)
                }
                is ScParenthesisedExpr -> {
                    current = current.innerElement().orNull()
                }
                is ScReferenceExpression -> {
                    current = resolvedInitializer(current)
                }
                else -> current = null
            }
        }
        val following = mutableListOf<ScMethodCall>()
        var cursor: ScMethodCall = call
        while (true) {
            val next = enclosingQualifierCall(cursor) ?: break
            if (isDecoratorCall(next)) {
                following += next
            }
            cursor = next
        }
        return preceding.asReversed() + call + following
    }

    private fun invokedQualifier(call: ScMethodCall): ScExpression? = (call.invokedExpr as? ScReferenceExpression)?.qualifier()?.orNull()

    private fun resolvedInitializer(reference: ScReferenceExpression): ScExpression? {
        var resolved = reference.resolve()
        // `val builder = ...` references resolve to the binding pattern, not the val statement.
        if (resolved is ScBindingPattern) {
            resolved = PsiTreeUtil.getParentOfType(resolved, ScValueOrVariableDefinition::class.java)
        }
        return unwrap((resolved as? ScValueOrVariableDefinition)?.expr()?.orNull())
    }

    private fun unwrap(expression: ScExpression?): ScExpression? {
        var current = expression
        while (current is ScParenthesisedExpr) {
            current = current.innerElement().orNull()
        }
        return current
    }

    private fun enclosingQualifierCall(expression: ScMethodCall): ScMethodCall? {
        var element: PsiElement? = expression.parent
        while (element != null) {
            if (element is ScMethodCall) {
                if (unwrap(invokedQualifier(element)) == expression) {
                    return element
                }
            }
            element = element.parent
        }
        return null
    }
}
