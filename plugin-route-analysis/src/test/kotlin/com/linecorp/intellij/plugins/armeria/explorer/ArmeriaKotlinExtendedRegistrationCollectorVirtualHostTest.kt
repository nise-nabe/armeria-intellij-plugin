package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaKotlinExtendedRegistrationCollectorVirtualHostTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerKotlinExtendedRegistrationCollectorStubs()
    }

    fun testCollectKotlinChainedVirtualHostDoesNotAnnotatePrecedingServiceRoute() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service("/api", ApiService())
                    .virtualHost("api.example.com")
                    .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class ApiService {}")

        val routes = ArmeriaRouteCollector.collect(project)
        val serviceRoute = routes.firstOrNull { it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("/api", serviceRoute!!.path)
        assertEquals("", serviceRoute.virtualHostName)
    }

    fun testCollectKotlinVirtualHostThenServiceAnnotatesServiceRoute() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .virtualHost("api.example.com")
                    .service("/api", ApiService())
                    .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class ApiService {}")

        val routes = ArmeriaRouteCollector.collect(project)
        val serviceRoute = routes.firstOrNull { it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("/api", serviceRoute!!.path)
        assertEquals("api.example.com", serviceRoute.virtualHostName)
    }

    fun testCollectKotlinNestedVirtualHostLambdaUsesInnerHostname() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .virtualHost("outer.example.com") { outer ->
                        outer.virtualHost("inner.example.com") { inner ->
                            inner.service("/api", ApiService())
                        }
                    }
                    .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class ApiService {}")

        val routes = ArmeriaRouteCollector.collect(project)
        val serviceRoute = routes.firstOrNull { it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("/api", serviceRoute!!.path)
        assertEquals("inner.example.com", serviceRoute.virtualHostName)
    }

    fun testCollectKotlinVirtualHostRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .virtualHost("api.example.com")
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val virtualHostRoute = routes.firstOrNull { it.routeMatch == RouteMatch.VIRTUAL_HOST }
        assertNotNull(virtualHostRoute)
        assertEquals("api.example.com", virtualHostRoute!!.virtualHostName)
    }

    fun testCollectKotlinVirtualHostLambdaAnnotatesFileService() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import java.io.File

            fun main() {
                Server.builder()
                    .virtualHost("api.example.com") { sb ->
                        sb.fileService("/files/", File("/tmp"))
                    }
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val fileRoute = routes.firstOrNull { it.routeMatch == RouteMatch.FILE_SERVICE }
        assertNotNull(fileRoute)
        assertEquals("/files/", fileRoute!!.path)
        assertEquals("api.example.com", fileRoute.virtualHostName)
    }
}
