package com.linecorp.intellij.plugins.armeria.client

import com.intellij.microservices.endpoints.ModuleEndpointsFilter
import com.linecorp.intellij.plugins.armeria.test.ArmeriaClientFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArmeriaKotlinClientEndpointsProviderTest : ArmeriaClientFixtureTestBase() {
    fun testKotlinWebClientOfAppearsAsHttpClientEndpoint() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.WebClient

            fun main() {
                WebClient.of("https://api.example.com/v1")
            }
            """.trimIndent(),
        )

        val provider = ArmeriaClientEndpointsProvider()
        val groups = provider.getEndpointGroups(project, ModuleEndpointsFilter(module, true, true)).toList()
        val group = groups.single()
        val endpoint = provider.getEndpoints(group).single()
        assertEquals("https://api.example.com/v1", endpoint.uri)
        assertEquals("HTTP", endpoint.clientType)
        assertTrue(endpoint.target.contains("WebClient"))
        assertTrue(group.sourceKey.contains("file:"))
        assertNotNull(provider.getNavigationElement(group, endpoint))
    }
}
