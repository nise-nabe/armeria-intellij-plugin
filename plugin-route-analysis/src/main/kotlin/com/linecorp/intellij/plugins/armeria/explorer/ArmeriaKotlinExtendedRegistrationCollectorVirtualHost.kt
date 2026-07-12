package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiElement
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaKotlinExtendedRegistrationCollectorVirtualHost {

    fun addVirtualHost(
        call: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val key = ArmeriaKotlinRegistrationChainSupport.registrationKey(call) ?: return
        val pathArg = call.valueArguments.firstOrNull()?.getArgumentExpression()
        val hostname = ArmeriaKotlinExpressionSupport.extractKotlinString(pathArg) ?: pathArg?.text
            ?: message("route.explorer.target.virtualHost")
        if (seenRegistrations.add(key)) {
            routes += ArmeriaRoute.create(
                element = call,
                protocol = RouteProtocol.HTTP.presentableName(),
                httpMethod = "",
                path = "/",
                target = hostname,
                routeMatch = RouteMatch.VIRTUAL_HOST,
                virtualHostName = hostname,
                decorators = ArmeriaKotlinDecoratorSupport.collectProgrammaticDecorators(call, "/"),
                timeoutHints = ArmeriaKotlinTimeoutSupport.collectBuilderTimeoutHints(call),
            )
        }
        collectVirtualHostScopedRegistrations(call, routes, seenRegistrations, hostname)
    }

    private fun collectVirtualHostScopedRegistrations(
        virtualHostCall: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        hostname: String,
    ) {
        val scopedKeys = linkedSetOf<String>()
        collectForwardChainedRegistrations(virtualHostCall, routes, seenRegistrations, hostname, scopedKeys)
        for (argument in virtualHostCall.valueArguments) {
            val argumentExpression = argument.getArgumentExpression() ?: continue
            val lambdaBody = (argumentExpression as? KtLambdaExpression
                ?: argumentExpression.getParentOfType<KtLambdaExpression>(strict = false))
                ?.bodyExpression
                ?: continue
            collectRegistrationsInVirtualHostScope(
                lambdaBody,
                virtualHostCall,
                routes,
                seenRegistrations,
                hostname,
                scopedKeys,
            )
        }
        annotateRoutesByKeys(routes, scopedKeys, hostname) { route ->
            val element = route.pointer.element as? KtCallExpression ?: return@annotateRoutesByKeys null
            ArmeriaKotlinRegistrationChainSupport.registrationKey(element)
        }
    }

    private fun annotateRoutesByKeys(
        routes: MutableList<ArmeriaRoute>,
        registrationKeys: Set<String>,
        hostname: String,
        routeKey: (ArmeriaRoute) -> String?,
    ) {
        if (registrationKeys.isEmpty()) {
            return
        }
        for (index in routes.indices) {
            val key = routeKey(routes[index]) ?: continue
            if (key in registrationKeys) {
                ArmeriaRouteVirtualHostAnnotator.annotateRouteAt(routes, index, hostname)
            }
        }
    }

    private fun collectForwardChainedRegistrations(
        virtualHostCall: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        hostname: String,
        scopedKeys: MutableSet<String>,
    ) {
        var current = ArmeriaKotlinRegistrationChainSupport.findNextChainedCall(virtualHostCall)
        while (current != null) {
            val methodName = ArmeriaKotlinRegistrationChainSupport.resolveCallName(current)
            if (methodName == "build" && current.valueArguments.isEmpty()) {
                break
            }
            processVirtualHostScopedCall(current, routes, seenRegistrations, hostname, scopedKeys)
            current = ArmeriaKotlinRegistrationChainSupport.findNextChainedCall(current)
        }
    }

    private fun collectRegistrationsInVirtualHostScope(
        root: KtExpression,
        outerVirtualHostCall: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        hostname: String,
        scopedKeys: MutableSet<String>,
    ) {
        val calls = buildList {
            if (root is KtCallExpression) {
                add(root)
            }
            root.forEachDescendant { element ->
                (element as? KtCallExpression)?.let(::add)
            }
        }
        calls.forEach { nested ->
            if (nested == outerVirtualHostCall || isInsideInnerVirtualHost(nested, outerVirtualHostCall)) {
                return@forEach
            }
            processVirtualHostScopedCall(nested, routes, seenRegistrations, hostname, scopedKeys)
        }
    }

    private fun isInsideInnerVirtualHost(
        element: PsiElement,
        outerVirtualHostCall: KtCallExpression,
    ): Boolean {
        var parent = element.parent
        while (parent != null && parent != outerVirtualHostCall) {
            if (parent is KtCallExpression &&
                ArmeriaKotlinRegistrationChainSupport.resolveCallName(parent) == ServiceRegistrationMethod.VIRTUAL_HOST.methodName &&
                parent != outerVirtualHostCall
            ) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

    private fun processVirtualHostScopedCall(
        call: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        hostname: String,
        scopedKeys: MutableSet<String>,
    ) {
        val methodName = ArmeriaKotlinRegistrationChainSupport.resolveCallName(call) ?: return
        when {
            methodName == ServiceRegistrationMethod.VIRTUAL_HOST.methodName -> {
                if (ArmeriaBuilderCallHeuristics.isClearlyNonArmeriaKotlinRegistrationCall(call)) {
                    return
                }
                addVirtualHost(call, routes, seenRegistrations)
            }
            methodName == "build" -> {
                val sizeBefore = routes.size
                if (ArmeriaBuilderCallHeuristics.looksLikeArmeriaFluentRouteBuild(call)) {
                    ArmeriaKotlinExtendedRegistrationCollectorFluentRoute.tryCollectFluentRoute(
                        call,
                        routes,
                        seenRegistrations,
                        requireBuilderCall = true,
                    )
                }
                ArmeriaKotlinRegistrationChainSupport.registrationKey(call)?.let(scopedKeys::add)
                annotateVirtualHostForCall(call, routes, sizeBefore, hostname)
            }
            methodName in CoreServiceRegistrationMethod.METHOD_NAMES -> {
                if (ArmeriaBuilderCallHeuristics.isClearlyNonArmeriaKotlinRegistrationCall(call)) {
                    return
                }
                val sizeBefore = routes.size
                ArmeriaKotlinRouteCollector.addServiceRegistrationFromCall(call, routes, seenRegistrations)
                ArmeriaKotlinRegistrationChainSupport.registrationKey(call)?.let(scopedKeys::add)
                annotateVirtualHostForCall(call, routes, sizeBefore, hostname)
            }
            methodName in ServiceRegistrationMethod.EXTENDED_METHOD_NAMES -> {
                if (ArmeriaBuilderCallHeuristics.isClearlyNonArmeriaKotlinRegistrationCall(call)) {
                    return
                }
                val sizeBefore = routes.size
                ArmeriaKotlinExtendedRegistrationCollector.collectFromKotlinCall(call, methodName, routes, seenRegistrations)
                ArmeriaKotlinRegistrationChainSupport.registrationKey(call)?.let(scopedKeys::add)
                annotateVirtualHostForCall(call, routes, sizeBefore, hostname)
            }
        }
    }

    private fun annotateVirtualHostForCall(
        call: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        sizeBefore: Int,
        hostname: String,
    ) {
        val key = ArmeriaKotlinRegistrationChainSupport.registrationKey(call)
        if (key != null) {
            ArmeriaRouteVirtualHostAnnotator.annotateByKey(routes, key, hostname) { route ->
                val element = route.pointer.element as? KtCallExpression ?: return@annotateByKey null
                ArmeriaKotlinRegistrationChainSupport.registrationKey(element)
            }
        } else {
            ArmeriaRouteVirtualHostAnnotator.annotateRoutesAddedSince(routes, sizeBefore, hostname)
        }
    }
}
