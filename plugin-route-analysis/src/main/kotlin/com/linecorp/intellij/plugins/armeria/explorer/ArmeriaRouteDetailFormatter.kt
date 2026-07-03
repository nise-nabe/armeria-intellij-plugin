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
            if (route.timeoutHints.isNotEmpty()) {
                add(message("route.explorer.detail.timeouts", route.timeoutHints.joinToString()))
            }
        }
        return parts.joinToString("\n")
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
            RouteMatch.NON_HTTP -> message("route.explorer.registration.nonHttp", route.protocol, route.path)
            RouteMatch.RUNTIME -> message("route.explorer.registration.runtime", route.httpMethod, route.path)
        }
    }
}
