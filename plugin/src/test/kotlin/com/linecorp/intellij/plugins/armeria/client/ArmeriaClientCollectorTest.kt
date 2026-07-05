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
        assertEquals("WebClient transport", endpoint.transport)
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
                public static final SessionProtocol HTTPS = new SessionProtocol();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.endpoint;

            public interface EndpointGroup {
                static EndpointGroup of(String uri) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.endpoint.dns;

            public final class DnsServiceEndpointGroup {
                public static com.linecorp.armeria.client.endpoint.EndpointGroup of(String domain) {
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
            package com.linecorp.armeria.client.brave;

            public final class BraveClient {
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
            package com.linecorp.armeria.client.circuitbreaker;

            public final class CircuitBreakerClient {
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

                public static ArmeriaRetrofitBuilder builder(String uri) {
                    return null;
                }

                public static ArmeriaRetrofitBuilder builder(
                        com.linecorp.armeria.common.SessionProtocol protocol,
                        com.linecorp.armeria.client.endpoint.EndpointGroup endpointGroup) {
                    return null;
                }

                public static retrofit2.Retrofit of(com.linecorp.armeria.client.WebClient webClient) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.retrofit2;

            public final class ArmeriaRetrofitBuilder {
                public ArmeriaRetrofitBuilder decorator(Object decorator) {
                    return this;
                }

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

            public final class GrpcClient {
                public static GrpcClientBuilder builder() {
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
                public static final SessionProtocol HTTPS = new SessionProtocol();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.endpoint;

            public interface EndpointGroup {
                static EndpointGroup of(String uri) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.endpoint.dns;

            public final class DnsServiceEndpointGroup {
                public static com.linecorp.armeria.client.endpoint.EndpointGroup of(String domain) {
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
            package com.linecorp.armeria.client.brave;

            public final class BraveClient {
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
            package com.linecorp.armeria.client.circuitbreaker;

            public final class CircuitBreakerClient {
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

                public static ArmeriaRetrofitBuilder builder(String uri) {
                    return null;
                }

                public static ArmeriaRetrofitBuilder builder(
                        com.linecorp.armeria.common.SessionProtocol protocol,
                        com.linecorp.armeria.client.endpoint.EndpointGroup endpointGroup) {
                    return null;
                }

                public static retrofit2.Retrofit of(com.linecorp.armeria.client.WebClient webClient) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.retrofit2;

            public final class ArmeriaRetrofitBuilder {
                public ArmeriaRetrofitBuilder decorator(Object decorator) {
                    return this;
                }

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
            package com.linecorp.armeria.common;

            public final class SessionProtocol {
                public static final SessionProtocol HTTP = new SessionProtocol();
                public static final SessionProtocol HTTPS = new SessionProtocol();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.endpoint;

            public interface EndpointGroup {
                static EndpointGroup of(String uri) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.endpoint.dns;

            public final class DnsServiceEndpointGroup {
                public static com.linecorp.armeria.client.endpoint.EndpointGroup of(String domain) {
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
            package com.linecorp.armeria.client.brave;

            public final class BraveClient {
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
            package com.linecorp.armeria.client.circuitbreaker;

            public final class CircuitBreakerClient {
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

                public static ArmeriaRetrofitBuilder builder(String uri) {
                    return null;
                }

                public static ArmeriaRetrofitBuilder builder(
                        com.linecorp.armeria.common.SessionProtocol protocol,
                        com.linecorp.armeria.client.endpoint.EndpointGroup endpointGroup) {
                    return null;
                }

                public static retrofit2.Retrofit of(com.linecorp.armeria.client.WebClient webClient) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.retrofit2;

            public final class ArmeriaRetrofitBuilder {
                public ArmeriaRetrofitBuilder decorator(Object decorator) {
                    return this;
                }

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
            package com.linecorp.armeria.client.thrift;

            public final class ThriftClient {
                public static ThriftClientBuilder builder() {
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
    }
}
