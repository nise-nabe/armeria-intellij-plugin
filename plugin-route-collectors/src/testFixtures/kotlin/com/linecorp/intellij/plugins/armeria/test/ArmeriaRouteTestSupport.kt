package com.linecorp.intellij.plugins.armeria.test

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch

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
    httpMethod?.let { check(route.httpMethod == it) { "Expected httpMethod '$it' but was '${route.httpMethod}'" } }
    pathType?.let { check(route.pathType == it) { "Expected pathType '$it' but was '${route.pathType}'" } }
    return route
}
