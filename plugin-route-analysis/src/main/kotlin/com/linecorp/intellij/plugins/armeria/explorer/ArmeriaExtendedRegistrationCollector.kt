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

    fun visitMethodCallExpression(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        collectFromMethodCall(expression, routes, seenRegistrations)
        tryCollectFluentRoute(expression, routes, seenRegistrations)
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
        if (requireBuilderCall && !ArmeriaBuilderCallHeuristics.looksLikeJavaBuilderCall(expression)) {
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
        val hostname = extractString(expression.argumentList.expressions.firstOrNull())
            ?: expression.argumentList.expressions.firstOrNull()?.text
            ?: message("route.explorer.target.virtualHost")
        if (seenRegistrations.add(key)) {
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
        }
        collectVirtualHostScopedRegistrations(expression, routes, seenRegistrations, hostname)
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
        addFluentRouteFromBuild(buildCall, routes, seenRegistrations, requireRouteAnchor = false)
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
        if (requireBuilderCall && !ArmeriaBuilderCallHeuristics.looksLikeJavaBuilderCall(buildCall)) {
            return
        }
        addFluentRouteFromBuild(buildCall, routes, seenRegistrations, requireRouteAnchor = true)
    }

    private fun addFluentRouteFromBuild(
        buildCall: PsiMethodCallExpression,
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

    private fun extractFluentRouteChain(
        buildCall: PsiMethodCallExpression,
        requireRouteAnchor: Boolean,
    ): FluentRouteChainInfo? {
        if (buildCall.methodExpression.referenceName != "build") {
            return null
        }
        val steps = mutableListOf<RegistrationChainStep>()
        var current = previousMethodCallInChain(buildCall)
        while (current != null) {
            steps += toChainStep(current)
            current = previousMethodCallInChain(current)
        }
        val handlerArg = buildCall.argumentList.expressions.firstOrNull()?.text
        return ArmeriaRegistrationChainReducer.reduceFluentRouteChain(
            stepsFromBuildUpward = steps,
            requireRouteAnchor = requireRouteAnchor,
            handlerTarget = handlerArg,
            defaultTarget = message("route.explorer.target.fluentRoute"),
        )
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
        virtualHostCall: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        hostname: String,
        scopedKeys: MutableSet<String>,
    ) {
        var current = findImmediateNextChainedCall(virtualHostCall)
        while (current != null) {
            val methodName = current.methodExpression.referenceName
            if (methodName == "build" && current.argumentList.expressionCount == 0) {
                break
            }
            processVirtualHostScopedCall(current, routes, seenRegistrations, hostname, scopedKeys)
            current = findImmediateNextChainedCall(current)
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
        val methodCalls = buildList {
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
                    tryCollectFluentRoute(call, routes, seenRegistrations, requireBuilderCall = true)
                }
                registrationKey(call)?.let(scopedKeys::add)
                annotateVirtualHostForCall(call, routes, sizeBefore, hostname)
            }
            methodName in CoreServiceRegistrationMethod.METHOD_NAMES -> {
                if (ArmeriaBuilderCallHeuristics.isClearlyNonArmeriaJavaRegistrationCall(call)) {
                    return
                }
                val sizeBefore = routes.size
                ArmeriaRouteCollector.addServiceRegistrationFromCall(call, routes, seenRegistrations)
                registrationKey(call)?.let(scopedKeys::add)
                annotateVirtualHostForCall(call, routes, sizeBefore, hostname)
            }
            methodName in ServiceRegistrationMethod.EXTENDED_METHOD_NAMES -> {
                if (ArmeriaBuilderCallHeuristics.isClearlyNonArmeriaJavaRegistrationCall(call)) {
                    return
                }
                val sizeBefore = routes.size
                collectFromMethodCall(call, routes, seenRegistrations, requireBuilderCall = false)
                registrationKey(call)?.let(scopedKeys::add)
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
        val key = registrationKey(call)
        if (key != null) {
            ArmeriaRouteVirtualHostAnnotator.annotateByKey(routes, key, hostname) { route ->
                val element = route.pointer.element as? PsiMethodCallExpression ?: return@annotateByKey null
                registrationKey(element)
            }
        } else {
            ArmeriaRouteVirtualHostAnnotator.annotateRoutesAddedSince(routes, sizeBefore, hostname)
        }
    }

    private fun extractRouteDecoratorChain(routeDecoratorCall: PsiMethodCallExpression): RouteDecoratorChainInfo {
        val outerBuild = findForwardChainedCall(routeDecoratorCall) { call ->
            call.methodExpression.referenceName == "build" && call.argumentList.expressionCount == 0
        }
        val chainCalls = methodCallsBetweenInStatement(routeDecoratorCall, outerBuild)
        val steps = chainCalls.map(::toChainStep)
        return ArmeriaRegistrationChainReducer.reduceRouteDecoratorChain(
            steps = steps,
            defaultDecoratorLabel = message("route.explorer.target.routeDecorator"),
        )
    }

    private fun toChainStep(call: PsiMethodCallExpression): RegistrationChainStep {
        return RegistrationChainStep(
            methodName = call.methodExpression.referenceName.orEmpty(),
            firstStringArg = extractString(call.argumentList.expressions.firstOrNull()),
            rawMethodArgs = call.argumentList.expressions.map { it.text },
        )
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

    private fun findImmediateNextChainedCall(call: PsiMethodCallExpression): PsiMethodCallExpression? {
        var element: PsiElement? = call
        while (element != null) {
            val parent = element.parent ?: return null
            when (parent) {
                is PsiReferenceExpression -> {
                    val grandParent = parent.parent
                    if (grandParent is PsiMethodCallExpression &&
                        grandParent.methodExpression == parent &&
                        grandParent !== call
                    ) {
                        return grandParent
                    }
                }
                is PsiMethodCallExpression -> {
                    if (parent.methodExpression.qualifierExpression == element && parent !== call) {
                        return parent
                    }
                }
            }
            element = parent
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

    private fun extractString(expression: PsiExpression?): String? {
        return when (expression) {
            null -> null
            is PsiLiteralExpression -> expression.value as? String
            else -> expression.text.takeIf { it.isNotBlank() }?.trim('"')
        }
    }
}
