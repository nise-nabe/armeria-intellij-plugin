package com.linecorp.intellij.plugins.armeria.explorer.collector.registration.java
import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.explorer.collector.annotation.ArmeriaTimeoutSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.decorator.ArmeriaDecoratorSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaRegistrationChainReducer
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.FluentRouteChainInfo
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.RegistrationChainStep
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaExtendedRegistrationCollectorFluentRoute {
    fun tryCollectFluentRoute(
        buildCall: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        requireBuilderCall: Boolean = true,
    ) {
        if (buildCall.methodExpression.referenceName != "build") {
            return
        }
        if (buildCall.argumentList.expressionCount == 0) {
            return
        }
        if (requireBuilderCall && !ArmeriaBuilderCallHeuristics.looksLikeArmeriaFluentRouteBuild(buildCall)) {
            return
        }
        addFluentRouteFromBuild(buildCall, routes, seenRegistrations, requireRouteAnchor = true)
    }

    fun addFluentRouteFromBuild(
        buildCall: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        requireRouteAnchor: Boolean,
    ) {
        val chainInfo = extractFluentRouteChain(buildCall, requireRouteAnchor) ?: return
        val key = ArmeriaJavaRegistrationChainSupport.registrationKey(buildCall) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        routes += createFluentRoute(buildCall, chainInfo)
    }

    fun extractFluentRouteChain(
        buildCall: PsiMethodCallExpression,
        requireRouteAnchor: Boolean,
    ): FluentRouteChainInfo? {
        if (buildCall.methodExpression.referenceName != "build") {
            return null
        }
        val steps = mutableListOf<RegistrationChainStep>()
        var current = ArmeriaJavaRegistrationChainSupport.previousMethodCallInChain(buildCall)
        while (current != null) {
            steps += ArmeriaJavaRegistrationChainSupport.toChainStep(current)
            current = ArmeriaJavaRegistrationChainSupport.previousMethodCallInChain(current)
        }
        val handlerArg =
            buildCall.argumentList.expressions
                .firstOrNull()
                ?.text
        return ArmeriaRegistrationChainReducer.reduceFluentRouteChain(
            stepsFromBuildUpward = steps,
            requireRouteAnchor = requireRouteAnchor,
            handlerTarget = handlerArg,
            defaultTarget = message("route.explorer.target.fluentRoute"),
        )
    }

    private fun createFluentRoute(
        element: PsiMethodCallExpression,
        chainInfo: FluentRouteChainInfo,
    ): ArmeriaRoute =
        ArmeriaRoute.create(
            element = element,
            protocol = RouteProtocol.HTTP.presentableName(),
            httpMethod = chainInfo.httpMethod,
            path = chainInfo.path,
            target = chainInfo.target,
            routeMatch = RouteMatch.ROUTE_FLUENT,
            pathType = chainInfo.pathType,
            decorators = ArmeriaDecoratorSupport.collectProgrammaticDecorators(element, chainInfo.path),
            timeoutHints = ArmeriaTimeoutSupport.collectBuilderTimeoutHints(element),
        )
}
