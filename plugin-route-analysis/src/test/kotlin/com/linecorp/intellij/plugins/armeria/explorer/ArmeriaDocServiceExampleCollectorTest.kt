package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteAnalysisCollector
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceSupport
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaDocServiceExampleCollectorTest : ArmeriaFixtureTestBase() {
    fun testExampleRequestsAndHeadersAttachToAnnotatedRoute() {
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.common.HttpHeaders;
            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.docs.DocService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                            .annotatedService(new HelloService())
                            .service(
                                    "/docs",
                                    DocService.builder()
                                            .exampleHeaders(
                                                    HelloService.class,
                                                    HttpHeaders.of("authorization", "bearer-token"))
                                            .exampleRequests(HelloService.class, "hello", "{\"name\":\"Armeria\"}")
                                            .build())
                            .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteAnalysisCollector.collect(project)
        val hello = routes.single { it.path == "/hello" && it.httpMethod == "GET" }

        assertEquals(listOf("{\"name\":\"Armeria\"}"), hello.exampleRequests)
        assertEquals(listOf("authorization: bearer-token"), hello.exampleHeaders)
        assertEquals(
            "http://localhost:8080/docs/#/methods/example.HelloService/hello",
            ArmeriaDocServiceSupport.debugFormUrl(hello, routes),
        )
        assertTrue(routes.any { it.isDocService && it.path == "/docs" })
    }

    fun testExampleRequestsMatchByServiceNameString() {
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.annotation.Post;

            public class ItemService {
                @Post("/items")
                public String create() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.docs.DocService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                            .service(
                                    "/docs",
                                    DocService.builder()
                                            .exampleRequests(
                                                    "example.ItemService",
                                                    "create",
                                                    "{\"sku\":\"a1\"}")
                                            .build())
                            .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteAnalysisCollector.collect(project)
        val create = routes.single { it.path == "/items" }

        assertEquals(listOf("{\"sku\":\"a1\"}"), create.exampleRequests)
    }
}
