package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiElement
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

internal object ArmeriaKotlinExtendedRegistrationCollectorFluentRoute {

    fun tryCollectFluentRoute(
        buildCall: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        requireBuilderCall: Boolean = true,
    ) {
        if (ArmeriaKotlinRegistrationChainSupport.resolveCallName(buildCall) != "build") {
            return
        }
        if (buildCall.valueArguments.isEmpty()) {
            return
        }
        if (requireBuilderCall && !ArmeriaBuilderCallHeuristics.looksLikeArmeriaFluentRouteBuild(buildCall)) {
            return
        }
        addFluentRouteFromBuild(buildCall, routes, seenRegistrations, requireRouteAnchor = true)
    }

    fun addFluentRouteFromBuild(
        buildCall: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        requireRouteAnchor: Boolean,
    ) {
        val chainInfo = extractFluentRouteChain(buildCall, requireRouteAnchor) ?: return
        val key = ArmeriaKotlinRegistrationChainSupport.registrationKey(buildCall) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        routes += createFluentRoute(buildCall, chainInfo)
    }

    fun extractFluentRouteChain(
        buildCall: KtCallExpression,
        requireRouteAnchor: Boolean,
    ): FluentRouteChainInfo? {
        if (ArmeriaKotlinRegistrationChainSupport.resolveCallName(buildCall) != "build") {
            return null
        }
        val steps = mutableListOf<RegistrationChainStep>()
        var current: KtCallExpression? = ArmeriaKotlinRegistrationChainSupport.parentCallExpression(buildCall)
        while (current != null) {
            steps += ArmeriaKotlinRegistrationChainSupport.toChainStep(current)
            current = ArmeriaKotlinRegistrationChainSupport.parentCallExpression(current)
        }
        val handlerArg = buildCall.valueArguments.firstOrNull()?.getArgumentExpression()?.text
        return ArmeriaRegistrationChainReducer.reduceFluentRouteChain(
            stepsFromBuildUpward = steps,
            requireRouteAnchor = requireRouteAnchor,
            handlerTarget = handlerArg,
            defaultTarget = message("route.explorer.target.fluentRoute"),
        )
    }

    fun findFluentRouteBuildInLambda(root: KtExpression): KtCallExpression? {
        var found: KtCallExpression? = null
        root.forEachDescendant { element ->
            if (found != null) {
                return@forEachDescendant
            }
            val call = element as? KtCallExpression ?: return@forEachDescendant
            if (ArmeriaKotlinRegistrationChainSupport.resolveCallName(call) == "build" &&
                extractFluentRouteChain(call, requireRouteAnchor = false) != null
            ) {
                found = call
            }
        }
        return found
    }

    private fun createFluentRoute(
        element: KtCallExpression,
        chainInfo: FluentRouteChainInfo,
    ): ArmeriaRoute {
        return ArmeriaRoute.create(
            element = element,
            protocol = RouteProtocol.HTTP.presentableName(),
            httpMethod = chainInfo.httpMethod,
            path = chainInfo.path,
            target = chainInfo.target,
            routeMatch = RouteMatch.ROUTE_FLUENT,
            pathType = chainInfo.pathType,
            decorators = ArmeriaKotlinDecoratorSupport.collectProgrammaticDecorators(element, chainInfo.path),
            timeoutHints = ArmeriaKotlinTimeoutSupport.collectBuilderTimeoutHints(element),
        )
    }

    private fun findFluentRouteBuildAfterCall(withRouteCall: KtCallExpression): KtCallExpression? {
        findFluentRouteBuildInScope(
            ArmeriaKotlinExpressionSupport.containingKotlinStatementExpression(withRouteCall),
            withRouteCall,
        )?.let { return it }
        val blockScope = ArmeriaKotlinExpressionSupport.containingKotlinExpressionScope(withRouteCall)
        if (blockScope === ArmeriaKotlinExpressionSupport.containingKotlinStatementExpression(withRouteCall)) {
            return null
        }
        if (blockScope is KtBlockExpression && blockScope.parent is KtNamedFunction) {
            return null
        }
        return findFluentRouteBuildInScope(blockScope, withRouteCall)
    }

    private fun findFluentRouteBuildInScope(
        scope: PsiElement,
        withRouteCall: KtCallExpression,
    ): KtCallExpression? {
        var found: KtCallExpression? = null
        scope.forEachDescendant { element ->
            if (found != null) {
                return@forEachDescendant
            }
            val call = element as? KtCallExpression ?: return@forEachDescendant
            if (call.textRange.startOffset <= withRouteCall.textRange.startOffset) {
                return@forEachDescendant
            }
            if (ArmeriaKotlinRegistrationChainSupport.resolveCallName(call) == "build" &&
                extractFluentRouteChain(call, requireRouteAnchor = false) != null
            ) {
                found = call
            }
        }
        return found
    }

    fun findFluentRouteBuildForWithRoute(
        withRouteCall: KtCallExpression,
        lambdaBody: KtExpression,
    ): KtCallExpression? {
        return findFluentRouteBuildInLambda(lambdaBody)
            ?: findFluentRouteBuildAfterCall(withRouteCall)
    }
}
