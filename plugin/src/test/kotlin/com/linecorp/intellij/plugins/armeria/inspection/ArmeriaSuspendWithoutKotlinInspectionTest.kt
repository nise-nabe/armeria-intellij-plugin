package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaSuspendWithoutKotlinInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaSuspendWithoutKotlinInspection())
    }

    @Test
    fun highlightsSuspendGetWithoutArmeriaKotlin() {
        configureSuspendRoute()
        val highlights = missingKotlinHighlights()
        assertEquals(1, highlights.size, highlights.joinToString { it.description.orEmpty() })
        val info = highlights.single()
        val highlighted =
            myFixture.editor.document.text
                .substring(info.startOffset, info.endOffset)
        assertEquals("suspend", highlighted)
    }

    @Test
    fun highlightsSuspendPostWithoutArmeriaKotlin() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Post

            class HelloService {
                @Post("/hello")
                suspend fun hello(): String = "hello"
            }
            """.trimIndent(),
        )
        assertEquals(1, missingKotlinHighlights().size)
    }

    @Test
    fun allowsSuspendGetWhenCoroutineContextServiceResolves() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.kotlin;
            public final class CoroutineContextService {}
            """.trimIndent(),
        )
        configureSuspendRoute()
        assertTrue(missingKotlinHighlights().isEmpty())
    }

    @Test
    fun allowsSuspendGetWhenInternalMarkerResolves() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.internal.common.kotlin;
            public final class ArmeriaKotlinUtil {}
            """.trimIndent(),
        )
        configureSuspendRoute()
        assertTrue(missingKotlinHighlights().isEmpty())
    }

    @Test
    fun doesNotHighlightNonSuspendGet() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )
        assertTrue(missingKotlinHighlights().isEmpty())
    }

    @Test
    fun doesNotHighlightSuspendHelperWithoutRouteAnnotation() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"

                private suspend fun helper() = Unit
            }
            """.trimIndent(),
        )
        assertTrue(missingKotlinHighlights().isEmpty())
    }

    private fun configureSuspendRoute() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                suspend fun hello(): String = "hello"
            }
            """.trimIndent(),
        )
    }

    private fun missingKotlinHighlights() =
        myFixture.doHighlighting().filter {
            it.description == message("inspection.missing.armeria.kotlin.problem")
        }
}
