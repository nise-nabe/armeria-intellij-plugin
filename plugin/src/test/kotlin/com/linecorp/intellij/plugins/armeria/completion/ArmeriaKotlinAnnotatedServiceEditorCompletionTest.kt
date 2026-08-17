package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.psi.PsiClass
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.jetbrains.kotlin.psi.KtClassOrObject
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
        assertEquals("MyHandler", resolvedName(resolved))
    }

    @Test
    fun navigatesFromExceptionHandlerObjectLiteral() {
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

            object MyHandler : ExceptionHandlerFunction
            """.trimIndent(),
        )
        val resolved =
            assertNotNull(
                myFixture.file
                    .findReferenceAt(myFixture.editor.caretModel.offset)
                    ?.resolve(),
            )
        assertEquals("MyHandler", resolvedName(resolved))
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

    @Test
    fun insertsShortNameForSamePackageImplementor() {
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;

            public class OtherHandler implements ExceptionHandlerFunction {}
            """.trimIndent(),
        )
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

        selectLookup("MyHandler")
        assertTrue("MyHandler::class" in myFixture.file.text, myFixture.file.text)
        assertTrue("example.MyHandler::class" !in myFixture.file.text, myFixture.file.text)
    }

    @Test
    fun navigatesFromExceptionHandlerInAnotherFile() {
        myFixture.addFileToProject(
            "example/OtherHandler.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction

            class OtherHandler : ExceptionHandlerFunction
            """.trimIndent(),
        )
        myFixture.configureByText(
            "UserService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.ExceptionHandler
            import com.linecorp.armeria.server.annotation.Get

            @ExceptionHandler(Other<caret>Handler::class)
            class UserService {
                @Get("/users")
                fun users(): String = "ok"
            }
            """.trimIndent(),
        )
        val resolved =
            assertNotNull(
                myFixture.file
                    .findReferenceAt(myFixture.editor.caretModel.offset)
                    ?.resolve(),
            )
        assertEquals("OtherHandler", resolvedName(resolved))
    }

    @Test
    fun insertsQualifiedNameForOtherPackageImplementor() {
        myFixture.addFileToProject(
            "other/OtherHandler.kt",
            """
            package other

            import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction

            class OtherHandler : ExceptionHandlerFunction
            """.trimIndent(),
        )
        myFixture.configureByText(
            "UserService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.ExceptionHandler
            import com.linecorp.armeria.server.annotation.Get

            @ExceptionHandler(<caret>)
            class UserService {
                @Get("/users")
                fun users(): String = "ok"
            }
            """.trimIndent(),
        )

        selectLookup("OtherHandler")
        assertTrue(
            "other.OtherHandler::class" in myFixture.file.text,
            myFixture.file.text,
        )
    }

    private fun resolvedName(resolved: Any): String? =
        when (resolved) {
            is PsiClass -> resolved.name
            is KtClassOrObject -> resolved.name
            else -> resolved.toString()
        }

    private fun lookupStrings(): List<String> {
        val elements = myFixture.complete(CompletionType.BASIC)
        if (elements != null) {
            return elements.map { it.lookupString }
        }
        return myFixture.lookupElementStrings.orEmpty()
    }

    private fun selectLookup(lookupString: String) {
        val elements = myFixture.complete(CompletionType.BASIC)
        if (elements != null) {
            val match = elements.first { it.lookupString == lookupString }
            myFixture.lookup.currentItem = match
            myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        }
    }
}
