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

                public static WebClientBuilder builder(
                        com.linecorp.armeria.common.SessionProtocol protocol,
                        com.linecorp.armeria.client.endpoint.EndpointGroup endpointGroup) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.example;

            public final class WebClientBuilder {
                public WebClientBuilder decorator(Object decorator) {
                    return this;
                }

                public WebClient build() {
                    return null;
                }
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertTrue(endpoints.isEmpty())
    }

    fun testCollectWebClientWithDecoratorsFromKotlin() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.WebClient
            import com.linecorp.armeria.client.logging.LoggingClient
            import com.linecorp.armeria.client.retrying.RetryingClient

            fun main() {
                WebClient.builder("https://example.com")
                    .decorator(LoggingClient.newDecorator())
                    .decorator(RetryingClient.newDecorator())
                    .build()
            }
            """.trimIndent(),
        )

        val endpoint = ArmeriaClientCollector.collect(project).single()

        assertEquals(listOf("Logging", "Retrying"), endpoint.decorators)
    }

    fun testCollectWebClientWithEndpointGroupFromKotlin() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.WebClient
            import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup
            import com.linecorp.armeria.common.SessionProtocol

            fun main() {
                WebClient.builder(SessionProtocol.HTTP, DnsAddressEndpointGroup.of("example.com", 8080))
            }
            """.trimIndent(),
        )

        val endpoint = ArmeriaClientCollector.collect(project).single()

        assertEquals("example.com", endpoint.uri)
        assertTrue(endpoint.endpointGroup!!.startsWith("DnsAddressEndpointGroup"))
    }

    fun testCollectRetrofitBuilderWithWebClientTransportFromKotlin() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.WebClient
            import com.linecorp.armeria.client.retrofit2.ArmeriaRetrofit

            fun main() {
                ArmeriaRetrofit.builder(WebClient.of("https://api.example.com")).build()
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        val endpoint = endpoints.single()
        assertEquals("Retrofit", endpoint.clientType)
        assertEquals("https://api.example.com", endpoint.uri)
        assertEquals("WebClient transport", endpoint.transport)
    }

    fun testCollectRetrofitOfWithInlineWebClientDecoratorsFromKotlin() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.WebClient
            import com.linecorp.armeria.client.brave.BraveClient
            import com.linecorp.armeria.client.retrofit2.ArmeriaRetrofit

            fun main() {
                ArmeriaRetrofit.of(
                    WebClient.builder("https://api.example.com")
                        .decorator(BraveClient.newDecorator())
                        .build(),
                )
            }
            """.trimIndent(),
        )

        val endpoint = ArmeriaClientCollector.collect(project).single()

        assertEquals("https://api.example.com", endpoint.uri)
        assertEquals("WebClient transport", endpoint.transport)
        assertEquals(listOf("Brave"), endpoint.decorators)
    }

    fun testCollectRetrofitBuilderWithWebClientEndpointGroupTransportFromKotlin() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.WebClient
            import com.linecorp.armeria.client.endpoint.EndpointGroup
            import com.linecorp.armeria.client.retrofit2.ArmeriaRetrofit
            import com.linecorp.armeria.common.SessionProtocol

            fun main() {
                ArmeriaRetrofit.builder(
                    WebClient.builder(SessionProtocol.HTTP, EndpointGroup.of("https://lb.example.com")).build(),
                ).build()
            }
            """.trimIndent(),
        )

        val endpoint = ArmeriaClientCollector.collect(project).single()

        assertEquals("Retrofit", endpoint.clientType)
        assertEquals("https://lb.example.com", endpoint.uri)
        assertEquals("WebClient transport", endpoint.transport)
        assertTrue(endpoint.endpointGroup!!.startsWith("EndpointGroup"))
    }

    fun testCollectRetrofitBuilderWithEndpointGroupFromKotlin() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.endpoint.EndpointGroup
            import com.linecorp.armeria.client.retrofit2.ArmeriaRetrofit
            import com.linecorp.armeria.common.SessionProtocol

            fun main() {
                ArmeriaRetrofit.builder(SessionProtocol.HTTPS, EndpointGroup.of("https://lb.example.com"))
            }
            """.trimIndent(),
        )

        val endpoint = ArmeriaClientCollector.collect(project).single()

        assertEquals("Retrofit", endpoint.clientType)
        assertEquals("https://lb.example.com", endpoint.uri)
        assertTrue(endpoint.endpointGroup!!.startsWith("EndpointGroup"))
        assertNull(endpoint.transport)
    }

    fun testNestedWebClientFactoryInsideRetrofitBuilderIsNotDuplicatedFromKotlin() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.WebClient
            import com.linecorp.armeria.client.retrofit2.ArmeriaRetrofit

            fun main() {
                ArmeriaRetrofit.builder(WebClient.builder("https://api.example.com")).build()
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        val endpoint = endpoints.single()
        assertEquals("Retrofit", endpoint.clientType)
        assertEquals("https://api.example.com", endpoint.uri)
        assertEquals("WebClient transport", endpoint.transport)
    }

}
