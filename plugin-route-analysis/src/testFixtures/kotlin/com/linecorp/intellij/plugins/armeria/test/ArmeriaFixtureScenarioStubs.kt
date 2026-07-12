package com.linecorp.intellij.plugins.armeria.test

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

fun JavaCodeInsightTestFixture.registerRouteDetailFormatterStubs() {

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

            public @interface Decorator {
                Class<?>[] value();
            }
            """.trimIndent(),
        )
        this.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface ExceptionHandler {
                Class<?>[] value();
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
            }
            """.trimIndent(),
        )
    }

fun JavaCodeInsightTestFixture.registerRouteDuplicateIndexStubs() {

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

            public @interface Post {
                String value() default "";
                String path() default "";
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

                public ServerBuilder serviceUnder(String pathPrefix, Object service) {
                    return this;
                }

                public ServerBuilder annotatedService(Object service) {
                    return this;
                }

                public ServerBuilder annotatedService(String pathPrefix, Object service) {
                    return this;
                }

                public ServerBuilder virtualHost(String hostname) {
                    return this;
                }

                public ServerBuilder virtualHost(String hostname, java.util.function.Consumer<ServerBuilder> customizer) {
                    return this;
                }

                public ServerBuilder healthCheckService() {
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

                public ServerBuilder methods(Object... methods) {
                    return this;
                }

                public ServerBuilder pathPrefix(String pathPrefix) {
                    return this;
                }

                public ServerBuilder build(Object handler) {
                    return this;
                }

                public com.linecorp.armeria.server.Server build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }

fun JavaCodeInsightTestFixture.registerRouteCollectorStubs() {

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

            public @interface PathPrefix {
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

                public ServerBuilder serviceUnder(String prefix, Object service) {
                    return this;
                }

                public ServerBuilder annotatedService(Object service) {
                    return this;
                }

                public ServerBuilder decorator(Object decorator) {
                    return this;
                }

                public ServerBuilder decorator(String pathPattern, Object decorator) {
                    return this;
                }

                public ServerBuilder requestTimeout(Object duration) {
                    return this;
                }

                public ServerBuilder responseTimeout(Object duration) {
                    return this;
                }

                public ServerBuilder idleTimeout(Object duration) {
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
            package com.linecorp.armeria.server.logging;

            public final class LoggingService {
            }
            """.trimIndent(),
        )
        this.addClass(
            """
            package com.linecorp.armeria.server.cors;

            public final class CorsService {
            }
            """.trimIndent(),
        )
        this.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcService {
                public static GrpcServiceBuilder builder(Object bindableService) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        this.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcServiceBuilder {
                public com.linecorp.armeria.server.grpc.GrpcService build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        this.addClass(
            """
            package com.linecorp.armeria.server.docs;

            public final class DocService {
                public static DocServiceBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        this.addClass(
            """
            package com.linecorp.armeria.server.docs;

            public final class DocServiceBuilder {
                public com.linecorp.armeria.server.docs.DocService build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }

fun JavaCodeInsightTestFixture.registerKotlinRouteCollectorStubs() {

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

            public @interface PathPrefix {
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

                public ServerBuilder serviceUnder(String pathPrefix, Object service) {
                    return this;
                }

                public ServerBuilder annotatedService(Object service) {
                    return this;
                }

                public ServerBuilder annotatedService(String pathPrefix, Object service) {
                    return this;
                }

                public ServerBuilder decorator(Object decorator) {
                    return this;
                }

                public ServerBuilder decorator(String pathPattern, Object decorator) {
                    return this;
                }

                public ServerBuilder requestTimeout(Object duration) {
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
            package com.linecorp.armeria.server.logging;

            public final class LoggingService {
                public static Object newDecorator() {
                    return null;
                }

                public static LoggingServiceBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        this.addClass(
            """
            package com.linecorp.armeria.server.logging;

            public final class LoggingServiceBuilder {
                public Object newDecorator() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        this.addClass(
            """
            package com.linecorp.armeria.server.cors;

            public final class CorsService {
            }
            """.trimIndent(),
        )
        this.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcService {
                public static GrpcServiceBuilder builder(Object bindableService) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        this.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcServiceBuilder {
                public com.linecorp.armeria.server.grpc.GrpcService build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        this.addClass(
            """
            package com.linecorp.armeria.server.docs;

            public final class DocService {
                public static DocServiceBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        this.addClass(
            """
            package com.linecorp.armeria.server.docs;

            public final class DocServiceBuilder {
                public com.linecorp.armeria.server.docs.DocService build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }
