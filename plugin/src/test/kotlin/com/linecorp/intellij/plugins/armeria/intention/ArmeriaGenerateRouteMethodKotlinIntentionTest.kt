package com.linecorp.intellij.plugins.armeria.intention

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaGenerateRouteMethodKotlinIntentionTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerContentAnnotationStubs()
    }

    fun testGenerateRouteMethodInAnnotatedServiceClass() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
                <caret>
            }
            """.trimIndent(),
        )

        assertIntentionAvailableAndInvoke(ArmeriaGenerateRouteMethodKotlinIntention())

        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("@Get(\"/handler\")"))
        assertTrue(updated.contains("fun handler(): String"))
        assertFalse(updated.contains("suspend fun handler()"))
    }

    fun testGenerateSuspendWhenArmeriaKotlinIsOnClasspath() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.internal.common.kotlin;
            public final class ArmeriaKotlinUtil {}
            """.trimIndent(),
        )
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
                <caret>
            }
            """.trimIndent(),
        )

        assertIntentionAvailableAndInvoke(ArmeriaGenerateRouteMethodKotlinIntention())

        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("suspend fun handler(): String"), updated)
    }

    fun testGeneratePostJsonRouteMethod() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
                <caret>
            }
            """.trimIndent(),
        )

        assertIntentionAvailableAndInvoke(ArmeriaGeneratePostJsonRouteMethodKotlinIntention())

        val updated = myFixture.editor.document.text
        assertTrue(updated.contains("@Post(\"/handler\")"), updated)
        assertTrue(updated.contains("@ConsumesJson"), updated)
        assertTrue(updated.contains("@ProducesJson"), updated)
        assertTrue(updated.contains("fun handler(): String"), updated)
    }

    fun testNotAvailableOutsideAnnotatedServiceClass() {
        myFixture.configureByText(
            "Plain.kt",
            """
            package example

            class Plain {
                <caret>
            }
            """.trimIndent(),
        )
        val intention = ArmeriaGenerateRouteMethodKotlinIntention()
        val element = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!
        assertFalse(intention.isAvailable(myFixture.project, myFixture.editor, element))
    }

    private fun assertIntentionAvailableAndInvoke(intention: ArmeriaGenerateRouteMethodKotlinIntention) {
        val element = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!
        assertTrue(intention.isAvailable(myFixture.project, myFixture.editor, element))
        intention.invoke(myFixture.project, myFixture.editor, element)
    }
}
