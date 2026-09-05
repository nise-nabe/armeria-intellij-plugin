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
        assertGraphqlHighlights(1, "join")
        assertExecutorHighlights(1)
    }

    @Test
    fun allowsGraphqlDataFetcherJoinWithBlockingExecutor() {
        configureGraphqlFetcher(useBlockingExecutor = true)
        assertGraphqlHighlights(0, "join")
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
        assertGraphqlHighlights(1, "join")
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
        assertGraphqlHighlights(0, "join")
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
        assertHttpHighlights(1, "join")
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
        assertHttpHighlights(1, "join")
    }

    @Test
    fun highlightsGraphqlDataFetcherPassedByVariable() {
        myFixture.configureByText(
            "UserFetcher.kt",
            """
            package example

            import com.linecorp.armeria.server.graphql.GraphqlService
            import graphql.schema.DataFetcher
            import graphql.schema.idl.TypeRuntimeWiring
            import java.util.concurrent.CompletableFuture

            class Server {
                fun graphql(): Any {
                    val fetcher = UserFetcher()
                    return GraphqlService.builder()
                        .runtimeWiring { TypeRuntimeWiring().dataFetcher("user", fetcher) }
                        .build()
                }
            }

            class UserFetcher : DataFetcher<String> {
                override fun get(env: Any): String =
                    CompletableFuture.completedFuture("ok").join()
            }
            """.trimIndent(),
        )
        assertGraphqlHighlights(1, "join")
        assertExecutorHighlights(1)
    }

    @Test
    fun ignoresUnregisteredDataFetcherJoin() {
        myFixture.configureByText(
            "UserFetcher.kt",
            """
            package example

            import graphql.schema.DataFetcher
            import java.util.concurrent.CompletableFuture

            class UserFetcher : DataFetcher<String> {
                override fun get(env: Any): String =
                    CompletableFuture.completedFuture("ok").join()
            }
            """.trimIndent(),
        )
        assertGraphqlHighlights(0, "join")
    }

    @Test
    fun graphqlDataFetcherDoesNotOfferBlockingQuickFix() {
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
                        .build()
            }

            class UserFetcher : DataFetcher<String> {
                override fun get(env: Any): String =
                    CompletableFuture.completedFuture("ok").<caret>join()
            }
            """.trimIndent(),
        )
        assertGraphqlHighlights(1, "join")
        myFixture.doHighlighting()
        val names = myFixture.getAvailableQuickFixes().map { it.text }
        assertTrue(message("inspection.missing.blocking.quickfix.method") !in names)
    }

    @Test
    fun addUseBlockingTaskExecutorQuickFixRemovesHighlight() {
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
                    GraphqlService.<caret>builder()
                        .runtimeWiring { TypeRuntimeWiring().dataFetcher("user", UserFetcher()) }
                        .build()
            }

            class UserFetcher : DataFetcher<String> {
                override fun get(env: Any): String =
                    CompletableFuture.completedFuture("ok").join()
            }
            """.trimIndent(),
        )
        assertExecutorHighlights(1)
        assertGraphqlHighlights(1, "join")
        applyQuickFix(message("inspection.missing.blocking.quickfix.executor"))
        assertTrue(myFixture.file.text.contains("useBlockingTaskExecutor(true)"))
        assertExecutorHighlights(0)
        assertGraphqlHighlights(0, "join")
    }

    @Test
    fun addUseBlockingTaskExecutorQuickFixRewritesFalse() {
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
                    GraphqlService.<caret>builder()
                        .runtimeWiring { TypeRuntimeWiring().dataFetcher("user", UserFetcher()) }
                        .useBlockingTaskExecutor(false)
                        .build()
            }

            class UserFetcher : DataFetcher<String> {
                override fun get(env: Any): String =
                    CompletableFuture.completedFuture("ok").join()
            }
            """.trimIndent(),
        )
        assertExecutorHighlights(1)
        applyQuickFix(message("inspection.missing.blocking.quickfix.executor"))
        assertTrue(myFixture.file.text.contains("useBlockingTaskExecutor(true)"))
        assertTrue(!myFixture.file.text.contains("useBlockingTaskExecutor(false)"))
        assertExecutorHighlights(0)
        assertGraphqlHighlights(0, "join")
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

    @Test
    fun addClassBlockingQuickFixWhenEveryMethodBlocks() {
        myFixture.configureByText(
            "SlowService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import java.util.concurrent.CompletableFuture

            class SlowService {
                @Get("/a")
                fun a(): String = CompletableFuture.completedFuture("ok").<caret>join()

                @Get("/b")
                fun b(): String = CompletableFuture.completedFuture("ok").join()
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(2, "join")
        applyQuickFix(message("inspection.missing.blocking.quickfix.class"))
        assertBlockingHighlights(0, "join")
        assertTrue(myFixture.file.text.contains("@Blocking"))
    }

    @Test
    fun ignoresUnrelatedRuntimeWiringFluentApi() {
        myFixture.configureByText(
            "Other.kt",
            """
            package example

            import java.util.concurrent.CompletableFuture

            class Other {
                fun runtimeWiring(configurer: (Any) -> Unit): Other = this

                fun setup() {
                    runtimeWiring { CompletableFuture.completedFuture("ok").join() }
                }
            }
            """.trimIndent(),
        )
        assertGraphqlHighlights(0, "join")
        assertExecutorHighlights(0)
    }

    @Test
    fun highlightsGraphqlJoinNestedInsideUnrelatedRuntimeWiring() {
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
                        .runtimeWiring {
                            Other().runtimeWiring {
                                TypeRuntimeWiring().dataFetcher("user") { env ->
                                    CompletableFuture.completedFuture("ok").join()
                                }
                            }
                        }
                        .build()
            }

            class Other {
                fun runtimeWiring(configurer: (Any) -> Unit): Other = this
            }
            """.trimIndent(),
        )
        assertGraphqlHighlights(1, "join")
        assertExecutorHighlights(1)
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
        messageKey: String = "inspection.missing.blocking.problem",
    ) {
        val expected = message(messageKey, methodName)
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(expectedCount, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }

    private fun assertGraphqlHighlights(
        expectedCount: Int,
        methodName: String,
    ) {
        assertBlockingHighlights(expectedCount, methodName, "inspection.missing.blocking.problem.graphql")
    }

    private fun assertHttpHighlights(
        expectedCount: Int,
        methodName: String,
    ) {
        assertBlockingHighlights(expectedCount, methodName, "inspection.missing.blocking.problem.httpservice")
    }

    private fun assertExecutorHighlights(expectedCount: Int) {
        val expected = message("inspection.missing.blocking.graphql.executor")
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(expectedCount, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }
}
