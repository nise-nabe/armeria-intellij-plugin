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

            public ServerBuilder healthCheckService() {
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

            public ServerBuilder healthCheckService() {
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

            public Object build() {
                return null;
            }
        }
        """.trimIndent(),
    )
}
