package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.completion.CompletionType
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ArmeriaParamCompletionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerContentAnnotationStubs()
    }

    @Test
    fun completesPathVariablesInParam() {
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Param;

            public class UserService {
                @Get("/users/{id}/posts/{postId}")
                public String handler(@Param("<caret>") String value) {
                    return value;
                }
            }
            """.trimIndent(),
        )

        val lookups = lookupStrings()
        assertTrue("id" in lookups, lookups.toString())
        assertTrue("postId" in lookups, lookups.toString())
    }

    @Test
    fun completesKnownHeaders() {
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Header;

            public class UserService {
                @Get("/users")
                public String handler(@Header("Auth<caret>") String value) {
                    return value;
                }
            }
            """.trimIndent(),
        )

        val lookups = lookupStrings()
        assertTrue("Authorization" in lookups, lookups.toString())
    }

    @Test
    fun completesCookieNamesFromTheSameClass() {
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Cookie;
            import com.linecorp.armeria.server.annotation.Get;

            public class UserService {
                @Get("/session")
                public String session(@Cookie("sessionId") String sessionId) {
                    return sessionId;
                }

                @Get("/profile")
                public String profile(@Cookie("<caret>") String value) {
                    return value;
                }
            }
            """.trimIndent(),
        )

        val lookups = lookupStrings()
        assertTrue("sessionId" in lookups, lookups.toString())
    }

    @Test
    fun renameParamUpdatesPathVariable() {
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Param;

            public class UserService {
                @Get("/users/{id}")
                public String handler(@Param("i<caret>d") String id) {
                    return id;
                }
            }
            """.trimIndent(),
        )

        myFixture.renameElementAtCaret("userId")
        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("@Get(\"/users/{userId}\")"), updated)
        assertTrue(updated.contains("@Param(\"userId\")"), updated)
    }

    private fun lookupStrings(): List<String> {
        val elements = myFixture.complete(CompletionType.BASIC)
        if (elements != null) {
            return elements.map { it.lookupString }
        }
        return myFixture.lookupElementStrings.orEmpty().ifEmpty {
            listOf(myFixture.editor.document.text)
        }
    }
}
