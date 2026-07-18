package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaExtendedRegistrationCollectorVirtualHost {
    fun addVirtualHost(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val key = ArmeriaJavaRegistrationChainSupport.registrationKey(expression) ?: return
        val hostname =
            ArmeriaJavaRegistrationChainSupport.extractString(expression.argumentList.expressions.firstOrNull())
                ?: expression.argumentList.expressions
                    .firstOrNull()
                    ?.text
                ?: message("route.explorer.target.virtualHost")
        if (seenRegistrations.add(key)) {
            routes +=
                ArmeriaRoute.create(
                    element = expression,
                    protocol = RouteProtocol.HTTP.presentableName(),
                    httpMethod = "",
                    path = "/",
                    target = hostname,
                    routeMatch = RouteMatch.VIRTUAL_HOST,
                    virtualHostName = hostname,
                    decorators = ArmeriaDecoratorSupport.collectProgrammaticDecorators(expression, "/"),
                    timeoutHints = ArmeriaTimeoutSupport.collectBuilderTimeoutHints(expression),
                )
        }
        collectVirtualHostScopedRegistrations(expression, routes, seenRegistrations, hostname)
    }

    private fun collectVirtualHostScopedRegistrations(
        virtualHostCall: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        hostname: String,
    ) {
        val scopedKeys = linkedSetOf<String>()
        collectForwardChainedRegistrations(virtualHostCall, routes, seenRegistrations, hostname, scopedKeys)
        for (argument in virtualHostCall.argumentList.expressions) {
            val lambdaBody = (argument as? PsiLambdaExpression)?.body ?: continue
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
            val element = route.pointer.element as? PsiMethodCallExpression ?: return@annotateRoutesByKeys null
            ArmeriaJavaRegistrationChainSupport.registrationKey(element)
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
        virtualHostCall: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        hostname: String,
        scopedKeys: MutableSet<String>,
    ) {
        var current = ArmeriaJavaRegistrationChainSupport.findImmediateNextChainedCall(virtualHostCall)
        while (current != null) {
            val methodName = current.methodExpression.referenceName
            if (methodName == "build" && current.argumentList.expressionCount == 0) {
                break
            }
            processVirtualHostScopedCall(current, routes, seenRegistrations, hostname, scopedKeys)
            current = ArmeriaJavaRegistrationChainSupport.findImmediateNextChainedCall(current)
        }
    }

    private fun collectRegistrationsInVirtualHostScope(
        root: PsiElement,
        outerVirtualHostCall: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        hostname: String,
        scopedKeys: MutableSet<String>,
    ) {
        val methodCalls =
            buildList {
                if (root is PsiMethodCallExpression) {
                    add(root)
                }
                addAll(PsiTreeUtil.findChildrenOfType(root, PsiMethodCallExpression::class.java))
            }
        methodCalls.forEach { nested ->
            if (nested == outerVirtualHostCall || isInsideInnerVirtualHost(nested, outerVirtualHostCall)) {
                return@forEach
            }
            processVirtualHostScopedCall(nested, routes, seenRegistrations, hostname, scopedKeys)
        }
    }

    private fun isInsideInnerVirtualHost(
        element: PsiElement,
        outerVirtualHostCall: PsiMethodCallExpression,
    ): Boolean {
        var parent = element.parent
        while (parent != null && parent != outerVirtualHostCall) {
            if (parent is PsiMethodCallExpression &&
                parent.methodExpression.referenceName == ServiceRegistrationMethod.VIRTUAL_HOST.methodName &&
                parent != outerVirtualHostCall
            ) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

    private fun processVirtualHostScopedCall(
        call: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        hostname: String,
        scopedKeys: MutableSet<String>,
    ) {
        val methodName = call.methodExpression.referenceName ?: return
        when {
            methodName == ServiceRegistrationMethod.VIRTUAL_HOST.methodName -> {
                if (ArmeriaBuilderCallHeuristics.isClearlyNonArmeriaJavaRegistrationCall(call)) {
                    return
                }
                addVirtualHost(call, routes, seenRegistrations)
            }
            methodName == "build" -> {
                val sizeBefore = routes.size
                if (ArmeriaBuilderCallHeuristics.looksLikeArmeriaFluentRouteBuild(call)) {
                    ArmeriaExtendedRegistrationCollectorFluentRoute.tryCollectFluentRoute(
                        call,
                        routes,
                        seenRegistrations,
                        requireBuilderCall = true,
                    )
                }
                ArmeriaJavaRegistrationChainSupport.registrationKey(call)?.let(scopedKeys::add)
                annotateVirtualHostForCall(call, routes, sizeBefore, hostname)
            }
            methodName in CoreServiceRegistrationMethod.METHOD_NAMES -> {
                if (ArmeriaBuilderCallHeuristics.isClearlyNonArmeriaJavaRegistrationCall(call)) {
                    return
                }
                val sizeBefore = routes.size
                ArmeriaRouteCollectorServiceRegistration.addServiceRegistrationFromCall(call, routes, seenRegistrations)
                ArmeriaJavaRegistrationChainSupport.registrationKey(call)?.let(scopedKeys::add)
                annotateVirtualHostForCall(call, routes, sizeBefore, hostname)
            }
            methodName in ServiceRegistrationMethod.EXTENDED_METHOD_NAMES -> {
                if (ArmeriaBuilderCallHeuristics.isClearlyNonArmeriaJavaRegistrationCall(call)) {
                    return
                }
                val sizeBefore = routes.size
                ArmeriaExtendedRegistrationCollector.collectFromMethodCall(call, routes, seenRegistrations, requireBuilderCall = false)
                ArmeriaJavaRegistrationChainSupport.registrationKey(call)?.let(scopedKeys::add)
                annotateVirtualHostForCall(call, routes, sizeBefore, hostname)
            }
        }
    }

    private fun annotateVirtualHostForCall(
        call: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        sizeBefore: Int,
        hostname: String,
    ) {
        val key = ArmeriaJavaRegistrationChainSupport.registrationKey(call)
        if (key != null) {
            ArmeriaRouteVirtualHostAnnotator.annotateByKey(routes, key, hostname) { route ->
                val element = route.pointer.element as? PsiMethodCallExpression ?: return@annotateByKey null
                ArmeriaJavaRegistrationChainSupport.registrationKey(element)
            }
        } else {
            ArmeriaRouteVirtualHostAnnotator.annotateRoutesAddedSince(routes, sizeBefore, hostname)
        }
    }
}
