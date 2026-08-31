package com.linecorp.intellij.plugins.armeria.explorer.collector.registration.java

import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaListenPortSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute

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
        val arguments = expression.argumentList.expressions
        val port = ArmeriaListenPortSupport.extractJavaPort(arguments.firstOrNull()) ?: return
        val extraArgs = arguments.drop(1)
        val protocolLabel =
            ArmeriaListenPortSupport.protocolLabel(
                methodName = methodName,
                extraArgsPresent = extraArgs.isNotEmpty(),
                resolvedProtocolNames = extraArgs.mapNotNull(ArmeriaListenPortSupport::resolveJavaSessionProtocol),
            ) ?: return
        routes += ArmeriaListenPortSupport.listenPortRoute(expression, port, protocolLabel)
    }
}
