package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.command.WriteCommandAction
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
                public String handler(@Header("<caret>") String value) {
                    return value;
                }
            }
            """.trimIndent(),
        )

        assertHeaderLookups()
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

        val reference = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
        assertTrue(reference != null, "Expected a path-variable reference at the caret")
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
            reference!!.handleElementRename("userId")
        }
        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("@Get(\"/users/{userId}\")"), updated)
        assertTrue(updated.contains("@Param(\"userId\")"), updated)
    }

    @Test
    fun renamePathPrefixUpdatesSiblingMethods() {
        myFixture.configureByText(
            "OrgService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Param;
            import com.linecorp.armeria.server.annotation.PathPrefix;

            @PathPrefix("/orgs/{org}")
            public class OrgService {
                @Get("/users/{id}")
                public String users(@Param("o<caret>rg") String org, @Param("id") String id) {
                    return org;
                }

                @Get("/items")
                public String items(@Param("org") String org) {
                    return org;
                }
            }
            """.trimIndent(),
        )

        val reference = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
        assertTrue(reference != null, "Expected a path-variable reference at the caret")
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
            reference!!.handleElementRename("organization")
        }
        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("@PathPrefix(\"/orgs/{organization}\")"), updated)
        assertTrue(updated.contains("@Param(\"organization\")"), updated)
        assertTrue(!updated.contains("@Param(\"org\")"), updated)
    }

    private fun lookupStrings(): List<String> {
        val elements = myFixture.complete(CompletionType.BASIC)
        if (elements != null) {
            return elements.map { it.lookupString }
        }
        return myFixture.lookupElementStrings.orEmpty()
    }

    private fun assertHeaderLookups() {
        val lookups = lookupStrings()
        if (lookups.isEmpty()) {
            assertTrue(
                myFixture.editor.document.text
                    .contains("Authorization"),
                "expected header lookup or unique insertion of Authorization",
            )
            return
        }
        assertTrue("Authorization" in lookups, lookups.toString())
    }
}
