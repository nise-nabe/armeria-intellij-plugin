package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaRouteDuplicateIndexTest : ArmeriaFixtureTestBase() {

    override fun registerArmeriaStubs() {
        registerRouteDuplicateIndexStubs()
    }

    fun testDuplicateServiceRegistrationsInDifferentFilesAreReported() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/dup", new FirstService())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Extra {
                public static void register(com.linecorp.armeria.server.ServerBuilder sb) {
                    sb.service("/dup", new SecondService());
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class FirstService {}")
        myFixture.addClass("package example; public class SecondService {}")

        val groups = ArmeriaRouteDuplicateIndex.duplicateGroups(project)

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().routes.size)
        assertEquals(setOf("/dup"), groups.single().routes.map { it.path }.toSet())
    }

    fun testDifferentHttpMethodsOnSamePathAreNotDuplicates() {
        myFixture.configureByText(
            "Routes.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Post;

            public class Routes {
                @Get("/resource")
                public String read() {
                    return "read";
                }

                @Post("/resource")
                public String write() {
                    return "write";
                }
            }
            """.trimIndent(),
        )

        assertTrue(ArmeriaRouteDuplicateIndex.duplicateGroups(project).isEmpty())
    }

    fun testCrossClassAnnotatedRoutesAreReported() {
        myFixture.configureByText(
            "First.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class First {
                @Get("/shared")
                public String first() {
                    return "first";
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class Second {
                @Get("/shared")
                public String second() {
                    return "second";
                }
            }
            """.trimIndent(),
        )

        val groups = ArmeriaRouteDuplicateIndex.duplicateGroups(project)

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().routes.size)
    }

    fun testInClassJavaAnnotatedDuplicatesAreExcluded() {
        myFixture.configureByText(
            "BadService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class BadService {
                @Get("/dup")
                public String first() {
                    return "first";
                }

                @Get("/dup")
                public String second() {
                    return "second";
                }
            }
            """.trimIndent(),
        )

        assertTrue(ArmeriaRouteDuplicateIndex.duplicateGroups(project).isEmpty())
    }

    fun testInClassKotlinAnnotatedDuplicatesAreExcluded() {
        myFixture.configureByText(
            "BadService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class BadService {
                @Get("/dup")
                fun first(): String = "first"

                @Get("/dup")
                fun second(): String = "second"
            }
            """.trimIndent(),
        )

        assertTrue(ArmeriaRouteDuplicateIndex.duplicateGroups(project).isEmpty())
    }

    fun testCatchAllServiceCountsOverlapsPerRoute() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/shared", new ServiceHandler())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Post;

            public class AnnotatedHandler {
                @Get("/shared")
                public String read() {
                    return "read";
                }

                @Post("/shared")
                public String write() {
                    return "write";
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class ServiceHandler {}")

        val groups = ArmeriaRouteDuplicateIndex.duplicateGroups(project)
        assertEquals(1, groups.size)

        val routes = groups.single().routes
        val getRoute = routes.single { it.httpMethod == "GET" }
        val postRoute = routes.single { it.httpMethod == "POST" }
        val serviceRoute = routes.single { it.routeMatch == RouteMatch.SERVICE }

        assertEquals(
            2,
            ArmeriaRouteDuplicateIndex.duplicateHitsInFile(project, getRoute.pointer.element!!.containingFile)
                .single { it.registrationLabel == "GET /shared" }
                .registrationCount,
        )
        assertEquals(
            2,
            ArmeriaRouteDuplicateIndex.duplicateHitsInFile(project, postRoute.pointer.element!!.containingFile)
                .single { it.registrationLabel == "POST /shared" }
                .registrationCount,
        )
        assertEquals(
            3,
            ArmeriaRouteDuplicateIndex.duplicateHitsInFile(project, serviceRoute.pointer.element!!.containingFile)
                .single()
                .registrationCount,
        )
    }

    fun testAnnotatedRouteConflictsWithServiceRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/shared", new ServiceHandler())
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
                @Get("/shared")
                public String handle() {
                    return "shared";
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class ServiceHandler {}")

        val groups = ArmeriaRouteDuplicateIndex.duplicateGroups(project)

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().routes.size)
        assertEquals(
            setOf(RouteMatch.SERVICE, RouteMatch.ANNOTATED_HTTP),
            groups.single().routes.map { it.routeMatch }.toSet(),
        )
    }

    fun testDuplicateAnnotatedServiceRegistrationsAreReported() {
        myFixture.configureByText(
            "FirstMain.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class FirstMain {
                public static void main(String[] args) {
                    Server.builder()
                        .annotatedService("/api", new FirstService())
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
                        .annotatedService("/api", new SecondService())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class FirstService {}")
        myFixture.addClass("package example; public class SecondService {}")

        val groups = ArmeriaRouteDuplicateIndex.duplicateGroups(project)

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().routes.size)
        assertTrue(groups.single().routes.all { it.routeMatch == RouteMatch.ANNOTATED_SERVICE })
    }

    fun testDuplicateKotlinServiceRegistrationsAreReported() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service("/dup", FirstService())
                    .build()
            }

            fun secondMain() {
                Server.builder()
                    .service("/dup", SecondService())
                    .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class FirstService {}")
        myFixture.addClass("package example; public class SecondService {}")

        val groups = ArmeriaRouteDuplicateIndex.duplicateGroups(project)

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().routes.size)
    }

    fun testAnnotatedServiceWithoutPrefixDoesNotConflictWithUnrelatedRoutes() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .annotatedService(new AnnotatedService())
                        .service("/foo", new FooService())
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
                @Get("/bar")
                public String handle() {
                    return "bar";
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class AnnotatedService {}")
        myFixture.addClass("package example; public class FooService {}")

        assertTrue(ArmeriaRouteDuplicateIndex.duplicateGroups(project).isEmpty())
    }

    fun testServiceUnderWithTrailingSlashConflictsWithAnnotatedRoute() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serviceUnder("/api/", new ApiService())
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
                @Get("/api/foo")
                public String handle() {
                    return "foo";
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class ApiService {}")

        assertEquals(1, ArmeriaRouteDuplicateIndex.duplicateGroups(project).size)
    }

    fun testServiceUnderConflictsWithAnnotatedRouteUnderPrefix() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serviceUnder("/api", new ApiService())
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
                @Get("/api/foo")
                public String handle() {
                    return "foo";
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class ApiService {}")

        val groups = ArmeriaRouteDuplicateIndex.duplicateGroups(project)

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().routes.size)
        assertEquals(
            setOf(RouteMatch.SERVICE_UNDER, RouteMatch.ANNOTATED_HTTP),
            groups.single().routes.map { it.routeMatch }.toSet(),
        )
    }

    fun testSamePathOnDifferentVirtualHostsAreNotDuplicates() {
        myFixture.configureByText(
            "FirstMain.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class FirstMain {
                public static void main(String[] args) {
                    Server.builder()
                        .virtualHost("a.example.com")
                        .service("/api", new FirstService())
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
                        .virtualHost("b.example.com")
                        .service("/api", new SecondService())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class FirstService {}")
        myFixture.addClass("package example; public class SecondService {}")

        val routes = ArmeriaRouteCollector.collect(project)
        val serviceRoutes = routes.filter { it.routeMatch == RouteMatch.SERVICE && it.path == "/api" }
        assertEquals(2, serviceRoutes.size)
        assertEquals(
            setOf("a.example.com", "b.example.com"),
            serviceRoutes.map { it.virtualHostName }.toSet(),
        )
        assertTrue(ArmeriaRouteDuplicateIndex.duplicateGroups(project).isEmpty())
    }

    fun testUnrelatedServiceUnderPrefixesAreNotDuplicates() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serviceUnder("/api", new ApiService())
                        .serviceUnder("/foo", new FooService())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class ApiService {}")
        myFixture.addClass("package example; public class FooService {}")

        assertTrue(ArmeriaRouteDuplicateIndex.duplicateGroups(project).isEmpty())
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
            groups.single().routes.map { it.routeMatch }.toSet(),
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
            groups.single().routes.map { it.routeMatch }.toSet(),
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

    fun testDuplicateHitsAreIndexedByFile() {
        myFixture.configureByText(
            "First.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class First {
                @Get("/shared")
                public String first() {
                    return "first";
                }
            }
            """.trimIndent(),
        )
        val secondClass = myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class Second {
                @Get("/shared")
                public String second() {
                    return "second";
                }
            }
            """.trimIndent(),
        )

        val firstFile = myFixture.file
        val secondFile = secondClass.containingFile
        val firstHits = ArmeriaRouteDuplicateIndex.duplicateHitsInFile(project, firstFile)
        val secondHits = ArmeriaRouteDuplicateIndex.duplicateHitsInFile(project, secondFile)

        assertEquals(1, firstHits.size)
        assertEquals(1, secondHits.size)
        assertEquals("GET /shared", firstHits.single().registrationLabel)
        assertEquals(2, firstHits.single().registrationCount)
    }

}
