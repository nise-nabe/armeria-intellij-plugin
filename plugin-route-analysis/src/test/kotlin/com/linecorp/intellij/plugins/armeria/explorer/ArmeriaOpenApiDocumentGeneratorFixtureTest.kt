package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaOpenApiDocumentGenerator
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaOpenApiDocumentGeneratorFixtureTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerContentAnnotationStubs()
    }

    fun testGetUsersIdProducesJsonExportsPathParameterAndJsonResponse() {
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.ProducesJson;

            public class UserService {
                @Get("/users/{id}")
                @ProducesJson
                public String getUser() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val yaml = ArmeriaOpenApiDocumentGenerator.document(collectRoutes())

        assertTrue(yaml.contains("\"/users/{id}\""), yaml)
        assertTrue(yaml.contains("get:"), yaml)
        assertTrue(yaml.contains("name: \"id\""), yaml)
        assertTrue(yaml.contains("in: \"path\""), yaml)
        assertTrue(yaml.contains("\"application/json\":"), yaml)
        assertTrue(yaml.contains("type: \"object\""), yaml)
        assertFalse(yaml.contains("Omitted"), yaml)
    }

    fun testPostConsumesJsonExportsRequestBody() {
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

        val yaml = ArmeriaOpenApiDocumentGenerator.document(collectRoutes())

        assertTrue(yaml.contains("\"/items/{id}\""), yaml)
        assertTrue(yaml.contains("post:"), yaml)
        assertTrue(yaml.contains("requestBody:"), yaml)
        assertTrue(yaml.contains("name: \"id\""), yaml)
        assertTrue(yaml.contains("\"application/json\":"), yaml)
    }
}
