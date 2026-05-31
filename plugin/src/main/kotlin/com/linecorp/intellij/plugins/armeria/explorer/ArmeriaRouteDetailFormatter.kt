package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiMethodCallExpression
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
            add(message("route.explorer.badge.staticAnalysis"))
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
        }
        return parts.joinToString("\n")
    }

    fun registrationSummary(route: ArmeriaRoute): String {
        val element = route.pointer.element ?: return fallbackRegistrationSummary(route)
        if (element is PsiMethodCallExpression) {
            val methodName = element.methodExpression.referenceName ?: return fallbackRegistrationSummary(route)
            return when (methodName) {
                "service" -> message("route.explorer.registration.service", route.path)
                "serviceUnder" -> message("route.explorer.registration.serviceUnder", route.path)
                "annotatedService" -> message("route.explorer.registration.annotatedService", route.path)
                else -> message("route.explorer.registration.builderCall", methodName, route.path)
            }
        }
        return when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP -> message(
                "route.explorer.registration.annotatedMethod",
                route.httpMethod,
                route.path,
            )
            else -> fallbackRegistrationSummary(route)
        }
    }

    private fun fallbackRegistrationSummary(route: ArmeriaRoute): String {
        return when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP -> message(
                "route.explorer.registration.annotatedMethod",
                route.httpMethod,
                route.path,
            )
            RouteMatch.ANNOTATED_SERVICE -> message("route.explorer.registration.annotatedService", route.path)
            RouteMatch.SERVICE -> message("route.explorer.registration.service", route.path)
            RouteMatch.SERVICE_UNDER -> message("route.explorer.registration.serviceUnder", route.path)
            RouteMatch.NON_HTTP -> message("route.explorer.registration.nonHttp", route.protocol, route.path)
        }
    }
}
