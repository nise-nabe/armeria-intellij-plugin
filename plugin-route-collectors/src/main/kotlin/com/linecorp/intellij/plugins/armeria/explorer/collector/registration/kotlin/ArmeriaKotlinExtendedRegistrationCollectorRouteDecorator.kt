package com.linecorp.intellij.plugins.armeria.explorer.collector.registration.kotlin
import com.linecorp.intellij.plugins.armeria.explorer.collector.annotation.ArmeriaKotlinTimeoutSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaRegistrationChainReducer
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.RouteDecoratorChainInfo
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaKotlinExtendedRegistrationCollectorRouteDecorator {
    fun addWithRoute(
        call: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val argumentExpression = call.valueArguments.firstOrNull()?.getArgumentExpression() ?: return
        val lambdaBody =
            (
                argumentExpression as? KtLambdaExpression
                    ?: argumentExpression.getParentOfType<KtLambdaExpression>(strict = false)
            )?.bodyExpression
                ?: return
        val buildCall =
            ArmeriaKotlinExtendedRegistrationCollectorFluentRoute.findFluentRouteBuildForWithRoute(call, lambdaBody)
                ?: return
        ArmeriaKotlinExtendedRegistrationCollectorFluentRoute.addFluentRouteFromBuild(
            buildCall,
            routes,
            seenRegistrations,
            requireRouteAnchor = false,
        )
    }

    fun createRouteDecoratorRoute(
        call: KtCallExpression,
        chainInfo: RouteDecoratorChainInfo,
    ): ArmeriaRoute =
        ArmeriaRoute.create(
            element = call,
            protocol = RouteProtocol.HTTP.presentableName(),
            httpMethod = chainInfo.methods,
            path = chainInfo.pathPattern,
            target = chainInfo.decoratorLabel,
            routeMatch = RouteMatch.ROUTE_DECORATOR,
            pathType = chainInfo.pathType,
            decorators = listOf(chainInfo.decoratorLabel),
            timeoutHints = ArmeriaKotlinTimeoutSupport.collectBuilderTimeoutHints(call),
        )

    fun extractRouteDecoratorChain(routeDecoratorCall: KtCallExpression): RouteDecoratorChainInfo {
        val outerBuild =
            ArmeriaKotlinRegistrationChainSupport.findForwardChainedCall(routeDecoratorCall) { call ->
                ArmeriaKotlinRegistrationChainSupport.resolveCallName(call) == "build" && call.valueArguments.isEmpty()
            }
        val chainCalls = ArmeriaKotlinRegistrationChainSupport.methodCallsBetweenInStatement(routeDecoratorCall, outerBuild)
        val steps = chainCalls.map(ArmeriaKotlinRegistrationChainSupport::toChainStep)
        return ArmeriaRegistrationChainReducer.reduceRouteDecoratorChain(
            steps = steps,
            defaultDecoratorLabel = message("route.explorer.target.routeDecorator"),
        )
    }
}
