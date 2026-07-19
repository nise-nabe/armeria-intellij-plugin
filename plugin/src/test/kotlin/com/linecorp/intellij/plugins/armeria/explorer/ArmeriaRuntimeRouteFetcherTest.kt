package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmeriaRuntimeRouteFetcherTest {
    @Test
    fun fetchFromSpecification_mapsRoutesToRuntimeArmeriaRoutes() {
        val json = javaClass.getResourceAsStream("/doc-service-specification.json")!!.reader().readText()

        val routes =
            ArmeriaRuntimeRouteFetcher.fetchFromSpecification(
                specificationJson = json,
                moduleName = "Runtime (DocService)",
                protocol = "DocService (runtime)",
            )

        assertEquals(3, routes.size)
        assertTrue(routes.all { it.routeMatch == RouteMatch.RUNTIME })
        assertEquals(
            setOf("GET /api/users/{id}", "POST /api/users"),
            routes
                .filter { it.target.startsWith("com.example.FooService") }
                .map { "${it.httpMethod} ${it.path}" }
                .toSet(),
        )
    }
}
