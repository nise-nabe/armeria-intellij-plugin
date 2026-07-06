package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiStatement
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaKotlinExtendedRegistrationCollector {

    fun collectFromFile(
        file: KtFile,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        file.forEachDescendant { element ->
            val call = element as? KtCallExpression ?: return@forEachDescendant
            val methodName = resolveCallName(call) ?: return@forEachDescendant
            if (methodName == "build") {
                tryCollectFluentRoute(call, routes, seenRegistrations)
            }
            if (methodName !in ServiceRegistrationMethod.EXTENDED_METHOD_NAMES) {
                return@forEachDescendant
            }
            if (!ArmeriaBuilderCallHeuristics.looksLikeKotlinBuilderCall(call)) {
                return@forEachDescendant
            }
            collectFromKotlinCall(call, methodName, routes, seenRegistrations)
        }
    }

    private fun collectFromKotlinCall(
        call: KtCallExpression,
        methodName: String,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        when (ServiceRegistrationMethod.fromMethodName(methodName)) {
            ServiceRegistrationMethod.WITH_ROUTE -> {
                addWithRoute(call, routes, seenRegistrations)
                return
            }
            ServiceRegistrationMethod.VIRTUAL_HOST -> {
                addVirtualHost(call, routes, seenRegistrations)
                return
            }
            else -> Unit
        }
        val key = registrationKey(call) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        val pathArg = call.valueArguments.firstOrNull()?.getArgumentExpression()
        when (ServiceRegistrationMethod.fromMethodName(methodName)) {
            ServiceRegistrationMethod.FILE_SERVICE -> {
                val rawPath = ArmeriaKotlinExpressionSupport.extractKotlinString(pathArg) ?: "/"
                val (pathType, normalizedPath) = ArmeriaRouteSupport.parsePathType(rawPath)
                val target = call.valueArguments.getOrNull(1)?.getArgumentExpression()?.text
                    ?: message("route.explorer.target.fileService")
                routes += ArmeriaRoute.create(
                    element = call,
                    protocol = RouteProtocol.HTTP.presentableName(),
                    httpMethod = "",
                    path = normalizedPath,
                    target = target,
                    routeMatch = RouteMatch.FILE_SERVICE,
                    pathType = pathType,
                    decorators = ArmeriaKotlinDecoratorSupport.collectProgrammaticDecorators(call, normalizedPath),
                    timeoutHints = ArmeriaKotlinTimeoutSupport.collectBuilderTimeoutHints(call),
                )
            }
            ServiceRegistrationMethod.HEALTH_CHECK_SERVICE -> {
                val path = pathArg?.let(ArmeriaKotlinExpressionSupport::extractKotlinString)?.let(ArmeriaRouteSupport::normalizePath)
                    ?: "/internal/healthcheck"
                routes += ArmeriaRoute.create(
                    element = call,
                    protocol = RouteProtocol.HTTP.presentableName(),
                    httpMethod = "GET",
                    path = path,
                    target = message("route.explorer.target.healthCheck"),
                    routeMatch = RouteMatch.HEALTH_CHECK,
                    decorators = ArmeriaKotlinDecoratorSupport.collectProgrammaticDecorators(call, path),
                    timeoutHints = ArmeriaKotlinTimeoutSupport.collectBuilderTimeoutHints(call),
                )
            }
            ServiceRegistrationMethod.ROUTE_DECORATOR -> {
                val chainInfo = extractRouteDecoratorChain(call)
                routes += ArmeriaRoute.create(
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
            }
            ServiceRegistrationMethod.DECORATOR_UNDER -> {
                val rawPath = ArmeriaKotlinExpressionSupport.extractKotlinString(pathArg) ?: return
                val (pathType, normalizedPath) = ArmeriaRouteSupport.parsePathType(rawPath)
                val decoratorArg = call.valueArguments.getOrNull(1)?.getArgumentExpression()
                val decoratorLabel = decoratorArg?.text?.let(ArmeriaDecoratorSupport::labelDecorator)
                    ?: message("route.explorer.target.decoratorUnder")
                routes += ArmeriaRoute.create(
                    element = call,
                    protocol = RouteProtocol.HTTP.presentableName(),
                    httpMethod = "",
                    path = normalizedPath,
                    target = decoratorLabel,
                    routeMatch = RouteMatch.DECORATOR_UNDER,
                    pathType = pathType,
                    decorators = listOf(decoratorLabel),
                    timeoutHints = ArmeriaKotlinTimeoutSupport.collectBuilderTimeoutHints(call),
                )
            }
            else -> Unit
        }
    }

    private fun addVirtualHost(
        call: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val key = registrationKey(call) ?: return
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

    private fun addWithRoute(
        call: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val argumentExpression = call.valueArguments.firstOrNull()?.getArgumentExpression() ?: return
        val lambdaBody = (argumentExpression as? KtLambdaExpression
            ?: argumentExpression.getParentOfType<KtLambdaExpression>(strict = false))
            ?.bodyExpression
            ?: return
        val buildCall = findFluentRouteBuildInLambda(lambdaBody)
            ?: findFluentRouteBuildAfterCall(call)
            ?: return
        addFluentRouteFromBuild(buildCall, routes, seenRegistrations, requireRouteAnchor = false)
    }

    private fun tryCollectFluentRoute(
        buildCall: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        requireBuilderCall: Boolean = true,
    ) {
        if (resolveCallName(buildCall) != "build") {
            return
        }
        if (buildCall.valueArguments.isEmpty()) {
            return
        }
        if (requireBuilderCall && !ArmeriaBuilderCallHeuristics.looksLikeKotlinBuilderCall(buildCall)) {
            return
        }
        addFluentRouteFromBuild(buildCall, routes, seenRegistrations, requireRouteAnchor = true)
    }

    private fun addFluentRouteFromBuild(
        buildCall: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        requireRouteAnchor: Boolean,
    ) {
        val chainInfo = extractFluentRouteChain(buildCall, requireRouteAnchor) ?: return
        val key = registrationKey(buildCall) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        routes += createFluentRoute(buildCall, chainInfo)
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

    private fun extractFluentRouteChain(
        buildCall: KtCallExpression,
        requireRouteAnchor: Boolean,
    ): FluentRouteChainInfo? {
        if (resolveCallName(buildCall) != "build") {
            return null
        }
        val steps = mutableListOf<RegistrationChainStep>()
        var current: KtCallExpression? = parentCallExpression(buildCall)
        while (current != null) {
            steps += toChainStep(current)
            current = parentCallExpression(current)
        }
        val handlerArg = buildCall.valueArguments.firstOrNull()?.getArgumentExpression()?.text
        return ArmeriaRegistrationChainReducer.reduceFluentRouteChain(
            stepsFromBuildUpward = steps,
            requireRouteAnchor = requireRouteAnchor,
            handlerTarget = handlerArg,
            defaultTarget = message("route.explorer.target.fluentRoute"),
        )
    }

    private fun extractRouteDecoratorChain(routeDecoratorCall: KtCallExpression): RouteDecoratorChainInfo {
        val outerBuild = findForwardChainedCall(routeDecoratorCall) { call ->
            resolveCallName(call) == "build" && call.valueArguments.isEmpty()
        }
        val chainCalls = methodCallsBetweenInStatement(routeDecoratorCall, outerBuild)
        val steps = chainCalls.map(::toChainStep)
        return ArmeriaRegistrationChainReducer.reduceRouteDecoratorChain(
            steps = steps,
            defaultDecoratorLabel = message("route.explorer.target.routeDecorator"),
        )
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
            registrationKey(element)
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
        var current = findNextChainedCall(virtualHostCall)
        while (current != null) {
            val methodName = resolveCallName(current)
            if (methodName == "build" && current.valueArguments.isEmpty()) {
                break
            }
            processVirtualHostScopedCall(current, routes, seenRegistrations, hostname, scopedKeys)
            current = findNextChainedCall(current)
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
                resolveCallName(parent) == ServiceRegistrationMethod.VIRTUAL_HOST.methodName &&
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
        val methodName = resolveCallName(call) ?: return
        when {
            methodName == ServiceRegistrationMethod.VIRTUAL_HOST.methodName -> {
                addVirtualHost(call, routes, seenRegistrations)
            }
            methodName == "build" -> {
                val sizeBefore = routes.size
                if (ArmeriaBuilderCallHeuristics.looksLikeArmeriaFluentRouteBuild(call)) {
                    tryCollectFluentRoute(call, routes, seenRegistrations, requireBuilderCall = true)
                }
                registrationKey(call)?.let(scopedKeys::add)
                annotateVirtualHostForCall(call, routes, sizeBefore, hostname)
            }
            methodName in CoreServiceRegistrationMethod.METHOD_NAMES -> {
                val sizeBefore = routes.size
                ArmeriaKotlinRouteCollector.addServiceRegistrationFromCall(call, routes, seenRegistrations)
                registrationKey(call)?.let(scopedKeys::add)
                annotateVirtualHostForCall(call, routes, sizeBefore, hostname)
            }
            methodName in ServiceRegistrationMethod.EXTENDED_METHOD_NAMES -> {
                val sizeBefore = routes.size
                if (ArmeriaBuilderCallHeuristics.looksLikeKotlinBuilderCall(call)) {
                    collectFromKotlinCall(call, methodName, routes, seenRegistrations)
                }
                registrationKey(call)?.let(scopedKeys::add)
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
        val key = registrationKey(call)
        if (key != null) {
            ArmeriaRouteVirtualHostAnnotator.annotateByKey(routes, key, hostname) { route ->
                val element = route.pointer.element as? KtCallExpression ?: return@annotateByKey null
                registrationKey(element)
            }
        } else {
            ArmeriaRouteVirtualHostAnnotator.annotateRoutesAddedSince(routes, sizeBefore, hostname)
        }
    }

    private fun toChainStep(call: KtCallExpression): RegistrationChainStep {
        return RegistrationChainStep(
            methodName = resolveCallName(call).orEmpty(),
            firstStringArg = ArmeriaKotlinExpressionSupport.extractKotlinString(call.valueArguments.firstOrNull()?.getArgumentExpression()),
            rawMethodArgs = call.valueArguments.mapNotNull { it.getArgumentExpression()?.text },
        )
    }

    private fun methodCallsBetweenInStatement(
        start: KtCallExpression,
        stopExclusive: KtCallExpression?,
    ): List<KtCallExpression> {
        val statement = start.getParentOfType<PsiStatement>(strict = false) ?: return listOf(start)
        val startOffset = start.textRange.startOffset
        val stopOffset = stopExclusive?.textRange?.startOffset ?: Int.MAX_VALUE
        val calls = mutableListOf<KtCallExpression>()
        statement.forEachDescendant { element ->
            val call = element as? KtCallExpression ?: return@forEachDescendant
            if (call.textRange.startOffset in startOffset until stopOffset) {
                calls += call
            }
        }
        return calls.sortedBy { it.textRange.startOffset }
    }

    private fun parentCallExpression(call: KtCallExpression): KtCallExpression? {
        val parent = call.parent
        return when (parent) {
            is KtDotQualifiedExpression -> {
                when (val receiver = parent.receiverExpression) {
                    is KtCallExpression -> receiver
                    is KtDotQualifiedExpression -> receiver.selectorExpression as? KtCallExpression
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun findForwardChainedCall(
        start: KtCallExpression,
        predicate: (KtCallExpression) -> Boolean,
    ): KtCallExpression? {
        var current: KtCallExpression? = start
        while (current != null) {
            if (predicate(current)) {
                return current
            }
            current = findNextChainedCall(current)
        }
        return null
    }

    private fun findNextChainedCall(call: KtCallExpression): KtCallExpression? {
        val parent = call.parent
        if (parent is KtDotQualifiedExpression && parent.receiverExpression == call) {
            return parent.selectorExpression as? KtCallExpression
        }
        if (parent is KtDotQualifiedExpression) {
            val grandParent = parent.parent as? KtDotQualifiedExpression
            if (grandParent != null && grandParent.receiverExpression == parent) {
                return grandParent.selectorExpression as? KtCallExpression
            }
        }
        return null
    }

    private fun findFluentRouteBuildAfterCall(withRouteCall: KtCallExpression): KtCallExpression? {
        val statement = withRouteCall.getParentOfType<PsiStatement>(strict = false) ?: return null
        var found: KtCallExpression? = null
        statement.forEachDescendant { element ->
            val call = element as? KtCallExpression ?: return@forEachDescendant
            if (call.textRange.startOffset <= withRouteCall.textRange.startOffset) {
                return@forEachDescendant
            }
            if (resolveCallName(call) == "build" && extractFluentRouteChain(call, requireRouteAnchor = false) != null) {
                found = call
            }
        }
        return found
    }

    private fun findFluentRouteBuildInLambda(root: KtExpression): KtCallExpression? {
        var found: KtCallExpression? = null
        root.forEachDescendant { element ->
            if (found != null) {
                return@forEachDescendant
            }
            val call = element as? KtCallExpression ?: return@forEachDescendant
            if (resolveCallName(call) == "build" && extractFluentRouteChain(call, requireRouteAnchor = false) != null) {
                found = call
            }
        }
        return found
    }

    private fun registrationKey(call: KtCallExpression): String? {
        val virtualFile = call.containingKtFile.virtualFile ?: return null
        val methodName = resolveCallName(call) ?: return null
        return ArmeriaRouteSupport.registrationKey(
            virtualFile.path,
            call.textRange,
            methodName,
        )
    }

    private fun resolveCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }
}
