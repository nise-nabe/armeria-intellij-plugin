package com.linecorp.intellij.plugins.armeria.test

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

fun JavaCodeInsightTestFixture.registerArmeriaServiceStubs() {
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
    registerKnownHttpServiceStubs()
}

fun JavaCodeInsightTestFixture.registerKnownHttpServiceStubs() {
    this.addClass(
        """
        package com.linecorp.armeria.server;

        public interface HttpService {
            HttpService decorate(Object decorator);
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.grpc;

        public final class GrpcService {
            public static GrpcServiceBuilder builder() {
                return null;
            }

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
            public GrpcServiceBuilder addService(Object bindableService) {
                return this;
            }

            public GrpcServiceBuilder addServices(Object... bindableServices) {
                return this;
            }

            public GrpcServiceBuilder enableUnframedRequests(boolean enabled) {
                return this;
            }

            public GrpcServiceBuilder enableUnframedRequests(Object errorHandler) {
                return this;
            }

            public com.linecorp.armeria.server.grpc.GrpcService build() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.docs;

        public final class DocService implements com.linecorp.armeria.server.HttpService {
            public DocService() {
            }

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

            public DocServiceBuilder exampleHeaders(com.linecorp.armeria.common.HttpHeaders... exampleHeaders) {
                return this;
            }

            public DocServiceBuilder exampleHeaders(Class<?> serviceType, com.linecorp.armeria.common.HttpHeaders... exampleHeaders) {
                return this;
            }

            public DocServiceBuilder exampleHeaders(Class<?> serviceType, String methodName, com.linecorp.armeria.common.HttpHeaders... exampleHeaders) {
                return this;
            }

            public DocServiceBuilder exampleHeaders(String serviceName, com.linecorp.armeria.common.HttpHeaders... exampleHeaders) {
                return this;
            }

            public DocServiceBuilder exampleHeaders(String serviceName, String methodName, com.linecorp.armeria.common.HttpHeaders... exampleHeaders) {
                return this;
            }

            public DocServiceBuilder exampleRequests(Class<?> serviceType, String methodName, String... exampleRequests) {
                return this;
            }

            public DocServiceBuilder exampleRequests(String serviceName, String methodName, String... exampleRequests) {
                return this;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.common;

        public final class HttpHeaders {
            public static HttpHeaders of(String name, String value) {
                return null;
            }

            public static HttpHeaders of(String name1, String value1, String name2, String value2) {
                return null;
            }

            public static HttpHeaders of(String name1, String value1, String name2, String value2, String name3, String value3) {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.metric;

        public final class PrometheusExpositionService implements com.linecorp.armeria.server.HttpService {
            public static PrometheusExpositionService of(Object collectorRegistry) {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.file;

        public final class FileService {
            public static FileService of(java.io.File root) {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.websocket;

        public final class WebSocketService {
            public static WebSocketService of(Object handler) {
                return null;
            }

            public static WebSocketServiceBuilder builder() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.websocket;

        public final class WebSocketServiceBuilder {
            public com.linecorp.armeria.server.websocket.WebSocketService build() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.healthcheck;

        public final class HealthCheckService {
            public static HealthCheckService of() {
                return null;
            }

            public static HealthCheckServiceBuilder builder() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.healthcheck;

        public final class HealthCheckServiceBuilder {
            public com.linecorp.armeria.server.healthcheck.HealthCheckService build() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.streaming;

        public final class ServerSentEvents {
            public static Object fromPublisher(Object publisher) {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.saml;

        public final class SamlService implements com.linecorp.armeria.server.HttpService {
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.saml;

        public final class SamlServiceProvider {
            public static SamlServiceProviderBuilder builder() {
                return null;
            }

            public com.linecorp.armeria.server.saml.SamlService newSamlService() {
                return null;
            }

            public Object newSamlDecorator() {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.saml;

        public final class SamlServiceProviderBuilder {
            public com.linecorp.armeria.server.saml.SamlServiceProvider build() {
                return null;
            }
        }
        """.trimIndent(),
    )
}

fun JavaCodeInsightTestFixture.registerArmeriaIdlStubs() {
    this.addClass(
        """
        package com.linecorp.armeria.server.graphql;

        public final class GraphqlService {
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.thrift;

        public final class THttpService {
        }
        """.trimIndent(),
    )
}

fun JavaCodeInsightTestFixture.registerSpringAnnotationStubs() {
    this.addClass(
        """
        package org.springframework.context.annotation;

        public @interface Bean {
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package org.springframework.context.annotation;

        public @interface Configuration {
        }
        """.trimIndent(),
    )
}

fun JavaCodeInsightTestFixture.registerArmeriaSpringStubs() {
    this.addClass(
        """
        package com.linecorp.armeria.spring;

        @FunctionalInterface
        public interface ArmeriaServerConfigurator {
            void configure(com.linecorp.armeria.server.ServerBuilder serverBuilder);
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.spring;

        @FunctionalInterface
        public interface DocServiceConfigurator {
            void configure(Object builder);
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.spring;

        @FunctionalInterface
        public interface HealthCheckServiceConfigurator {
            void configure(Object builder);
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.spring;

        @FunctionalInterface
        public interface MetricCollectingServiceConfigurator {
            void configure(Object builder);
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.spring;

        @FunctionalInterface
        public interface ArmeriaClientConfigurator {
            void configure(Object builder);
        }
        """.trimIndent(),
    )
}

fun JavaCodeInsightTestFixture.registerAthenzStubs() {
    this.addClass(
        """
        package com.linecorp.armeria.client.athenz;

        public final class ZtsBaseClient {
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.athenz;

        public final class AthenzServiceDecoratorFactory {
        }
        """.trimIndent(),
    )
}

fun JavaCodeInsightTestFixture.registerDropwizardStubs() {
    this.addClass(
        """
        package com.linecorp.armeria.dropwizard;

        public final class ArmeriaBundle {
        }
        """.trimIndent(),
    )
}
