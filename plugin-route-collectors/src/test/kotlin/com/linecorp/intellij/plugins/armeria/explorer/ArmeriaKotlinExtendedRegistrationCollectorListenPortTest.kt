package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull as kotlinAssertNotNull

class ArmeriaKotlinExtendedRegistrationCollectorListenPortTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerKotlinExtendedRegistrationCollectorStubs()
    }

    fun testCollectKotlinHttpListenPort() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .http(8080)
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val port = routes.firstOrNull { it.routeMatch == RouteMatch.LISTEN_PORT }
        kotlinAssertNotNull(port)
        assertEquals(":8080", port.path)
        assertEquals("HTTP", port.protocol)
    }

    fun testCollectKotlinUnifiedHttpAndHttpsPort() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.common.SessionProtocol
            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .port(8888, SessionProtocol.HTTP, SessionProtocol.HTTPS)
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val port = routes.firstOrNull { it.routeMatch == RouteMatch.LISTEN_PORT }
        kotlinAssertNotNull(port)
        assertEquals(":8888", port.path)
        assertEquals("HTTP+HTTPS", port.protocol)
    }

    fun testCollectKotlinProxyUnificationPort() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.common.SessionProtocol
            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .port(9999, SessionProtocol.PROXY, SessionProtocol.HTTP, SessionProtocol.HTTPS)
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val port = routes.firstOrNull { it.routeMatch == RouteMatch.LISTEN_PORT }
        kotlinAssertNotNull(port)
        assertEquals(":9999", port.path)
        assertEquals("PROXY+HTTP+HTTPS", port.protocol)
    }

    fun testIgnoreNonArmeriaKotlinHttpCalls() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            fun main() {
                OtherBuilder.builder()
                    .http(8080)
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.LISTEN_PORT })
    }
}
