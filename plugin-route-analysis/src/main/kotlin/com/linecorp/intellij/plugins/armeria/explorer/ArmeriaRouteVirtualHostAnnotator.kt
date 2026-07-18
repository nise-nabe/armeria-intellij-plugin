package com.linecorp.intellij.plugins.armeria.explorer

internal object ArmeriaRouteVirtualHostAnnotator {
    fun annotateRoutesAddedSince(
        routes: MutableList<ArmeriaRoute>,
        sizeBefore: Int,
        hostname: String,
    ) {
        for (index in sizeBefore until routes.size) {
            annotateRouteAt(routes, index, hostname)
        }
    }

    fun annotateByKey(
        routes: MutableList<ArmeriaRoute>,
        registrationKey: String,
        hostname: String,
        routeKey: (ArmeriaRoute) -> String?,
    ) {
        val index = routes.indexOfLast { routeKey(it) == registrationKey }
        annotateRouteAt(routes, index, hostname)
    }

    fun annotateRouteAt(
        routes: MutableList<ArmeriaRoute>,
        index: Int,
        hostname: String,
    ) {
        if (index !in routes.indices) {
            return
        }
        val route = routes[index]
        if (route.virtualHostName.isEmpty() && route.routeMatch != RouteMatch.VIRTUAL_HOST) {
            routes[index] = route.copy(virtualHostName = hostname)
        }
    }
}
