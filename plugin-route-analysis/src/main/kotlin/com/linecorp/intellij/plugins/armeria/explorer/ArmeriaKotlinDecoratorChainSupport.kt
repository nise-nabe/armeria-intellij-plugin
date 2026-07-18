package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType

internal object ArmeriaKotlinDecoratorChainSupport {
    fun collectKotlinDecoratorsFromReceiver(
        expression: KtExpression,
        candidates: LinkedHashSet<ArmeriaDecoratorSupport.DecoratorCandidate>,
    ) {
        var current: KtExpression? = kotlinChainReceiver(expression)
        while (current != null) {
            val decoratorCall = asKotlinCallExpression(current)
            if (decoratorCall != null && isKotlinArmeriaDecoratorCall(decoratorCall)) {
                ArmeriaKotlinDecoratorTargetSupport.extractKotlinDecoratorCandidate(decoratorCall)?.let { candidates += it }
            }
            current = kotlinChainReceiver(current)
        }
    }

    fun asKotlinCallExpression(expression: KtExpression): KtCallExpression? {
        return when (expression) {
            is KtCallExpression -> expression
            is KtDotQualifiedExpression -> expression.selectorExpression as? KtCallExpression
            else -> null
        }
    }

    fun kotlinChainReceiver(expression: KtExpression): KtExpression? {
        return when (expression) {
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
        }
    }

    fun resolveKotlinCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }

    fun isKotlinArmeriaDecoratorCall(call: KtCallExpression): Boolean {
        if (resolveKotlinCallName(call) != "decorator") {
            return false
        }
        val dotQualifiedParent = call.parent as? KtDotQualifiedExpression
        if (dotQualifiedParent != null && isKotlinServerBuilderReceiver(dotQualifiedParent.receiverExpression)) {
            return true
        }
        if (ArmeriaKotlinDecoratorScopeSupport.hasScopeBuilderReceiver(call)) {
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

    fun isKotlinServerBuilderReceiver(receiver: KtExpression): Boolean {
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
                is KtProperty -> {
                    val typeText = resolveKotlinTypeReferenceText(resolved.typeReference)
                    if (typeText != null && ArmeriaRouteSupport.isServerBuilderType(typeText)) {
                        return true
                    }
                    val initializerText = resolved.initializer?.text
                    if (initializerText != null &&
                        ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(initializerText)
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun resolveKotlinTypeReferenceText(typeReference: KtTypeReference?): String? {
        if (typeReference == null) {
            return null
        }
        val userType = unwrapUserType(typeReference.typeElement)
        val resolved = userType?.referenceExpression?.references?.firstOrNull()?.resolve()
        return when (resolved) {
            is KtTypeAlias -> resolved.getTypeReference()?.text ?: typeReference.text
            is KtClass -> resolved.fqName?.asString() ?: typeReference.text
            else -> userType?.text ?: typeReference.text
        }
    }

    /**
     * Resolves bean/parameter/property name references to a service type string
     * (e.g. `TomcatService`) so servlet mounts match Java target extraction.
     * Follows property initializers with a cycle guard.
     */
    fun resolveKotlinTypedNameTarget(
        expression: KtNameReferenceExpression,
        visitedProperties: MutableSet<KtProperty> = mutableSetOf(),
        resolveExpression: (KtExpression, MutableSet<KtProperty>) -> String,
    ): String? {
        val resolved = expression.references.firstOrNull()?.resolve()
        when (resolved) {
            is KtParameter -> {
                resolveKotlinTypeReferenceText(resolved.typeReference)?.let { return it }
            }
            is KtProperty -> {
                resolveKotlinTypeReferenceText(resolved.typeReference)?.let { return it }
                val initializer = resolved.initializer
                if (initializer != null && visitedProperties.add(resolved)) {
                    return resolveExpression(initializer, visitedProperties)
                }
            }
            is PsiVariable -> return resolved.type.presentableText
        }
        return resolveQualifiedClassName(resolved)
    }

    private fun unwrapUserType(typeElement: KtTypeElement?): KtUserType? =
        when (typeElement) {
            is KtUserType -> typeElement
            is KtNullableType -> unwrapUserType(typeElement.innerType)
            else -> null
        }

    fun receiverChainContainsServerBuilder(receiver: KtExpression): Boolean {
        var current: KtExpression? = receiver
        while (current != null) {
            if (isKotlinServerBuilderReceiver(current)) {
                return true
            }
            current = kotlinChainReceiver(current)
        }
        return false
    }

    fun resolveQualifiedClassName(resolved: PsiElement?): String? {
        return when (resolved) {
            is com.intellij.psi.PsiClass -> resolved.qualifiedName
            is PsiMethod -> resolved.containingClass?.qualifiedName
            is KtClass -> resolved.fqName?.asString()
            else -> null
        }
    }
}
