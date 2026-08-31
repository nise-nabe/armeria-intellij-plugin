package com.linecorp.intellij.plugins.armeria.explorer.collector.registration.kotlin

import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaListenPortSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty

internal object ArmeriaKotlinExtendedRegistrationCollectorListenPort {
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
        val port =
            extractPort(call.valueArguments.firstOrNull()?.getArgumentExpression()) ?: return
        val protocolLabel =
            ArmeriaListenPortSupport.protocolLabel(
                methodName,
                call.valueArguments.drop(1).map { argument ->
                    argument.getArgumentExpression()?.text.orEmpty()
                },
            )
        val path = ArmeriaListenPortSupport.displayPath(port)
        routes +=
            ArmeriaRoute.create(
                element = call,
                protocol = protocolLabel,
                httpMethod = "",
                path = path,
                target = message("route.explorer.target.listenPort"),
                routeMatch = RouteMatch.LISTEN_PORT,
                excludeFromDuplicateIndex = true,
            )
    }

    private fun extractPort(expression: KtExpression?): Int? {
        val unwrapped = unwrap(expression) ?: return null
        when (unwrapped) {
            is KtConstantExpression -> unwrapped.text.toIntOrNull()?.let { return it.takeIf(ArmeriaListenPortSupport::isValidPort) }
            is KtNameReferenceExpression -> {
                val resolved = unwrapped.references.firstOrNull()?.resolve()
                if (resolved is KtProperty) {
                    extractPort(resolved.initializer)?.let { return it }
                }
            }
        }
        return unwrapped.text.toIntOrNull()?.takeIf(ArmeriaListenPortSupport::isValidPort)
    }

    private fun unwrap(expression: KtExpression?): KtExpression? {
        var current = expression ?: return null
        var hops = 0
        while (hops < 8) {
            val parenthesized = current as? KtParenthesizedExpression
            current = parenthesized?.expression ?: return current
            hops++
        }
        return current
    }
}
