package com.linecorp.intellij.plugins.armeria.client

import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase

class ArmeriaScalaClientCollectorTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    fun testCollectWebClientOfFromScala() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.client;

            public final class WebClient {
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.client.WebClient

            object Main {
              val client = WebClient.of("https://example.com")
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        val endpoint = endpoints.single()
        assertEquals("HTTP", endpoint.clientType)
        assertEquals("https://example.com", endpoint.uri)
        assertEquals("WebClient", endpoint.target)
        assertNotNull(endpoint.sourceOffset)
        assertTrue(endpoint.sourceOffset!! > 0)
    }

    fun testCollectMultipleClientsFromSameScalaFile() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.client;

            public final class WebClient {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.grpc;

            public final class GrpcClients {
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.client.WebClient
            import com.linecorp.armeria.client.grpc.GrpcClients

            object Main {
              val web = WebClient.of("https://example.com")
              val grpc = GrpcClients.of("https://grpc.example.com")
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(2, endpoints.size)
        val byUri = endpoints.associateBy { it.uri }
        assertEquals("WebClient", byUri.getValue("https://example.com").target)
        assertEquals("GrpcClients", byUri.getValue("https://grpc.example.com").target)
        assertNotEquals(
            byUri.getValue("https://example.com").sourceOffset,
            byUri.getValue("https://grpc.example.com").sourceOffset,
        )
    }
}
