package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiClass
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArmeriaAnnotatedServiceEditorCompletionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerContentAnnotationStubs()
        registerClassValuedAnnotationStubs()
    }

    @Test
    fun completesJsonAndPlainTextMediaTypes() {
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Produces;

            public class UserService {
                @Get("/users")
                @Produces("<caret>")
                public String users() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val lookups = lookupStrings()
        assertTrue("application/json" in lookups, lookups.toString())
        assertTrue("text/plain" in lookups, lookups.toString())
        assertTrue("application/binary" in lookups, lookups.toString())
    }

    @Test
    fun completesConsumesMediaTypes() {
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Consumes;
            import com.linecorp.armeria.server.annotation.Post;

            public class UserService {
                @Post("/users")
                @Consumes("<caret>")
                public String create() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val lookups = lookupStrings()
        assertTrue("application/json" in lookups, lookups.toString())
        assertTrue("text/plain" in lookups, lookups.toString())
    }

    @Test
    fun navigatesFromImportedExceptionHandlerWhenAnotherPackageSharesTheName() {
        myFixture.addClass(
            """
            package other;

            import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;

            public class OtherHandler implements ExceptionHandlerFunction {}
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package clash;

            import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;

            public class OtherHandler implements ExceptionHandlerFunction {}
            """.trimIndent(),
        )
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.ExceptionHandler;
            import com.linecorp.armeria.server.annotation.Get;
            import other.OtherHandler;

            @ExceptionHandler(Other<caret>Handler.class)
            public class UserService {
                @Get("/users")
                public String users() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )
        val resolved = resolvedClassesAtCaret()
        assertTrue(
            resolved.any { it.qualifiedName == "other.OtherHandler" },
            resolved.joinToString { it.qualifiedName.orEmpty() },
        )
        assertTrue(
            resolved.none { it.qualifiedName == "clash.OtherHandler" },
            resolved.joinToString { it.qualifiedName.orEmpty() },
        )
    }

    @Test
    fun doesNotResolveClassLiteralQualifierToTheHandlerType() {
        myFixture.addClass(
            """
            package other;

            import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;

            public class OtherHandler implements ExceptionHandlerFunction {}
            """.trimIndent(),
        )
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.ExceptionHandler;
            import com.linecorp.armeria.server.annotation.Get;

            @ExceptionHandler(ot<caret>her.OtherHandler.class)
            public class UserService {
                @Get("/users")
                public String users() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )
        val at = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
        assertNotNull(at)
        assertTrue(
            at.references.none { reference ->
                !reference.isSoft && (reference.resolve() as? PsiClass)?.name == "OtherHandler"
            },
            at.references.joinToString { "${it.canonicalText}/${it.isSoft}/${it.resolve()}" },
        )
    }

    @Test
    fun navigatesFromExceptionHandlerClassLiteral() {
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.ExceptionHandler;
            import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;
            import com.linecorp.armeria.server.annotation.Get;

            @ExceptionHandler(My<caret>Handler.class)
            public class UserService {
                @Get("/users")
                public String users() {
                    return "ok";
                }
            }

            class MyHandler implements ExceptionHandlerFunction {}
            """.trimIndent(),
        )
        val resolved =
            assertNotNull(
                myFixture.file
                    .findReferenceAt(myFixture.editor.caretModel.offset)
                    ?.resolve(),
            )
        val psiClass = assertNotNull(resolved as? PsiClass, resolved.toString())
        assertEquals("MyHandler", psiClass.name)
    }

    @Test
    fun completesExceptionHandlerImplementors() {
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.ExceptionHandler;
            import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;
            import com.linecorp.armeria.server.annotation.Get;

            @ExceptionHandler(<caret>)
            public class UserService {
                @Get("/users")
                public String users() {
                    return "ok";
                }
            }

            class MyHandler implements ExceptionHandlerFunction {}
            """.trimIndent(),
        )

        val lookups = lookupStrings()
        assertTrue("MyHandler" in lookups, lookups.toString())
    }

    @Test
    fun navigatesFromQualifiedExceptionHandlerWhenSameFileNameClashes() {
        myFixture.addClass(
            """
            package other;

            import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;

            public class OtherHandler implements ExceptionHandlerFunction {}
            """.trimIndent(),
        )
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.ExceptionHandler;
            import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;
            import com.linecorp.armeria.server.annotation.Get;

            @ExceptionHandler(other.Other<caret>Handler.class)
            public class UserService {
                @Get("/users")
                public String users() {
                    return "ok";
                }
            }

            class OtherHandler implements ExceptionHandlerFunction {}
            """.trimIndent(),
        )
        val resolved = resolvedClassesAtCaret()
        assertTrue(
            resolved.any { it.qualifiedName == "other.OtherHandler" },
            resolved.joinToString { it.qualifiedName.orEmpty() },
        )
        assertTrue(
            resolved.none { it.qualifiedName == "example.OtherHandler" },
            resolved.joinToString { it.qualifiedName.orEmpty() },
        )
    }

    @Test
    fun insertsQualifiedNameForOtherPackageImplementor() {
        myFixture.addClass(
            """
            package other;

            import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;

            public class OtherHandler implements ExceptionHandlerFunction {}
            """.trimIndent(),
        )
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.ExceptionHandler;
            import com.linecorp.armeria.server.annotation.Get;

            @ExceptionHandler(<caret>)
            public class UserService {
                @Get("/users")
                public String users() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        selectLookup("OtherHandler")
        assertTrue(
            "other.OtherHandler.class" in myFixture.file.text,
            myFixture.file.text,
        )
    }

    @Test
    fun completesAttributeNamesFromTheSameClass() {
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Attribute;
            import com.linecorp.armeria.server.annotation.Get;

            public class UserService {
                @Get("/session")
                public String session(@Attribute("user") Object user) {
                    return "ok";
                }

                @Get("/profile")
                public String profile(@Attribute("<caret>") Object value) {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val lookups = lookupStrings()
        assertTrue("user" in lookups, lookups.toString())
    }

    private fun resolvedClassesAtCaret(): List<PsiClass> {
        val at = myFixture.file.findElementAt(myFixture.editor.caretModel.offset) ?: return emptyList()
        return (at.references.toList() + at.parent.references.toList())
            .mapNotNull { it.resolve() as? PsiClass }
            .distinct()
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
