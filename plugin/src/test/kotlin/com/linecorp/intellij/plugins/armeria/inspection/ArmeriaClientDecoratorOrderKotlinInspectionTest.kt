package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaClientDecoratorOrderKotlinInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        myFixture.registerClientDecoratorInspectionStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaClientDecoratorOrderKotlinInspection())
    }

    @Test
    fun highlightsLoggingAfterRetrying() {
        configureClient(
            """
            WebClient.builder("https://example.com")
                .decorator(RetryingClient.newDecorator())
                .decorator(LoggingClient.newDecorator())
                .build()
            """.trimIndent(),
        )
        assertDecoratorHighlights(message("inspection.decorator.order.logging.after.retry"), 1)
    }

    @Test
    fun allowsLoggingBeforeRetrying() {
        configureClient(
            """
            WebClient.builder("https://example.com")
                .decorator(LoggingClient.newDecorator())
                .decorator(RetryingClient.newDecorator())
                .build()
            """.trimIndent(),
        )
        assertDecoratorHighlights(message("inspection.decorator.order.logging.after.retry"), 0)
    }

    private fun configureClient(body: String) {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.WebClient
            import com.linecorp.armeria.client.logging.LoggingClient
            import com.linecorp.armeria.client.retry.RetryingClient

            fun main() {
                $body
            }
            """.trimIndent(),
        )
    }

    private fun assertDecoratorHighlights(
        expected: String,
        count: Int,
    ) {
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(count, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }
}
