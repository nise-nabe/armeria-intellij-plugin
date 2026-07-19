package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaRouteDetailFormatter
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaAnnotatedMetadataSupportTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerArmeriaBlockingAnnotationStubs()
        registerContentAnnotationStubs()
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
        assertEquals(
            listOf(
                message("route.explorer.hint.matchesHeader", "client-type=android"),
                message("route.explorer.hint.statusCode", "201"),
                message("route.explorer.hint.consumes", "application/json"),
                message("route.explorer.hint.produces", "application/json"),
                message("route.explorer.hint.pathVariables", "id"),
            ),
            route.contentHints,
        )
        assertEquals(
            listOf(message("route.explorer.execution.blocking")),
            route.executionHints,
        )

        val attachments = ArmeriaRouteDetailFormatter.attachmentsLine(route)
        assertEquals(
            listOf(
                message("route.explorer.detail.execution", route.executionHints.joinToString()),
                message(
                    "route.explorer.detail.content",
                    route.contentHints.joinToString(" · "),
                ),
            ).joinToString("\n"),
            attachments,
        )
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
        assertEquals(
            listOf(
                message("route.explorer.hint.description", "Returns a greeting."),
                message("route.explorer.hint.description", "Greets users by name."),
                message("route.explorer.hint.pathVariables", "name"),
            ),
            route.contentHints,
        )
    }

    fun testCollectRepeatableConsumesAnnotations() {
        myFixture.configureByText(
            "ItemService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.*;

            public class ItemService {
                @Post("/items")
                @Consumes("application/json")
                @Consumes("application/xml")
                public String create() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val route = routes.single()
        assertEquals(
            listOf(message("route.explorer.hint.consumes", "application/json, application/xml")),
            route.contentHints,
        )
    }

    fun testRegexPathSkipsPathVariables() {
        myFixture.configureByText(
            "RegexService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.*;

            public class RegexService {
                @Get("regex:\\d{2,3}")
                public String match() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val route = routes.single()
        assertEquals(PathType.REGEX, route.pathType)
        assertTrue(route.contentHints.none { it.contains("Path variables") })
    }

    fun testDuplicateDescriptionIsNotRepeated() {
        myFixture.configureByText(
            "DupService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.*;

            @Description("Shared summary.")
            public class DupService {
                @Get("/")
                @Description("Shared summary.")
                public String handle() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val route = routes.single()
        assertEquals(
            listOf(message("route.explorer.hint.description", "Shared summary.")),
            route.contentHints,
        )
    }
}
