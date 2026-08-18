package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.openapi.application.ApplicationManager
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaMissingBlockingExtendedKotlinInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerArmeriaBlockingAnnotationStubs()
        myFixture.registerMissingBlockingGraphqlStubs()
        myFixture.registerMissingBlockingHttpServiceStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaMissingBlockingKotlinInspection())
    }

    @Test
    fun highlightsGraphqlDataFetcherJoin() {
        configureGraphqlFetcher(useBlockingExecutor = false)
        assertBlockingHighlights(1, "join")
        assertExecutorHighlights(1)
    }

    @Test
    fun allowsGraphqlDataFetcherJoinWithBlockingExecutor() {
        configureGraphqlFetcher(useBlockingExecutor = true)
        assertBlockingHighlights(0, "join")
        assertExecutorHighlights(0)
    }

    @Test
    fun highlightsGraphqlDataFetcherLambdaJoin() {
        myFixture.configureByText(
            "Server.kt",
            """
            package example

            import com.linecorp.armeria.server.graphql.GraphqlService
            import graphql.schema.idl.TypeRuntimeWiring
            import java.util.concurrent.CompletableFuture

            class Server {
                fun graphql(): Any =
                    GraphqlService.builder()
                        .runtimeWiring { TypeRuntimeWiring().dataFetcher("user") { env ->
                            CompletableFuture.completedFuture("ok").join()
                        } }
                        .build()
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(1, "join")
        assertExecutorHighlights(1)
    }

    @Test
    fun allowsGraphqlDataFetcherLambdaWithBlockingExecutor() {
        myFixture.configureByText(
            "Server.kt",
            """
            package example

            import com.linecorp.armeria.server.graphql.GraphqlService
            import graphql.schema.idl.TypeRuntimeWiring
            import java.util.concurrent.CompletableFuture

            class Server {
                fun graphql(): Any =
                    GraphqlService.builder()
                        .runtimeWiring { TypeRuntimeWiring().dataFetcher("user") { env ->
                            CompletableFuture.completedFuture("ok").join()
                        } }
                        .useBlockingTaskExecutor(true)
                        .build()
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(0, "join")
        assertExecutorHighlights(0)
    }

    @Test
    fun highlightsHttpServiceServeJoin() {
        myFixture.configureByText(
            "MyService.kt",
            """
            package example

            import com.linecorp.armeria.server.HttpService
            import java.util.concurrent.CompletableFuture

            class MyService : HttpService {
                override fun serve(ctx: Any, req: Any): Any =
                    CompletableFuture.completedFuture("ok").join()
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(1, "join")
    }

    @Test
    fun highlightsAbstractHttpServiceDoGetJoin() {
        myFixture.configureByText(
            "MyService.kt",
            """
            package example

            import com.linecorp.armeria.server.AbstractHttpService
            import java.util.concurrent.CompletableFuture

            class MyService : AbstractHttpService() {
                override fun doGet(ctx: Any, req: Any): Any =
                    CompletableFuture.completedFuture("ok").join()
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(1, "join")
    }

    @Test
    fun addBlockingQuickFixRemovesHighlight() {
        myFixture.configureByText(
            "SlowService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import java.util.concurrent.CompletableFuture

            class SlowService {
                @Get("/slow")
                fun slow(): String = CompletableFuture.completedFuture("ok").<caret>join()
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(1, "join")
        applyQuickFix(message("inspection.missing.blocking.quickfix.method"))
        assertBlockingHighlights(0, "join")
        assertTrue(myFixture.file.text.contains("@Blocking"))
    }

    private fun configureGraphqlFetcher(useBlockingExecutor: Boolean) {
        val executorCall =
            if (useBlockingExecutor) {
                ".useBlockingTaskExecutor(true)"
            } else {
                ""
            }
        myFixture.configureByText(
            "UserFetcher.kt",
            """
            package example

            import com.linecorp.armeria.server.graphql.GraphqlService
            import graphql.schema.DataFetcher
            import graphql.schema.idl.TypeRuntimeWiring
            import java.util.concurrent.CompletableFuture

            class Server {
                fun graphql(): Any =
                    GraphqlService.builder()
                        .runtimeWiring { TypeRuntimeWiring().dataFetcher("user", UserFetcher()) }
                        $executorCall
                        .build()
            }

            class UserFetcher : DataFetcher<String> {
                override fun get(env: Any): String =
                    CompletableFuture.completedFuture("ok").join()
            }
            """.trimIndent(),
        )
    }

    private fun applyQuickFix(name: String) {
        myFixture.doHighlighting()
        val quickFix = myFixture.getAvailableQuickFixes().first { it.text == name }
        ApplicationManager.getApplication().invokeAndWait {
            myFixture.launchAction(quickFix)
        }
    }

    private fun assertBlockingHighlights(
        expectedCount: Int,
        methodName: String,
    ) {
        val expected = message("inspection.missing.blocking.problem", methodName)
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(expectedCount, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }

    private fun assertExecutorHighlights(expectedCount: Int) {
        val expected = message("inspection.missing.blocking.graphql.executor")
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(expectedCount, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }
}
