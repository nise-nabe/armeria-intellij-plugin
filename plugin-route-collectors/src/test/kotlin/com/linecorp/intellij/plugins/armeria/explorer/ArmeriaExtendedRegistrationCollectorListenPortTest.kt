package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull as kotlinAssertNotNull

class ArmeriaExtendedRegistrationCollectorListenPortTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerExtendedRegistrationCollectorStubs()
    }

    fun testCollectHttpListenPort() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .http(8080)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val port = routes.firstOrNull { it.routeMatch == RouteMatch.LISTEN_PORT }
        kotlinAssertNotNull(port)
        assertEquals(":8080", port.path)
        assertEquals("HTTP", port.protocol)
    }

    fun testCollectHttpsListenPort() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .https(8443)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val port = routes.firstOrNull { it.routeMatch == RouteMatch.LISTEN_PORT }
        kotlinAssertNotNull(port)
        assertEquals(":8443", port.path)
        assertEquals("HTTPS", port.protocol)
    }

    fun testCollectUnifiedHttpAndHttpsPort() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.common.SessionProtocol;
            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .port(8888, SessionProtocol.HTTP, SessionProtocol.HTTPS)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val port = routes.firstOrNull { it.routeMatch == RouteMatch.LISTEN_PORT }
        kotlinAssertNotNull(port)
        assertEquals(":8888", port.path)
        assertEquals("HTTP+HTTPS", port.protocol)
    }

    fun testCollectProxyUnificationPort() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.common.SessionProtocol;
            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .port(9999, SessionProtocol.PROXY, SessionProtocol.HTTP, SessionProtocol.HTTPS)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val port = routes.firstOrNull { it.routeMatch == RouteMatch.LISTEN_PORT }
        kotlinAssertNotNull(port)
        assertEquals(":9999", port.path)
        assertEquals("PROXY+HTTP+HTTPS", port.protocol)
    }

    fun testCollectH1PortAsHttps() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.common.SessionProtocol;
            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .port(8443, SessionProtocol.H1)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val port = routes.firstOrNull { it.routeMatch == RouteMatch.LISTEN_PORT }
        kotlinAssertNotNull(port)
        assertEquals(":8443", port.path)
        assertEquals("HTTPS", port.protocol)
    }

    fun testCollectHttpConstantPort() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                private static final int PORT = 8080;

                public static void main(String[] args) {
                    Server.builder()
                        .http(PORT)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val port = routes.firstOrNull { it.routeMatch == RouteMatch.LISTEN_PORT }
        kotlinAssertNotNull(port)
        assertEquals(":8080", port.path)
    }

    fun testIgnoreNonArmeriaHttpCalls() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            public class Main {
                public static void main(String[] args) {
                    OtherBuilder.builder()
                        .http(8080)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.LISTEN_PORT })
    }

    fun testCollectPortWithoutProtocolsDefaultsToHttp() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .port(8080)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val port = routes.firstOrNull { it.routeMatch == RouteMatch.LISTEN_PORT }
        kotlinAssertNotNull(port)
        assertEquals(":8080", port.path)
        assertEquals("HTTP", port.protocol)
    }

    fun testOmitUnresolvedProtocolArguments() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .port(8080, UNKNOWN)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.LISTEN_PORT })
    }

    fun testOmitSessionProtocolOfCall() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.common.SessionProtocol;
            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .port(8080, SessionProtocol.of("http"))
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.LISTEN_PORT })
    }

    fun testOmitUnresolvedPortArgument() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    int unknown = Integer.parseInt(args[0]);
                    Server.builder()
                        .http(unknown)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.LISTEN_PORT })
    }
}
