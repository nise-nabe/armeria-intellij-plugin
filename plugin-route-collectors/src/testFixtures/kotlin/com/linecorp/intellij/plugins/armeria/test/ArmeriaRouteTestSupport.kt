package com.linecorp.intellij.plugins.armeria.test

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import kotlin.test.assertEquals
import kotlin.test.fail

fun List<ArmeriaRoute>.route(
    match: RouteMatch? = null,
    path: String? = null,
    httpMethod: String? = null,
    pathType: PathType? = null,
): ArmeriaRoute {
    val filtered =
        filter { route ->
            (match == null || route.routeMatch == match) &&
                (path == null || route.path == path) &&
                (httpMethod == null || route.httpMethod == httpMethod) &&
                (pathType == null || route.pathType == pathType)
        }
    val criteria = routeCriteriaDescription(match, path, httpMethod, pathType)
    return when (filtered.size) {
        1 -> filtered.single()
        0 ->
            fail(
                "No route matching $criteria in " +
                    map { it.routeMatch to it.path },
            )
        else ->
            fail(
                "Multiple routes matching $criteria: " +
                    filtered.map { it.routeMatch to it.path },
            )
    }
}

fun List<ArmeriaRoute>.singleRoute(): ArmeriaRoute {
    if (size != 1) {
        fail("Expected a single route but found $size: ${map { it.routeMatch to it.path }}")
    }
    return single()
}

fun List<ArmeriaRoute>.assertRoute(
    match: RouteMatch? = null,
    path: String? = null,
    httpMethod: String? = null,
    pathType: PathType? = null,
): ArmeriaRoute {
    require(match != null || path != null || httpMethod != null || pathType != null) {
        "assertRoute requires at least one expected field; use singleRoute() for cardinality-only checks"
    }
    val route = route(match = match, path = path, httpMethod = httpMethod, pathType = pathType)
    httpMethod?.let { assertEquals(it, route.httpMethod, message = "httpMethod") }
    pathType?.let { assertEquals(it, route.pathType, message = "pathType") }
    return route
}

private fun routeCriteriaDescription(
    match: RouteMatch?,
    path: String?,
    httpMethod: String?,
    pathType: PathType?,
): String =
    buildString {
        append("match=").append(match)
        append(" path=").append(path)
        append(" httpMethod=").append(httpMethod)
        append(" pathType=").append(pathType)
    }
