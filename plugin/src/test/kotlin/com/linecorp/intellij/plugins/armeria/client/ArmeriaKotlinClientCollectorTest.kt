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

        val endpoint = ArmeriaClientCollector.collect(project).single()

        assertEquals("Retrofit", endpoint.clientType)
        assertEquals("https://api.example.com", endpoint.uri)
        assertEquals("WebClient transport", endpoint.transport)
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
            package com.linecorp.armeria.client;

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

        myFixture.addClass(
            """
            package com.linecorp.armeria.common;

            public final class SessionProtocol {
                public static final SessionProtocol HTTP = new SessionProtocol();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.endpoint;

            public interface EndpointGroup {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.endpoint.dns;

            public final class DnsAddressEndpointGroup {
                public static com.linecorp.armeria.client.endpoint.EndpointGroup of(String host, int port) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.logging;

            public final class LoggingClient {
                public static Object newDecorator() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.retrying;

            public final class RetryingClient {
                public static Object newDecorator() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.retrofit2;

            public final class ArmeriaRetrofit {
                public static ArmeriaRetrofitBuilder builder(com.linecorp.armeria.client.WebClient webClient) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.retrofit2;

            public final class ArmeriaRetrofitBuilder {
                public retrofit2.Retrofit build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package retrofit2;

            public final class Retrofit {
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
            package com.linecorp.armeria.common;

            public final class SessionProtocol {
                public static final SessionProtocol HTTP = new SessionProtocol();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.endpoint;

            public interface EndpointGroup {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.endpoint.dns;

            public final class DnsAddressEndpointGroup {
                public static com.linecorp.armeria.client.endpoint.EndpointGroup of(String host, int port) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.logging;

            public final class LoggingClient {
                public static Object newDecorator() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.retrying;

            public final class RetryingClient {
                public static Object newDecorator() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.retrofit2;

            public final class ArmeriaRetrofit {
                public static ArmeriaRetrofitBuilder builder(com.linecorp.armeria.client.WebClient webClient) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.retrofit2;

            public final class ArmeriaRetrofitBuilder {
                public retrofit2.Retrofit build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package retrofit2;

            public final class Retrofit {
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
