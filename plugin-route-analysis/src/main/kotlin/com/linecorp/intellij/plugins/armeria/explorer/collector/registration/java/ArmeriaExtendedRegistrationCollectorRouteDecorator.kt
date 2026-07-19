package com.linecorp.intellij.plugins.armeria.explorer.collector.registration.java
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.collector.annotation.ArmeriaTimeoutSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaRegistrationChainReducer
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.RouteDecoratorChainInfo
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaExtendedRegistrationCollectorRouteDecorator {
    fun addRouteDecorator(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val key = ArmeriaJavaRegistrationChainSupport.registrationKey(expression) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        val chainInfo = extractRouteDecoratorChain(expression)
        routes +=
            ArmeriaRoute.create(
                element = expression,
                protocol = RouteProtocol.HTTP.presentableName(),
                httpMethod = chainInfo.methods,
                path = chainInfo.pathPattern,
                target = chainInfo.decoratorLabel,
                routeMatch = RouteMatch.ROUTE_DECORATOR,
                pathType = chainInfo.pathType,
                decorators = listOf(chainInfo.decoratorLabel),
                timeoutHints = ArmeriaTimeoutSupport.collectBuilderTimeoutHints(expression),
            )
    }

    fun addWithRoute(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val lambdaBody = (expression.argumentList.expressions.firstOrNull() as? PsiLambdaExpression)?.body ?: return
        val buildCall =
            PsiTreeUtil
                .findChildrenOfType(lambdaBody, PsiMethodCallExpression::class.java)
                .filter { it.methodExpression.referenceName == "build" }
                .firstOrNull {
                    ArmeriaExtendedRegistrationCollectorFluentRoute.extractFluentRouteChain(it, requireRouteAnchor = false) != null
                }
                ?: return
        ArmeriaExtendedRegistrationCollectorFluentRoute.addFluentRouteFromBuild(
            buildCall,
            routes,
            seenRegistrations,
            requireRouteAnchor = false,
        )
    }

    private fun extractRouteDecoratorChain(routeDecoratorCall: PsiMethodCallExpression): RouteDecoratorChainInfo {
        val outerBuild =
            ArmeriaJavaRegistrationChainSupport.findForwardChainedCall(routeDecoratorCall) { call ->
                call.methodExpression.referenceName == "build" && call.argumentList.expressionCount == 0
            }
        val chainCalls = ArmeriaJavaRegistrationChainSupport.methodCallsBetweenInStatement(routeDecoratorCall, outerBuild)
        val steps = chainCalls.map(ArmeriaJavaRegistrationChainSupport::toChainStep)
        return ArmeriaRegistrationChainReducer.reduceRouteDecoratorChain(
            steps = steps,
            defaultDecoratorLabel = message("route.explorer.target.routeDecorator"),
        )
    }
}
