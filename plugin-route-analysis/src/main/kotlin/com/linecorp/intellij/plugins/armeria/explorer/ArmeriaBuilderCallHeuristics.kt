package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaBuilderCallHeuristics {

    private val IMPLICIT_RECEIVER_SCOPE_METHODS = setOf("apply", "run")
    private val EXPLICIT_PARAMETER_SCOPE_METHODS = setOf("also", "let")
    private val BUILDER_SCOPE_METHOD_NAMES = IMPLICIT_RECEIVER_SCOPE_METHODS + EXPLICIT_PARAMETER_SCOPE_METHODS

    fun looksLikeJavaBuilderCall(expression: PsiMethodCallExpression): Boolean {
        if (resolvesToArmeriaServerBuilder(expression)) {
            return true
        }
        val qualifierText = expression.methodExpression.qualifierExpression?.text ?: return false
        return ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(qualifierText) ||
            qualifierText.contains("routeDecorator")
    }

    fun looksLikeKotlinBuilderCall(call: KtCallExpression): Boolean {
        if (resolvesKotlinCallToArmeriaServerBuilder(call)) {
            return true
        }
        val dotQualified = when (val callee = call.calleeExpression) {
            is KtDotQualifiedExpression -> callee
            else -> call.parent as? KtDotQualifiedExpression
        }
        if (dotQualified != null && isKotlinServerBuilderReceiver(dotQualified.receiverExpression)) {
            return true
        }
        val receiverText = dotQualified?.receiverExpression?.text.orEmpty()
        if (receiverText.contains("routeDecorator")) {
            return true
        }
        return hasKotlinServerBuilderImplicitReceiver(call)
    }

    fun looksLikeArmeriaFluentRouteBuild(expression: PsiMethodCallExpression): Boolean {
        if (looksLikeJavaBuilderCall(expression)) {
            return true
        }
        var current = javaPreviousMethodCallInChain(expression)
        while (current != null) {
            if (current.methodExpression.referenceName == "route") {
                return looksLikeJavaBuilderCall(current)
            }
            current = javaPreviousMethodCallInChain(current)
        }
        return false
    }

    fun looksLikeArmeriaFluentRouteBuild(call: KtCallExpression): Boolean {
        if (looksLikeKotlinBuilderCall(call)) {
            return true
        }
        var current = kotlinParentCallExpression(call)
        while (current != null) {
            if (resolveKotlinCallName(current) == "route") {
                return looksLikeKotlinBuilderCall(current)
            }
            current = kotlinParentCallExpression(current)
        }
        return false
    }

    private fun resolvesToArmeriaServerBuilder(expression: PsiMethodCallExpression): Boolean {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
        return resolvedClass?.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX) == true
    }

    private fun resolvesKotlinCallToArmeriaServerBuilder(call: KtCallExpression): Boolean {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val dotQualified = call.parent as? KtDotQualifiedExpression
        if (dotQualified != null) {
            for (reference in dotQualified.references) {
                if (isArmeriaServerBuilderMethod(reference.resolve())) {
                    return true
                }
            }
        }
        val callee = call.calleeExpression ?: return false
        val references = when (callee) {
            is KtNameReferenceExpression -> callee.references.toList()
            is KtDotQualifiedExpression -> callee.references.toList()
            else -> emptyList()
        }
        return references.any { isArmeriaServerBuilderMethod(it.resolve()) }
    }

    private fun isArmeriaServerBuilderMethod(resolved: PsiElement?): Boolean {
        return resolved is PsiMethod &&
            resolved.containingClass?.qualifiedName?.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX) == true
    }

    private fun isKotlinServerBuilderReceiver(receiver: KtExpression): Boolean {
        val receiverExpression = unwrapKotlinReceiverExpression(receiver)
        val receiverText = receiverExpression.text
        if (ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(receiverText)) {
            return true
        }
        if (receiverExpression is KtNameReferenceExpression) {
            when (val resolved = receiverExpression.references.firstOrNull()?.resolve()) {
                is PsiVariable -> {
                    if (ArmeriaRouteSupport.isServerBuilderType(resolved.type.canonicalText)) {
                        return true
                    }
                }
                is KtProperty -> {
                    val typeText = resolveKotlinTypeReferenceText(resolved.typeReference)
                    if (typeText != null && ArmeriaRouteSupport.isServerBuilderType(typeText)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun resolveKotlinTypeReferenceText(typeReference: KtTypeReference?): String? {
        if (typeReference == null) {
            return null
        }
        val userType = typeReference.typeElement as? KtUserType
        val resolved = userType?.referenceExpression?.references?.firstOrNull()?.resolve()
        return when (resolved) {
            is KtTypeAlias -> resolved.getTypeReference()?.text ?: typeReference.text
            is KtClassOrObject -> resolved.fqName?.asString() ?: typeReference.text
            else -> typeReference.text
        }
    }

    private fun unwrapKotlinReceiverExpression(receiver: KtExpression): KtExpression {
        return when (receiver) {
            is KtUnaryExpression -> receiver.baseExpression?.let(::unwrapKotlinReceiverExpression) ?: receiver
            is KtParenthesizedExpression -> receiver.expression?.let(::unwrapKotlinReceiverExpression) ?: receiver
            else -> receiver
        }
    }

    private fun hasKotlinServerBuilderImplicitReceiver(call: KtCallExpression): Boolean {
        val lambda = call.getParentOfType<KtLambdaExpression>(strict = true) ?: return false
        val lambdaArgument = lambda.parent as? KtValueArgument ?: return false
        val scopeCall = lambdaArgument.parent as? KtCallExpression ?: return false
        val scopeMethod = resolveKotlinCallName(scopeCall) ?: return false
        if (scopeMethod !in BUILDER_SCOPE_METHOD_NAMES) {
            return false
        }
        val scopeReceiver = when (val callee = scopeCall.calleeExpression) {
            is KtDotQualifiedExpression -> callee.receiverExpression
            else -> (scopeCall.parent as? KtDotQualifiedExpression)?.receiverExpression
        } ?: return false
        if (!isKotlinServerBuilderReceiver(scopeReceiver) && !kotlinReceiverChainContainsServerBuilder(scopeReceiver)) {
            return false
        }
        return when (scopeMethod) {
            in IMPLICIT_RECEIVER_SCOPE_METHODS -> true
            in EXPLICIT_PARAMETER_SCOPE_METHODS -> isKotlinRegistrationOnScopeLambdaParameter(call, lambda)
            else -> false
        }
    }

    private fun isKotlinRegistrationOnScopeLambdaParameter(
        call: KtCallExpression,
        scopeLambda: KtLambdaExpression,
    ): Boolean {
        val dotQualified = when (val callee = call.calleeExpression) {
            is KtDotQualifiedExpression -> callee
            else -> call.parent as? KtDotQualifiedExpression
        } ?: return false

        val receiver = dotQualified.receiverExpression
        if (isKotlinServerBuilderReceiver(receiver)) {
            return true
        }
        val receiverName = (receiver as? KtNameReferenceExpression)?.getReferencedName() ?: return false
        if (scopeLambda.valueParameters.any { it.name == receiverName }) {
            return true
        }
        return scopeLambda.valueParameters.isEmpty() && receiverName == "it"
    }

    private fun kotlinReceiverChainContainsServerBuilder(receiver: KtExpression): Boolean {
        var current: KtExpression? = receiver
        while (current != null) {
            if (isKotlinServerBuilderReceiver(current)) {
                return true
            }
            current = when (current) {
                is KtDotQualifiedExpression -> current.receiverExpression
                is KtCallExpression -> {
                    when (val callee = current.calleeExpression) {
                        is KtDotQualifiedExpression -> callee.receiverExpression
                        else -> (current.parent as? KtDotQualifiedExpression)?.receiverExpression
                    }
                }
                else -> null
            }
        }
        return false
    }

    private fun resolveKotlinCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }

    private fun javaPreviousMethodCallInChain(call: PsiMethodCallExpression): PsiMethodCallExpression? {
        var expression: PsiElement? = call.methodExpression.qualifierExpression
        while (expression != null) {
            when (expression) {
                is PsiMethodCallExpression -> return expression
                is PsiReferenceExpression -> expression = expression.qualifier
                else -> return null
            }
        }
        return null
    }

    private fun kotlinParentCallExpression(call: KtCallExpression): KtCallExpression? {
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
}
