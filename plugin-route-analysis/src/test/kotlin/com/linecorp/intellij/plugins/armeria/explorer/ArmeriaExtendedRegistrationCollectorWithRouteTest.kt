package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaExtendedRegistrationCollectorWithRouteTest : ArmeriaFixtureTestBase() {

    override fun registerArmeriaStubs() {
        registerExtendedRegistrationCollectorStubs()
    }

    fun testCollectWithRouteRegistration() {
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
                            return route.post("/wrapped").build((ctx, req) -> null);
                        })
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val fluentRoutes = routes.filter { it.routeMatch == RouteMatch.ROUTE_FLUENT }
        assertEquals(1, fluentRoutes.size)
        assertEquals("POST", fluentRoutes.single().httpMethod)
        assertEquals("/wrapped", fluentRoutes.single().path)
    }


    fun testCollectWithRouteDoesNotDuplicateRouteAnchorFluentRoute() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.RouteBuilder;
            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .withRoute((RouteBuilder route) -> route.route().post("/wrapped").build((ctx, req) -> null))
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val fluentRoutes = routes.filter { it.routeMatch == RouteMatch.ROUTE_FLUENT }
        assertEquals(1, fluentRoutes.size)
        assertEquals("/wrapped", fluentRoutes.single().path)
    }


}
