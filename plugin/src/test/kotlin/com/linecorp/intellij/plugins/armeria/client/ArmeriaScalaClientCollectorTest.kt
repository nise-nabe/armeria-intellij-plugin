package com.linecorp.intellij.plugins.armeria.client

import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull as kotlinAssertNotNull

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
        val sourceOffset = kotlinAssertNotNull(endpoint.sourceOffset)
        assertTrue(sourceOffset > 0)
    }

    fun testIgnoresWebClientOfInsideStringLiteral() {
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
              val example = "WebClient.of(\"https://fake.example.com\")"
              val client = WebClient.of("https://example.com")
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        assertEquals("https://example.com", endpoints.single().uri)
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
        assertFalse(
            byUri.getValue("https://example.com").sourceOffset ==
                byUri.getValue("https://grpc.example.com").sourceOffset,
        )
    }

    fun testIgnoresQualifiedWebClientWithoutArmeriaImport() {
        myFixture.addClass(
            """
            package com.example;

            public final class WebClient {
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.server.Server

            object Main {
              val client = com.example.WebClient.of("https://fake.example.com")
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertTrue(endpoints.isEmpty())
    }

    fun testDeduplicatesQualifiedAndImportedWebClientFactoryCall() {
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
              val client = com.linecorp.armeria.client.WebClient.of("https://example.com")
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        assertEquals("https://example.com", endpoints.single().uri)
    }

    fun testCollectRestClientOfFromScala() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.client;

            public final class RestClient {
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.client.RestClient

            object Main {
              val client = RestClient.of("https://example.com/hello")
            }
            """.trimIndent(),
        )

        val endpoint = ArmeriaClientCollector.collect(project).single()

        assertEquals("RestClient", endpoint.clientType)
        assertEquals("https://example.com/hello", endpoint.uri)
        assertEquals("RestClient", endpoint.target)
    }

    fun testCollectWebClientBlockingConversionFromScala() {
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
              val client = WebClient.of("https://example.com/users").blocking()
            }
            """.trimIndent(),
        )

        val endpoint = ArmeriaClientCollector.collect(project).single()

        assertEquals("BlockingWebClient", endpoint.clientType)
        assertEquals("https://example.com/users", endpoint.uri)
    }
}
