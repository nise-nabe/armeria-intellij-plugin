package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaScalaRouteCollectorTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerArmeriaStubs()
    }

    fun testCollectServiceRegistrationFromScalaBuilderChain() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.server.Server

            object Main {
              Server.builder()
                .service("/api", new HelloService())
                .build()
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

        val routes = ArmeriaRouteCollector.collect(project)

        val serviceRoute = routes.firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("HelloService", serviceRoute!!.target)
    }

    fun testCollectAnnotatedServiceRegistrationFromScala() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.server.Server

            object Main {
              Server.builder()
                .annotatedService("/prefix", new AnnotatedService())
                .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class AnnotatedService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val route = routes.firstOrNull { it.path == "/prefix" && it.routeMatch == RouteMatch.ANNOTATED_SERVICE }
        assertNotNull(route)
        assertEquals("AnnotatedService", route!!.target)
    }

    private fun registerArmeriaStubs() {
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

                public ServerBuilder annotatedService(Object service) {
                    return this;
                }

                public ServerBuilder annotatedService(String pathPrefix, Object service) {
                    return this;
                }

                public com.linecorp.armeria.server.Server build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }
}
