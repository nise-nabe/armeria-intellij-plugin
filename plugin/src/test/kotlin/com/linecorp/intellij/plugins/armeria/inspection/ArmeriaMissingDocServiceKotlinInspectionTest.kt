package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArmeriaMissingDocServiceKotlinInspectionTest : ArmeriaFixtureTestBase5() {
    @Test
    fun highlightsAnnotatedServiceWithoutDocService() {
        configureMain(
            """
            Server.builder()
                .annotatedService(HelloService())
                .build()
            """.trimIndent(),
        )
        assertHighlighted(expected = true)
    }

    @Test
    fun allowsAnnotatedServiceWhenDocServiceIsMounted() {
        configureMain(
            """
            Server.builder()
                .annotatedService(HelloService())
                .service("/docs", DocService())
                .build()
            """.trimIndent(),
        )
        assertHighlighted(expected = false)
    }

    @Test
    fun allowsAnnotatedServiceWhenDocServiceBuilderChainIsMounted() {
        configureMain(
            """
            Server.builder()
                .annotatedService(HelloService())
                .service("/docs", DocService.builder().build())
                .build()
            """.trimIndent(),
        )
        assertHighlighted(expected = false)
    }

    @Test
    fun allowsAnnotatedServiceWhenDocServiceBuilderResultIsAssigned() {
        configureMain(
            """
            val docs = DocService.builder().build()
            Server.builder()
                .annotatedService(HelloService())
                .service("/docs", docs)
                .build()
            """.trimIndent(),
        )
        assertHighlighted(expected = false)
    }

    @Test
    fun highlightsWhenMountedServiceVariablesCycle() {
        configureMain(
            """
            val first = second
            val second = first
            Server.builder()
                .annotatedService(HelloService())
                .service("/x", first)
                .build()
            """.trimIndent(),
        )
        assertHighlighted(expected = true)
    }

    @Test
    fun highlightsWhenDocServiceIsBuiltButNotMounted() {
        configureMain(
            """
            val docs = DocService.builder().build()
            Server.builder()
                .annotatedService(HelloService())
                .build()
            """.trimIndent(),
        )
        assertHighlighted(expected = true)
    }

    private fun configureMain(body: String) {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.docs.DocService

            class HelloService

            fun main() {
                $body
            }
            """.trimIndent(),
        )
    }

    private fun assertHighlighted(expected: Boolean) {
        val function =
            PsiTreeUtil.findChildrenOfType(myFixture.file, KtNamedFunction::class.java).single { it.name == "main" }
        val highlight = ArmeriaMissingDocServiceKotlinSupport.highlight(function)
        if (expected) {
            assertNotNull(highlight, "expected a missing-DocService highlight")
            assertEquals("builder", highlight.text)
        } else {
            assertNull(highlight, "did not expect a missing-DocService highlight")
        }
    }
}
