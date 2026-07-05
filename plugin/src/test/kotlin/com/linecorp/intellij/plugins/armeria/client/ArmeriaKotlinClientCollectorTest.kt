package com.linecorp.intellij.plugins.armeria.client

import com.linecorp.intellij.plugins.armeria.test.ArmeriaClientFixtureTestBase

class ArmeriaKotlinClientCollectorTest : ArmeriaClientFixtureTestBase() {

    fun testCollectWebClientOfFromKotlin() {
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

    fun testCollectWebClientBuilderFromKotlin() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.WebClient

            fun main() {
                WebClient.builder("https://api.example.com")
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        assertEquals("https://api.example.com", endpoints.single().uri)
    }

    fun testCollectGrpcClientsNewClientFromKotlin() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.grpc.GrpcClients

            fun main() {
                GrpcClients.newClient("https://grpc.example.com", MyStub::class.java)
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class MyStub {
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        val endpoint = endpoints.single()
        assertEquals("gRPC", endpoint.clientType)
        assertEquals("https://grpc.example.com", endpoint.uri)
    }

    fun testCollectWebClientOfViaKotlinImportAlias() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.WebClient.of

            fun main() {
                of("https://example.com")
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        assertEquals("WebClient", endpoints.single().target)
    }

    fun testIgnoresNoArgWebClientBuilderFromKotlin() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.WebClient

            fun main() {
                WebClient.builder()
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertTrue(endpoints.isEmpty())
    }

    fun testNoFalsePositiveOnNonArmeriaWebClient() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.example.WebClient

            fun main() {
                WebClient.builder("https://example.com")
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.example;

            public final class WebClient {
                public static WebClientBuilder builder(String uri) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.example;

            public final class WebClientBuilder {
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertTrue(endpoints.isEmpty())
    }

}
