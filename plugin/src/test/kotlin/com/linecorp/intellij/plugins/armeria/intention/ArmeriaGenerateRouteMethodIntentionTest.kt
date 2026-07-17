package com.linecorp.intellij.plugins.armeria.intention

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaGenerateRouteMethodIntentionTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerContentAnnotationStubs()
    }

    fun testGenerateRouteMethodInAnnotatedServiceClass() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
                <caret>
            }
            """.trimIndent(),
        )

        assertIntentionAvailableAndInvoke()

        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("@Get(\"/handler\")"))
        assertTrue(updated.contains("public String handler()"))
        assertTrue(updated.contains("return \"\";"))
        assertFalse(updated.contains("@com.linecorp.armeria.server.annotation.Get"))
    }

    fun testPathPrefixUsesRelativeMethodPath() {
        myFixture.configureByText(
            "PrefixedService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.PathPrefix;

            @PathPrefix("/v1")
            public class PrefixedService {
                <caret>
            }
            """.trimIndent(),
        )

        assertIntentionAvailableAndInvoke()

        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("@Get(\"/handler\")"))
        assertFalse(updated.contains("@Get(\"/v1/handler\")"))
    }

    fun testSuggestsUniqueMethodNameOnCollision() {
        myFixture.configureByText(
            "CollisionService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class CollisionService {
                @Get("/handler")
                public String handler() {
                    return "exists";
                }
                <caret>
            }
            """.trimIndent(),
        )

        assertIntentionAvailableAndInvoke()

        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("@Get(\"/handler2\")"))
        assertTrue(updated.contains("public String handler2()"))
    }

    fun testSuggestsUniquePathWhenGetPathCollidesWithDifferentMethodName() {
        myFixture.configureByText(
            "PathCollisionService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class PathCollisionService {
                @Get("/handler")
                public String hello() {
                    return "exists";
                }
                <caret>
            }
            """.trimIndent(),
        )

        assertIntentionAvailableAndInvoke()

        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("@Get(\"/handler2\")"))
        assertTrue(updated.contains("public String handler2()"))
        assertFalse(updated.contains("public String handler()"))
    }

    fun testNotAvailableOutsideAnnotatedServiceClass() {
        myFixture.configureByText(
            "Plain.java",
            """
            package example;

            public class Plain {
                <caret>
            }
            """.trimIndent(),
        )

        assertFalse(isIntentionAvailable())
    }

    fun testNotAvailableForUnrelatedAnnotationImportOnly() {
        myFixture.configureByText(
            "ParamOnly.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Param;

            public class ParamOnly {
                public void use(@Param("id") String id) {
                }
                <caret>
            }
            """.trimIndent(),
        )

        assertFalse(isIntentionAvailable())
    }

    fun testNotAvailableInsideMethodBody() {
        myFixture.configureByText(
            "BodyService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class BodyService {
                @Get("/hello")
                public String hello() {
                    return <caret>"hello";
                }
            }
            """.trimIndent(),
        )

        assertFalse(isIntentionAvailable())
    }

    fun testNotAvailableInsideFieldInitializer() {
        myFixture.configureByText(
            "FieldService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class FieldService {
                private String value = "<caret>x";

                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        assertFalse(isIntentionAvailable())
    }

    fun testNotAvailableInsideRouteAnnotationValue() {
        myFixture.configureByText(
            "AnnotationService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class AnnotationService {
                @Get("/hel<caret>lo")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        assertFalse(isIntentionAvailable())
    }

    private fun isIntentionAvailable(): Boolean {
        val intention = ArmeriaGenerateRouteMethodIntention()
        val element = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!
        return intention.isAvailable(myFixture.project, myFixture.editor, element)
    }

    private fun assertIntentionAvailableAndInvoke() {
        val intention = ArmeriaGenerateRouteMethodIntention()
        val element = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!
        assertTrue(intention.isAvailable(myFixture.project, myFixture.editor, element))
        intention.invoke(myFixture.project, myFixture.editor, element)
    }
}
