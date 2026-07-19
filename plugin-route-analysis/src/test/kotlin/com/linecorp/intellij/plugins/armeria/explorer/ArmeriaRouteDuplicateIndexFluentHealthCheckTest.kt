package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.duplicate.ArmeriaRouteDuplicateIndex
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaRouteDuplicateIndexFluentHealthCheckTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerRouteDuplicateIndexStubs()
    }

    fun testDuplicateFluentRoutesOnSamePathAreReported() {
        myFixture.configureByText(
            "FirstMain.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class FirstMain {
                public static void main(String[] args) {
                    Server.builder()
                        .route()
                        .post("/dup")
                        .build(new FirstHandler())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class SecondMain {
                public static void main(String[] args) {
                    Server.builder()
                        .route()
                        .post("/dup")
                        .build(new SecondHandler())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class FirstHandler {}")
        myFixture.addClass("package example; public class SecondHandler {}")

        val groups = ArmeriaRouteDuplicateIndex.duplicateGroups(project)

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().routes.size)
        assertTrue(groups.single().routes.all { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testFluentRouteWithMultipleMethodsConflictsWithSingleMethodRoute() {
        myFixture.configureByText(
            "FirstMain.java",
            """
            package example;

            import com.linecorp.armeria.common.HttpMethod;
            import com.linecorp.armeria.server.Server;

            public class FirstMain {
                public static void main(String[] args) {
                    Server.builder()
                        .route()
                        .path("/dup")
                        .methods(HttpMethod.POST, HttpMethod.PUT)
                        .build(new FirstHandler())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class SecondMain {
                public static void main(String[] args) {
                    Server.builder()
                        .route()
                        .post("/dup")
                        .build(new SecondHandler())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.common;

            public final class HttpMethod {
                public static final HttpMethod POST = new HttpMethod();
                public static final HttpMethod PUT = new HttpMethod();
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class FirstHandler {}")
        myFixture.addClass("package example; public class SecondHandler {}")

        val groups = ArmeriaRouteDuplicateIndex.duplicateGroups(project)

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().routes.size)
    }

    fun testFluentRouteWithPathPrefixConflictsWithAnnotatedRoute() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .route()
                        .pathPrefix("/api")
                        .get("/items")
                        .build(new Handler())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class AnnotatedHandler {
                @Get("/api/items")
                public String handle() {
                    return "items";
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class Handler {}")

        val groups = ArmeriaRouteDuplicateIndex.duplicateGroups(project)

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().routes.size)
        assertEquals(
            setOf(RouteMatch.ROUTE_FLUENT, RouteMatch.ANNOTATED_HTTP),
            groups
                .single()
                .routes
                .map { it.routeMatch }
                .toSet(),
        )
    }

    fun testDuplicateFluentRoutesWithDifferentMethodsOnSamePathAreNotDuplicates() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .route()
                        .get("/resource")
                        .build(new ReadHandler())
                        .route()
                        .post("/resource")
                        .build(new WriteHandler())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class ReadHandler {}")
        myFixture.addClass("package example; public class WriteHandler {}")

        assertTrue(ArmeriaRouteDuplicateIndex.duplicateGroups(project).isEmpty())
    }

    fun testHealthCheckServiceConflictsWithServiceRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/internal/healthcheck", new HealthHandler())
                        .healthCheckService()
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class HealthHandler {}")

        val groups = ArmeriaRouteDuplicateIndex.duplicateGroups(project)

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().routes.size)
        assertEquals(
            setOf(RouteMatch.SERVICE, RouteMatch.HEALTH_CHECK),
            groups
                .single()
                .routes
                .map { it.routeMatch }
                .toSet(),
        )
    }

    fun testHealthCheckDuplicateRegistrationLabelIncludesHttpMethod() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/internal/healthcheck", new HealthHandler())
                        .healthCheckService()
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class HealthHandler {}")

        val hits = ArmeriaRouteDuplicateIndex.duplicateHitsInFile(project, myFixture.file)
        val healthHit = hits.firstOrNull { it.registrationLabel.startsWith("GET ") }
        assertNotNull(healthHit)
        assertEquals("GET /internal/healthcheck", healthHit!!.registrationLabel)
    }
}
