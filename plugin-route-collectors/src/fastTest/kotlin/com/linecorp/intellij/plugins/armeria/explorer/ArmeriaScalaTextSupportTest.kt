package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaScalaTextSupport
import org.junit.Assert.assertEquals
import org.junit.Test

class ArmeriaScalaTextSupportTest {
    @Test
    fun findServiceRegistrationFromBuilderChain() {
        val matches =
            ArmeriaScalaTextSupport.findServiceRegistrations(
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

    @Test
    fun findAnnotatedServiceWithPathPrefix() {
        val matches =
            ArmeriaScalaTextSupport.findServiceRegistrations(
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

    @Test
    fun findAnnotatedServiceWithoutPathPrefix() {
        val matches =
            ArmeriaScalaTextSupport.findServiceRegistrations(
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

    @Test
    fun findServiceUnderWithPositionalArguments() {
        val matches =
            ArmeriaScalaTextSupport.findServiceRegistrations(
                """
                import com.linecorp.armeria.server.Server

                Server.builder()
                  .serviceUnder("/api", new ApiService())
                  .build()
                """.trimIndent(),
            )

        assertEquals(1, matches.size)
        val match = matches.single()
        assertEquals("serviceUnder", match.methodName)
        assertEquals("/api", match.path)
        assertEquals("ApiService", ArmeriaScalaTextSupport.renderScalaTarget(match.targetText))
    }

    @Test
    fun findServiceUnderWithNamedArguments() {
        val matches =
            ArmeriaScalaTextSupport.findServiceRegistrations(
                """
                import com.linecorp.armeria.server.Server

                Server.builder()
                  .serviceUnder(pathPrefix = "/v1", service = new HelloService())
                  .build()
                """.trimIndent(),
            )

        assertEquals(1, matches.size)
        val match = matches.single()
        assertEquals("serviceUnder", match.methodName)
        assertEquals("/v1", match.path)
        assertEquals("HelloService", ArmeriaScalaTextSupport.renderScalaTarget(match.targetText))
    }

    @Test
    fun findWebClientEndpoint() {
        val endpoints =
            ArmeriaScalaTextSupport.findClientEndpoints(
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
