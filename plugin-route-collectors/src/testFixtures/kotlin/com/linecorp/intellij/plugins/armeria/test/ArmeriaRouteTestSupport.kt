package com.linecorp.intellij.plugins.armeria.test

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import kotlin.test.assertEquals
import kotlin.test.fail

/** Finds exactly one route matching the optional criteria; fails with a descriptive message otherwise. */
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

/** Asserts the list contains exactly one route (cardinality only; no field matching). */
fun List<ArmeriaRoute>.singleRoute(): ArmeriaRoute {
    if (size != 1) {
        fail("Expected a single route but found $size: ${map { it.routeMatch to it.path }}")
    }
    return single()
}

/**
 * Asserts one route matches the given fields. When multiple routes may share a [path], pass [match]
 * or call [singleRoute] first so ambiguous lookups fail with a clear message.
 */
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
    match?.let { assertEquals(it, route.routeMatch, message = "routeMatch") }
    path?.let { assertEquals(it, route.path, message = "path") }
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
