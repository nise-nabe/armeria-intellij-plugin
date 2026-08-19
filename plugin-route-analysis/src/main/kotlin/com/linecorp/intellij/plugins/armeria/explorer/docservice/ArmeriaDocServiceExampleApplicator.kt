package com.linecorp.intellij.plugins.armeria.explorer.docservice

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute

object ArmeriaDocServiceExampleApplicator {
    fun apply(
        routes: List<ArmeriaRoute>,
        examples: ArmeriaDocServiceExampleIndex,
    ): List<ArmeriaRoute> {
        if (examples.isEmpty()) {
            return routes
        }
        return routes.map { route -> applyToRoute(route, examples) }
    }

    private fun applyToRoute(
        route: ArmeriaRoute,
        examples: ArmeriaDocServiceExampleIndex,
    ): ArmeriaRoute {
        val ref = ArmeriaDocServiceMethodRef.from(route) ?: return route
        val requests = examples.requestsFor(ref)
        val headers = examples.headersFor(ref)
        if (requests.isEmpty() && headers.isEmpty()) {
            return route
        }
        return route.copy(
            exampleRequests = (requests + route.exampleRequests).distinct(),
            exampleHeaders = (headers + route.exampleHeaders).distinct(),
        )
    }
}
