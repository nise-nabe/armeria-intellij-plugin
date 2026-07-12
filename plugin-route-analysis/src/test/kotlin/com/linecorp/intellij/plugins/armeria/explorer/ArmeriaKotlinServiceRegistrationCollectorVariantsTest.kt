package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaKotlinServiceRegistrationCollectorVariantsTest : ArmeriaFixtureTestBase() {

    override fun registerArmeriaStubs() {
        registerKotlinRouteCollectorStubs()
    }

    fun testCollectServiceRegistrationFromFullyQualifiedServerBuilder() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            fun main() {
                com.linecorp.armeria.server.Server.builder()
                    .service("/api", Any())
                    .build()
            }
            """.trimIndent(),
        )

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals(
            "Server.builder().service(\"/api\", …)",
            ArmeriaRouteDetailFormatter.registrationSummary(serviceRoute!!),
        )
    }
    fun testResolvedConstructorTargetIsNotMarkedUnresolved() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service("/api", HelloService())
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

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute!!.target)
        assertFalse(serviceRoute.targetUnresolved)
    }
    fun testCollectServiceRegistrationFromNullableServerBuilderVariable() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.ServerBuilder

            fun main() {
                val sb: ServerBuilder? = Server.builder()
                sb!!.service("/api", HelloService())
                sb!!.build()
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

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute!!.target)
    }
    fun testCollectServiceRegistrationFromAnnotatedServerBuilderVariable() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.ServerBuilder

            annotation class Ann

            fun main() {
                val sb: @Ann ServerBuilder? = Server.builder()
                sb!!.service("/api", HelloService())
                sb!!.build()
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

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute!!.target)
        assertFalse(serviceRoute.targetUnresolved)
    }
    fun testCollectServiceRegistrationWithKotlinDefinedServiceClass() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            class HelloService
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service("/api", HelloService())
                    .build()
            }
            """.trimIndent(),
        )

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute!!.target)
        assertFalse(serviceRoute.targetUnresolved)
    }
    fun testCollectServiceRegistrationWithJavaStaticFinalPath() {
        myFixture.addClass(
            """
            package example;

            public final class Routes {
                public static final String API_PATH = "/api";
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service(Routes.API_PATH, HelloService())
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

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
    }
    fun testCollectServiceRegistrationFromServerBuilderExtensionFunction() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.ServerBuilder

            fun ServerBuilder.configure() {
                service("/api", HelloService())
            }

            fun main() {
                Server.builder()
                    .configure()
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

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute!!.target)
    }
    fun testCollectServiceRegistrationFromServerBuilderTypeAlias() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.ServerBuilder

            typealias SB = ServerBuilder

            fun main() {
                val sb: SB = Server.builder()
                sb.service("/api", HelloService())
                sb.build()
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

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute!!.target)
    }
    fun testCollectServiceRegistrationFromParenthesizedServerBuilderReceiver() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.ServerBuilder

            fun main() {
                val sb: ServerBuilder = Server.builder()
                (sb).service("/api", HelloService())
                sb.build()
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

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute!!.target)
    }
}
