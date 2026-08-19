package com.linecorp.intellij.plugins.armeria.client

import com.intellij.microservices.endpoints.ModuleEndpointsFilter
import com.intellij.microservices.endpoints.presentation.HttpMethodPresentation
import com.intellij.microservices.url.UrlPath
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaClientHttpRequestSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaHttpRequestGenerator
import com.linecorp.intellij.plugins.armeria.test.ArmeriaClientFixtureTestBase
import com.linecorp.intellij.plugins.armeria.test.registerArmeriaAnnotationStubs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArmeriaClientInvocationCollectorTest : ArmeriaClientFixtureTestBase() {
    fun testCollectRestClientGetCallSite() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.RestClient;

            public class Main {
                public static void main(String[] args) {
                    RestClient.of("https://example.com").get("/users/{id}");
                }
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)
        assertEquals(2, endpoints.size)
        val factory = endpoints.single { !it.isCallSite }
        val callSite = endpoints.single { it.isCallSite }
        assertEquals("https://example.com", factory.uri)
        assertEquals("GET", callSite.httpMethod)
        assertEquals("/users/{id}", callSite.requestPath)
        assertEquals("https://example.com", callSite.uri)
        assertEquals("RestClient", callSite.clientType)
    }

    fun testCollectRestClientGetFromVariable() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.RestClient;

            public class Main {
                public static void main(String[] args) {
                    RestClient client = RestClient.of("https://api.example.com");
                    client.get("/users/{id}");
                }
            }
            """.trimIndent(),
        )

        val callSite = ArmeriaClientCollector.collect(project).single { it.isCallSite }
        assertEquals("GET", callSite.httpMethod)
        assertEquals("/users/{id}", callSite.requestPath)
        assertEquals("https://api.example.com", callSite.uri)
    }

    fun testCollectPathConstant() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.RestClient;

            public class Main {
                private static final String USERS = "/users/{id}";

                public static void main(String[] args) {
                    RestClient.of("https://example.com").get(USERS);
                }
            }
            """.trimIndent(),
        )

        val callSite = ArmeriaClientCollector.collect(project).single { it.isCallSite }
        assertEquals("/users/{id}", callSite.requestPath)
    }

    fun testCollectPostContentAndHeader() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.RestClient;
            import com.linecorp.armeria.common.MediaType;

            public class Main {
                public static void main(String[] args) {
                    RestClient.of("https://example.com")
                            .post("/upload")
                            .header("Authorization", "Bearer token")
                            .content(MediaType.JSON, "{\"foo\":\"bar\"}")
                            .execute();
                }
            }
            """.trimIndent(),
        )

        val callSite = ArmeriaClientCollector.collect(project).single { it.isCallSite }
        assertEquals("POST", callSite.httpMethod)
        assertEquals("/upload", callSite.requestPath)
        assertEquals("application/json", callSite.contentType)
        assertEquals("{\"foo\":\"bar\"}", callSite.requestBody)
        assertTrue(callSite.requestHeaders.contains("Authorization: Bearer token"))
    }

    fun testCollectWebClientExecuteRequestHeaders() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;
            import com.linecorp.armeria.common.HttpHeaderNames;
            import com.linecorp.armeria.common.HttpMethod;
            import com.linecorp.armeria.common.RequestHeaders;

            public class Main {
                public static void main(String[] args) {
                    WebClient.of("https://example.com")
                            .execute(RequestHeaders.of(HttpMethod.GET, "/foo/bar.json",
                                    HttpHeaderNames.ACCEPT, "application/json"));
                }
            }
            """.trimIndent(),
        )

        val callSite = ArmeriaClientCollector.collect(project).single { it.isCallSite }
        assertEquals("GET", callSite.httpMethod)
        assertEquals("/foo/bar.json", callSite.requestPath)
        assertTrue(callSite.requestHeaders.contains("Accept: application/json"))
    }

    fun testDoesNotCollectMapGet() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import java.util.Collections;
            import java.util.Map;

            public class Main {
                public static void main(String[] args) {
                    Map<String, String> map = Collections.emptyMap();
                    map.get("/users");
                }
            }
            """.trimIndent(),
        )

        assertTrue(ArmeriaClientCollector.collect(project).none { it.isCallSite })
    }

    fun testFactoryWithoutCallSitesStillCollected() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.RestClient;

            public class Main {
                public static void main(String[] args) {
                    RestClient.of("https://example.com/hello");
                }
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)
        assertEquals(1, endpoints.size)
        assertFalse(endpoints.single().isCallSite)
    }

    fun testCallSiteAppearsInEndpointsWithMethodAndPath() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.RestClient;

            public class Main {
                public static void main(String[] args) {
                    RestClient.of("https://api.example.com").get("/users/{id}");
                }
            }
            """.trimIndent(),
        )

        val provider = ArmeriaClientEndpointsProvider()
        val group = provider.getEndpointGroups(project, ModuleEndpointsFilter(module, true, true)).single()
        val callSite = provider.getEndpoints(group).single { it.isCallSite }
        val presentation = provider.getEndpointPresentation(group, callSite) as HttpMethodPresentation
        assertEquals("/users/{id}", presentation.presentableText)
        assertEquals(listOf("GET"), presentation.endpointMethods)
        val target = provider.getUrlTargetInfo(group, callSite).single()
        assertEquals(setOf("GET"), target.methods)
        assertEquals(
            listOf(
                UrlPath.PathSegment.Exact(""),
                UrlPath.PathSegment.Exact("users"),
                UrlPath.PathSegment.Variable("id"),
            ),
            target.path.segments,
        )
    }

    fun testCallSiteMatchesAnnotatedRouteByRequestPath() {
        myFixture.registerArmeriaAnnotationStubs()
        myFixture.addFileToProject(
            "src/Service.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class Service {
                @Get("/users/{id}")
                public String user() {
                    return "user";
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
                    RestClient.of("https://example.com").get("/users/{id}");
                }
            }
            """.trimIndent(),
        )

        val callSite = ArmeriaClientCollector.collect(project).single { it.isCallSite }
        val route = ArmeriaRouteCollector.collect(project).single { it.path == "/users/{id}" }
        assertTrue(ArmeriaClientRouteLinkSupport.matches(callSite, route))
        assertEquals(route.path, ArmeriaClientRouteLinkSupport.matchingRoutes(project, callSite).single().path)
    }

    fun testGenerateHttpRequestFromGetCallSite() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.RestClient;

            public class Main {
                public static void main(String[] args) {
                    RestClient.of("https://api.example.com").get("/users/{id}");
                }
            }
            """.trimIndent(),
        )

        val callSite = ArmeriaClientCollector.collect(project).single { it.isCallSite }
        assertTrue(ArmeriaClientHttpRequestSupport.supports(callSite))
        val route = assertNotNull(ArmeriaClientHttpRequestSupport.toRoute(callSite))
        val text =
            ArmeriaHttpRequestGenerator.requestText(
                route,
                ArmeriaClientHttpRequestSupport.baseUrl(callSite),
            )
        assertTrue(text.contains("GET https://api.example.com/users/{id}"))
        assertEquals("armeria-get-users--id-.http", ArmeriaHttpRequestGenerator.fileName(route))
    }

    fun testZkFactoryCallSiteUsesRequestPathNotConnectionString() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;
            import com.linecorp.armeria.client.endpoint.zookeeper.ZooKeeperEndpointGroup;
            import com.linecorp.armeria.common.SessionProtocol;

            public class Main {
                public static void main(String[] args) {
                    WebClient.builder(SessionProtocol.HTTP, ZooKeeperEndpointGroup.of("zk://zk.example.com/armeria"))
                            .build()
                            .get("/users");
                }
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)
        val callSite = endpoints.single { it.isCallSite }
        assertEquals("/users", callSite.requestPath)
        assertTrue(ArmeriaClientEndpointsSupport.isVisible(callSite))
        assertFalse(
            ArmeriaClientRouteLinkSupport.matches(
                clientType = callSite.clientType,
                uri = callSite.uri,
                routeProtocol = "HTTP",
                routePath = "/armeria",
                requestPath = callSite.requestPath,
                httpMethod = callSite.httpMethod,
            ),
        )
    }
}
