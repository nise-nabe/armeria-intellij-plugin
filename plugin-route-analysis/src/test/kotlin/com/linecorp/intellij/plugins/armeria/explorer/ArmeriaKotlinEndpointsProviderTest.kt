package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.microservices.endpoints.ModuleEndpointsFilter
import com.linecorp.intellij.plugins.armeria.explorer.endpoints.ArmeriaEndpointsProvider
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArmeriaKotlinEndpointsProviderTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerKotlinRouteCollectorStubs()
    }

    @Test
    fun annotatedGet_appearsAsHttpServerEndpoint() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )

        val provider = ArmeriaEndpointsProvider()
        val groups = provider.getEndpointGroups(project, ModuleEndpointsFilter(module, true, true)).toList()
        val group = groups.single()
        val route = provider.getEndpoints(group).single()
        assertEquals("/hello", route.path)
        assertEquals("GET", route.httpMethod)
        assertEquals(RouteMatch.ANNOTATED_HTTP, route.routeMatch)
        assertTrue(route.target.contains("HelloService#hello"))
        assertNotNull(provider.getNavigationElement(group, route))
    }
}
