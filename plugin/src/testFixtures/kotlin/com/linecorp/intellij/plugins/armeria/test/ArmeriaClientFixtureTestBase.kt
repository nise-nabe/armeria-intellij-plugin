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

                public Object blocking() {
                    return null;
                }

                public Object asRestClient() {
                    return null;
                }

                public Object get(String path) {
                    return null;
                }

                public Object get(String path, String pathParam) {
                    return null;
                }

                public Object post(String path) {
                    return null;
                }

                public Object post(String path, String content) {
                    return null;
                }

                public Object post(String path, com.linecorp.armeria.common.MediaType contentType, String content) {
                    return null;
                }

                public Object put(String path) {
                    return null;
                }

                public Object put(String path, String content) {
                    return null;
                }

                public Object delete(String path) {
                    return null;
                }

                public Object patch(String path) {
                    return null;
                }

                public Object patch(String path, String content) {
                    return null;
                }

                public Object execute(Object request) {
                    return null;
                }

                public Object execute(
                        com.linecorp.armeria.common.HttpMethod method,
                        String path) {
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

                public WebClientBuilder auth(Object auth) {
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
            package com.linecorp.armeria.client;

            public final class RestClient {
                public static RestClient of(String uri) {
                    return null;
                }

                public static RestClient of(WebClient webClient) {
                    return null;
                }

                public static RestClientBuilder builder() {
                    return null;
                }

                public static RestClientBuilder builder(String uri) {
                    return null;
                }

                public static RestClientBuilder builder(WebClient webClient) {
                    return null;
                }

                public static RestClientBuilder builder(
                        com.linecorp.armeria.common.SessionProtocol protocol,
                        com.linecorp.armeria.client.endpoint.EndpointGroup endpointGroup) {
                    return null;
                }

                public RestClientPreparation get(String path) {
                    return null;
                }

                public RestClientPreparation get(String path, String pathParam) {
                    return null;
                }

                public RestClientPreparation post(String path) {
                    return null;
                }

                public RestClientPreparation put(String path) {
                    return null;
                }

                public RestClientPreparation delete(String path) {
                    return null;
                }

                public RestClientPreparation patch(String path) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client;

            public final class RestClientBuilder {
                public RestClientBuilder decorator(Object decorator) {
                    return this;
                }

                public RestClient build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client;

            public final class RestClientPreparation {
                public RestClientPreparation header(CharSequence name, Object value) {
                    return this;
                }

                public RestClientPreparation content(String content) {
                    return this;
                }

                public RestClientPreparation content(com.linecorp.armeria.common.MediaType contentType, String content) {
                    return this;
                }

                public RestClientPreparation pathParam(String name, Object value) {
                    return this;
                }

                public Object execute() {
                    return null;
                }

                public <T> Object execute(Class<T> type) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client;

            public final class BlockingWebClient {
                public static BlockingWebClient of(String uri) {
                    return null;
                }

                public static BlockingWebClientBuilder builder() {
                    return null;
                }

                public static BlockingWebClientBuilder builder(String uri) {
                    return null;
                }

                public Object get(String path) {
                    return null;
                }

                public Object get(String path, String pathParam) {
                    return null;
                }

                public Object post(String path) {
                    return null;
                }

                public Object post(String path, String content) {
                    return null;
                }

                public Object put(String path) {
                    return null;
                }

                public Object delete(String path) {
                    return null;
                }

                public Object patch(String path) {
                    return null;
                }

                public Object execute(Object request) {
                    return null;
                }

                public Object execute(
                        com.linecorp.armeria.common.HttpMethod method,
                        String path) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client;

            public final class BlockingWebClientBuilder {
                public BlockingWebClientBuilder decorator(Object decorator) {
                    return this;
                }

                public BlockingWebClient build() {
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
            package com.linecorp.armeria.common;

            public final class MediaType {
                public static final MediaType JSON = new MediaType();
                public static final MediaType JSON_UTF_8 = new MediaType();
                public static final MediaType PLAIN_TEXT = new MediaType();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.common;

            public final class HttpMethod {
                public static final HttpMethod GET = new HttpMethod();
                public static final HttpMethod POST = new HttpMethod();
                public static final HttpMethod PUT = new HttpMethod();
                public static final HttpMethod DELETE = new HttpMethod();
                public static final HttpMethod PATCH = new HttpMethod();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.common;

            public final class HttpHeaderNames {
                public static final CharSequence CONTENT_TYPE = "Content-Type";
                public static final CharSequence ACCEPT = "Accept";
                public static final CharSequence AUTHORIZATION = "Authorization";
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.common;

            public final class RequestHeaders {
                public static RequestHeaders of(HttpMethod method, String path, Object... rest) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.common;

            public final class HttpRequest {
                public static HttpRequest of(HttpMethod method, String path) {
                    return null;
                }

                public static HttpRequest of(RequestHeaders headers) {
                    return null;
                }
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
            package com.linecorp.armeria.client.auth.oauth2;

            public final class OAuth2Client {
                public static Object newDecorator() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.throttling;

            public final class ThrottlingClient {
                public static Object newDecorator() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.encoding;

            public final class DecodingClient {
                public static Object newDecorator() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.encoding;

            public final class EncodingClient {
                public static Object newDecorator() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.endpoint.healthcheck;

            public final class HealthCheckedEndpointGroup {
                public static com.linecorp.armeria.client.endpoint.EndpointGroup of(
                        com.linecorp.armeria.client.endpoint.EndpointGroup delegate) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.endpoint.zookeeper;

            public final class ZooKeeperEndpointGroup {
                public static com.linecorp.armeria.client.endpoint.EndpointGroup of(String connectionStr) {
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
