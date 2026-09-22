package com.linecorp.intellij.plugins.armeria.test

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

fun JavaCodeInsightTestFixture.registerExtendedRegistrationCollectorStubs() {
    this.addClass(
        """
        package com.linecorp.armeria.server.annotation;

        public @interface Get {
            String value() default "";
            String path() default "";
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.annotation;

        public @interface Path {
            String value();
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server;

        public final class Server {
            public static ServerBuilder builder() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server;

        public final class ServerBuilder {
            public ServerBuilder service(String path, Object service) {
                return this;
            }

            public ServerBuilder fileService(String path, java.io.File root) {
                return this;
            }

            public ServerBuilder fileService(String path, java.nio.file.Path root) {
                return this;
            }

            public ServerBuilder fileService(String path, com.linecorp.armeria.server.file.FileService service) {
                return this;
            }

            public ServerBuilder healthCheckService() {
                return this;
            }

            public ServerBuilder healthCheckService(String path) {
                return this;
            }

            public ServerBuilder virtualHost(String hostname) {
                return this;
            }

            public ServerBuilder virtualHost(String hostname, java.util.function.Consumer<ServerBuilder> customizer) {
                return this;
            }

            public ServerBuilder routeDecorator() {
                return this;
            }

            public ServerBuilder path(String pathPattern) {
                return this;
            }

            public ServerBuilder path(String prefix, String pathPattern) {
                return this;
            }

            public ServerBuilder withRoute(java.util.function.Function<RouteBuilder, RouteBuilder> fn) {
                return this;
            }

            public ServerBuilder route() {
                return this;
            }

            public ServerBuilder post(String path) {
                return this;
            }

            public ServerBuilder get(String path) {
                return this;
            }

            public ServerBuilder pathPrefix(String pathPrefix) {
                return this;
            }

            public ServerBuilder methods(Object... methods) {
                return this;
            }

            public ServerBuilder build(Object handler) {
                return this;
            }

            public ServerBuilder decoratorUnder(String path, Object decorator) {
                return this;
            }

            public ServerBuilder http(int port) {
                return this;
            }

            public ServerBuilder https(int port) {
                return this;
            }

            public ServerBuilder port(int port) {
                return this;
            }

            public ServerBuilder port(int port, Object... protocols) {
                return this;
            }

            public ServerBuilder serverListener(Object listener) {
                return this;
            }

            public com.linecorp.armeria.server.Server build() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server;

        public final class RouteBuilder {
            public RouteBuilder route() {
                return this;
            }

            public RouteBuilder post(String path) {
                return this;
            }

            public RouteBuilder build(Object handler) {
                return this;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.logging;

        public final class LoggingService {
            public static LoggingServiceBuilder builder() {
                return null;
            }

            public static Object newDecorator() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.logging;

        public final class LoggingServiceBuilder {
            public LoggingServiceBuilder path(String pathPattern) {
                return this;
            }

            public Object build() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.common;

        public final class HttpMethod {
            public static final HttpMethod POST = null;
            public static final HttpMethod PUT = null;
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.common;

        public final class SessionProtocol {
            public static final SessionProtocol HTTP = new SessionProtocol();
            public static final SessionProtocol HTTPS = new SessionProtocol();
            public static final SessionProtocol PROXY = new SessionProtocol();
            public static final SessionProtocol H1 = new SessionProtocol();
            public static final SessionProtocol H2 = new SessionProtocol();
            public static final SessionProtocol H1C = new SessionProtocol();
            public static final SessionProtocol H2C = new SessionProtocol();

            public static SessionProtocol of(String uriText) {
                return HTTP;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package example;

        public final class OtherBuilder {
            public static OtherBuilder builder() {
                return null;
            }

            public OtherBuilder route() {
                return this;
            }

            public OtherBuilder post(String path) {
                return this;
            }

            public OtherBuilder build(Object handler) {
                return this;
            }

            public OtherBuilder virtualHost(String hostname) {
                return this;
            }

            public OtherBuilder service(String path, Object handler) {
                return this;
            }

            public OtherBuilder http(int port) {
                return this;
            }

            public Object build() {
                return null;
            }
        }
        """.trimIndent(),
    )
}

