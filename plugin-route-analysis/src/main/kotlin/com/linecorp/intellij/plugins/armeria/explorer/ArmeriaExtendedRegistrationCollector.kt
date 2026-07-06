package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiStatement
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaExtendedRegistrationCollector {

    fun collectFromJavaFile(
        file: PsiJavaFile,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                collectFromMethodCall(expression, routes, seenRegistrations)
                tryCollectFluentRoute(expression, routes, seenRegistrations)
                super.visitMethodCallExpression(expression)
            }
        })
    }

    fun collectFromMethodCall(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        requireBuilderCall: Boolean = true,
    ) {
        val methodName = expression.methodExpression.referenceName ?: return
        if (methodName !in ServiceRegistrationMethod.EXTENDED_METHOD_NAMES) {
            return
        }
        if (requireBuilderCall && !looksLikeArmeriaBuilderCall(expression)) {
            return
        }
        when (ServiceRegistrationMethod.fromMethodName(methodName)) {
            ServiceRegistrationMethod.FILE_SERVICE -> addFileService(expression, routes, seenRegistrations)
            ServiceRegistrationMethod.HEALTH_CHECK_SERVICE -> addHealthCheck(expression, routes, seenRegistrations)
            ServiceRegistrationMethod.VIRTUAL_HOST -> addVirtualHost(expression, routes, seenRegistrations)
            ServiceRegistrationMethod.ROUTE_DECORATOR -> addRouteDecorator(expression, routes, seenRegistrations)
            ServiceRegistrationMethod.WITH_ROUTE -> addWithRoute(expression, routes, seenRegistrations)
            ServiceRegistrationMethod.DECORATOR_UNDER -> addDecoratorUnder(expression, routes, seenRegistrations)
            else -> Unit
        }
    }

    private fun addFileService(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val key = registrationKey(expression) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        val path = extractString(expression.argumentList.expressions.getOrNull(0)) ?: "/"
        val (pathType, normalizedPath) = ArmeriaRouteSupport.parsePathType(path)
        val targetExpr = expression.argumentList.expressions.getOrNull(1)
        val target = targetExpr?.text ?: message("route.explorer.target.fileService")
        routes += ArmeriaRoute.create(
            element = expression,
            protocol = RouteProtocol.HTTP.presentableName(),
            httpMethod = "",
            path = normalizedPath,
            target = target,
            routeMatch = RouteMatch.FILE_SERVICE,
            pathType = pathType,
            decorators = ArmeriaDecoratorSupport.collectProgrammaticDecorators(expression, normalizedPath),
            timeoutHints = ArmeriaTimeoutSupport.collectBuilderTimeoutHints(expression),
        )
    }

    private fun addHealthCheck(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val key = registrationKey(expression) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        val path = expression.argumentList.expressions.firstOrNull()
            ?.let(::extractString)
            ?.let(ArmeriaRouteSupport::normalizePath)
            ?: "/internal/healthcheck"
        routes += ArmeriaRoute.create(
            element = expression,
            protocol = RouteProtocol.HTTP.presentableName(),
            httpMethod = "GET",
            path = path,
            target = message("route.explorer.target.healthCheck"),
            routeMatch = RouteMatch.HEALTH_CHECK,
            decorators = ArmeriaDecoratorSupport.collectProgrammaticDecorators(expression, path),
            timeoutHints = ArmeriaTimeoutSupport.collectBuilderTimeoutHints(expression),
        )
    }

    private fun addVirtualHost(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val key = registrationKey(expression) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        val hostname = extractString(expression.argumentList.expressions.firstOrNull())
            ?: expression.argumentList.expressions.firstOrNull()?.text
            ?: message("route.explorer.target.virtualHost")
        routes += ArmeriaRoute.create(
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
        collectNestedRegistrations(expression, routes, seenRegistrations, hostname)
    }

    private fun addDecoratorUnder(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val key = registrationKey(expression) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        val rawPath = extractString(expression.argumentList.expressions.getOrNull(0)) ?: return
        val (pathType, normalizedPath) = ArmeriaRouteSupport.parsePathType(rawPath)
        val decoratorArg = expression.argumentList.expressions.getOrNull(1)
        val decoratorLabel = decoratorArg?.text?.let(ArmeriaDecoratorSupport::labelDecorator)
            ?: message("route.explorer.target.decoratorUnder")
        routes += ArmeriaRoute.create(
            element = expression,
            protocol = RouteProtocol.HTTP.presentableName(),
            httpMethod = "",
            path = normalizedPath,
            target = decoratorLabel,
            routeMatch = RouteMatch.DECORATOR_UNDER,
            pathType = pathType,
            decorators = listOf(decoratorLabel),
            timeoutHints = ArmeriaTimeoutSupport.collectBuilderTimeoutHints(expression),
        )
    }

    private fun addWithRoute(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val lambdaBody = (expression.argumentList.expressions.firstOrNull() as? PsiLambdaExpression)?.body ?: return
        val buildCall = PsiTreeUtil.findChildrenOfType(lambdaBody, PsiMethodCallExpression::class.java)
            .filter { it.methodExpression.referenceName == "build" }
            .firstOrNull { extractFluentRouteChain(it, requireRouteAnchor = false) != null }
            ?: return
        val chainInfo = extractFluentRouteChain(buildCall, requireRouteAnchor = false) ?: return
        val key = registrationKey(buildCall) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        routes += createFluentRoute(buildCall, chainInfo)
    }

    private fun tryCollectFluentRoute(
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
        if (requireBuilderCall && !looksLikeArmeriaBuilderCall(buildCall)) {
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
        element: PsiMethodCallExpression,
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
            decorators = ArmeriaDecoratorSupport.collectProgrammaticDecorators(element, chainInfo.path),
            timeoutHints = ArmeriaTimeoutSupport.collectBuilderTimeoutHints(element),
        )
    }

    private data class FluentRouteChainInfo(
        val httpMethod: String,
        val path: String,
        val pathType: PathType,
        val target: String,
    )

    private fun extractFluentRouteChain(
        buildCall: PsiMethodCallExpression,
        requireRouteAnchor: Boolean,
    ): FluentRouteChainInfo? {
        if (buildCall.methodExpression.referenceName != "build") {
            return null
        }
        var current = previousMethodCallInChain(buildCall)
        var httpMethod = ""
        var path = "/"
        var pathType = PathType.EXACT
        var foundRoute = false
        var foundPath = false
        while (current != null) {
            when (current.methodExpression.referenceName) {
                "route" -> {
                    foundRoute = true
                    break
                }
                in ServiceRegistrationMethod.FLUENT_ROUTE_HTTP_METHODS -> {
                    httpMethod = current.methodExpression.referenceName!!.uppercase()
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
                    val raw = extractString(current.argumentList.expressions.firstOrNull()) ?: path
                    val parsed = ArmeriaRouteSupport.parsePathType("prefix:$raw")
                    pathType = parsed.first
                    path = parsed.second
                    foundPath = true
                }
                "pathRegex" -> {
                    val raw = extractString(current.argumentList.expressions.firstOrNull()) ?: path
                    val parsed = ArmeriaRouteSupport.parsePathType("regex:$raw")
                    pathType = parsed.first
                    path = parsed.second
                    foundPath = true
                }
                "pathGlob" -> {
                    val raw = extractString(current.argumentList.expressions.firstOrNull()) ?: path
                    val parsed = ArmeriaRouteSupport.parsePathType("glob:$raw")
                    pathType = parsed.first
                    path = parsed.second
                    foundPath = true
                }
                "methods", "method" -> {
                    httpMethod = current.argumentList.expressions.joinToString(", ") { argument ->
                        argument.text.removePrefix("HttpMethod.").uppercase()
                    }
                }
            }
            current = previousMethodCallInChain(current)
        }
        if (requireRouteAnchor && !foundRoute) {
            return null
        }
        if (!requireRouteAnchor && !foundPath) {
            return null
        }
        val handlerArg = buildCall.argumentList.expressions.firstOrNull()?.text
        return FluentRouteChainInfo(
            httpMethod = httpMethod,
            path = path,
            pathType = pathType,
            target = handlerArg ?: message("route.explorer.target.fluentRoute"),
        )
    }

    private fun parsePathFromCall(call: PsiMethodCallExpression): Pair<PathType, String>? {
        val raw = extractString(call.argumentList.expressions.firstOrNull()) ?: return null
        return ArmeriaRouteSupport.parsePathType(raw)
    }

    private fun addRouteDecorator(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val key = registrationKey(expression) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        val chainInfo = extractRouteDecoratorChain(expression)
        routes += ArmeriaRoute.create(
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

    private fun collectNestedRegistrations(
        virtualHostCall: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        hostname: String,
    ) {
        var current = previousMethodCallInChain(virtualHostCall)
        while (current != null) {
            val methodName = current.methodExpression.referenceName
            if (methodName in ServiceRegistrationMethod.METHOD_NAMES - ServiceRegistrationMethod.EXTENDED_METHOD_NAMES &&
                looksLikeArmeriaBuilderCall(current)
            ) {
                val added = ArmeriaRouteCollector.addServiceRegistrationFromCall(current, routes, seenRegistrations)
                annotateRouteWithVirtualHost(routes, current, hostname, added)
            }
            if (methodName in ServiceRegistrationMethod.EXTENDED_METHOD_NAMES &&
                methodName != ServiceRegistrationMethod.VIRTUAL_HOST.methodName
            ) {
                val beforeSize = routes.size
                collectFromMethodCall(current, routes, seenRegistrations)
                annotateRouteWithVirtualHost(routes, current, hostname, routes.size > beforeSize)
            }
            current = previousMethodCallInChain(current)
        }
        annotatePrecedingRoutesInStatement(virtualHostCall, routes, hostname)
        PsiTreeUtil.findChildrenOfType(virtualHostCall, PsiMethodCallExpression::class.java).forEach { nested ->
            if (nested == virtualHostCall) {
                return@forEach
            }
            annotateNestedVirtualHostRegistration(nested, routes, seenRegistrations, hostname)
        }
    }

    private fun annotateNestedVirtualHostRegistration(
        nested: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        hostname: String,
    ) {
        when (nested.methodExpression.referenceName) {
            "build" -> {
                val beforeSize = routes.size
                tryCollectFluentRoute(nested, routes, seenRegistrations, requireBuilderCall = false)
                annotateRouteWithVirtualHost(routes, nested, hostname, routes.size > beforeSize)
            }
            in ServiceRegistrationMethod.CORE_METHOD_NAMES -> {
                val added = ArmeriaRouteCollector.addServiceRegistrationFromCall(nested, routes, seenRegistrations)
                annotateRouteWithVirtualHost(routes, nested, hostname, added)
            }
            in ServiceRegistrationMethod.EXTENDED_METHOD_NAMES -> {
                if (nested.methodExpression.referenceName == ServiceRegistrationMethod.VIRTUAL_HOST.methodName) {
                    return
                }
                val beforeSize = routes.size
                collectFromMethodCall(nested, routes, seenRegistrations, requireBuilderCall = false)
                annotateRouteWithVirtualHost(routes, nested, hostname, routes.size > beforeSize)
            }
        }
    }

    private fun annotatePrecedingRoutesInStatement(
        anchorCall: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        hostname: String,
    ) {
        if (anchorCall.methodExpression.referenceName != ServiceRegistrationMethod.VIRTUAL_HOST.methodName) {
            return
        }
        val virtualFile = anchorCall.containingFile.virtualFile ?: return
        val statement = PsiTreeUtil.getParentOfType(anchorCall, PsiStatement::class.java) ?: return
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
        registrationCall: PsiMethodCallExpression,
        hostname: String,
        addedNewRoute: Boolean,
    ) {
        if (addedNewRoute) {
            annotateLastRouteWithVirtualHost(routes, hostname)
            return
        }
        val registrationCallKey = registrationKey(registrationCall) ?: return
        val index = routes.indexOfLast { route ->
            val element = route.pointer.element as? PsiMethodCallExpression ?: return@indexOfLast false
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

    private data class RouteDecoratorChainInfo(
        val pathPattern: String,
        val pathType: PathType,
        val methods: String,
        val decoratorLabel: String,
    )

    private fun extractRouteDecoratorChain(routeDecoratorCall: PsiMethodCallExpression): RouteDecoratorChainInfo {
        val outerBuild = findForwardChainedCall(routeDecoratorCall) { call ->
            call.methodExpression.referenceName == "build" && call.argumentList.expressionCount == 0
        }
        val chainCalls = methodCallsBetweenInStatement(routeDecoratorCall, outerBuild)
        var pathPattern = "/**"
        var pathType = PathType.GLOB
        var methods = ""
        var decoratorLabel = message("route.explorer.target.routeDecorator")
        for (methodCall in chainCalls) {
            when (methodCall.methodExpression.referenceName) {
                "path", "pathPrefix", "pathRegex", "pathGlob" -> {
                    val raw = extractString(methodCall.argumentList.expressions.firstOrNull()) ?: pathPattern
                    val parsed = ArmeriaRouteSupport.parsePathType(
                        when (methodCall.methodExpression.referenceName) {
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
                    methods = methodCall.argumentList.expressions.joinToString(", ") { argument ->
                        argument.text.removePrefix("HttpMethod.").uppercase()
                    }
                }
                "build" -> {
                    val decoratorArg = methodCall.argumentList.expressions.firstOrNull()
                    if (decoratorArg != null) {
                        decoratorLabel = ArmeriaDecoratorSupport.labelDecorator(decoratorArg.text)
                    }
                }
            }
        }
        return RouteDecoratorChainInfo(pathPattern, pathType, methods, decoratorLabel)
    }

    private fun methodCallsBetweenInStatement(
        start: PsiMethodCallExpression,
        stopExclusive: PsiMethodCallExpression?,
    ): List<PsiMethodCallExpression> {
        val statement = PsiTreeUtil.getParentOfType(start, PsiStatement::class.java) ?: return listOf(start)
        val startOffset = start.textRange.startOffset
        val stopOffset = stopExclusive?.textRange?.startOffset ?: Int.MAX_VALUE
        return PsiTreeUtil.findChildrenOfType(statement, PsiMethodCallExpression::class.java)
            .filter { call -> call.textRange.startOffset in startOffset until stopOffset }
            .sortedBy { it.textRange.startOffset }
    }

    private fun previousMethodCallInChain(call: PsiMethodCallExpression): PsiMethodCallExpression? {
        var expression: PsiElement? = call.methodExpression.qualifierExpression
        while (expression != null) {
            when (expression) {
                is PsiMethodCallExpression -> return expression
                is PsiReferenceExpression -> expression = expression.qualifier
                else -> return null
            }
        }
        return null
    }

    private fun findForwardChainedCall(
        start: PsiMethodCallExpression,
        predicate: (PsiMethodCallExpression) -> Boolean,
    ): PsiMethodCallExpression? {
        var current: PsiMethodCallExpression? = start
        while (current != null) {
            if (predicate(current)) {
                return current
            }
            current = findNextChainedMethodCall(current)
        }
        return null
    }

    private fun findNextChainedMethodCall(call: PsiMethodCallExpression): PsiMethodCallExpression? {
        var element: PsiElement? = call
        while (element != null) {
            val parent = element.parent ?: return null
            when (parent) {
                is PsiMethodCallExpression -> {
                    if (parent.methodExpression.qualifierExpression == element ||
                        (parent.methodExpression.qualifierExpression as? PsiReferenceExpression)?.qualifier == element
                    ) {
                        return parent
                    }
                }
                is PsiReferenceExpression -> {
                    val grandParent = parent.parent
                    if (grandParent is PsiMethodCallExpression &&
                        (grandParent.methodExpression.qualifierExpression == parent ||
                            (grandParent.methodExpression.qualifierExpression as? PsiReferenceExpression)?.qualifier == parent)
                    ) {
                        return grandParent
                    }
                }
            }
            element = parent
        }
        return null
    }

    private fun registrationKey(expression: PsiMethodCallExpression): String? {
        val virtualFile = expression.containingFile?.virtualFile ?: return null
        val methodName = expression.methodExpression.referenceName ?: return null
        return ArmeriaRouteSupport.registrationKey(
            virtualFile.path,
            expression.textRange,
            methodName,
        )
    }

    private fun looksLikeArmeriaBuilderCall(expression: PsiMethodCallExpression): Boolean {
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
        if (resolvedClass?.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX) == true) {
            return true
        }
        val qualifierText = expression.methodExpression.qualifierExpression?.text ?: return false
        return ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(qualifierText) ||
            qualifierText.contains("routeDecorator")
    }

    private fun extractString(expression: PsiExpression?): String? {
        return when (expression) {
            null -> null
            is PsiLiteralExpression -> expression.value as? String
            else -> expression.text.takeIf { it.isNotBlank() }?.trim('"')
        }
    }
}
