package com.linecorp.intellij.plugins.armeria.explorer.collector.registration.java
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.explorer.collector.annotation.ArmeriaTimeoutSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.decorator.ArmeriaDecoratorSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.explorer.model.ServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaExtendedRegistrationCollector {
    fun collectFromJavaFile(
        file: PsiJavaFile,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        file.accept(
            object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    collectFromMethodCall(expression, routes, seenRegistrations)
                    ArmeriaExtendedRegistrationCollectorFluentRoute.tryCollectFluentRoute(expression, routes, seenRegistrations)
                    super.visitMethodCallExpression(expression)
                }
            },
        )
    }

    fun visitMethodCallExpression(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        collectFromMethodCall(expression, routes, seenRegistrations)
        ArmeriaExtendedRegistrationCollectorFluentRoute.tryCollectFluentRoute(expression, routes, seenRegistrations)
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
            ServiceRegistrationMethod.VIRTUAL_HOST ->
                ArmeriaExtendedRegistrationCollectorVirtualHost.addVirtualHost(expression, routes, seenRegistrations)
            ServiceRegistrationMethod.ROUTE_DECORATOR ->
                ArmeriaExtendedRegistrationCollectorRouteDecorator.addRouteDecorator(expression, routes, seenRegistrations)
            ServiceRegistrationMethod.WITH_ROUTE ->
                ArmeriaExtendedRegistrationCollectorRouteDecorator.addWithRoute(expression, routes, seenRegistrations)
            ServiceRegistrationMethod.DECORATOR_UNDER -> addDecoratorUnder(expression, routes, seenRegistrations)
            else -> Unit
        }
    }

    private fun addFileService(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val key = ArmeriaJavaRegistrationChainSupport.registrationKey(expression) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        val path = ArmeriaJavaRegistrationChainSupport.extractString(expression.argumentList.expressions.getOrNull(0)) ?: "/"
        val (pathType, normalizedPath) = ArmeriaRouteSupport.parsePathType(path)
        val targetExpr = expression.argumentList.expressions.getOrNull(1)
        val target = targetExpr?.text ?: message("route.explorer.target.fileService")
        routes +=
            ArmeriaRoute.create(
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
        val key = ArmeriaJavaRegistrationChainSupport.registrationKey(expression) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        val path =
            expression.argumentList.expressions
                .firstOrNull()
                ?.let(ArmeriaJavaRegistrationChainSupport::extractString)
                ?.let(ArmeriaRouteSupport::normalizePath)
                ?: "/internal/healthcheck"
        routes +=
            ArmeriaRoute.create(
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

    private fun addDecoratorUnder(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        val key = ArmeriaJavaRegistrationChainSupport.registrationKey(expression) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        val rawPath = ArmeriaJavaRegistrationChainSupport.extractString(expression.argumentList.expressions.getOrNull(0)) ?: return
        val (pathType, normalizedPath) = ArmeriaRouteSupport.parsePathType(rawPath)
        val decoratorArg = expression.argumentList.expressions.getOrNull(1)
        val decoratorLabel =
            decoratorArg?.text?.let(ArmeriaDecoratorSupport::labelDecorator)
                ?: message("route.explorer.target.decoratorUnder")
        routes +=
            ArmeriaRoute.create(
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
}
