package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaMissingBlockingKotlinInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerArmeriaBlockingAnnotationStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaMissingBlockingKotlinInspection())
    }

    @Test
    fun highlightsJoinWithoutBlocking() {
        myFixture.configureByText(
            "SlowService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import java.util.concurrent.CompletableFuture

            class SlowService {
                @Get("/slow")
                fun slow(): String = CompletableFuture.completedFuture("ok").join()
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(1, "join")
    }

    @Test
    fun allowsJoinWithBlocking() {
        myFixture.configureByText(
            "SlowService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Blocking
            import com.linecorp.armeria.server.annotation.Get
            import java.util.concurrent.CompletableFuture

            class SlowService {
                @Blocking
                @Get("/slow")
                fun slow(): String = CompletableFuture.completedFuture("ok").join()
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(0, "join")
    }

    @Test
    fun highlightsGrpcImplBaseOverride() {
        myFixture.addClass(
            """
            package example;

            public class HelloServiceImplBase {
                public void sayHello() {
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import java.util.concurrent.CompletableFuture

            class HelloService : HelloServiceImplBase() {
                override fun sayHello() {
                    CompletableFuture.completedFuture("ok").join()
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(1, "join")
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
