package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

fun JavaCodeInsightTestFixture.registerServerDecoratorInspectionStubs() {
    addClass(
        """
        package com.linecorp.armeria.server;

        public interface HttpService {
            HttpService decorate(Object decorator);
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.server;

        public interface HttpServiceWithRoutes extends HttpService {
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.server;

        public final class Server {
            public static ServerBuilder builder() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.server;

        public final class ServerBuilder {
            public ServerBuilder service(HttpService service) {
                return this;
            }

            public ServerBuilder service(String path, HttpService service) {
                return this;
            }

            public ServerBuilder service(HttpServiceWithRoutes service, Object decorator) {
                return this;
            }

            public ServerBuilder service(HttpServiceWithRoutes service, Object first, Object second) {
                return this;
            }

            public ServerBuilder serviceUnder(String pathPrefix, Object service) {
                return this;
            }

            public ServerBuilder decorator(Object decorator) {
                return this;
            }

            public ServerBuilder decorator(String pathPattern, Object decorator) {
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
    addClass(
        """
        package com.linecorp.armeria.server.grpc;

        public final class GrpcService implements com.linecorp.armeria.server.HttpServiceWithRoutes {
            public static GrpcServiceBuilder builder() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.server.grpc;

        public final class GrpcServiceBuilder {
            public GrpcServiceBuilder addService(Object bindableService) {
                return this;
            }

            public com.linecorp.armeria.server.grpc.GrpcService build() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.server.logging;

        public final class LoggingService {
            public static Object newDecorator() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.server.cors;

        public final class CorsService {
            public static Object newDecorator() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.server.auth;

        public final class AuthService {
            public static Object newDecorator() {
                return null;
            }
        }
        """.trimIndent(),
    )
}
