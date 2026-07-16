package com.linecorp.intellij.plugins.armeria.client

import com.linecorp.intellij.plugins.armeria.test.ArmeriaClientFixtureTestBase

class ArmeriaClientCollectorTest : ArmeriaClientFixtureTestBase() {

    fun testCollectWebClientOf() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;

            public class Main {
                public static void main(String[] args) {
                    WebClient.of("https://example.com");
                }
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        val endpoint = endpoints.single()
        assertEquals("HTTP", endpoint.clientType)
        assertEquals("https://example.com", endpoint.uri)
        assertTrue(endpoint.target.contains("WebClient"))
    }

    fun testCollectWebClientBuilderWithUri() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;

            public class Main {
                public static void main(String[] args) {
                    WebClient.builder("https://api.example.com");
                }
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        assertEquals("https://api.example.com", endpoints.single().uri)
    }

    fun testCollectGrpcClientsNewClient() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.grpc.GrpcClients;

            public class Main {
                public static void main(String[] args) {
                    GrpcClients.newClient("https://grpc.example.com", MyStub.class);
                }
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

    fun testCollectThriftClientsNewClient() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.thrift.ThriftClients;

            public class Main {
                public static void main(String[] args) {
                    ThriftClients.newClient("https://thrift.example.com", MyIface.class);
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class MyIface {
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        val endpoint = endpoints.single()
        assertEquals("Thrift", endpoint.clientType)
        assertEquals("https://thrift.example.com", endpoint.uri)
    }

    fun testIgnoresNoArgWebClientBuilder() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;

            public class Main {
                public static void main(String[] args) {
                    WebClient.builder();
                }
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertTrue(endpoints.isEmpty())
    }

    fun testNoFalsePositiveOnUnrelatedBuilder() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            public class Main {
                public static void main(String[] args) {
                    StringBuilder.builder();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public final class StringBuilder {
                public static StringBuilder builder() {
                    return new StringBuilder();
                }
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertTrue(endpoints.isEmpty())
    }

    fun testCollectWebClientOfViaStaticImport() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import static com.linecorp.armeria.client.WebClient.of;

            public class Main {
                public static void main(String[] args) {
                    of("https://example.com");
                }
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        assertEquals("WebClient", endpoints.single().target)
    }

    fun testDeduplicatesSameCallSite() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;

            public class Main {
                public static void main(String[] args) {
                    WebClient.of("https://example.com");
                }
            }
            """.trimIndent(),
        )

        val first = ArmeriaClientCollector.collect(project)
        val second = ArmeriaClientCollector.collect(project)

        assertEquals(1, first.size)
        assertEquals(first, second)
    }

    fun testCollectWebClientWithDecorators() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;
            import com.linecorp.armeria.client.logging.LoggingClient;
            import com.linecorp.armeria.client.retrying.RetryingClient;

            public class Main {
                public static void main(String[] args) {
                    WebClient.builder("https://example.com")
                             .decorator(LoggingClient.newDecorator())
                             .decorator(RetryingClient.newDecorator())
                             .build();
                }
            }
            """.trimIndent(),
        )

        val endpoint = ArmeriaClientCollector.collect(project).single()

        assertEquals("https://example.com", endpoint.uri)
        assertEquals(listOf("Logging", "Retrying"), endpoint.decorators)
    }

    fun testCollectWebClientWithEndpointGroup() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;
            import com.linecorp.armeria.client.endpoint.dns.DnsServiceEndpointGroup;
            import com.linecorp.armeria.common.SessionProtocol;

            public class Main {
                public static void main(String[] args) {
                    WebClient.builder(SessionProtocol.HTTP, DnsServiceEndpointGroup.of("k8s.default.svc.cluster.local."));
                }
            }
            """.trimIndent(),
        )

        val endpoint = ArmeriaClientCollector.collect(project).single()

        assertEquals("k8s.default.svc.cluster.local.", endpoint.uri)
        assertEquals("DnsServiceEndpointGroup (k8s.default.svc.cluster.local.)", endpoint.endpointGroup)
    }

    fun testCollectRetrofitBuilderWithWebClientTransport() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;
            import com.linecorp.armeria.client.retrofit2.ArmeriaRetrofit;

            public class Main {
                public static void main(String[] args) {
                    ArmeriaRetrofit.builder(WebClient.of("https://api.example.com")).build();
                }
            }
            """.trimIndent(),
        )

