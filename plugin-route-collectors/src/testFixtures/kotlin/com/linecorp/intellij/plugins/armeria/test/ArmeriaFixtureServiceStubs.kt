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
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.metric;

        public final class PrometheusExpositionService {
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
}
