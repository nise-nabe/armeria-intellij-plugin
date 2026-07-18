package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaExtendedRegistrationCollectorIgnoreTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerExtendedRegistrationCollectorStubs()
    }

    fun testIgnoreNonArmeriaFluentBuildCalls() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            public class Main {
                public static void main(String[] args) {
                    OtherBuilder.builder()
                        .route()
                        .post("/nope")
                        .build((ctx, req) -> null)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testIgnoreNonArmeriaVirtualHostCalls() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            public class Main {
                public static void main(String[] args) {
                    OtherBuilder.builder()
                        .virtualHost("nope.example.com")
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.VIRTUAL_HOST })
    }

    fun testIgnoreNonArmeriaFluentBuildInVirtualHostLambda() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .virtualHost("api.example.com", vh -> {
                            OtherBuilder.builder()
                                .route()
                                .post("/nope")
                                .build(new Object())
                                .build();
                        })
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testIgnoreNonArmeriaServiceInVirtualHostLambda() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .virtualHost("api.example.com", vh -> {
                            OtherBuilder.builder()
                                .service("/nope", new Object())
                                .build();
                        })
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.SERVICE })
    }
}
