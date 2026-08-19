package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaMissingDocServiceKotlinInspectionTest : ArmeriaFixtureTestBase5() {
    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaMissingDocServiceKotlinInspection())
    }

    @Test
    fun highlightsAnnotatedServiceWithoutDocService() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.docs.DocService

            class HelloService

            fun main() {
                Server.builder()
                    .annotatedService(HelloService())
                    .build()
            }
            """.trimIndent(),
        )
        assertHighlights(1)
    }

    @Test
    fun allowsAnnotatedServiceWhenDocServiceIsMounted() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.docs.DocService

            class HelloService

            fun main() {
                Server.builder()
                    .annotatedService(HelloService())
                    .service("/docs", DocService())
                    .build()
            }
            """.trimIndent(),
        )
        assertHighlights(0)
    }

    private fun assertHighlights(count: Int) {
        val expected = message("inspection.missing.docservice.problem")
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(count, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }
}
