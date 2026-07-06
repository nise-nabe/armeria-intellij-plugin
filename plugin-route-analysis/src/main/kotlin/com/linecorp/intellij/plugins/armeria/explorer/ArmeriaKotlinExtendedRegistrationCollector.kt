package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiStatement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaKotlinExtendedRegistrationCollector {

    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            val ktFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            if (!ArmeriaKotlinRouteCollector.referencesArmeriaKotlinContent(ktFile)) {
                continue
            }
            collectFromFile(ktFile, routes, seenRegistrations)
        }
    }

    private fun collectFromFile(
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
            if (!ArmeriaKotlinRouteCollector.looksLikeArmeriaBuilderCall(call)) {
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
                val rawPath = extractKotlinString(pathArg) ?: "/"
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
                val path = pathArg?.let(::extractKotlinString)?.let(ArmeriaRouteSupport::normalizePath)
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
                val rawPath = extractKotlinString(pathArg) ?: return
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
        if (!seenRegistrations.add(key)) {
            return
        }
        val pathArg = call.valueArguments.firstOrNull()?.getArgumentExpression()
        val hostname = extractKotlinString(pathArg) ?: pathArg?.text
            ?: message("route.explorer.target.virtualHost")
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
        collectNestedRegistrations(call, routes, seenRegistrations, hostname)
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
        val chainInfo = extractFluentRouteChain(buildCall, requireRouteAnchor = false) ?: return
        val key = registrationKey(buildCall) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        routes += createFluentRoute(buildCall, chainInfo)
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
        val chainInfo = extractFluentRouteChain(buildCall, requireRouteAnchor = true) ?: return
        if (requireBuilderCall && !ArmeriaKotlinRouteCollector.looksLikeArmeriaBuilderCall(buildCall)) {
            return
        }
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

    private data class FluentRouteChainInfo(
        val httpMethod: String,
        val path: String,
        val pathType: PathType,
        val target: String,
    )

    private fun extractFluentRouteChain(
        buildCall: KtCallExpression,
        requireRouteAnchor: Boolean,
    ): FluentRouteChainInfo? {
        if (resolveCallName(buildCall) != "build") {
            return null
        }
        var current: KtCallExpression? = parentCallExpression(buildCall)
        var httpMethod = ""
        var path = "/"
        var pathType = PathType.EXACT
        var foundRoute = false
        var foundPath = false
        while (current != null) {
            when (resolveCallName(current)) {
                "route" -> {
                    foundRoute = true
                    break
                }
                in ServiceRegistrationMethod.FLUENT_ROUTE_HTTP_METHODS -> {
                    httpMethod = resolveCallName(current)!!.uppercase()
                    parsePathFromCall(current)?.let { parsed ->
                        path = parsed.second
                        pathType = parsed.first
                        foundPath = true
                    }
                }
                "path" -> parsePathFromCall(current)?.let { parsed ->
                    path = parsed.second
                    pathType = parsed.first
                    foundPath = true
                }
                "pathPrefix" -> {
                    val raw = extractKotlinString(current.valueArguments.firstOrNull()?.getArgumentExpression()) ?: path
                    val parsed = ArmeriaRouteSupport.parsePathType("prefix:$raw")
                    pathType = parsed.first
                    path = parsed.second
                    foundPath = true
                }
                "pathRegex" -> {
                    val raw = extractKotlinString(current.valueArguments.firstOrNull()?.getArgumentExpression()) ?: path
                    val parsed = ArmeriaRouteSupport.parsePathType("regex:$raw")
                    pathType = parsed.first
                    path = parsed.second
                    foundPath = true
                }
                "pathGlob" -> {
                    val raw = extractKotlinString(current.valueArguments.firstOrNull()?.getArgumentExpression()) ?: path
                    val parsed = ArmeriaRouteSupport.parsePathType("glob:$raw")
                    pathType = parsed.first
                    path = parsed.second
                    foundPath = true
                }
                "methods", "method" -> {
                    httpMethod = current.valueArguments.joinToString(", ") { argument ->
                        argument.getArgumentExpression()?.text?.removePrefix("HttpMethod.")?.uppercase().orEmpty()
                    }
                }
            }
            current = parentCallExpression(current)
        }
        if (requireRouteAnchor && !foundRoute) {
            return null
        }
        if (!requireRouteAnchor && !foundPath) {
            return null
        }
        val handlerArg = buildCall.valueArguments.firstOrNull()?.getArgumentExpression()?.text
        return FluentRouteChainInfo(
            httpMethod = httpMethod,
            path = path,
            pathType = pathType,
            target = handlerArg ?: message("route.explorer.target.fluentRoute"),
        )
    }

    private fun parsePathFromCall(call: KtCallExpression): Pair<PathType, String>? {
        val raw = extractKotlinString(call.valueArguments.firstOrNull()?.getArgumentExpression()) ?: return null
        return ArmeriaRouteSupport.parsePathType(raw)
    }

    private data class RouteDecoratorChainInfo(
        val pathPattern: String,
        val pathType: PathType,
        val methods: String,
        val decoratorLabel: String,
    )

    private fun extractRouteDecoratorChain(routeDecoratorCall: KtCallExpression): RouteDecoratorChainInfo {
        val outerBuild = findForwardChainedCall(routeDecoratorCall) { call ->
            resolveCallName(call) == "build" && call.valueArguments.isEmpty()
        }
        val chainCalls = methodCallsBetweenInStatement(routeDecoratorCall, outerBuild)
        var pathPattern = "/**"
        var pathType = PathType.GLOB
        var methods = ""
        var decoratorLabel = message("route.explorer.target.routeDecorator")
        for (call in chainCalls) {
            when (resolveCallName(call)) {
                "path", "pathPrefix", "pathRegex", "pathGlob" -> {
                    val raw = extractKotlinString(call.valueArguments.firstOrNull()?.getArgumentExpression())
                        ?: pathPattern
                    val parsed = ArmeriaRouteSupport.parsePathType(
                        when (resolveCallName(call)) {
                            "pathPrefix" -> "prefix:$raw"
                            "pathRegex" -> "regex:$raw"
                            "pathGlob" -> "glob:$raw"
                            else -> raw
                        },
                    )
                    pathType = parsed.first
                    pathPattern = parsed.second
                }
                "methods", "method" -> {
                    methods = call.valueArguments.joinToString(", ") { argument ->
                        argument.getArgumentExpression()?.text?.removePrefix("HttpMethod.")?.uppercase().orEmpty()
                    }
                }
                "build" -> {
                    val decoratorArg = call.valueArguments.firstOrNull()?.getArgumentExpression()
                    if (decoratorArg != null && call.valueArguments.isNotEmpty()) {
                        decoratorLabel = ArmeriaDecoratorSupport.labelDecorator(decoratorArg.text)
                    }
                }
            }
        }
        return RouteDecoratorChainInfo(pathPattern, pathType, methods, decoratorLabel)
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

    private fun collectNestedRegistrations(
        virtualHostCall: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        hostname: String,
    ) {
        var current: KtCallExpression? = previousCallInChain(virtualHostCall)
        while (current != null) {
            val methodName = resolveCallName(current) ?: break
            if (methodName in ServiceRegistrationMethod.METHOD_NAMES - ServiceRegistrationMethod.EXTENDED_METHOD_NAMES &&
                ArmeriaKotlinRouteCollector.looksLikeArmeriaBuilderCall(current)
            ) {
                val beforeSize = routes.size
                ArmeriaKotlinRouteCollector.collectServiceRegistrationsInScope(current, routes, seenRegistrations)
                annotateRouteWithVirtualHost(routes, current, hostname, routes.size > beforeSize)
            }
            if (methodName in ServiceRegistrationMethod.EXTENDED_METHOD_NAMES &&
                methodName != ServiceRegistrationMethod.VIRTUAL_HOST.methodName
            ) {
                val beforeSize = routes.size
                collectFromKotlinCall(current, methodName, routes, seenRegistrations)
                annotateRouteWithVirtualHost(routes, current, hostname, routes.size > beforeSize)
            }
            current = previousCallInChain(current)
        }
        annotatePrecedingRoutesInStatement(virtualHostCall, routes, hostname)
        virtualHostCall.forEachDescendant { element ->
            val nested = element as? KtCallExpression ?: return@forEachDescendant
            if (nested == virtualHostCall) {
                return@forEachDescendant
            }
            annotateNestedVirtualHostRegistration(nested, routes, seenRegistrations, hostname)
        }
    }

    private fun annotateNestedVirtualHostRegistration(
        nested: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        hostname: String,
    ) {
        when (resolveCallName(nested)) {
            "build" -> {
                val beforeSize = routes.size
                tryCollectFluentRoute(nested, routes, seenRegistrations, requireBuilderCall = false)
                annotateRouteWithVirtualHost(routes, nested, hostname, routes.size > beforeSize)
            }
            in ServiceRegistrationMethod.CORE_METHOD_NAMES -> {
                val beforeSize = routes.size
                ArmeriaKotlinRouteCollector.collectServiceRegistrationsInScope(nested, routes, seenRegistrations)
                annotateRouteWithVirtualHost(routes, nested, hostname, routes.size > beforeSize)
            }
            in ServiceRegistrationMethod.EXTENDED_METHOD_NAMES -> {
                if (resolveCallName(nested) == ServiceRegistrationMethod.VIRTUAL_HOST.methodName) {
                    return
                }
                val methodName = resolveCallName(nested) ?: return
                val beforeSize = routes.size
                collectFromKotlinCall(nested, methodName, routes, seenRegistrations)
                annotateRouteWithVirtualHost(routes, nested, hostname, routes.size > beforeSize)
            }
        }
    }

    private fun annotatePrecedingRoutesInStatement(
        anchorCall: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        hostname: String,
    ) {
        if (resolveCallName(anchorCall) != ServiceRegistrationMethod.VIRTUAL_HOST.methodName) {
            return
        }
        val virtualFile = anchorCall.containingKtFile.virtualFile ?: return
        val statement = anchorCall.getParentOfType<PsiStatement>(strict = false) ?: return
        for (index in routes.indices) {
            val route = routes[index]
            if (route.virtualHostName.isNotEmpty() || route.routeMatch == RouteMatch.VIRTUAL_HOST) {
                continue
            }
            val element = route.pointer.element ?: continue
            if (element.containingFile.virtualFile != virtualFile) {
                continue
            }
            if (PsiTreeUtil.getParentOfType(element, PsiStatement::class.java) != statement) {
                continue
            }
            routes[index] = route.copy(virtualHostName = hostname)
        }
    }

    private fun annotateRouteWithVirtualHost(
        routes: MutableList<ArmeriaRoute>,
        registrationCall: KtCallExpression,
        hostname: String,
        addedNewRoute: Boolean,
    ) {
        if (addedNewRoute) {
            annotateLastRouteWithVirtualHost(routes, hostname)
            return
        }
        val registrationCallKey = registrationKey(registrationCall) ?: return
        val index = routes.indexOfLast { route ->
            val element = route.pointer.element as? KtCallExpression ?: return@indexOfLast false
            registrationKey(element) == registrationCallKey
        }
        if (index < 0) {
            return
        }
        val route = routes[index]
        if (route.virtualHostName.isEmpty() && route.routeMatch != RouteMatch.VIRTUAL_HOST) {
            routes[index] = route.copy(virtualHostName = hostname)
        }
    }

    private fun annotateLastRouteWithVirtualHost(routes: MutableList<ArmeriaRoute>, hostname: String) {
        val last = routes.lastOrNull() ?: return
        if (last.virtualHostName.isNotEmpty() || last.routeMatch == RouteMatch.VIRTUAL_HOST) {
            return
        }
        routes[routes.lastIndex] = last.copy(virtualHostName = hostname)
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

    private fun parentReceiverExpression(call: KtCallExpression): KtExpression? {
        val parent = call.parent as? KtDotQualifiedExpression ?: return null
        return parent.receiverExpression
    }

    private fun previousCallInChain(call: KtCallExpression): KtCallExpression? {
        val receiver = parentReceiverExpression(call) ?: return null
        return when (receiver) {
            is KtCallExpression -> receiver
            is KtDotQualifiedExpression -> receiver.selectorExpression as? KtCallExpression
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

    private fun extractKotlinString(expression: KtExpression?): String? {
        return when (expression) {
            is KtStringTemplateExpression -> expression.entries.joinToString("") { it.text }.trim('"')
            else -> expression?.text?.trim('"')
        }
    }
}
