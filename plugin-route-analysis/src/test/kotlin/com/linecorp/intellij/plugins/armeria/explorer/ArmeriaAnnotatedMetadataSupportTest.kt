package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaRouteDetailFormatter
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    fun testCollectJsonHelperMediaTypes() {
        myFixture.configureByText(
            "JsonService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.*;

            public class JsonService {
                @Post("/items")
                @ConsumesJson
                @ProducesJson
                public String create() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val route = routes.single()
        assertEquals(
            listOf(
                message("route.explorer.hint.consumes", "application/json"),
                message("route.explorer.hint.produces", "application/json"),
            ),
            route.contentHints,
        )
    }

    fun testCollectClassLevelJsonHelperMediaTypes() {
        myFixture.configureByText(
            "JsonService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.*;

            @ConsumesJson
            @ProducesJson
            public class JsonService {
                @Post("/items")
                public String create() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val route = routes.single()
        assertEquals(
            listOf(
                message("route.explorer.hint.consumes", "application/json"),
                message("route.explorer.hint.produces", "application/json"),
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

    fun testCollectMatchesParamAndDefault() {
        myFixture.configureByText(
            "ItemService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.*;

            public class ItemService {
                @Get("/items")
                @MatchesParam("env=prod")
                public String list(@Param("limit") @Default("20") String limit) {
                    return limit;
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val route = routes.single()
        assertEquals(
            listOf(
                message("route.explorer.hint.matchesParam", "env=prod"),
                message("route.explorer.hint.default", "limit=20"),
            ),
            route.contentHints,
        )
    }

    fun testCollectProducesTextHelper() {
        myFixture.configureByText(
            "TextService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.*;

            public class TextService {
                @Get("/plain")
                @ProducesText
                public String plain() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val route = routes.single()
        assertEquals(
            listOf(message("route.explorer.hint.produces", "text/plain")),
            route.contentHints,
        )
    }

    fun testCollectMatchesParamFromKotlin() {
        myFixture.configureByText(
            "ItemService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.MatchesParam

            class ItemService {
                @Get("/items")
                @MatchesParam("env=prod")
                fun list(): String = "ok"
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val route = routes.single()
        assertEquals(
            listOf(message("route.explorer.hint.matchesParam", "env=prod")),
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

    fun testRegexNamedGroupsArePathVariables() {
        myFixture.configureByText(
            "RegexService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.*;

            public class RegexService {
                @Get("regex:^(?<id>\\d+)$")
                public String match() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val route = routes.single()
        assertEquals(PathType.REGEX, route.pathType)
        assertEquals(
            listOf(message("route.explorer.hint.pathVariables", "id")),
            route.contentHints,
        )
    }

    fun testGlobWildcardsArePathVariables() {
        myFixture.configureByText(
            "GlobService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.*;

            public class GlobService {
                @Get("glob:/*/hello/**")
                public String match() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val route = routes.single()
        assertEquals(PathType.GLOB, route.pathType)
        assertEquals(
            listOf(message("route.explorer.hint.pathVariables", "0, 1")),
            route.contentHints,
        )
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
