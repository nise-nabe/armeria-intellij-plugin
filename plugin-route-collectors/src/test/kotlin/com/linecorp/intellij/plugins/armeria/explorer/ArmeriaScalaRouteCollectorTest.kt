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
        assertFalse(serviceRoute.targetUnresolved)
        assertNotNull(serviceRoute.sourceOffset)
        assertTrue(serviceRoute.sourceOffset!! > 0)
        assertFalse(serviceRoute.resolveSourceHint().endsWith(":1"))
    }

    fun testCollectMultipleServiceRegistrationsFromSameScalaFile() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.server.Server

            object Main {
              Server.builder()
                .service("/api", new HelloService())
                .service("/admin", new AdminService())
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
        myFixture.addClass(
            """
            package example;

            public class AdminService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val serviceRoutes = routes.filter { it.routeMatch == RouteMatch.SERVICE }
        assertEquals(2, serviceRoutes.size)
        val byPath = serviceRoutes.associateBy { it.path }
        assertEquals("HelloService", byPath.getValue("/api").target)
        assertEquals("AdminService", byPath.getValue("/admin").target)
        assertNotEquals(byPath.getValue("/api").sourceOffset, byPath.getValue("/admin").sourceOffset)
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
