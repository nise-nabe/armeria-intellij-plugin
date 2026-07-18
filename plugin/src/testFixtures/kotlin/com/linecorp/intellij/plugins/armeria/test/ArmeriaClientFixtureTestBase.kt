package com.linecorp.intellij.plugins.armeria.test

abstract class ArmeriaClientFixtureTestBase : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerArmeriaClientStubs()
    }

    protected fun registerArmeriaClientStubs() {
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
            package com.linecorp.armeria.client.endpoint.dns;

            public final class DnsAddressEndpointGroup {
                public static com.linecorp.armeria.client.endpoint.EndpointGroup of(String hostname, int port) {
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

                public static ArmeriaRetrofitBuilder builder(com.linecorp.armeria.client.WebClientBuilder webClientBuilder) {
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
            package com.linecorp.armeria.client.grpc;

            public final class GrpcClientBuilder {
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
