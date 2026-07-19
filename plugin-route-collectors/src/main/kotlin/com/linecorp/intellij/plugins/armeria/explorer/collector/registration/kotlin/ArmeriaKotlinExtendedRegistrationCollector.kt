package com.linecorp.intellij.plugins.armeria.explorer.collector.registration.kotlin
import com.linecorp.intellij.plugins.armeria.explorer.collector.annotation.ArmeriaKotlinTimeoutSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.decorator.ArmeriaDecoratorSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.decorator.ArmeriaKotlinDecoratorSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.explorer.model.ServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

object ArmeriaKotlinExtendedRegistrationCollector {
    fun collectFromFile(
        file: KtFile,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        file.forEachDescendant { element ->
            val call = element as? KtCallExpression ?: return@forEachDescendant
            val methodName = ArmeriaKotlinRegistrationChainSupport.resolveCallName(call) ?: return@forEachDescendant
            if (methodName == "build") {
                ArmeriaKotlinExtendedRegistrationCollectorFluentRoute.tryCollectFluentRoute(call, routes, seenRegistrations)
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

    internal fun collectFromKotlinCall(
        call: KtCallExpression,
        methodName: String,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
    ) {
        when (ServiceRegistrationMethod.fromMethodName(methodName)) {
            ServiceRegistrationMethod.WITH_ROUTE -> {
                ArmeriaKotlinExtendedRegistrationCollectorRouteDecorator.addWithRoute(call, routes, seenRegistrations)
                return
            }
            ServiceRegistrationMethod.VIRTUAL_HOST -> {
                ArmeriaKotlinExtendedRegistrationCollectorVirtualHost.addVirtualHost(call, routes, seenRegistrations)
                return
            }
            else -> Unit
        }
        val key = ArmeriaKotlinRegistrationChainSupport.registrationKey(call) ?: return
        if (!seenRegistrations.add(key)) {
            return
        }
        val pathArg = call.valueArguments.firstOrNull()?.getArgumentExpression()
        when (ServiceRegistrationMethod.fromMethodName(methodName)) {
            ServiceRegistrationMethod.FILE_SERVICE -> {
                val rawPath = ArmeriaKotlinExpressionSupport.extractKotlinString(pathArg) ?: "/"
                val (pathType, normalizedPath) = ArmeriaRouteSupport.parsePathType(rawPath)
                val target =
                    call.valueArguments
                        .getOrNull(1)
                        ?.getArgumentExpression()
                        ?.text
                        ?: message("route.explorer.target.fileService")
                routes +=
                    ArmeriaRoute.create(
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
                val path =
                    pathArg?.let(ArmeriaKotlinExpressionSupport::extractKotlinString)?.let(ArmeriaRouteSupport::normalizePath)
                        ?: "/internal/healthcheck"
                routes +=
                    ArmeriaRoute.create(
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
                val chainInfo = ArmeriaKotlinExtendedRegistrationCollectorRouteDecorator.extractRouteDecoratorChain(call)
                routes += ArmeriaKotlinExtendedRegistrationCollectorRouteDecorator.createRouteDecoratorRoute(call, chainInfo)
            }
            ServiceRegistrationMethod.DECORATOR_UNDER -> {
                val rawPath = ArmeriaKotlinExpressionSupport.extractKotlinString(pathArg) ?: return
                val (pathType, normalizedPath) = ArmeriaRouteSupport.parsePathType(rawPath)
                val decoratorArg = call.valueArguments.getOrNull(1)?.getArgumentExpression()
                val decoratorLabel =
                    decoratorArg?.text?.let(ArmeriaDecoratorSupport::labelDecorator)
                        ?: message("route.explorer.target.decoratorUnder")
                routes +=
                    ArmeriaRoute.create(
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
}
