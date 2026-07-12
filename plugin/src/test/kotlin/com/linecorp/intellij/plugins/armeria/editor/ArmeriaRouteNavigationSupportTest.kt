package com.linecorp.intellij.plugins.armeria.editor

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

class ArmeriaRouteNavigationSupportTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerArmeriaStubs()
    }

    fun testRoutePathFromAnnotatedHandler() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;
            import com.linecorp.armeria.server.annotation.*;
            @PathPrefix("/api") public class HelloService {
                @Get("/hello") public String hello() { return "hello"; }
            }
            """.trimIndent(),
        )
        val method = findMethod("hello")
        assertEquals("GET", ArmeriaRouteNavigationSupport.httpMethod(method))
        assertEquals("/api/hello", ArmeriaRouteNavigationSupport.routePath(method))
    }

    fun testRelatedItemsBetweenHandlerAndServiceRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example; import com.linecorp.armeria.server.Server;
            public class Main { public static void main(String[] a) {
                Server.builder().service("/api", new HelloService()).build();
            }}
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example; import com.linecorp.armeria.server.annotation.Get;
            public class HelloService { @Get("/hello") public String hello() { return "hello"; } }
            """.trimIndent(),
        )
        val handler = findMethod("hello")
        assertEquals(1, ArmeriaRouteNavigationSupport.relatedRegistrations(handler).size)
        val reg = ArmeriaRouteNavigationSupport.relatedRegistrations(handler).single()
        assertTrue(reg is com.intellij.psi.PsiMethodCallExpression)
        assertEquals("service", (reg as com.intellij.psi.PsiMethodCallExpression).methodExpression.referenceName)
        assertEquals(1, ArmeriaRouteNavigationSupport.relatedHandlers(reg).size)
    }

    fun testRelatedItemsBetweenHandlerAndRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example; import com.linecorp.armeria.server.Server;
            public class Main { public static void main(String[] a) {
                Server.builder().annotatedService(new HelloService()).build();
            }}
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example; import com.linecorp.armeria.server.annotation.Get;
            public class HelloService { @Get("/hello") public String hello() { return "hello"; } }
            """.trimIndent(),
        )
        val handler = findMethod("hello")
        assertEquals(1, ArmeriaRouteNavigationSupport.relatedRegistrations(handler).size)
        val reg = ArmeriaRouteNavigationSupport.relatedRegistrations(handler).single()
        assertTrue(reg is com.intellij.psi.PsiMethodCallExpression)
        assertEquals("annotatedService", (reg as com.intellij.psi.PsiMethodCallExpression).methodExpression.referenceName)
        assertEquals(1, ArmeriaRouteNavigationSupport.relatedHandlers(reg).size)
    }

    fun testKotlinRoutePathFromAnnotatedHandler() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.PathPrefix

            @PathPrefix("/api")
            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )

        val function = PsiTreeUtil.findChildOfType(myFixture.file, KtNamedFunction::class.java)!!
        assertEquals("GET", ArmeriaRouteNavigationSupport.httpMethod(function))
        assertEquals("/api/hello", ArmeriaRouteNavigationSupport.routePath(function))
    }

    fun testRelatedItemsBetweenKotlinHandlerAndRegistration() {
        val helloServiceFile = myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .annotatedService(HelloService())
                    .build()
            }
            """.trimIndent(),
        )

        val mainFile = myFixture.file
        val registrationCall = PsiTreeUtil.collectElementsOfType(mainFile, KtCallExpression::class.java)
            .first { it.text.contains("annotatedService") }
        assertEquals(1, ArmeriaRouteNavigationSupport.relatedHandlers(registrationCall).size)

        val function = PsiTreeUtil.findChildOfType(helloServiceFile, KtNamedFunction::class.java)!!
        assertEquals(1, ArmeriaRouteNavigationSupport.relatedRegistrations(function).size)
        assertTrue(ArmeriaRouteNavigationSupport.relatedRegistrations(function).single() is KtCallExpression)
    }

    fun testRelatedItemsWithJavaVariableReference() {
        myFixture.configureByText(
            "Main.java",
            """
            package com.acme;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    HelloService service = new HelloService();
                    Server.builder().annotatedService(service).build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.acme;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() { return "hello"; }
            }
            """.trimIndent(),
        )

        val handler = findMethod("com.acme.HelloService", "hello")
        assertEquals(1, ArmeriaRouteNavigationSupport.relatedRegistrations(handler).size)
        val registration = ArmeriaRouteNavigationSupport.relatedRegistrations(handler).single()
        assertEquals(1, ArmeriaRouteNavigationSupport.relatedHandlers(registration).size)
    }

    fun testRelatedItemsWithKotlinVariableReference() {
        val helloServiceFile = myFixture.configureByText(
            "HelloService.kt",
            """
            package com.acme

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.kt",
            """
            package com.acme

            import com.linecorp.armeria.server.Server

            fun main() {
                val service = HelloService()
                Server.builder()
                    .annotatedService(service)
                    .build()
            }
            """.trimIndent(),
        )

        val function = PsiTreeUtil.findChildOfType(helloServiceFile, KtNamedFunction::class.java)!!
        assertEquals(1, ArmeriaRouteNavigationSupport.relatedRegistrations(function).size)
        val registration = ArmeriaRouteNavigationSupport.relatedRegistrations(function).single()
        assertEquals(1, ArmeriaRouteNavigationSupport.relatedHandlers(registration).size)
    }

    fun testRoutePathJoinsMultiplePaths() {
        myFixture.configureByText(
            "MultiPathService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class MultiPathService {
                @Get({"/one", "/two"})
                public String multi() { return "multi"; }
            }
            """.trimIndent(),
        )

        val method = findMethod("example.MultiPathService", "multi")
        assertEquals("/one, /two", ArmeriaRouteNavigationSupport.routePath(method))
    }

    fun testRoutePathJoinsGetAnnotationWithPathAnnotation() {
        myFixture.configureByText(
            "PathAnnotationService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Path;

            public class PathAnnotationService {
                @Get
                @Path("/hello")
                public String hello() { return "hello"; }
            }
            """.trimIndent(),
        )

        val method = findMethod("example.PathAnnotationService", "hello")
        assertEquals("/hello", ArmeriaRouteNavigationSupport.routePath(method))
    }

    fun testRoutePathPreservesPathTypePrefix() {
        myFixture.configureByText(
            "PrefixService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.PathPrefix;

            @PathPrefix("/api")
            public class PrefixService {
                @Get("prefix:/hello")
                public String hello() { return "hello"; }
            }
            """.trimIndent(),
        )

        val method = findMethod("example.PrefixService", "hello")
        assertEquals("prefix:/api/hello", ArmeriaRouteNavigationSupport.routePath(method))
    }

    fun testRoutePathPreservesRegexPathTypePrefix() {
        myFixture.configureByText(
            "RegexService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.PathPrefix;

            @PathPrefix("regex:^/api")
            public class RegexService {
                @Get("regex:^/hello$")
                public String hello() { return "hello"; }
            }
            """.trimIndent(),
        )

        val method = findMethod("example.RegexService", "hello")
        assertEquals("regex:^/api/hello$", ArmeriaRouteNavigationSupport.routePath(method))
    }

    fun testFindClassByTargetResolvesSimpleClassName() {
        myFixture.addClass("package services; public class HelloService {}")

        val resolved = ArmeriaRouteNavigationSupport.findClassByTarget(project, "HelloService")

        assertNotNull(resolved)
        assertEquals("services.HelloService", resolved!!.qualifiedName)
    }

    fun testFindClassByTargetReturnsNullWhenAmbiguous() {
        myFixture.addClass("package foo; public class Dup {}")
        myFixture.addClass("package bar; public class Dup {}")

        assertNull(ArmeriaRouteNavigationSupport.findClassByTarget(project, "Dup"))
    }

    fun testRelatedHandlersResolvesNewExpressionWhenSimpleNameIsAmbiguous() {
        myFixture.configureByText(
            "Main.java",
            """
            package app;

            import com.linecorp.armeria.server.Server;
            import services.HelloService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder().annotatedService(new HelloService()).build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package services;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() { return "hello"; }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package other; public class HelloService {}")

        val handler = findMethod("services.HelloService", "hello")
        val registration = ArmeriaRouteNavigationSupport.relatedRegistrations(handler).single()
        assertEquals(1, ArmeriaRouteNavigationSupport.relatedHandlers(registration).size)
    }

    fun testRelatedHandlersResolvesUnqualifiedServiceClass() {
        myFixture.configureByText(
            "Main.java",
            """
            package app;

            import com.linecorp.armeria.server.Server;
            import services.HelloService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder().annotatedService(new HelloService()).build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package services;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() { return "hello"; }
            }
            """.trimIndent(),
        )

        val handler = findMethod("services.HelloService", "hello")
        val registration = ArmeriaRouteNavigationSupport.relatedRegistrations(handler).single()
        assertEquals(1, ArmeriaRouteNavigationSupport.relatedHandlers(registration).size)
    }

    private fun findMethod(className: String, name: String): PsiMethod {
        val clazz = JavaPsiFacade.getInstance(project).findClass(
            className,
            GlobalSearchScope.projectScope(project),
        )
        assertNotNull(clazz)
        val method = clazz!!.findMethodsByName(name, false).singleOrNull()
        assertNotNull(method)
        return method!!
    }

    private fun findMethod(name: String): PsiMethod = findMethod("example.HelloService", name)

    private fun registerArmeriaStubs() {
        myFixture.addClass("package com.linecorp.armeria.server.annotation; public @interface Get { String[] value() default {}; String[] path() default {}; }")
        myFixture.addClass("package com.linecorp.armeria.server.annotation; public @interface Path { String[] value() default {}; }")
        myFixture.addClass("package com.linecorp.armeria.server.annotation; public @interface PathPrefix { String value(); }")
        myFixture.addClass("package com.linecorp.armeria.server; public final class Server { public static ServerBuilder builder() { return null; } }")
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;
            public final class ServerBuilder {
                public ServerBuilder service(String path, Object handler) { return this; }
                public ServerBuilder annotatedService(Object service) { return this; }
                public com.linecorp.armeria.server.Server build() { return null; }
            }
            """.trimIndent(),
        )
    }
}
