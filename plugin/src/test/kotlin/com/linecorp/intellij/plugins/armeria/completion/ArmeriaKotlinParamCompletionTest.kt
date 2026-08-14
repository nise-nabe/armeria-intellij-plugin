package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.command.WriteCommandAction
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
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

        val reference = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
        assertTrue(reference != null, "Expected a path-variable reference at the caret")
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
            reference!!.handleElementRename("userId")
        }
        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("@Get(\"/users/{userId}\")"), updated)
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
