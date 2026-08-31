package com.linecorp.intellij.plugins.armeria.explorer.collector.registration.kotlin

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaListenPortSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal object ArmeriaKotlinExtendedRegistrationCollectorListenPort {
    private const val MAX_INITIALIZER_HOPS = 8

    fun collect(
        call: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val methodName = ArmeriaKotlinRegistrationChainSupport.resolveCallName(call) ?: return
        if (methodName !in ArmeriaListenPortSupport.METHOD_NAMES) {
            return
        }
        if (!ArmeriaBuilderCallHeuristics.looksLikeKotlinBuilderCall(call)) {
            return
        }
        val key = ArmeriaKotlinRegistrationChainSupport.registrationKey(call) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        val argumentExpressions =
            call.valueArguments.mapNotNull { argument -> argument.getArgumentExpression() }
        val port = extractPort(argumentExpressions.firstOrNull()) ?: return
        val extraArgs = argumentExpressions.drop(1)
        val protocolLabel =
            ArmeriaListenPortSupport.protocolLabel(
                methodName = methodName,
                extraArgsPresent = extraArgs.isNotEmpty(),
                resolvedProtocolNames = extraArgs.mapNotNull(::resolveKotlinSessionProtocol),
            ) ?: return
        routes += ArmeriaListenPortSupport.listenPortRoute(call, port, protocolLabel)
    }

    private fun extractPort(
        expression: KtExpression?,
        hops: Int = 0,
        visited: MutableSet<PsiElement> = mutableSetOf(),
    ): Int? {
        if (expression == null || hops > MAX_INITIALIZER_HOPS) {
            return null
        }
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return null
        when (unwrapped) {
            is KtConstantExpression ->
                return ArmeriaListenPortSupport
                    .parseIntLiteral(unwrapped.text)
                    ?.takeIf(ArmeriaListenPortSupport::isValidPort)
            is KtNameReferenceExpression -> {
                val resolved = unwrapped.references.firstOrNull()?.resolve() ?: return null
                return extractPortFromResolved(resolved, hops, visited)
            }
            is KtDotQualifiedExpression -> {
                val selector = unwrapped.selectorExpression as? KtNameReferenceExpression ?: return null
                val resolved = selector.references.firstOrNull()?.resolve() ?: return null
                return extractPortFromResolved(resolved, hops, visited)
            }
            else -> return null
        }
    }

    private fun extractPortFromResolved(
        resolved: PsiElement,
        hops: Int,
        visited: MutableSet<PsiElement>,
    ): Int? {
        if (!visited.add(resolved)) {
            return null
        }
        when (resolved) {
            is KtProperty -> return extractPort(resolved.initializer, hops + 1, visited)
            is PsiVariable -> {
                when (val constant = resolved.computeConstantValue()) {
                    is Number -> return constant.toInt().takeIf(ArmeriaListenPortSupport::isValidPort)
                }
                return ArmeriaListenPortSupport.extractJavaPort(resolved.initializer)
            }
            else -> return null
        }
    }

    private fun resolveKotlinSessionProtocol(expression: KtExpression): String? {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return null
        when (unwrapped) {
            is KtCallExpression -> return null
            is KtStringTemplateExpression ->
                return ArmeriaKotlinExpressionSupport.extractKotlinString(unwrapped)?.uppercase()
            is KtNameReferenceExpression -> {
                val resolved = unwrapped.references.firstOrNull()?.resolve()
                return sessionProtocolNameFromResolved(resolved)
            }
            is KtDotQualifiedExpression -> {
                val selector = unwrapped.selectorExpression
                if (selector is KtCallExpression) {
                    return null
                }
                val nameRef = selector as? KtNameReferenceExpression ?: return null
                if (receiverIsSessionProtocol(unwrapped.receiverExpression)) {
                    return nameRef.getReferencedName()
                }
                return sessionProtocolNameFromResolved(nameRef.references.firstOrNull()?.resolve())
            }
            else -> return null
        }
    }

    private fun receiverIsSessionProtocol(receiver: KtExpression): Boolean {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(receiver) ?: receiver
        val name =
            when (unwrapped) {
                is KtNameReferenceExpression -> unwrapped.getReferencedName()
                is KtDotQualifiedExpression ->
                    (unwrapped.selectorExpression as? KtNameReferenceExpression)?.getReferencedName()
                else -> null
            }
        if (name == ArmeriaListenPortSupport.SESSION_PROTOCOL_SIMPLE_NAME) {
            return true
        }
        val resolved =
            when (unwrapped) {
                is KtNameReferenceExpression -> unwrapped.references.firstOrNull()?.resolve()
                is KtDotQualifiedExpression ->
                    (unwrapped.selectorExpression as? KtNameReferenceExpression)
                        ?.references
                        ?.firstOrNull()
                        ?.resolve()
                else -> null
            }
        return ArmeriaListenPortSupport.isSessionProtocolClass(resolved as? PsiClass)
    }

    private fun sessionProtocolNameFromResolved(resolved: PsiElement?): String? {
        val field = resolved as? PsiField ?: return null
        if (!ArmeriaListenPortSupport.isSessionProtocolClass(field.containingClass)) {
            return null
        }
        return field.name
    }
}
