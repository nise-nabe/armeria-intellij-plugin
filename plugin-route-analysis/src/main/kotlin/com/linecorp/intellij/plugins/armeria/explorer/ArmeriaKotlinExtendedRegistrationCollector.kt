package com.linecorp.intellij.plugins.armeria.explorer

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
            ServiceRegistrationMethod.VIRTUAL_HOST -> {
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
            ServiceRegistrationMethod.WITH_ROUTE -> addWithRoute(call, routes, seenRegistrations)
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

    private fun addWithRoute(
        call: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val lambdaBody = call.valueArguments.firstOrNull()?.getArgumentExpression()
            ?.getParentOfType<KtLambdaExpression>(strict = false)
            ?.bodyExpression
            ?: return
        val buildCall = findDescendantCall(lambdaBody) { resolveCallName(it) == "build" } ?: return
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
    ) {
        if (!ArmeriaKotlinRouteCollector.looksLikeArmeriaBuilderCall(buildCall)) {
            return
        }
        val chainInfo = extractFluentRouteChain(buildCall, requireRouteAnchor = true) ?: return
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
        var pathPattern = "/**"
        var pathType = PathType.EXACT
        var methods = ""
        var decoratorLabel = message("route.explorer.target.routeDecorator")
        var current: KtCallExpression? = routeDecoratorCall
        while (current != null) {
            when (resolveCallName(current)) {
                "path", "pathPrefix", "pathRegex", "pathGlob" -> {
                    val raw = extractKotlinString(current.valueArguments.firstOrNull()?.getArgumentExpression())
                        ?: pathPattern
                    val parsed = ArmeriaRouteSupport.parsePathType(
                        when (resolveCallName(current)) {
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
                    methods = current.valueArguments.joinToString(", ") { argument ->
                        argument.getArgumentExpression()?.text.orEmpty()
                    }
                }
                "build" -> {
                    val decoratorArg = current.valueArguments.firstOrNull()?.getArgumentExpression()
                    if (decoratorArg != null) {
                        decoratorLabel = ArmeriaDecoratorSupport.labelDecorator(decoratorArg.text)
                    }
                }
            }
            current = parentCallExpression(current)
        }
        return RouteDecoratorChainInfo(pathPattern, pathType, methods, decoratorLabel)
    }

    private fun collectNestedRegistrations(
        virtualHostCall: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        hostname: String,
    ) {
        var current: KtExpression? = parentReceiverExpression(virtualHostCall)
        while (current != null) {
            val call = current as? KtCallExpression ?: break
            val methodName = resolveCallName(call) ?: break
            if (methodName in ServiceRegistrationMethod.METHOD_NAMES - ServiceRegistrationMethod.EXTENDED_METHOD_NAMES &&
                ArmeriaKotlinRouteCollector.looksLikeArmeriaBuilderCall(call)
            ) {
                ArmeriaKotlinRouteCollector.collectServiceRegistrationsInScope(call, routes, seenRegistrations)
                annotateLastRouteWithVirtualHost(routes, hostname)
            }
            if (methodName in ServiceRegistrationMethod.EXTENDED_METHOD_NAMES &&
                methodName != ServiceRegistrationMethod.VIRTUAL_HOST.methodName
            ) {
                collectFromKotlinCall(call, methodName, routes, seenRegistrations)
                annotateLastRouteWithVirtualHost(routes, hostname)
            }
            current = parentReceiverExpression(call)
        }
        virtualHostCall.forEachDescendant { element ->
            val nested = element as? KtCallExpression ?: return@forEachDescendant
            if (nested == virtualHostCall) {
                return@forEachDescendant
            }
            val methodName = resolveCallName(nested) ?: return@forEachDescendant
            if (methodName in ServiceRegistrationMethod.METHOD_NAMES - ServiceRegistrationMethod.EXTENDED_METHOD_NAMES &&
                ArmeriaKotlinRouteCollector.looksLikeArmeriaBuilderCall(nested)
            ) {
                ArmeriaKotlinRouteCollector.collectServiceRegistrationsInScope(nested, routes, seenRegistrations)
                annotateLastRouteWithVirtualHost(routes, hostname)
            }
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
                val receiver = parent.receiverExpression
                when (receiver) {
                    is KtCallExpression -> receiver
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

    private fun findDescendantCall(
        root: KtExpression,
        predicate: (KtCallExpression) -> Boolean,
    ): KtCallExpression? {
        var found: KtCallExpression? = null
        root.forEachDescendant { element ->
            val call = element as? KtCallExpression ?: return@forEachDescendant
            if (predicate(call)) {
                found = call
            }
        }
        return found
    }

    private fun registrationKey(call: KtCallExpression): String? {
        val virtualFile = call.containingKtFile.virtualFile ?: return null
        return "${virtualFile.path}:${call.textRange.startOffset}"
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
