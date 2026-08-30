package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

fun JavaCodeInsightTestFixture.registerProductionChecklistInspectionStubs() {
    addClass(
        """
        package com.linecorp.armeria.server;

        public final class Server {
            public static ServerBuilder builder() {
                return new ServerBuilder();
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.server;

        public final class ServerBuilder {
            public ServerBuilder maxNumConnections(int maxNumConnections) {
                return this;
            }

            public ServerBuilder requestTimeout(Object duration) {
                return this;
            }

            public ServerBuilder requestTimeoutMillis(long requestTimeoutMillis) {
                return this;
            }

            public ServerBuilder maxRequestLength(long maxRequestLength) {
                return this;
            }

            public ServerBuilder http(int port) {
                return this;
            }

            public com.linecorp.armeria.server.Server build() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client;

        public final class WebClient {
            public static WebClientBuilder builder(String uri) {
                return new WebClientBuilder();
            }

            public static WebClient of(String uri) {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client;

        public final class WebClientBuilder {
            public WebClientBuilder decorator(Object decorator) {
                return this;
            }

            public WebClientBuilder factory(ClientFactory factory) {
                return this;
            }

            public com.linecorp.armeria.client.WebClient build() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client;

        public final class ClientFactory {
            public static ClientFactoryBuilder builder() {
                return new ClientFactoryBuilder();
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client;

        public final class ClientFactoryBuilder {
            public ClientFactoryBuilder maxNumEventLoopsPerEndpoint(int num) {
                return this;
            }

            public ClientFactory build() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client.retry;

        public final class RetryingClient {
            public static Object newDecorator() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client.circuitbreaker;

        public final class CircuitBreakerClient {
            public static Object newDecorator() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client.endpoint;

        public interface EndpointGroup extends AutoCloseable {
            @Override
            void close();

            void closeAsync();
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client.endpoint.dns;

        public final class DnsAddressEndpointGroup implements com.linecorp.armeria.client.endpoint.EndpointGroup {
            public static DnsAddressEndpointGroup of(String hostname, int port) {
                return new DnsAddressEndpointGroup();
            }

            @Override
            public void close() {
            }

            @Override
            public void closeAsync() {
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client.zookeeper;

        public final class ZooKeeperEndpointGroup implements com.linecorp.armeria.client.endpoint.EndpointGroup {
            public static ZooKeeperEndpointGroup of(String connectionStr) {
                return new ZooKeeperEndpointGroup();
            }

            @Override
            public void close() {
            }

            @Override
            public void closeAsync() {
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.common;

        public interface FlagsProvider {
            default int priority() {
                return 0;
            }
        }
        """.trimIndent(),
    )
}
