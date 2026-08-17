package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiClass
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

    private fun lookupStrings(): List<String> {
        val elements = myFixture.complete(CompletionType.BASIC)
        if (elements != null) {
            return elements.map { it.lookupString }
        }
        return myFixture.lookupElementStrings.orEmpty()
    }
}
