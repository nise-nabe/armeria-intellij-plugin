package com.linecorp.intellij.plugins.armeria.test

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import org.junit.Assert.assertEquals

fun List<ArmeriaRoute>.route(
    match: RouteMatch? = null,
    path: String? = null,
): ArmeriaRoute {
    val filtered =
        filter { route ->
            (match == null || route.routeMatch == match) &&
                (path == null || route.path == path)
        }
    return when (filtered.size) {
        1 -> filtered.single()
        0 ->
            error(
                "No route matching match=$match path=$path in " +
                    map { it.routeMatch to it.path },
            )
        else ->
            error(
                "Multiple routes matching match=$match path=$path: " +
                    filtered.map { it.routeMatch to it.path },
            )
    }
}

fun List<ArmeriaRoute>.singleRoute(): ArmeriaRoute {
    if (size != 1) {
        error("Expected a single route but found $size: ${map { it.routeMatch to it.path }}")
    }
    return single()
}

fun List<ArmeriaRoute>.assertRoute(
    match: RouteMatch? = null,
    path: String? = null,
    httpMethod: String? = null,
    pathType: PathType? = null,
): ArmeriaRoute {
    val route = route(match = match, path = path)
    httpMethod?.let { assertEquals("httpMethod", it, route.httpMethod) }
    pathType?.let { assertEquals("pathType", it, route.pathType) }
    return route
}
