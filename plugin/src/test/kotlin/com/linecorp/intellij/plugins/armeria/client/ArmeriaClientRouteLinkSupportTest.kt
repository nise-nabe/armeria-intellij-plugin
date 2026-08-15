package com.linecorp.intellij.plugins.armeria.client

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaClientFixtureTestBase
import com.linecorp.intellij.plugins.armeria.test.registerArmeriaAnnotationStubs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaClientRouteLinkSupportTest : ArmeriaClientFixtureTestBase() {
    override fun setUp() {
        super.setUp()
        myFixture.registerArmeriaAnnotationStubs()
    }

    fun testParseHttpsUriPathAndHost() {
        val parts = ArmeriaClientRouteLinkSupport.parseClientUri("https://example.com/hello")

        assertEquals("example.com", parts.host)
        assertEquals("/hello", parts.path)
    }

    fun testParseOriginOnlyUri() {
        val parts = ArmeriaClientRouteLinkSupport.parseClientUri("https://example.com")

        assertEquals("example.com", parts.host)
        assertEquals("/", parts.path)
    }

    fun testParseHostnameWithoutScheme() {
        val parts = ArmeriaClientRouteLinkSupport.parseClientUri("k8s.default.svc.cluster.local.")

        assertEquals("k8s.default.svc.cluster.local", parts.host)
        assertEquals("/", parts.path)
    }

    fun testPathsOverlapExactAndPrefix() {
        assertTrue(ArmeriaClientRouteLinkSupport.pathsOverlap("/hello", "/hello"))
        assertTrue(ArmeriaClientRouteLinkSupport.pathsOverlap("/api", "/api/users"))
        assertTrue(ArmeriaClientRouteLinkSupport.pathsOverlap("/api/users", "/api"))
        assertFalse(ArmeriaClientRouteLinkSupport.pathsOverlap("/", "/hello"))
        assertFalse(ArmeriaClientRouteLinkSupport.pathsOverlap("/hello", "/world"))
    }

    fun testOriginOnlyUriDoesNotMatchRoutePath() {
        assertFalse(
            ArmeriaClientRouteLinkSupport.matches(
                clientType = "HTTP",
                uri = "https://example.com",
                routeProtocol = "HTTP",
                routePath = "/hello",
            ),
        )
    }

    fun testRestClientUriMatchesAnnotatedPath() {
        assertTrue(
            ArmeriaClientRouteLinkSupport.matches(
                clientType = "RestClient",
                uri = "https://example.com/hello",
                routeProtocol = "HTTP",
                routePath = "/hello",
            ),
        )
    }

    fun testGrpcClientDoesNotMatchHttpRoute() {
        assertFalse(
            ArmeriaClientRouteLinkSupport.matches(
                clientType = "gRPC",
                uri = "https://example.com/hello",
                routeProtocol = "HTTP",
                routePath = "/hello",
            ),
        )
    }

    fun testDecoratorRouteIsNotMatchable() {
        assertFalse(
            ArmeriaClientRouteLinkSupport.matches(
                clientType = "HTTP",
                uri = "https://example.com/hello",
                routeProtocol = "HTTP",
                routePath = "/hello",
                routeMatch = RouteMatch.ROUTE_DECORATOR,
            ),
        )
    }

    fun testCollectedClientMatchesCollectedRoute() {
        myFixture.addFileToProject(
            "src/Service.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class Service {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Client.java",
            """
            package example;

            import com.linecorp.armeria.client.RestClient;

            public class Client {
                public static void main(String[] args) {
                    RestClient.of("https://example.com/hello");
                }
            }
            """.trimIndent(),
        )

        val endpoint = ArmeriaClientCollector.collect(project).single()
        val route = ArmeriaRouteCollector.collect(project).single { it.path == "/hello" }

        assertTrue(ArmeriaClientRouteLinkSupport.matches(endpoint, route))
        assertEquals(route.path, ArmeriaClientRouteLinkSupport.matchingRoutes(project, endpoint).single().path)
        assertEquals(endpoint.uri, ArmeriaClientRouteLinkSupport.matchingClients(project, route).single().uri)
    }
}
