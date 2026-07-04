package com.linecorp.intellij.plugins.armeria.client

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaKotlinClientCollectorTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerArmeriaClientStubs()
    }

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

    private fun registerArmeriaClientStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.client;

            public final class WebClient {
                public static WebClient of(String uri) {
                    return null;
                }

                public static WebClientBuilder builder() {
                    return null;
                }

                public static WebClientBuilder builder(String uri) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client;

            public final class WebClientBuilder {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.grpc;

            public final class GrpcClients {
                public static GrpcClientBuilder builder(String uri) {
                    return null;
                }

                public static Object newClient(String uri, Class<?> stubClass) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.grpc;

            public final class GrpcClientBuilder {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.thrift;

            public final class ThriftClients {
                public static ThriftClientBuilder builder(String uri) {
                    return null;
                }

                public static Object newClient(String uri, Class<?> ifaceClass) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.thrift;

            public final class ThriftClientBuilder {
            }
            """.trimIndent(),
        )
    }
}
