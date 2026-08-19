package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteAnalysisCollector
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceSupport
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertEquals

class ArmeriaDocServiceExampleCollectorKotlinTest : ArmeriaFixtureTestBase() {
    fun testKotlinExampleRequestsAttachToAnnotatedRoute() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.common.HttpHeaders
            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.docs.DocService

            class HelloService {
                @Get("/hello")
                fun hello(): String = "ok"
            }

            fun main() {
                Server.builder()
                    .annotatedService(HelloService())
                    .service(
                        "/docs",
                        DocService.builder()
                            .exampleHeaders(HelloService::class.java, HttpHeaders.of("authorization", "bearer-token"))
                            .exampleRequests(HelloService::class.java, "hello", "{\"name\":\"Armeria\"}")
                            .build(),
                    )
                    .build()
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
    }
}
