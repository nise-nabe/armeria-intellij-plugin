package com.linecorp.intellij.plugins.armeria.test

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * Shared Armeria PSI stubs for [LightJavaCodeInsightFixtureTestCase] subclasses.
 */
abstract class ArmeriaFixtureTestBase : LightJavaCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        registerArmeriaStubs()
    }

    protected open fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerArmeriaServerStubs()
        registerArmeriaServiceStubs()
    }

    protected fun registerArmeriaAnnotationStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Get {
                String value() default "";
                String path() default "";
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Post {
                String value() default "";
                String path() default "";
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface PathPrefix {
                String value();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Decorator {
                Class<?>[] value();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface ExceptionHandler {
                Class<?>[] value();
            }
            """.trimIndent(),
        )
    }

    protected fun registerArmeriaBlockingAnnotationStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Blocking {}
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface NonBlocking {}
            """.trimIndent(),
        )
    }

    protected fun registerResolvableArmeriaServerStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;

            public final class Server {
                public static ServerBuilder builder() {
                    return new ServerBuilder();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;

            public final class ServerBuilder {
                public ServerBuilder service(String path, Object service) {
                    return this;
                }

                public com.linecorp.armeria.server.Server build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }

    protected fun registerArmeriaServerStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;

            public final class Server {
                public static ServerBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
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

                public ServerBuilder requestTimeout() {
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
    }

    protected fun registerMinimalArmeriaServerStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;

            public final class Server {
                public static ServerBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
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

                public Server build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }

    protected fun registerArmeriaServiceStubs() {
        myFixture.addClass(
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
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.logging;

            public final class LoggingServiceBuilder {
                public Object newDecorator() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.cors;

            public final class CorsService {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcService {
                public static GrpcServiceBuilder builder(Object bindableService) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcServiceBuilder {
                public com.linecorp.armeria.server.grpc.GrpcService build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.docs;

            public final class DocService {
                public static DocServiceBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
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

    protected fun registerArmeriaIdlStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.graphql;

            public final class GraphqlService {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.thrift;

            public final class THttpService {
            }
            """.trimIndent(),
        )
    }

    protected fun registerSpringAnnotationStubs() {
        myFixture.addClass(
            """
            package org.springframework.context.annotation;

            public @interface Bean {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package org.springframework.context.annotation;

            public @interface Configuration {
            }
            """.trimIndent(),
        )
    }

    protected fun registerArmeriaSpringStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.spring;

            @FunctionalInterface
            public interface ArmeriaServerConfigurator {
                void configure(com.linecorp.armeria.server.ServerBuilder serverBuilder);
            }
            """.trimIndent(),
        )
    }

    protected fun registerRouteDetailFormatterStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Get {
                String value() default "";
                String path() default "";
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Decorator {
                Class<?>[] value();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface ExceptionHandler {
                Class<?>[] value();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;

            public final class Server {
                public static ServerBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
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

    protected fun registerRouteDuplicateIndexStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Get {
                String value() default "";
                String path() default "";
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Post {
                String value() default "";
                String path() default "";
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;

            public final class Server {
                public static ServerBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
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

    protected fun registerRouteCollectorStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Get {
                String value() default "";
                String path() default "";
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface PathPrefix {
                String value();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;

            public final class Server {
                public static ServerBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
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
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.logging;

            public final class LoggingService {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.cors;

            public final class CorsService {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcService {
                public static GrpcServiceBuilder builder(Object bindableService) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcServiceBuilder {
                public com.linecorp.armeria.server.grpc.GrpcService build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.docs;

            public final class DocService {
                public static DocServiceBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
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

    protected fun registerKotlinRouteCollectorStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Get {
                String value() default "";
                String path() default "";
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface PathPrefix {
                String value();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;

            public final class Server {
                public static ServerBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
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
        myFixture.addClass(
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
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.logging;

            public final class LoggingServiceBuilder {
                public Object newDecorator() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.cors;

            public final class CorsService {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcService {
                public static GrpcServiceBuilder builder(Object bindableService) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcServiceBuilder {
                public com.linecorp.armeria.server.grpc.GrpcService build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.docs;

            public final class DocService {
                public static DocServiceBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
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
}