        val endpoint = ArmeriaClientCollector.collect(project).single()

        assertEquals("Retrofit", endpoint.clientType)
        assertEquals("https://api.example.com", endpoint.uri)
        assertEquals("WebClient transport", endpoint.transport)
    }

    fun testCollectRetrofitOfWithInlineWebClientDecorators() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;
            import com.linecorp.armeria.client.brave.BraveClient;
            import com.linecorp.armeria.client.retrofit2.ArmeriaRetrofit;

            public class Main {
                public static void main(String[] args) {
                    ArmeriaRetrofit.of(
                        WebClient.builder("https://api.example.com")
                                 .decorator(BraveClient.newDecorator())
                                 .build());
                }
            }
            """.trimIndent(),
        )

        val endpoint = ArmeriaClientCollector.collect(project).single()

        assertEquals("https://api.example.com", endpoint.uri)
        assertEquals("WebClient transport", endpoint.transport)
        assertEquals(listOf("Brave"), endpoint.decorators)
    }

    fun testCollectRetrofitBuilderWithWebClientEndpointGroupTransport() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;
            import com.linecorp.armeria.client.endpoint.EndpointGroup;
            import com.linecorp.armeria.client.retrofit2.ArmeriaRetrofit;
            import com.linecorp.armeria.common.SessionProtocol;

            public class Main {
                public static void main(String[] args) {
                    ArmeriaRetrofit.builder(
                        WebClient.builder(SessionProtocol.HTTP, EndpointGroup.of("https://lb.example.com")).build()
                    ).build();
                }
            }
            """.trimIndent(),
        )

        val endpoint = ArmeriaClientCollector.collect(project).single()

        assertEquals("Retrofit", endpoint.clientType)
        assertEquals("https://lb.example.com", endpoint.uri)
        assertEquals("WebClient transport", endpoint.transport)
        assertTrue(endpoint.endpointGroup!!.startsWith("EndpointGroup"))
    }

    fun testCollectRetrofitBuilderWithEndpointGroup() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.endpoint.EndpointGroup;
            import com.linecorp.armeria.client.retrofit2.ArmeriaRetrofit;
            import com.linecorp.armeria.common.SessionProtocol;

            public class Main {
                public static void main(String[] args) {
                    ArmeriaRetrofit.builder(SessionProtocol.HTTPS, EndpointGroup.of("https://lb.example.com"));
                }
            }
            """.trimIndent(),
        )

        val endpoint = ArmeriaClientCollector.collect(project).single()

        assertEquals("Retrofit", endpoint.clientType)
        assertEquals("https://lb.example.com", endpoint.uri)
        assertTrue(endpoint.endpointGroup!!.startsWith("EndpointGroup"))
        assertNull(endpoint.transport)
    }

    fun testCollectRetrofitBuilderWithDecoratedWebClientBuilderTransport() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;
            import com.linecorp.armeria.client.logging.LoggingClient;
            import com.linecorp.armeria.client.retrofit2.ArmeriaRetrofit;

            public class Main {
                public static void main(String[] args) {
                    ArmeriaRetrofit.builder(
                        WebClient.builder("https://api.example.com")
                                 .decorator(LoggingClient.newDecorator())
                    ).build();
                }
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        val endpoint = endpoints.single()
        assertEquals("Retrofit", endpoint.clientType)
        assertEquals("https://api.example.com", endpoint.uri)
        assertEquals("WebClient transport", endpoint.transport)
        assertEquals(listOf("Logging"), endpoint.decorators)
    }

    fun testCollectWebClientWithCircuitBreakerDecorator() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;
            import com.linecorp.armeria.client.circuitbreaker.CircuitBreakerClient;

            public class Main {
                public static void main(String[] args) {
                    WebClient.builder("https://example.com")
                             .decorator(CircuitBreakerClient.newDecorator())
                             .build();
                }
            }
            """.trimIndent(),
        )

        val endpoint = ArmeriaClientCollector.collect(project).single()

        assertEquals(listOf("Circuit breaker"), endpoint.decorators)
    }

}
