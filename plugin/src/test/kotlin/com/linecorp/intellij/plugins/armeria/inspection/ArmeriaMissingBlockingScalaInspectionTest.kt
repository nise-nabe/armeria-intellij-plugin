package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaMissingBlockingScalaInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerArmeriaBlockingAnnotationStubs()
        myFixture.registerMissingBlockingHttpServiceStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaMissingBlockingScalaInspection())
    }

    @Test
    fun highlightsThreadSleepWithoutBlocking() {
        myFixture.configureByText(
            "SlowService.scala",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class SlowService {
                @Get("/slow")
                def slow(): String = {
                    Thread.sleep(1)
                    "ok"
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(1, "sleep")
    }

    @Test
    fun highlightsJoinWithoutBlocking() {
        myFixture.configureByText(
            "SlowService.scala",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import java.util.concurrent.CompletableFuture

            class SlowService {
                @Get("/slow")
                def slow(): String = CompletableFuture.completedFuture("ok").join()
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(1, "join")
    }

    @Test
    fun allowsJoinWithBlocking() {
        myFixture.configureByText(
            "SlowService.scala",
            """
            package example

            import com.linecorp.armeria.server.annotation.Blocking
            import com.linecorp.armeria.server.annotation.Get
            import java.util.concurrent.CompletableFuture

            class SlowService {
                @Blocking
                @Get("/slow")
                def slow(): String = CompletableFuture.completedFuture("ok").join()
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(0, "join")
    }

    @Test
    fun allowsJoinWithClassBlocking() {
        myFixture.configureByText(
            "SlowService.scala",
            """
            package example

            import com.linecorp.armeria.server.annotation.Blocking
            import com.linecorp.armeria.server.annotation.Get
            import java.util.concurrent.CompletableFuture

            @Blocking
            class SlowService {
                @Get("/slow")
                def slow(): String = CompletableFuture.completedFuture("ok").join()
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(0, "join")
    }

    @Test
    fun ignoresCallsInsideNestedFunction() {
        myFixture.configureByText(
            "NestedService.scala",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import java.util.concurrent.CompletableFuture

            class NestedService {
                @Get("/nested")
                def nested(): String = {
                    def inner(): String = CompletableFuture.completedFuture("ok").join()
                    inner()
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(0, "join")
    }

    @Test
    fun highlightsHttpServiceOverride() {
        myFixture.configureByText(
            "MyHttpService.scala",
            """
            package example

            import com.linecorp.armeria.server.AbstractHttpService
            import java.util.concurrent.CompletableFuture

            class MyHttpService extends AbstractHttpService {
                override def doGet(ctx: Any, req: Any): Any = {
                    CompletableFuture.completedFuture("ok").join()
                    null
                }
            }
            """.trimIndent(),
        )
        val expected = message("inspection.missing.blocking.problem.httpservice", "join")
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(1, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }

    @Test
    fun allowsJoinWithNonBlocking() {
        myFixture.configureByText(
            "SlowService.scala",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.NonBlocking
            import java.util.concurrent.CompletableFuture

            class SlowService {
                @NonBlocking
                @Get("/slow")
                def slow(): String = CompletableFuture.completedFuture("ok").join()
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(0, "join")
    }

    private fun assertBlockingHighlights(
        expectedCount: Int,
        methodName: String,
    ) {
        val expected = message("inspection.missing.blocking.problem", methodName)
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(expectedCount, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }
}
