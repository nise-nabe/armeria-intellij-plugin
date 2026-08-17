package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaHttpRequestGenerator
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertTrue

class ArmeriaHttpRequestGeneratorFixtureTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerContentAnnotationStubs()
    }

    fun testPostConsumesJsonGeneratesJsonHttpRequest() {
        myFixture.configureByText(
            "ItemService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.ConsumesJson;
            import com.linecorp.armeria.server.annotation.Post;
            import com.linecorp.armeria.server.annotation.ProducesJson;

            public class ItemService {
                @Post("/items/{id}")
                @ConsumesJson
                @ProducesJson
                public String create() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val route = ArmeriaRouteCollector.collect(project).single()
        val text = ArmeriaHttpRequestGenerator.requestText(route)

        assertTrue(text.contains("POST http://localhost:8080/items/{id}"), text)
        assertTrue(text.contains("Content-Type: application/json"), text)
        assertTrue(text.contains("Accept: application/json"), text)
        assertTrue(text.contains("{}"), text)
    }

    fun testClassLevelConsumesJsonGeneratesJsonHttpRequest() {
        myFixture.configureByText(
            "ItemService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.ConsumesJson;
            import com.linecorp.armeria.server.annotation.Post;
            import com.linecorp.armeria.server.annotation.ProducesJson;

            @ConsumesJson
            @ProducesJson
            public class ItemService {
                @Post("/items/{id}")
                public String create() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val route = ArmeriaRouteCollector.collect(project).single()
        val text = ArmeriaHttpRequestGenerator.requestText(route)

        assertTrue(text.contains("POST http://localhost:8080/items/{id}"), text)
        assertTrue(text.contains("Content-Type: application/json"), text)
        assertTrue(text.contains("{}"), text)
    }

    fun testDescriptionBecomesHttpComment() {
        myFixture.configureByText(
            "GreetService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Description;
            import com.linecorp.armeria.server.annotation.Get;

            public class GreetService {
                @Get("/hello")
                @Description("Returns a greeting.")
                public String greet() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val route = ArmeriaRouteCollector.collect(project).single()
        val text = ArmeriaHttpRequestGenerator.requestText(route)
        assertTrue(text.contains("# Returns a greeting."), text)
        assertTrue(text.contains("GET http://localhost:8080/hello"), text)
    }
}
