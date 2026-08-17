package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiClass
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.jetbrains.kotlin.psi.KtClass
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArmeriaKotlinAnnotatedServiceEditorCompletionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerContentAnnotationStubs()
        registerClassValuedAnnotationStubs()
    }

    @Test
    fun completesJsonAndPlainTextMediaTypes() {
        myFixture.configureByText(
            "UserService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.Produces

            class UserService {
                @Get("/users")
                @Produces("<caret>")
                fun users(): String = "ok"
            }
            """.trimIndent(),
        )

        val lookups = lookupStrings()
        assertTrue("application/json" in lookups, lookups.toString())
        assertTrue("text/plain" in lookups, lookups.toString())
    }

    @Test
    fun navigatesFromExceptionHandlerClassLiteral() {
        myFixture.configureByText(
            "UserService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.ExceptionHandler
            import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction
            import com.linecorp.armeria.server.annotation.Get

            @ExceptionHandler(My<caret>Handler::class)
            class UserService {
                @Get("/users")
                fun users(): String = "ok"
            }

            class MyHandler : ExceptionHandlerFunction
            """.trimIndent(),
        )
        val resolved =
            assertNotNull(
                myFixture.file
                    .findReferenceAt(myFixture.editor.caretModel.offset)
                    ?.resolve(),
            )
        val name =
            when (resolved) {
                is PsiClass -> resolved.name
                is KtClass -> resolved.name
                else -> resolved.toString()
            }
        assertEquals("MyHandler", name)
    }

    @Test
    fun completesExceptionHandlerImplementors() {
        myFixture.configureByText(
            "UserService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.ExceptionHandler
            import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction
            import com.linecorp.armeria.server.annotation.Get

            @ExceptionHandler(<caret>)
            class UserService {
                @Get("/users")
                fun users(): String = "ok"
            }

            class MyHandler : ExceptionHandlerFunction
            """.trimIndent(),
        )

        val lookups = lookupStrings()
        assertTrue("MyHandler" in lookups, lookups.toString())
    }

    private fun lookupStrings(): List<String> {
        val elements = myFixture.complete(CompletionType.BASIC)
        if (elements != null) {
            return elements.map { it.lookupString }
        }
        return myFixture.lookupElementStrings.orEmpty()
    }
}
