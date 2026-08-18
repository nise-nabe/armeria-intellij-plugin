package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.openapi.application.ApplicationManager
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaMissingBlockingExtendedInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerArmeriaBlockingAnnotationStubs()
        myFixture.registerMissingBlockingGraphqlStubs()
        myFixture.registerMissingBlockingHttpServiceStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaMissingBlockingInspection())
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
    fun allowsGraphqlDataFetcherJoinWithBlockingAnnotation() {
        myFixture.configureByText(
            "UserFetcher.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Blocking;
            import com.linecorp.armeria.server.graphql.GraphqlService;
            import graphql.schema.DataFetcher;
            import graphql.schema.idl.TypeRuntimeWiring;
            import java.util.concurrent.CompletableFuture;

            public class Server {
                public Object graphql() {
                    return GraphqlService.builder()
                            .runtimeWiring(c -> new TypeRuntimeWiring().dataFetcher("user", new UserFetcher()))
                            .build();
                }
            }

            class UserFetcher implements DataFetcher<String> {
                @Blocking
                public String get(Object env) {
                    return CompletableFuture.completedFuture("ok").join();
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(0, "join")
        assertExecutorHighlights(0)
    }

    @Test
    fun ignoresUnregisteredDataFetcherJoin() {
        myFixture.configureByText(
            "UserFetcher.java",
            """
            package example;

            import graphql.schema.DataFetcher;
            import java.util.concurrent.CompletableFuture;

            class UserFetcher implements DataFetcher<String> {
                public String get(Object env) {
                    return CompletableFuture.completedFuture("ok").join();
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(0, "join")
        assertExecutorHighlights(0)
    }

    @Test
    fun highlightsGraphqlDataFetcherLambdaJoin() {
        myFixture.configureByText(
            "Server.java",
            """
            package example;

            import com.linecorp.armeria.server.graphql.GraphqlService;
            import graphql.schema.idl.TypeRuntimeWiring;
            import java.util.concurrent.CompletableFuture;

            public class Server {
                public Object graphql() {
                    return GraphqlService.builder()
                            .runtimeWiring(c -> new TypeRuntimeWiring().dataFetcher("user", env -> {
                                return CompletableFuture.completedFuture("ok").join();
                            }))
                            .build();
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(1, "join")
        assertExecutorHighlights(1)
    }

    @Test
    fun allowsGraphqlDataFetcherLambdaWithBlockingExecutor() {
        myFixture.configureByText(
            "Server.java",
            """
            package example;

            import com.linecorp.armeria.server.graphql.GraphqlService;
            import graphql.schema.idl.TypeRuntimeWiring;
            import java.util.concurrent.CompletableFuture;

            public class Server {
                public Object graphql() {
                    return GraphqlService.builder()
                            .runtimeWiring(c -> new TypeRuntimeWiring().dataFetcher("user", env -> {
                                return CompletableFuture.completedFuture("ok").join();
                            }))
                            .useBlockingTaskExecutor(true)
                            .build();
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(0, "join")
        assertExecutorHighlights(0)
    }

    @Test
    fun highlightsHttpServiceServeJoin() {
        myFixture.configureByText(
            "MyService.java",
            """
            package example;

            import com.linecorp.armeria.server.HttpService;
            import java.util.concurrent.CompletableFuture;

            public class MyService implements HttpService {
                @Override
                public Object serve(Object ctx, Object req) {
                    return CompletableFuture.completedFuture("ok").join();
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(1, "join")
    }

    @Test
    fun highlightsAbstractHttpServiceDoGetJoin() {
        myFixture.configureByText(
            "MyService.java",
            """
            package example;

            import com.linecorp.armeria.server.AbstractHttpService;
            import java.util.concurrent.CompletableFuture;

            public class MyService extends AbstractHttpService {
                @Override
                protected Object doGet(Object ctx, Object req) {
                    return CompletableFuture.completedFuture("ok").join();
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(1, "join")
    }

    @Test
    fun allowsHttpServiceServeWithBlocking() {
        myFixture.configureByText(
            "MyService.java",
            """
            package example;

            import com.linecorp.armeria.server.HttpService;
            import com.linecorp.armeria.server.annotation.Blocking;
            import java.util.concurrent.CompletableFuture;

            public class MyService implements HttpService {
                @Blocking
                @Override
                public Object serve(Object ctx, Object req) {
                    return CompletableFuture.completedFuture("ok").join();
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(0, "join")
    }

    @Test
    fun addBlockingQuickFixRemovesHighlight() {
        myFixture.configureByText(
            "SlowService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import java.util.concurrent.CompletableFuture;

            public class SlowService {
                @Get("/slow")
                public String slow() {
                    return CompletableFuture.completedFuture("ok").<caret>join();
                }
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
            "SlowService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import java.util.concurrent.CompletableFuture;

            public class SlowService {
                @Get("/a")
                public String a() {
                    return CompletableFuture.completedFuture("ok").<caret>join();
                }

                @Get("/b")
                public String b() {
                    return CompletableFuture.completedFuture("ok").join();
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(2, "join")
        applyQuickFix(message("inspection.missing.blocking.quickfix.class"))
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
            "UserFetcher.java",
            """
            package example;

            import com.linecorp.armeria.server.graphql.GraphqlService;
            import graphql.schema.DataFetcher;
            import graphql.schema.idl.TypeRuntimeWiring;
            import java.util.concurrent.CompletableFuture;

            public class Server {
                public Object graphql() {
                    return GraphqlService.builder()
                            .runtimeWiring(c -> new TypeRuntimeWiring().dataFetcher("user", new UserFetcher()))
                            $executorCall
                            .build();
                }
            }

            class UserFetcher implements DataFetcher<String> {
                public String get(Object env) {
                    return CompletableFuture.completedFuture("ok").join();
                }
            }
            """.trimIndent(),
        )
    }

    private fun applyQuickFix(name: String) {
        myFixture.doHighlighting()
        val quickFix = myFixture.getAvailableQuickFixes().single { it.text == name }
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
