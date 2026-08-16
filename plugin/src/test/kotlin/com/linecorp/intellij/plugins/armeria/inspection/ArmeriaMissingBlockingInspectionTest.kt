package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaMissingBlockingInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerArmeriaBlockingAnnotationStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaMissingBlockingInspection())
    }

    @Test
    fun highlightsJoinWithoutBlocking() {
        myFixture.configureByText(
            "SlowService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import java.util.concurrent.CompletableFuture;

            public class SlowService {
                @Get("/slow")
                public String slow() {
                    return CompletableFuture.completedFuture("ok").join();
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(1, "join")
    }

    @Test
    fun allowsJoinWithBlocking() {
        myFixture.configureByText(
            "SlowService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Blocking;
            import com.linecorp.armeria.server.annotation.Get;
            import java.util.concurrent.CompletableFuture;

            public class SlowService {
                @Blocking
                @Get("/slow")
                public String slow() {
                    return CompletableFuture.completedFuture("ok").join();
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(0, "join")
    }

    @Test
    fun allowsJoinWithClassBlocking() {
        myFixture.configureByText(
            "SlowService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Blocking;
            import com.linecorp.armeria.server.annotation.Get;
            import java.util.concurrent.CompletableFuture;

            @Blocking
            public class SlowService {
                @Get("/slow")
                public String slow() {
                    return CompletableFuture.completedFuture("ok").join();
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(0, "join")
    }

    @Test
    fun allowsJoinWithNonBlocking() {
        myFixture.configureByText(
            "SlowService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.NonBlocking;
            import java.util.concurrent.CompletableFuture;

            public class SlowService {
                @NonBlocking
                @Get("/slow")
                public String slow() {
                    return CompletableFuture.completedFuture("ok").join();
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(0, "join")
    }

    @Test
    fun ignoresJoinOutsideAnnotatedRoute() {
        myFixture.configureByText(
            "Helper.java",
            """
            package example;

            import java.util.concurrent.CompletableFuture;

            public class Helper {
                public String slow() {
                    return CompletableFuture.completedFuture("ok").join();
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(0, "join")
    }

    @Test
    fun highlightsThreadSleepWithoutBlocking() {
        myFixture.configureByText(
            "SlowService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class SlowService {
                @Get("/slow")
                public String slow() throws Exception {
                    Thread.sleep(1);
                    return "ok";
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(1, "sleep")
    }

    @Test
    fun highlightsJdbcWithoutBlocking() {
        myFixture.addClass(
            """
            package java.sql;

            public class DriverManager {
                public static Object getConnection(String url) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "DbService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import java.sql.DriverManager;

            public class DbService {
                @Get("/db")
                public String db() throws Exception {
                    DriverManager.getConnection("jdbc:example");
                    return "ok";
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(1, "getConnection")
    }

    @Test
    fun highlightsFilesReadWithoutBlocking() {
        myFixture.configureByText(
            "FileService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import java.nio.file.Files;
            import java.nio.file.Path;

            public class FileService {
                @Get("/file")
                public String file() throws Exception {
                    return new String(Files.readAllBytes(Path.of("x")));
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(1, "readAllBytes")
    }

    @Test
    fun ignoresJoinInsideSupplyAsync() {
        myFixture.configureByText(
            "SlowService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import java.util.concurrent.CompletableFuture;

            public class SlowService {
                @Get("/slow")
                public CompletableFuture<String> slow() {
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            Thread.sleep(1);
                        } catch (Exception ignored) {
                        }
                        return "ok";
                    });
                }
            }
            """.trimIndent(),
        )
        assertBlockingHighlights(0, "sleep")
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
            "HelloService.java",
            """
            package example;

            import java.util.concurrent.CompletableFuture;

            public class HelloService extends HelloServiceImplBase {
                @Override
                public void sayHello() {
                    CompletableFuture.completedFuture("ok").join();
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
        if (expectedCount > 0) {
            assertTrue(highlights.all { it.description == expected })
        }
    }
}
