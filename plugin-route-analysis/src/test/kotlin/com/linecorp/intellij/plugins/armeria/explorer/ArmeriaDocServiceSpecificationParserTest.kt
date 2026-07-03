package com.linecorp.intellij.plugins.armeria.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmeriaDocServiceSpecificationParserTest {
    @Test
    fun parse_extractsEndpointPathMappingsAndHttpMethods() {
        val json = javaClass.getResourceAsStream("/doc-service-specification.json")!!.reader().readText()

        val parsed = ArmeriaDocServiceSpecificationParser.parse(json)

        assertEquals("/docs", parsed.docServiceMountPath)
        assertEquals(
            setOf(
                "GET /api/users/{id}",
                "POST /api/users",
                "POST /armeria.grpc.testing.TestService/UnaryCall",
            ),
            parsed.routes.map { "${it.httpMethod} ${it.path}" }.toSet(),
        )
    }

    @Test
    fun parse_usesExamplePathsWhenEndpointsMissing() {
        val json = """
            {
              "services": [
                {
                  "name": "com.example.DemoService",
                  "methods": [
                    {
                      "name": "health",
                      "httpMethod": "GET",
                      "examplePaths": ["/health", "/ready"]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = ArmeriaDocServiceSpecificationParser.parse(json)

        assertEquals(
            setOf("GET /health", "GET /ready"),
            parsed.routes.map { "${it.httpMethod} ${it.path}" }.toSet(),
        )
    }

    @Test
    fun parse_ignoresOpenApiStylePathFields() {
        val json = """
            {
              "services": [],
              "paths": {
                "/legacy": {
                  "get": {}
                }
              }
            }
        """.trimIndent()

        val parsed = ArmeriaDocServiceSpecificationParser.parse(json)

        assertTrue(parsed.routes.isEmpty())
    }
}
