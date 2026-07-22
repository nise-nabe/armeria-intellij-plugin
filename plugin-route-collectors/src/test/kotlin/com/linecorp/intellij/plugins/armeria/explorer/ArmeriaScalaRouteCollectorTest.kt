package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaScalaRouteCollectorTest : ArmeriaFixtureTestBase() {
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
}
