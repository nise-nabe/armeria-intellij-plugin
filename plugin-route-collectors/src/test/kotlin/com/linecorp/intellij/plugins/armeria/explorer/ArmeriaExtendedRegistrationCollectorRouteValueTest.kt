package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import com.linecorp.intellij.plugins.armeria.test.assertRoute
import com.linecorp.intellij.plugins.armeria.test.singleRoute
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaExtendedRegistrationCollectorRouteValueTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerExtendedRegistrationCollectorStubs()
    }

    fun testCollectsServiceRegistrationWithRouteBuilder() {
        configureService(
            """
            Server.builder()
                .service(Route.builder().path("/x").methods(HttpMethod.POST).build(), new HelloService())
                .build();
            """,
        )

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/x", httpMethod = "POST")
            .also { route -> assertEquals("example.HelloService", route.target) }
    }

    fun testCollectsServiceRegistrationWithRouteOf() {
        configureService(
            """
            Server.builder()
                .service(Route.of("/x"), new HelloService())
                .build();
            """,
        )

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/x", httpMethod = "", pathType = PathType.EXACT)
            .also { route -> assertEquals("example.HelloService", route.target) }
    }

    fun testCollectsServiceRegistrationWithRouteOfMethodAndPath() {
        configureService(
            """
            Server.builder()
                .service(Route.of(HttpMethod.POST, "/x"), new HelloService())
                .build();
            """,
        )

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/x", httpMethod = "POST")
    }

    fun testCollectsServiceRegistrationWithRouteVariableInitializer() {
        configureService(
            """
            Route route = Route.builder().path("/x").build();
            Server.builder()
                .service(route, new HelloService())
                .build();
            """,
        )

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/x")
            .also { route -> assertEquals("example.HelloService", route.target) }
    }

    fun testCollectsServiceRegistrationWithRouteReturningMethod() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.common.HttpMethod;
            import com.linecorp.armeria.server.Route;
            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service(getRoute(), new HelloService())
                        .build();
                }

                private static Route getRoute() {
                    return Route.builder().path("/x").methods(HttpMethod.POST).build();
                }
            }
            """.trimIndent(),
        )
        addHelloService()

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/x", httpMethod = "POST")
            .also { route -> assertEquals("example.HelloService", route.target) }
    }

    fun testSkipsServiceRegistrationWhenRouteValueCannotBeResolved() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Route;
            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Route route = Route.builder().path(dynamicPath()).build();
                    Server.builder()
                        .service(route, new HelloService())
                        .service(dynamicRoute(), new HelloService())
                        .build();
                }

                private static Route dynamicRoute() {
                    return Route.of(dynamicPath());
                }

                private static String dynamicPath() {
                    return "/dynamic";
                }
            }
            """.trimIndent(),
        )
        addHelloService()

        assertTrue(collectRoutes().none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testSkipsServiceRegistrationWhenRouteMethodReturnsDifferentPaths() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Route;
            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service(pick(true), new HelloService())
                        .build();
                }

                private static Route pick(boolean flag) {
                    if (flag) {
                        return Route.of("/a");
                    }
                    return Route.of("/b");
                }
            }
            """.trimIndent(),
        )
        addHelloService()

        assertTrue(collectRoutes().none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testCollectsStandaloneRouteBuilderBuildWithHandler() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Route;
            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Route.builder().path("/x").build(new HelloService());
                }
            }
            """.trimIndent(),
        )
        addHelloService()

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/x")
            .also { route -> assertTrue(route.target.contains("HelloService")) }
    }

    fun testSkipsUnmountedRouteBuilderBuild() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Route;

            public class Main {
                public static void main(String[] args) {
                    Route.builder().path("/services/greet").build();
                    Route.of("/services/hello");
                }
            }
            """.trimIndent(),
        )

        assertTrue(collectRoutes().none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testSkipsServerBuilderBuildWithHandler() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder().post("/not-a-route").build(new HelloService());
                }
            }
            """.trimIndent(),
        )
        addHelloService()

        assertTrue(collectRoutes().none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testSkipsNonArmeriaRouteOf() {
        myFixture.addClass(
            """
            package example;

            public final class Route {
                public static Object of(String path) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service(Route.of("/x"), new HelloService())
                        .build();
                }
            }
            """.trimIndent(),
        )
        addHelloService()

        assertTrue(collectRoutes().none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testCollectsDecoratorOnEnclosingServiceChain() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Route;
            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.logging.LoggingService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .decorator(LoggingService.class)
                        .service(Route.builder().path("/x").build(), new HelloService())
                        .build();
                }
            }
            """.trimIndent(),
        )
        addHelloService()

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/x")
            .also { route -> assertEquals(listOf("Logging"), route.decorators) }
    }

    fun testWithRouteServiceArgumentKeepsServiceTarget() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.RouteBuilder;
            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .withRoute((RouteBuilder route) -> {
                            Server.builder().service(route.route().post("/wrapped").build(), new HelloService());
                            return route;
                        })
                        .build();
                }
            }
            """.trimIndent(),
        )
        addHelloService()

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/wrapped", httpMethod = "POST")
            .also { route -> assertEquals("example.HelloService", route.target) }
    }

    private fun configureService(body: String) {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.common.HttpMethod;
            import com.linecorp.armeria.server.Route;
            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    ${body.trimIndent()}
                }
            }
            """.trimIndent(),
        )
        addHelloService()
    }

    private fun addHelloService() {
        myFixture.addClass(
            """
            package example;

            public class HelloService {
            }
            """.trimIndent(),
        )
    }
}
