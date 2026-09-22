package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.model.FileServiceRootKind
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import com.linecorp.intellij.plugins.armeria.test.assertRoute
import com.linecorp.intellij.plugins.armeria.test.singleRoute
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaExtendedRegistrationCollectorBasicTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerExtendedRegistrationCollectorStubs()
    }

    fun testCollectFileServiceRegistration() {
        configureFixture("extendedRegistration/basic/fileService/Main.java")
        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.FILE_SERVICE, path = "/files/")
            .also { route ->
                assertEquals(FileServiceRootKind.FILE_SYSTEM, route.fileServiceRoot?.kind)
                assertEquals("/tmp", route.fileServiceRoot?.path)
            }
    }

    fun testCollectFileServiceWithJavaConstantPath() {
        configureFixture("extendedRegistration/basic/fileServiceWithConstant/Main.java")
        collectRoutes().also { it.singleRoute() }.assertRoute(RouteMatch.FILE_SERVICE, path = "/files/")
    }

    fun testCollectFileServiceWithPathsGetRoot() {
        configureFixture("extendedRegistration/basic/fileServiceWithPaths/Main.java")
        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.FILE_SERVICE, path = "/static/")
            .also { route ->
                assertEquals(FileServiceRootKind.FILE_SYSTEM, route.fileServiceRoot?.kind)
                assertEquals("src/main/resources/static", route.fileServiceRoot?.path)
            }
    }

    fun testCollectFileServiceWithServiceOfRoot() {
        configureFixture("extendedRegistration/basic/fileServiceWithServiceOf/Main.java")
        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.FILE_SERVICE, path = "/static/")
            .also { route ->
                assertEquals(FileServiceRootKind.FILE_SYSTEM, route.fileServiceRoot?.kind)
                assertEquals("src/main/resources/public", route.fileServiceRoot?.path)
            }
    }

    fun testCollectHealthCheckRegistration() {
        configureFixture("extendedRegistration/basic/healthCheck/Main.java")
        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.HEALTH_CHECK, path = "/internal/healthcheck")
            .also { route ->
                assertEquals(RouteProtocol.HEALTH_CHECK.presentableName(), route.protocol)
                assertEquals("GET", route.httpMethod)
            }
    }

    fun testCollectFluentRouteRegistration() {
        configureFixture("extendedRegistration/basic/fluentRoute/Main.java")
        collectRoutes().also { it.singleRoute() }.assertRoute(
            RouteMatch.ROUTE_FLUENT,
            path = "/api/items",
            httpMethod = "POST",
        )
    }

    fun testCollectDecoratorUnderRegistration() {
        configureFixture("extendedRegistration/basic/decoratorUnder/Main.java")
        collectRoutes().also { it.singleRoute() }.assertRoute(RouteMatch.DECORATOR_UNDER, path = "/public")
    }

    fun testCollectPathAnnotationAndPathType() {
        configureFixture("extendedRegistration/basic/pathAnnotation/HelloService.java")
        collectRoutes().also { it.singleRoute() }.assertRoute(path = "/hello", pathType = PathType.PREFIX)
    }

    fun testCollectRegexPathAnnotationTrimsWhitespace() {
        configureFixture("extendedRegistration/basic/regexPathAnnotation/HelloService.java")
        collectRoutes().also { it.singleRoute() }.assertRoute(path = "/foo", pathType = PathType.REGEX)
    }

    fun testCollectGlobPathAnnotationNormalizesLeadingSlash() {
        configureFixture("extendedRegistration/basic/globPathAnnotation/HelloService.java")
        collectRoutes().also { it.singleRoute() }.assertRoute(path = "/foo/**", pathType = PathType.GLOB)
    }

    fun testCollectFluentRoutePathPrefix() {
        configureFixture("extendedRegistration/basic/fluentRoutePathPrefix/Main.java")
        collectRoutes().also { it.singleRoute() }.assertRoute(
            RouteMatch.ROUTE_FLUENT,
            path = "/api/items",
            httpMethod = "GET",
            pathType = PathType.EXACT,
        )
    }

    fun testSkipsFileServiceWithNonConstantPath() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import java.io.File;

            public class Main {
                public static void main(String[] args) {
                    String path = dynamicPath();
                    Server.builder()
                        .fileService(path, new File("/tmp"))
                        .build();
                }

                private static String dynamicPath() {
                    return "/dynamic";
                }
            }
            """.trimIndent(),
        )

        val routes = collectRoutes()

        assertTrue(routes.none { it.routeMatch == RouteMatch.FILE_SERVICE })
    }

    fun testSkipsHealthCheckServiceWithNonConstantPath() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    String path = dynamicPath();
                    Server.builder()
                        .healthCheckService(path)
                        .build();
                }

                private static String dynamicPath() {
                    return "/dynamic";
                }
            }
            """.trimIndent(),
        )

        val routes = collectRoutes()

        assertTrue(routes.none { it.routeMatch == RouteMatch.HEALTH_CHECK })
    }

    fun testSkipsFluentRouteWithNonConstantPath() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    String path = dynamicPath();
                    Server.builder()
                        .route()
                        .post(path)
                        .build((ctx, req) -> null)
                        .build();
                }

                private static String dynamicPath() {
                    return "/dynamic";
                }
            }
            """.trimIndent(),
        )

        val routes = collectRoutes()

        assertTrue(routes.none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testCollectsFluentRouteWithTwoArgPath() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .route()
                        .path("/api", "/v2")
                        .build(new Object());
                }
            }
            """.trimIndent(),
        )

        val routes = collectRoutes()

        kotlin.test.assertEquals(
            "/api/v2",
            routes.firstOrNull { it.routeMatch == RouteMatch.ROUTE_FLUENT }?.path,
        )
    }

    fun testSkipsFluentRouteWithUnresolvedTwoArgPath() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    String pattern = dynamicPath();
                    Server.builder()
                        .route()
                        .path("/api", pattern)
                        .build(new Object());
                }

                private static String dynamicPath() {
                    return "/dynamic";
                }
            }
            """.trimIndent(),
        )

        val routes = collectRoutes()

        assertTrue(routes.none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testSkipsFluentRouteWithNonConstantPathPrefix() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    String path = dynamicPath();
                    Server.builder()
                        .route()
                        .pathPrefix(path)
                        .get("/items")
                        .build((ctx, req) -> null)
                        .build();
                }

                private static String dynamicPath() {
                    return "/dynamic";
                }
            }
            """.trimIndent(),
        )

        val routes = collectRoutes()

        assertTrue(routes.none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testCollectsServiceRegistrationWithFluentRouteArgument() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.ServerBuilder;

            public class Main {
                public static void main(String[] args) {
                    ServerBuilder sb = Server.builder();
                    sb.service(sb.route().path("/fluent").build(), new HelloService());
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class HelloService {
            }
            """.trimIndent(),
        )

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/fluent")
            .also { route -> assertEquals("example.HelloService", route.target) }
    }

    fun testSkipsServiceRegistrationWithUnresolvedFluentRouteArgument() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.ServerBuilder;

            public class Main {
                public static void main(String[] args) {
                    String path = dynamicPath();
                    ServerBuilder sb = Server.builder();
                    sb.service(sb.route().path(path).build(), new HelloService());
                }

                private static String dynamicPath() {
                    return "/dynamic";
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class HelloService {
            }
            """.trimIndent(),
        )

        val routes = collectRoutes()

        assertTrue(routes.none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }
}
