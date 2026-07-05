package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaAnnotatedMetadataSupportTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerStubs()
    }

    fun testCollectContentHintsForAnnotatedRoute() {
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.*;

            @Blocking
            public class UserService {
                @StatusCode(201)
                @Post("/users/{id}")
                @Consumes("application/json")
                @Produces("application/json")
                @MatchesHeader("client-type=android")
                public String create(@Param("id") String id) {
                    return id;
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val route = routes.single()
        assertTrue(route.contentHints.any { it.contains("201") })
        assertTrue(route.contentHints.any { it.contains("application/json") })
        assertTrue(route.contentHints.any { it.contains("client-type=android") })
        assertTrue(route.contentHints.any { it.contains("id") })
        assertTrue(route.contentHints.any { it.contains("Blocking") })
    }

    fun testCollectColonStylePathVariablesAndDescription() {
        myFixture.configureByText(
            "GreetService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.*;

            @Description("Greets users by name.")
            public class GreetService {
                @Get("/hello/:name")
                @Description("Returns a greeting.")
                public String greet(@Param("name") String name) {
                    return name;
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val route = routes.single()
        assertTrue(route.contentHints.any { it.contains("name") })
        assertTrue(route.contentHints.any { it.contains("Returns a greeting") })
        assertTrue(route.contentHints.any { it.contains("Greets users by name") })
    }

    private fun registerStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;
            public @interface Get { String value() default ""; String path() default ""; }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;
            public @interface Post { String value() default ""; String path() default ""; }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;
            public @interface Param { String value() default ""; }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;
            public @interface StatusCode { int value(); }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;
            public @interface Consumes { String[] value(); }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;
            public @interface Produces { String[] value(); }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;
            public @interface MatchesHeader { String value(); }
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
            package com.linecorp.armeria.server.annotation;
            public @interface Description { String value() default ""; }
            """.trimIndent(),
        )
    }
}
