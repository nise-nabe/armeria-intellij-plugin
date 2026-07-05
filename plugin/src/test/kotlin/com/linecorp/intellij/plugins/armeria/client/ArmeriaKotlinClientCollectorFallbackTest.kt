package com.linecorp.intellij.plugins.armeria.client

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaKotlinClientCollectorFallbackTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        // Fallback test registers its own minimal WebClient stub.
    }

    fun testCollectWebClientOfViaImportWhenMethodResolutionFails() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.client;

            public final class WebClient {
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.WebClient

            fun main() {
                WebClient.of("https://example.com")
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        val endpoint = endpoints.single()
        assertEquals("HTTP", endpoint.clientType)
        assertEquals("https://example.com", endpoint.uri)
        assertEquals("WebClient", endpoint.target)
    }
}
