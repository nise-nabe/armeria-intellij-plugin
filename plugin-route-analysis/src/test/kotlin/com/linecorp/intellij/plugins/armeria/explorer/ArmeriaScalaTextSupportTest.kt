package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaScalaTextSupportTest : LightJavaCodeInsightFixtureTestCase() {
    fun testFindServiceRegistrationFromBuilderChain() {
        val matches = ArmeriaScalaTextSupport.findServiceRegistrations(
            """
            import com.linecorp.armeria.server.Server

            object Main {
              Server.builder()
                .service("/api", new HelloService())
                .build()
            }
            """.trimIndent(),
        )

        assertEquals(1, matches.size)
        val match = matches.single()
        assertEquals("service", match.methodName)
        assertEquals("/api", match.path)
        assertEquals("new HelloService()", match.targetText)
        assertEquals("HelloService", ArmeriaScalaTextSupport.renderScalaTarget(match.targetText))
    }

    fun testFindAnnotatedServiceWithPathPrefix() {
        val matches = ArmeriaScalaTextSupport.findServiceRegistrations(
            """
            import com.linecorp.armeria.server.Server

            Server.builder()
              .annotatedService("/prefix", new AnnotatedService())
              .build()
            """.trimIndent(),
        )

        assertEquals(1, matches.size)
        val match = matches.single()
        assertEquals("annotatedService", match.methodName)
        assertEquals("/prefix", match.path)
        assertEquals("AnnotatedService", ArmeriaScalaTextSupport.renderScalaTarget(match.targetText))
    }

    fun testFindAnnotatedServiceWithoutPathPrefix() {
        val matches = ArmeriaScalaTextSupport.findServiceRegistrations(
            """
            import com.linecorp.armeria.server.Server

            Server.builder()
              .annotatedService(new AnnotatedService())
              .build()
            """.trimIndent(),
        )

        assertEquals(1, matches.size)
        val match = matches.single()
        assertEquals("annotatedService", match.methodName)
        assertEquals("/", match.path)
    }

    fun testFindWebClientEndpoint() {
        val endpoints = ArmeriaScalaTextSupport.findClientEndpoints(
            """
            import com.linecorp.armeria.client.WebClient

            object Main {
              val client = WebClient.of("https://example.com")
            }
            """.trimIndent(),
        )

        assertEquals(1, endpoints.size)
        val endpoint = endpoints.single()
        assertEquals("WebClient", endpoint.clientSimpleName)
        assertEquals("https://example.com", endpoint.uri)
    }
}
