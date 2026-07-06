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
    fun parse_readsEscapedExamplePaths() {
        val json = """
            {
              "services": [
                {
                  "name": "com.example.DemoService",
                  "methods": [
                    {
                      "name": "quoted",
                      "httpMethod": "GET",
                      "examplePaths": ["/path/with\\backslash", "/plain"]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = ArmeriaDocServiceSpecificationParser.parse(json)

        assertEquals(
            setOf("GET /path/with\\backslash", "GET /plain"),
            parsed.routes.map { "${it.httpMethod} ${it.path}" }.toSet(),
        )
    }

    @Test
    fun parse_keepsDistinctRoutesWithSameMethodAndPath() {
        val json = """
            {
              "services": [
                {
                  "name": "com.example.FooService",
                  "methods": [
                    {
                      "name": "getUser",
                      "httpMethod": "GET",
                      "endpoints": [
                        { "pathMapping": "/api/users/{id}" }
                      ]
                    }
                  ]
                },
                {
                  "name": "com.example.BarService",
                  "methods": [
                    {
                      "name": "lookupUser",
                      "httpMethod": "GET",
                      "endpoints": [
                        { "pathMapping": "/api/users/{id}" }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = ArmeriaDocServiceSpecificationParser.parse(json)

        assertEquals(2, parsed.routes.size)
        assertEquals(
            setOf("com.example.FooService/getUser", "com.example.BarService/lookupUser"),
            parsed.routes.map { "${it.serviceName}/${it.methodName}" }.toSet(),
        )
    }

    @Test
    fun parse_readsJsonEscapeSequencesInStrings() {
        val json = """
            {
              "services": [
                {
                  "name": "com.example.DemoService",
                  "methods": [
                    {
                      "name": "escaped",
                      "httpMethod": "GET",
                      "examplePaths": ["/line\nbreak", "/tab\there", "/quote\"path"]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = ArmeriaDocServiceSpecificationParser.parse(json)

        assertEquals(
            setOf("GET /line\nbreak", "GET /tab\there", "GET /quote\"path"),
            parsed.routes.map { "${it.httpMethod} ${it.path}" }.toSet(),
        )
    }

    @Test
    fun parse_readsUnicodeEscapeSequenceInStrings() {
        val jsonUnicodePath = "/unicode" + '\\' + "u0041"
        val json = """
            {
              "services": [
                {
                  "name": "com.example.DemoService",
                  "methods": [
                    {
                      "name": "escaped",
                      "httpMethod": "GET",
                      "examplePaths": ["$jsonUnicodePath"]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = ArmeriaDocServiceSpecificationParser.parse(json)

        assertEquals(
            setOf("GET /unicodeA"),
            parsed.routes.map { "${it.httpMethod} ${it.path}" }.toSet(),
        )
    }

    @Test
    fun parse_readsServiceNameWhenMethodsAppearBeforeNameField() {
        val json = """
            {
              "services": [
                {
                  "methods": [
                    {
                      "name": "getUser",
                      "httpMethod": "GET",
                      "endpoints": [
                        { "pathMapping": "/api/users/{id}" }
                      ]
                    }
                  ],
                  "name": "com.example.FooService"
                }
              ]
            }
        """.trimIndent()

        val parsed = ArmeriaDocServiceSpecificationParser.parse(json)

        assertEquals(1, parsed.routes.size)
        assertEquals("com.example.FooService", parsed.routes.single().serviceName)
        assertEquals("getUser", parsed.routes.single().methodName)
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
