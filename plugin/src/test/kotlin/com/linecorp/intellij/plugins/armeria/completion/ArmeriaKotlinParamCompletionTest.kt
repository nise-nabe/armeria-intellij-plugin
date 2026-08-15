package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.command.WriteCommandAction
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArmeriaKotlinParamCompletionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerContentAnnotationStubs()
    }

    @Test
    fun completesPathVariablesInParam() {
        myFixture.configureByText(
            "UserService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.Param

            class UserService {
                @Get("/users/{id}/posts/{postId}")
                fun handler(@Param("<caret>") value: String): String = value
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
            "UserService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.Header

            class UserService {
                @Get("/users")
                fun handler(@Header("<caret>") value: String): String = value
            }
            """.trimIndent(),
        )

        assertHeaderLookups()
    }

    @Test
    fun renameParamUpdatesPathVariable() {
        myFixture.configureByText(
            "UserService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.Param

            class UserService {
                @Get("/users/{id}")
                fun handler(@Param("i<caret>d") id: String): String = id
            }
            """.trimIndent(),
        )

        val reference =
            assertNotNull(
                myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset),
                "Expected a path-variable reference at the caret",
            )
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
            reference.handleElementRename("userId")
        }
        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("@Get(\"/users/{userId}\")"), updated)
        assertTrue(updated.contains("@Param(\"userId\")"), updated)
    }

    @Test
    fun completesRegexNamedGroupsInParam() {
        myFixture.configureByText(
            "UserService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.Param

            class UserService {
                @Get("regex:^(?<userId>\\d+)$")
                fun handler(@Param("<caret>") value: String): String = value
            }
            """.trimIndent(),
        )

        val lookups = lookupStrings()
        assertTrue("userId" in lookups, lookups.toString())
    }

    @Test
    fun renameRegexNamedGroupUpdatesPath() {
        myFixture.configureByText(
            "UserService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.Param

            class UserService {
                @Get("regex:^(?<id>\\d+)$")
                fun handler(@Param("i<caret>d") id: String): String = id
            }
            """.trimIndent(),
        )

        val reference =
            assertNotNull(
                myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset),
                "Expected a path-variable reference at the caret",
            )
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
            reference.handleElementRename("userId")
        }
        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("(?<userId>"), updated)
        assertTrue(updated.contains("@Param(\"userId\")"), updated)
        assertTrue(!updated.contains("(?<id>"), updated)
    }

    @Test
    fun renamePathPrefixUpdatesSiblingMethods() {
        myFixture.configureByText(
            "OrgService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.Param
            import com.linecorp.armeria.server.annotation.PathPrefix

            @PathPrefix("/orgs/{org}")
            class OrgService {
                @Get("/users/{id}")
                fun users(@Param("o<caret>rg") org: String, @Param("id") id: String): String = org

                @Get("/items")
                fun items(@Param("org") org: String): String = org
            }
            """.trimIndent(),
        )

        val reference =
            assertNotNull(
                myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset),
                "Expected a path-variable reference at the caret",
            )
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
            reference.handleElementRename("organization")
        }
        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("@PathPrefix(\"/orgs/{organization}\")"), updated)
        assertTrue(updated.contains("@Param(\"organization\")"), updated)
        assertTrue(!updated.contains("@Param(\"org\")"), updated)
    }

    @Test
    fun renamePathVariableUpdatesImplicitParamName() {
        myFixture.configureByText(
            "UserService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.Param

            class UserService {
                @Get("/users/{i<caret>d}")
                fun handler(@Param id: String): String = id
            }
            """.trimIndent(),
        )

        val reference =
            assertNotNull(
                myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset),
                "Expected a path-variable reference at the caret",
            )
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
            reference.handleElementRename("userId")
        }
        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("@Get(\"/users/{userId}\")"), updated)
        assertTrue(updated.contains("userId: String"), updated)
        assertTrue(!updated.contains("@Param id:"), updated)
    }

    @Test
    fun renameCollectionLiteralPathVariable() {
        myFixture.configureByText(
            "UserService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.Param

            class UserService {
                @Get(value = ["/users/{i<caret>d}"])
                fun handler(@Param("id") id: String): String = id
            }
            """.trimIndent(),
        )

        val reference =
            assertNotNull(
                myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset),
                "Expected a path-variable reference at the caret",
            )
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
            reference.handleElementRename("userId")
        }
        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("/users/{userId}"), updated)
        assertTrue(updated.contains("@Param(\"userId\")"), updated)
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
