package com.linecorp.intellij.plugins.armeria.editor

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaRouteNavigationSupportTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() { super.setUp(); registerArmeriaStubs() }

    fun testRoutePathFromAnnotatedHandler() {
        myFixture.configureByText("HelloService.java", """
            package example;
            import com.linecorp.armeria.server.annotation.*;
            @PathPrefix("/api") public class HelloService {
                @Get("/hello") public String hello() { return "hello"; }
            }""".trimIndent())
        val method = findMethod("hello")
        assertEquals("GET", ArmeriaRouteNavigationSupport.httpMethod(method))
        assertEquals("/api/hello", ArmeriaRouteNavigationSupport.routePath(method))
    }

    fun testRelatedItemsBetweenHandlerAndRegistration() {
        myFixture.configureByText("Main.java", """
            package example; import com.linecorp.armeria.server.Server;
            public class Main { public static void main(String[] a) {
                Server.builder().annotatedService(new HelloService()).build();
            }}""".trimIndent())
        myFixture.addClass("""
            package example; import com.linecorp.armeria.server.annotation.Get;
            public class HelloService { @Get("/hello") public String hello() { return "hello"; } }
            """.trimIndent())
        val handler = findMethod("hello")
        assertEquals(1, ArmeriaRouteNavigationSupport.relatedRegistrations(handler).size)
        val reg = ArmeriaRouteNavigationSupport.relatedRegistrations(handler).single()
        assertEquals("annotatedService", reg.methodExpression.referenceName)
        assertEquals(1, ArmeriaRouteNavigationSupport.relatedHandlers(reg).size)
    }

    private fun findMethod(name: String): PsiMethod {
        val clazz = JavaPsiFacade.getInstance(project).findClass(
            "example.HelloService",
            GlobalSearchScope.projectScope(project),
        )
        assertNotNull(clazz)
        val method = clazz!!.findMethodsByName(name, false).singleOrNull()
        assertNotNull(method)
        return method!!
    }

    private fun registerArmeriaStubs() {
        myFixture.addClass("package com.linecorp.armeria.server.annotation; public @interface Get { String value() default \"\"; String path() default \"\"; }")
        myFixture.addClass("package com.linecorp.armeria.server.annotation; public @interface PathPrefix { String value(); }")
        myFixture.addClass("package com.linecorp.armeria.server; public final class Server { public static ServerBuilder builder() { return null; } }")
        myFixture.addClass("package com.linecorp.armeria.server; public final class ServerBuilder { public ServerBuilder annotatedService(Object s) { return this; } public com.linecorp.armeria.server.Server build() { return null; } }")
    }
}
