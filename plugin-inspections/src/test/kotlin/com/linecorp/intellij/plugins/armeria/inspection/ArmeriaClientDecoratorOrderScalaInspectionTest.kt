package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaClientDecoratorOrderScalaInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        myFixture.registerClientDecoratorInspectionStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaClientDecoratorOrderScalaInspection())
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

    @Test
    fun highlightsCircuitBreakerAfterRetrying() {
        configureClient(
            """
            WebClient.builder("https://example.com")
                .decorator(RetryingClient.newDecorator())
                .decorator(CircuitBreakerClient.newDecorator())
                .build()
            """.trimIndent(),
        )
        assertDecoratorHighlights(message("inspection.decorator.order.circuit.after.retry"), 1)
    }

    @Test
    fun highlightsLoggingAfterRetryingOnSplitChain() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.client.WebClient
            import com.linecorp.armeria.client.logging.LoggingClient
            import com.linecorp.armeria.client.retry.RetryingClient

            object Main {
                def main(): Unit = {
                    val builder = WebClient.builder("https://example.com")
                        .decorator(RetryingClient.newDecorator())
                    builder.decorator(LoggingClient.newDecorator()).build()
                }
            }
            """.trimIndent(),
        )
        assertDecoratorHighlights(message("inspection.decorator.order.logging.after.retry"), 1)
    }

    @Test
    fun highlightsLoggingAfterRetryingWithQualifiedReferences() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.client.WebClient

            object Main {
                def main(): Unit = {
                    WebClient.builder("https://example.com")
                        .decorator(com.linecorp.armeria.client.retry.RetryingClient.newDecorator())
                        .decorator(com.linecorp.armeria.client.logging.LoggingClient.newDecorator())
                        .build()
                }
            }
            """.trimIndent(),
        )
        assertDecoratorHighlights(message("inspection.decorator.order.logging.after.retry"), 1)
    }

    private fun configureClient(body: String) {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.client.WebClient
            import com.linecorp.armeria.client.circuitbreaker.CircuitBreakerClient
            import com.linecorp.armeria.client.logging.LoggingClient
            import com.linecorp.armeria.client.retry.RetryingClient

            object Main {
                def main(): Unit = {
                    $body
                }
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
