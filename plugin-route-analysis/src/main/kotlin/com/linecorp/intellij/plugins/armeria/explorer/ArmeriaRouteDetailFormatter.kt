package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.message

object ArmeriaRouteDetailFormatter {
    fun statusLine(route: ArmeriaRoute): String {
        val badges = buildList {
            if (route.targetUnresolved) {
                add(message("route.explorer.badge.unresolved"))
            }
            if (route.isDocService) {
                add(message("route.explorer.badge.docService"))
            }
            if (route.routeMatch == RouteMatch.RUNTIME) {
                add(message("route.explorer.badge.runtime"))
            } else {
                add(message("route.explorer.badge.staticAnalysis"))
            }
        }
        return badges.joinToString(" · ")
    }

    fun attachmentsLine(route: ArmeriaRoute): String {
        val parts = buildList {
            if (route.decorators.isNotEmpty()) {
                add(message("route.explorer.detail.decorators", route.decorators.joinToString()))
            }
            if (route.exceptionHandlers.isNotEmpty()) {
                add(message("route.explorer.detail.handlers", route.exceptionHandlers.joinToString()))
            }
            if (route.executionHints.isNotEmpty()) {
                add(message("route.explorer.detail.execution", route.executionHints.joinToString()))
            }
            if (route.pathType != PathType.EXACT) {
                add(message("route.explorer.detail.pathType", pathTypeLabel(route.pathType)))
            }
            if (route.virtualHostName.isNotEmpty()) {
                add(message("route.explorer.detail.virtualHost", route.virtualHostName))
            }
        }
        return parts.joinToString("\n")
    }

    private fun pathTypeLabel(pathType: PathType): String = when (pathType) {
        PathType.EXACT -> message("route.explorer.pathType.exact")
        PathType.PREFIX -> message("route.explorer.pathType.prefix")
        PathType.REGEX -> message("route.explorer.pathType.regex")
        PathType.GLOB -> message("route.explorer.pathType.glob")
    }

    fun registrationSummary(route: ArmeriaRoute): String {
        return when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP -> message(
                "route.explorer.registration.annotatedMethod",
                route.httpMethod,
                route.path,
            )
            RouteMatch.ANNOTATED_SERVICE -> if (route.annotatedServiceHasPathPrefix) {
                message("route.explorer.registration.builderCall", "annotatedService", route.path)
            } else {
                message("route.explorer.registration.annotatedService")
            }
            RouteMatch.SERVICE -> message("route.explorer.registration.service", route.path)
            RouteMatch.SERVICE_UNDER -> message("route.explorer.registration.serviceUnder", route.path)
            RouteMatch.FILE_SERVICE -> message("route.explorer.registration.fileService", route.path)
            RouteMatch.HEALTH_CHECK -> message("route.explorer.registration.healthCheck", route.path)
            RouteMatch.VIRTUAL_HOST -> message(
                "route.explorer.registration.virtualHost",
                route.virtualHostName.ifBlank { route.target },
            )
            RouteMatch.ROUTE_DECORATOR -> message("route.explorer.registration.routeDecorator", route.path)
            RouteMatch.NON_HTTP -> message("route.explorer.registration.nonHttp", route.protocol, route.path)
            RouteMatch.RUNTIME -> message("route.explorer.registration.runtime", route.httpMethod, route.path)
        }
    }
}