fun JavaCodeInsightTestFixture.registerKotlinExtendedRegistrationCollectorStubs() {
    this.addClass(
        """
        package com.linecorp.armeria.server;

        public final class Server {
            public static ServerBuilder builder() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server;

        public final class ServerBuilder {
            public ServerBuilder service(String path, Object service) {
                return this;
            }

            public ServerBuilder fileService(String path, java.io.File root) {
                return this;
            }

            public ServerBuilder fileService(String path, java.nio.file.Path root) {
                return this;
            }

            public ServerBuilder fileService(String path, com.linecorp.armeria.server.file.FileService service) {
                return this;
            }

            public ServerBuilder healthCheckService() {
                return this;
            }

            public ServerBuilder healthCheckService(String path) {
                return this;
            }

            public ServerBuilder virtualHost(String hostname) {
                return this;
            }

            public ServerBuilder virtualHost(String hostname, java.util.function.Consumer<ServerBuilder> customizer) {
                return this;
            }

            public ServerBuilder routeDecorator() {
                return this;
            }

            public ServerBuilder path(String pathPattern) {
                return this;
            }

            public ServerBuilder path(String prefix, String pathPattern) {
                return this;
            }

            public ServerBuilder methods(Object... methods) {
                return this;
            }

            public ServerBuilder withRoute(java.util.function.Function<RouteBuilder, RouteBuilder> fn) {
                return this;
            }

            public ServerBuilder route() {
                return this;
            }

            public ServerBuilder post(String path) {
                return this;
            }

            public ServerBuilder get(String path) {
                return this;
            }

            public ServerBuilder pathPrefix(String pathPrefix) {
                return this;
            }

            public ServerBuilder build(Object handler) {
                return this;
            }

            public ServerBuilder decoratorUnder(String path, Object decorator) {
                return this;
            }

            public ServerBuilder http(int port) {
                return this;
            }

            public ServerBuilder https(int port) {
                return this;
            }

            public ServerBuilder port(int port) {
                return this;
            }

            public ServerBuilder port(int port, Object... protocols) {
                return this;
            }

            public ServerBuilder serverListener(Object listener) {
                return this;
            }

            public com.linecorp.armeria.server.Server build() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server;

        public final class RouteBuilder {
            public RouteBuilder post(String path) {
                return this;
            }

            public RouteBuilder build(Object handler) {
                return this;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.logging;

        public final class LoggingService {
            public static LoggingServiceBuilder builder() {
                return null;
            }

            public static Object newDecorator() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.logging;

        public final class LoggingServiceBuilder {
            public LoggingServiceBuilder path(String pathPattern) {
                return this;
            }

            public Object build() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.common;

        public final class HttpMethod {
            public static final HttpMethod POST = null;
            public static final HttpMethod PUT = null;
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.common;

        public final class SessionProtocol {
            public static final SessionProtocol HTTP = new SessionProtocol();
            public static final SessionProtocol HTTPS = new SessionProtocol();
            public static final SessionProtocol PROXY = new SessionProtocol();
            public static final SessionProtocol H1 = new SessionProtocol();
            public static final SessionProtocol H2 = new SessionProtocol();
            public static final SessionProtocol H1C = new SessionProtocol();
            public static final SessionProtocol H2C = new SessionProtocol();

            public static SessionProtocol of(String uriText) {
                return HTTP;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package example;

        public final class OtherBuilder {
            public static OtherBuilder builder() {
                return null;
            }

            public OtherBuilder route() {
                return this;
            }

            public OtherBuilder post(String path) {
                return this;
            }

            public OtherBuilder build(Object handler) {
                return this;
            }

            public OtherBuilder service(String path, Object handler) {
                return this;
            }

            public OtherBuilder http(int port) {
                return this;
            }

            public Object build() {
                return null;
            }
        }
        """.trimIndent(),
    )
}

/**
 * Optional-integration stubs for server-side discovery registration
 * (`serverListener(...)` + `*UpdatingListener`). Tests that verify the classpath gate
 * must NOT call this — the listener classes stay absent from the fixture.
 */
