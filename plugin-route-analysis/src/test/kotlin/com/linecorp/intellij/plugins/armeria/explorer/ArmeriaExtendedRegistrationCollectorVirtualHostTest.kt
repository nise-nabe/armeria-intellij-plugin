package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaExtendedRegistrationCollectorVirtualHostTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerExtendedRegistrationCollectorStubs()
    }

    fun testCollectVirtualHostRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .virtualHost("api.example.com")
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val virtualHostRoute = routes.firstOrNull { it.routeMatch == RouteMatch.VIRTUAL_HOST }
        assertNotNull(virtualHostRoute)
        assertEquals("api.example.com", virtualHostRoute!!.target)
        assertEquals("api.example.com", virtualHostRoute.virtualHostName)
    }

    fun testCollectChainedVirtualHostDoesNotAnnotatePrecedingServiceRoute() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/api", new ApiService())
                        .virtualHost("api.example.com")
                        .build();
                }
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

    fun testCollectVirtualHostThenServiceAnnotatesServiceRoute() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .virtualHost("api.example.com")
                        .service("/api", new ApiService())
                        .build();
                }
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

    fun testCollectVirtualHostChainAnnotatesOnlyPostVirtualHostService() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/default", new ApiService())
                        .virtualHost("api.example.com")
                        .service("/hosted", new ApiService())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class ApiService {}")

        val routes = ArmeriaRouteCollector.collect(project)
        val defaultRoute = routes.firstOrNull { it.path == "/default" }
        val hostedRoute = routes.firstOrNull { it.path == "/hosted" }
        assertNotNull(defaultRoute)
        assertNotNull(hostedRoute)
        assertEquals("", defaultRoute!!.virtualHostName)
        assertEquals("api.example.com", hostedRoute!!.virtualHostName)
    }

    fun testCollectNestedVirtualHostLambdaUsesInnerHostname() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .virtualHost("outer.example.com", outer -> outer
                            .virtualHost("inner.example.com", inner -> inner
                                .service("/api", new ApiService())))
                        .build();
                }
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

    fun testCollectVirtualHostLambdaAnnotatesFileService() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import java.io.File;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .virtualHost("api.example.com", sb -> sb.fileService("/files/", new File("/tmp")))
                        .build();
                }
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
