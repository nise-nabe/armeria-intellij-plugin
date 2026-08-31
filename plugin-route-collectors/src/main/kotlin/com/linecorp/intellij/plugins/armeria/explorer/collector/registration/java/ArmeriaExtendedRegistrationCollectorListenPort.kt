package com.linecorp.intellij.plugins.armeria.explorer.collector.registration.java

import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaListenPortSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaExtendedRegistrationCollectorListenPort {
    fun collect(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val methodName = expression.methodExpression.referenceName ?: return
        if (methodName !in ArmeriaListenPortSupport.METHOD_NAMES) {
            return
        }
        if (!ArmeriaBuilderCallHeuristics.looksLikeJavaBuilderCall(expression)) {
            return
        }
        val key = ArmeriaJavaRegistrationChainSupport.registrationKey(expression) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        val port = ArmeriaListenPortSupport.extractJavaPort(expression.argumentList.expressions.firstOrNull()) ?: return
        val protocolLabel =
            ArmeriaListenPortSupport.protocolLabel(
                methodName,
                expression.argumentList.expressions
                    .drop(1)
                    .map { it.text },
            )
        val path = ArmeriaListenPortSupport.displayPath(port)
        routes +=
            ArmeriaRoute.create(
                element = expression,
                protocol = protocolLabel,
                httpMethod = "",
                path = path,
                target = message("route.explorer.target.listenPort"),
                routeMatch = RouteMatch.LISTEN_PORT,
                excludeFromDuplicateIndex = true,
            )
    }
}
