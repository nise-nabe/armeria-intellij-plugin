package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.JavaPsiFacade
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

import com.linecorp.intellij.plugins.armeria.message

class ArmeriaTimeoutSupportTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerArmeriaStubs()
    }

    fun testCollectTimeoutHints_scopesTimeoutCallsToRouteMethod() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }

                @Get("/other")
                public String other() {
                    requestTimeout(java.time.Duration.ofSeconds(5));
                    return "other";
                }
            }
            """.trimIndent(),
        )

        val helloMethod = findMethod("example.HelloService", "hello")
        val otherMethod = findMethod("example.HelloService", "other")

        assertEquals(emptyList<String>(), ArmeriaTimeoutSupport.collectTimeoutHints(helloMethod))
        assertEquals(
            listOf(message("route.explorer.timeout.value", message("route.explorer.timeout.request"), "java.time.Duration.ofSeconds(5)")),
            ArmeriaTimeoutSupport.collectTimeoutHints(otherMethod),
        )
    }

    fun testCollectTimeoutHints_includesBlockingAnnotation() {
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
        assertEquals(listOf(message("route.explorer.timeout.blocking")), ArmeriaTimeoutSupport.collectTimeoutHints(method))
    }

    fun testCollectTimeoutHints_ignoresTimeoutCallsInOtherMethods() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.annotation.Get;

            public class Main {
                public static void main(String[] args) {
                    Server.builder().requestTimeout(java.time.Duration.ofSeconds(30));
                }
            }

            class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val helloMethod = findMethod("example.HelloService", "hello")
        assertEquals(emptyList<String>(), ArmeriaTimeoutSupport.collectTimeoutHints(helloMethod))
    }

    fun testRouteCollector_doesNotAttachForeignTimeoutHints() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }

                @Get("/other")
                public String other() {
                    requestTimeout(java.time.Duration.ofSeconds(5));
                    return "other";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project).associateBy { it.path }
        assertEquals(emptyList<String>(), routes["/hello"]!!.timeoutHints)
        assertEquals(
            listOf(message("route.explorer.timeout.value", message("route.explorer.timeout.request"), "java.time.Duration.ofSeconds(5)")),
            routes["/other"]!!.timeoutHints,
        )
    }

    private fun findMethod(className: String, methodName: String) =
        JavaPsiFacade.getInstance(project)
            .findClass(className, myFixture.file.resolveScope)
            ?.findMethodsByName(methodName, false)
            ?.single()
            ?: error("Method not found: $className#$methodName")

    private fun registerArmeriaStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Get {
                String value() default "";
                String path() default "";
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Blocking {}
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;

            public final class Server {
                public static ServerBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;

            public final class ServerBuilder {
                public ServerBuilder requestTimeout(java.time.Duration duration) {
                    return this;
                }
            }
            """.trimIndent(),
        )
    }
}
