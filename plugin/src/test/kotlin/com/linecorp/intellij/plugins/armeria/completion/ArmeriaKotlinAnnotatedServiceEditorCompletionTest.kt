package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiClass
import com.linecorp.intellij.plugins.armeria.message
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
        assertTrue("application/binary" in lookups, lookups.toString())
    }

    @Test
    fun navigatesFromImportedExceptionHandlerWhenAnotherPackageSharesTheName() {
        myFixture.addFileToProject(
            "other/OtherHandler.kt",
            """
            package other

            import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction

            class OtherHandler : ExceptionHandlerFunction
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "clash/OtherHandler.kt",
            """
            package clash

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
            import other.OtherHandler

            @ExceptionHandler(Other<caret>Handler::class)
            class UserService {
                @Get("/users")
                fun users(): String = "ok"
            }
            """.trimIndent(),
        )
        val names = resolvedQualifiedNamesAtCaret()
        assertTrue("other.OtherHandler" in names, names.toString())
        assertTrue("clash.OtherHandler" !in names, names.toString())
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
    fun navigatesFromQualifiedExceptionHandlerWhenSameFileNameClashes() {
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
            import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction
            import com.linecorp.armeria.server.annotation.Get

            @ExceptionHandler(other.Other<caret>Handler::class)
            class UserService {
                @Get("/users")
                fun users(): String = "ok"
            }

            class OtherHandler : ExceptionHandlerFunction
            """.trimIndent(),
        )
        val names = resolvedQualifiedNamesAtCaret()
        assertTrue("other.OtherHandler" in names, names.toString())
        assertTrue("example.OtherHandler" !in names, names.toString())
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

    private fun resolvedQualifiedNamesAtCaret(): List<String> {
        val at = myFixture.file.findElementAt(myFixture.editor.caretModel.offset) ?: return emptyList()
        return (at.references.toList() + at.parent.references.toList())
            .mapNotNull { reference ->
                when (val resolved = reference.resolve()) {
                    is PsiClass -> resolved.qualifiedName
                    is KtClassOrObject -> resolved.fqName?.asString()
                    else -> null
                }
            }.distinct()
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
        val elements =
            assertNotNull(myFixture.complete(CompletionType.BASIC), myFixture.file.text)
        val expectedType = message("completion.exception.handler.type")
        val match =
            elements.firstOrNull { element ->
                element.lookupString == lookupString && presentedTypeText(element) == expectedType
            }
        assertNotNull(match, elements.joinToString { "${it.lookupString}/${presentedTypeText(it)}" })
        myFixture.lookup.currentItem = match
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    }

    private fun presentedTypeText(element: LookupElement): String = LookupElementPresentation.renderElement(element).typeText.orEmpty()
}
