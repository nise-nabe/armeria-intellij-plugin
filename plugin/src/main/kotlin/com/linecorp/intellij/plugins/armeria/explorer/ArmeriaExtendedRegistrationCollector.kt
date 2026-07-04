package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
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
                super.visitMethodCallExpression(expression)
            }
        })
    }

    fun collectFromMethodCall(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val methodName = expression.methodExpression.referenceName ?: return
        if (methodName !in ServiceRegistrationMethod.EXTENDED_METHOD_NAMES) {
            return
        }
        if (!looksLikeArmeriaBuilderCall(expression)) {
            return
        }
        when (ServiceRegistrationMethod.fromMethodName(methodName)) {
            ServiceRegistrationMethod.FILE_SERVICE -> addFileService(expression, routes, seenRegistrations)
            ServiceRegistrationMethod.HEALTH_CHECK_SERVICE -> addHealthCheck(expression, routes, seenRegistrations)
            ServiceRegistrationMethod.VIRTUAL_HOST -> addVirtualHost(expression, routes, seenRegistrations)
            ServiceRegistrationMethod.ROUTE_DECORATOR -> addRouteDecorator(expression, routes, seenRegistrations)
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
        var current: PsiExpression? = virtualHostCall.methodExpression.qualifierExpression
        while (current != null) {
            if (current is PsiMethodCallExpression) {
                val methodName = current.methodExpression.referenceName
                if (methodName in ServiceRegistrationMethod.METHOD_NAMES - ServiceRegistrationMethod.EXTENDED_METHOD_NAMES) {
                    ArmeriaRouteCollector.addServiceRegistrationFromCall(current, routes, seenRegistrations)
                    annotateLastRouteWithVirtualHost(routes, hostname)
                }
                if (methodName in ServiceRegistrationMethod.EXTENDED_METHOD_NAMES &&
                    methodName != ServiceRegistrationMethod.VIRTUAL_HOST.methodName
                ) {
                    collectFromMethodCall(current, routes, seenRegistrations)
                    annotateLastRouteWithVirtualHost(routes, hostname)
                }
                current = current.methodExpression.qualifierExpression
            } else {
                break
            }
        }
        PsiTreeUtil.findChildrenOfType(virtualHostCall, PsiMethodCallExpression::class.java).forEach { nested ->
            if (nested == virtualHostCall) {
                return@forEach
            }
            val methodName = nested.methodExpression.referenceName ?: return@forEach
            if (methodName in ServiceRegistrationMethod.METHOD_NAMES - ServiceRegistrationMethod.EXTENDED_METHOD_NAMES) {
                ArmeriaRouteCollector.addServiceRegistrationFromCall(nested, routes, seenRegistrations)
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

    private data class RouteDecoratorChainInfo(
        val pathPattern: String,
        val pathType: PathType,
        val methods: String,
        val decoratorLabel: String,
    )

    private fun extractRouteDecoratorChain(routeDecoratorCall: PsiMethodCallExpression): RouteDecoratorChainInfo {
        var pathPattern = "/**"
        var pathType = PathType.EXACT
        var methods = ""
        var decoratorLabel = message("route.explorer.target.routeDecorator")
        var current: PsiElement? = routeDecoratorCall
        while (current is PsiMethodCallExpression) {
            when (current.methodExpression.referenceName) {
                "path", "pathPrefix", "pathRegex", "pathGlob" -> {
                    val raw = extractString(current.argumentList.expressions.firstOrNull()) ?: pathPattern
                    val parsed = ArmeriaRouteSupport.parsePathType(
                        when (current.methodExpression.referenceName) {
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
                    methods = current.argumentList.expressions.joinToString(", ") { it.text }
                }
                "build" -> {
                    val decoratorArg = current.argumentList.expressions.firstOrNull()
                    if (decoratorArg != null) {
                        decoratorLabel = ArmeriaDecoratorSupport.labelDecorator(decoratorArg.text)
                    }
                }
            }
            current = current.methodExpression.qualifierExpression
        }
        return RouteDecoratorChainInfo(pathPattern, pathType, methods, decoratorLabel)
    }

    private fun registrationKey(expression: PsiMethodCallExpression): String? {
        val virtualFile = expression.containingFile?.virtualFile ?: return null
        return "${virtualFile.path}:${expression.textRange.startOffset}"
    }

    private fun looksLikeArmeriaBuilderCall(expression: PsiMethodCallExpression): Boolean {
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
        if (resolvedClass?.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX) == true) {
            return true
        }
        val qualifierText = expression.methodExpression.qualifierExpression?.text ?: return false
        return ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(qualifierText) ||
            qualifierText.contains("routeDecorator") ||
            qualifierText.contains("virtualHost")
    }

    private fun extractString(expression: PsiExpression?): String? {
        return when (expression) {
            null -> null
            is PsiLiteralExpression -> expression.value as? String
            else -> expression.text.takeIf { it.isNotBlank() }?.trim('"')
        }
    }
}
