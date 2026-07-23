package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaScalaTextSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertEquals(2, match.argumentCount)
        assertEquals("HelloService", ArmeriaScalaTextSupport.renderScalaTarget(match.targetText))
        assertFalse(
            ArmeriaScalaTextSupport.isUnresolvedScalaTarget(
                match.targetText,
                ArmeriaScalaTextSupport.renderScalaTarget(match.targetText),
            ),
        )
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
        assertEquals(2, match.argumentCount)
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
        assertEquals(1, match.argumentCount)
    }

    @Test
    fun findAnnotatedServiceWithRootPathPrefixKeepsTwoArguments() {
        val matches =
            ArmeriaScalaTextSupport.findServiceRegistrations(
                """
                import com.linecorp.armeria.server.Server

                Server.builder()
                  .annotatedService("/", new AnnotatedService())
                  .build()
                """.trimIndent(),
            )

        assertEquals(1, matches.size)
        val match = matches.single()
        assertEquals("annotatedService", match.methodName)
        assertEquals("/", match.path)
        assertEquals(2, match.argumentCount)
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
    fun findServiceUnderWithNamedArgumentsServiceFirst() {
        val matches =
            ArmeriaScalaTextSupport.findServiceRegistrations(
                """
                import com.linecorp.armeria.server.Server

                Server.builder()
                  .serviceUnder(service = new HelloService(), pathPrefix = "/v1")
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
    fun ignoresRegistrationsInsideLineComments() {
        val matches =
            ArmeriaScalaTextSupport.findServiceRegistrations(
                """
                import com.linecorp.armeria.server.Server

                object Main {
                  Server.builder()
                    // .service("/api", new HelloService())
                    .build()
                }
                """.trimIndent(),
            )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun keepsRegistrationWhenCommentMarkersAppearInsideStrings() {
        val matches =
            ArmeriaScalaTextSupport.findServiceRegistrations(
                """
                import com.linecorp.armeria.server.Server

                object Main {
                  val marker = "/*"
                  Server.builder()
                    .service("/api", new HelloService())
                    .build()
                  val end = "*/"
                }
                """.trimIndent(),
            )

        assertEquals(1, matches.size)
        assertEquals("/api", matches.single().path)
    }

    @Test
    fun keepsRegistrationWhenCommentMarkersAppearInsideTripleQuotedStrings() {
        val matches =
            ArmeriaScalaTextSupport.findServiceRegistrations(
                """
                import com.linecorp.armeria.server.Server

                object Main {
                  val docs = ${"\"\"\""}
                    // not a real comment
                    /* also not */
                  ${"\"\"\""}
                  Server.builder()
                    .service("/api", new HelloService())
                    .build()
                }
                """.trimIndent(),
            )

        assertEquals(1, matches.size)
        assertEquals("/api", matches.single().path)
    }

    @Test
    fun keepsRegistrationWhenSlashAndStarAppearInCharLiterals() {
        val matches =
            ArmeriaScalaTextSupport.findServiceRegistrations(
                """
                import com.linecorp.armeria.server.Server

                object Main {
                  val slash = '/'
                  val star = '*'
                  Server.builder()
                    .service("/api", new HelloService())
                    .build()
                }
                """.trimIndent(),
            )

        assertEquals(1, matches.size)
        assertEquals("/api", matches.single().path)
    }

    @Test
    fun ignoresServiceRegistrationInsideStringLiteral() {
        val matches =
            ArmeriaScalaTextSupport.findServiceRegistrations(
                """
                import com.linecorp.armeria.server.Server

                object Main {
                  val example = ".service(\"/fake\", new FakeService())"
                  Server.builder()
                    .service("/api", new HelloService())
                    .build()
                }
                """.trimIndent(),
            )

        assertEquals(1, matches.size)
        assertEquals("/api", matches.single().path)
    }

    @Test
    fun bareIdentifierTargetIsUnresolved() {
        assertTrue(ArmeriaScalaTextSupport.isUnresolvedScalaTarget("handler", "handler"))
        assertFalse(ArmeriaScalaTextSupport.isUnresolvedScalaTarget("new HelloService()", "HelloService"))
        assertFalse(ArmeriaScalaTextSupport.isUnresolvedScalaTarget("HelloService()", "HelloService"))
    }
}
