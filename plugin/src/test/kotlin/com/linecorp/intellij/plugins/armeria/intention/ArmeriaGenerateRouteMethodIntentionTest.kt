package com.linecorp.intellij.plugins.armeria.intention

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaGenerateRouteMethodIntentionTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerArmeriaStubs()
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

        val intention = ArmeriaGenerateRouteMethodIntention()
        val element = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!
        assertTrue(intention.isAvailable(myFixture.project, myFixture.editor, element))

        intention.invoke(myFixture.project, myFixture.editor, element)

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

        val intention = ArmeriaGenerateRouteMethodIntention()
        val element = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!
        assertTrue(intention.isAvailable(myFixture.project, myFixture.editor, element))

        intention.invoke(myFixture.project, myFixture.editor, element)

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

        val intention = ArmeriaGenerateRouteMethodIntention()
        val element = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!
        assertTrue(intention.isAvailable(myFixture.project, myFixture.editor, element))

        intention.invoke(myFixture.project, myFixture.editor, element)

        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("@Get(\"/handler2\")"))
        assertTrue(updated.contains("public String handler2()"))
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

        val intention = ArmeriaGenerateRouteMethodIntention()
        val element = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!
        assertFalse(intention.isAvailable(myFixture.project, myFixture.editor, element))
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

        val intention = ArmeriaGenerateRouteMethodIntention()
        val element = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!
        assertFalse(intention.isAvailable(myFixture.project, myFixture.editor, element))
    }

    private fun registerArmeriaStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Get {
                String value() default "";
                String path() default "";
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface PathPrefix {
                String value();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Param {
                String value();
            }
            """.trimIndent(),
        )
    }
}
