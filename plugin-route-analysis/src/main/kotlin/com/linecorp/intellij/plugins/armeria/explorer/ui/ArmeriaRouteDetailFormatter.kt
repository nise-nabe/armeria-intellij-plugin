package com.linecorp.intellij.plugins.armeria.explorer.ui

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.DelegationKind
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaRouteDetailFormatter {
    fun statusLine(route: ArmeriaRoute): String {
        val badges =
            buildList {
                if (route.targetUnresolved) {
                    add(message("route.explorer.badge.unresolved"))
                }
                if (route.isDocService) {
                    add(message("route.explorer.badge.docService"))
                }
                if (route.routeMatch == RouteMatch.RUNTIME) {
                    add(message("route.explorer.badge.runtime"))
                }
                route.delegationKind?.let { kind ->
                    add(delegationBadge(kind))
                }
                if (route.routeMatch != RouteMatch.RUNTIME) {
                    add(message("route.explorer.badge.staticAnalysis"))
                }
            }
        return badges.joinToString(" · ")
    }

    fun attachmentsLine(route: ArmeriaRoute): String {
        val parts =
            buildList {
                if (route.decorators.isNotEmpty()) {
                    add(message("route.explorer.detail.decorators", route.decorators.joinToString()))
                }
                if (route.exceptionHandlers.isNotEmpty()) {
                    add(message("route.explorer.detail.handlers", route.exceptionHandlers.joinToString()))
                }
                if (route.executionHints.isNotEmpty()) {
                    add(message("route.explorer.detail.execution", route.executionHints.joinToString()))
                }
                if (route.timeoutHints.isNotEmpty()) {
                    add(message("route.explorer.detail.timeouts", route.timeoutHints.joinToString()))
                }
                if (route.pathType != PathType.EXACT) {
                    add(message("route.explorer.detail.pathType", pathTypeLabel(route.pathType)))
                }
                if (route.virtualHostName.isNotEmpty()) {
                    add(message("route.explorer.detail.virtualHost", route.virtualHostName))
                }
                if (route.contentHints.isNotEmpty()) {
                    add(message("route.explorer.detail.content", route.contentHints.joinToString(" · ")))
                }
                if (route.delegationMountPath.isNotEmpty()) {
                    add(message("route.explorer.detail.delegationMount", route.delegationMountPath))
                }
            }
        return parts.joinToString("\n")
    }

    private fun pathTypeLabel(pathType: PathType): String =
        when (pathType) {
            PathType.EXACT -> message("route.explorer.pathType.exact")
            PathType.PREFIX -> message("route.explorer.pathType.prefix")
            PathType.REGEX -> message("route.explorer.pathType.regex")
            PathType.GLOB -> message("route.explorer.pathType.glob")
        }

    fun registrationSummary(route: ArmeriaRoute): String =
        when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP ->
                message(
                    "route.explorer.registration.annotatedMethod",
                    route.httpMethod,
                    route.path,
                )
            RouteMatch.ANNOTATED_SERVICE ->
                if (route.annotatedServiceHasPathPrefix) {
                    message("route.explorer.registration.builderCall", "annotatedService", route.path)
                } else {
                    message("route.explorer.registration.annotatedService")
                }
            RouteMatch.SERVICE -> message("route.explorer.registration.service", route.path)
            RouteMatch.SERVICE_UNDER -> message("route.explorer.registration.serviceUnder", route.path)
            RouteMatch.FILE_SERVICE -> message("route.explorer.registration.fileService", route.path)
            RouteMatch.HEALTH_CHECK -> message("route.explorer.registration.healthCheck", route.path)
            RouteMatch.VIRTUAL_HOST ->
                message(
                    "route.explorer.registration.virtualHost",
                    route.virtualHostName.ifBlank { route.target },
                )
            RouteMatch.ROUTE_DECORATOR -> message("route.explorer.registration.routeDecorator", route.path)
            RouteMatch.ROUTE_FLUENT ->
                message(
                    "route.explorer.registration.fluentRoute",
                    route.httpMethod.ifBlank { message("route.explorer.method.allHttp") },
                    route.path,
                )
            RouteMatch.DECORATOR_UNDER -> message("route.explorer.registration.decoratorUnder", route.path)
            RouteMatch.DELEGATED -> delegatedRegistrationSummary(route)
            RouteMatch.NON_HTTP -> message("route.explorer.registration.nonHttp", route.protocol, route.path)
            RouteMatch.RUNTIME -> message("route.explorer.registration.runtime", route.httpMethod, route.path)
            RouteMatch.CONFIG -> message("route.explorer.registration.config", route.httpMethod, route.path)
        }

    private fun delegatedRegistrationSummary(route: ArmeriaRoute): String {
        val method = route.httpMethod.ifBlank { message("route.explorer.method.allHttp") }
        return message(
            "route.explorer.registration.delegated",
            method,
            route.path,
            route.delegationMountPath,
        )
    }

    fun delegationBadge(kind: DelegationKind): String =
        when (kind) {
            DelegationKind.SPRING_MVC -> message("route.explorer.badge.springMvc")
            DelegationKind.SERVLET -> message("route.explorer.badge.servlet")
        }

    fun secondaryDelegationText(route: ArmeriaRoute): String? {
        if (route.delegationMountPath.isNotEmpty()) {
            return message("route.explorer.secondary.delegatedVia", route.delegationMountPath)
        }
        val kind = route.delegationKind ?: return null
        return message("route.explorer.secondary.separator") + delegationBadge(kind)
    }

    fun tooltipDelegationSuffix(route: ArmeriaRoute): String? {
        if (route.delegationMountPath.isEmpty()) {
            return null
        }
        return message("route.explorer.tooltip.delegatedVia", route.delegationMountPath)
    }
}
