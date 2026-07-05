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
    }
}
