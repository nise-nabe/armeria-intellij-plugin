package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaTimeoutSupportTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerArmeriaBlockingAnnotationStubs()
        registerArmeriaServerStubs()
    }

    fun testCollectBuilderTimeoutHints_requestTimeoutOnBuilderChain() {
        myFixture.addClass(
            """
            package java.time;

            public final class Duration {
                public static Duration ofSeconds(long seconds) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import java.time.Duration;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .requestTimeout(Duration.ofSeconds(5))
                        .service("/api", new HelloService());
                }
            }

            class HelloService {}
            """.trimIndent(),
        )

        val serviceCall = findServiceRegistrationCall("example.Main", "main")
        assertEquals(
            listOf(message("route.explorer.timeout.request", "Duration.ofSeconds(5)")),
            ArmeriaTimeoutSupport.collectBuilderTimeoutHints(serviceCall),
        )
    }

    fun testCollectBuilderTimeoutHints_skipsNoArgTimeoutCalls() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .requestTimeout()
                        .service("/api", new HelloService());
                }
            }

            class HelloService {}
            """.trimIndent(),
        )

        val serviceCall = findServiceRegistrationCall("example.Main", "main")
        assertEquals(
            listOf(message("route.explorer.timeout.request", "…")),
            ArmeriaTimeoutSupport.collectBuilderTimeoutHints(serviceCall),
        )
    }

    fun testCollectBuilderTimeoutHints_parenthesizedBuilderChain() {
        myFixture.addClass(
            """
            package java.time;

            public final class Duration {
                public static Duration ofSeconds(long seconds) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import java.time.Duration;

            public class Main {
                public static void main(String[] args) {
                    (Server.builder().requestTimeout(Duration.ofSeconds(5)))
                        .service("/api", new HelloService());
                }
            }

            class HelloService {}
            """.trimIndent(),
        )

        val serviceCall = findServiceRegistrationCall("example.Main", "main")
        assertEquals(
            listOf(message("route.explorer.timeout.request", "Duration.ofSeconds(5)")),
            ArmeriaTimeoutSupport.collectBuilderTimeoutHints(serviceCall),
        )
    }

    fun testCollectBuilderTimeoutHints_ignoresResolvedNonArmeriaTimeoutCalls() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/api", new HelloService());
                }
            }

            class HelloService {}
            """.trimIndent(),
        )

        val serviceCall = findServiceRegistrationCall("example.Main", "main")
        assertEquals(emptyList<String>(), ArmeriaTimeoutSupport.collectBuilderTimeoutHints(serviceCall))
    }

    fun testCollectExecutionHints_includesBlockingAnnotation() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Blocking;
            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Blocking
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val method = findMethod("example.HelloService", "hello")
        assertEquals(listOf(message("route.explorer.execution.blocking")), ArmeriaTimeoutSupport.collectExecutionHints(method))
    }

    fun testCollectExecutionHints_includesClassLevelBlockingAnnotation() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Blocking;
            import com.linecorp.armeria.server.annotation.Get;

            @Blocking
            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val method = findMethod("example.HelloService", "hello")
        assertEquals(listOf(message("route.explorer.execution.blocking")), ArmeriaTimeoutSupport.collectExecutionHints(method))
    }

    fun testCollectExecutionHints_prefersMethodAnnotationOverClassAnnotation() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Blocking;
            import com.linecorp.armeria.server.annotation.NonBlocking;
            import com.linecorp.armeria.server.annotation.Get;

            @NonBlocking
            public class HelloService {
                @Blocking
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val method = findMethod("example.HelloService", "hello")
        assertEquals(listOf(message("route.explorer.execution.blocking")), ArmeriaTimeoutSupport.collectExecutionHints(method))
    }

    fun testRouteCollector_doesNotAttachForeignTimeoutHints() {
        myFixture.addClass(
            """
            package java.time;

            public final class Duration {
                public static Duration ofSeconds(long seconds) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.annotation.Get;
            import java.time.Duration;

            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }

                @Get("/other")
                public String other() {
                    Server.builder().requestTimeout(Duration.ofSeconds(5));
                    return "other";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project).associateBy { it.path }
        assertEquals(emptyList<String>(), routes["/hello"]!!.timeoutHints)
        assertEquals(emptyList<String>(), routes["/hello"]!!.executionHints)
        assertEquals(emptyList<String>(), routes["/other"]!!.timeoutHints)
        assertEquals(emptyList<String>(), routes["/other"]!!.executionHints)
    }

    private fun findMethod(className: String, methodName: String) =
        JavaPsiFacade.getInstance(project)
            .findClass(className, myFixture.file.resolveScope)
            ?.findMethodsByName(methodName, false)
            ?.single()
            ?: error("Method not found: $className#$methodName")

    private fun findServiceRegistrationCall(className: String, methodName: String): PsiMethodCallExpression {
        val method = findMethod(className, methodName)
        return PsiTreeUtil.findChildrenOfType(method, PsiMethodCallExpression::class.java)
            .first { it.methodExpression.referenceName == "service" }
    }
}