fun JavaCodeInsightTestFixture.registerDiscoveryRegistrationStubs() {
    this.addClass(
        """
        package com.linecorp.armeria.server;

        public interface ServerListener {
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.client;

        public interface EndpointGroup {
            public static EndpointGroup of(String... endpoints) {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package org.apache.curator.framework;

        public interface CuratorFramework {
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.zookeeper;

        import com.linecorp.armeria.server.ServerListener;

        public final class ZooKeeperUpdatingListener implements ServerListener {
            public static ZooKeeperUpdatingListenerBuilder builder(String zkConnectionStr, String znodePath, ZooKeeperRegistrationSpec spec) {
                return null;
            }

            public static ZooKeeperUpdatingListenerBuilder builder(org.apache.curator.framework.CuratorFramework client, String znodePath, ZooKeeperRegistrationSpec spec) {
                return null;
            }

            public static ZooKeeperUpdatingListener of(String zkConnectionStr, String znodePath, ZooKeeperRegistrationSpec spec) {
                return null;
            }

            public static ZooKeeperUpdatingListener of(org.apache.curator.framework.CuratorFramework client, String znodePath, ZooKeeperRegistrationSpec spec) {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.zookeeper;

        public final class ZooKeeperUpdatingListenerBuilder {
            public ZooKeeperUpdatingListenerBuilder sessionTimeoutMillis(long timeout) {
                return this;
            }

            public ZooKeeperUpdatingListener build() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.zookeeper;

        public final class ZooKeeperRegistrationSpec {
            public static ZooKeeperRegistrationSpec curator(String serviceName) {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.eureka;

        import com.linecorp.armeria.server.ServerListener;

        public final class EurekaUpdatingListener implements ServerListener {
            public static EurekaUpdatingListenerBuilder builder(String eurekaUri) {
                return null;
            }

            public static EurekaUpdatingListenerBuilder builder(java.net.URI eurekaUri) {
                return null;
            }

            public static EurekaUpdatingListenerBuilder builder(com.linecorp.armeria.common.SessionProtocol sessionProtocol, com.linecorp.armeria.client.EndpointGroup endpointGroup) {
                return null;
            }

            public static EurekaUpdatingListenerBuilder builder(com.linecorp.armeria.common.SessionProtocol sessionProtocol, com.linecorp.armeria.client.EndpointGroup endpointGroup, String path) {
                return null;
            }

            public static EurekaUpdatingListener of(String eurekaUri) {
                return null;
            }

            public static EurekaUpdatingListener of(java.net.URI eurekaUri) {
                return null;
            }

            public static EurekaUpdatingListener of(com.linecorp.armeria.common.SessionProtocol sessionProtocol, com.linecorp.armeria.client.EndpointGroup endpointGroup) {
                return null;
            }

            public static EurekaUpdatingListener of(com.linecorp.armeria.common.SessionProtocol sessionProtocol, com.linecorp.armeria.client.EndpointGroup endpointGroup, String path) {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.eureka;

        public final class EurekaUpdatingListenerBuilder {
            public EurekaUpdatingListenerBuilder appName(String appName) {
                return this;
            }

            public EurekaUpdatingListenerBuilder instanceId(String instanceId) {
                return this;
            }

            public EurekaUpdatingListener build() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.consul;

        import com.linecorp.armeria.server.ServerListener;

        public final class ConsulUpdatingListener implements ServerListener {
            public static ConsulUpdatingListenerBuilder builder(java.net.URI consulUri, String serviceName) {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.consul;

        public final class ConsulUpdatingListenerBuilder {
            public ConsulUpdatingListenerBuilder serviceName(String serviceName) {
                return this;
            }

            public ConsulUpdatingListener build() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.nacos;

        import com.linecorp.armeria.server.ServerListener;

        public final class NacosUpdatingListener implements ServerListener {
            public static NacosUpdatingListenerBuilder builder(java.net.URI nacosUri, String serviceName) {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.nacos;

        public final class NacosUpdatingListenerBuilder {
            public NacosUpdatingListenerBuilder groupName(String groupName) {
                return this;
            }

            public NacosUpdatingListener build() {
                return null;
            }
        }
        """.trimIndent(),
    )
}
