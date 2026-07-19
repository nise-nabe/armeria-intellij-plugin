package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.duplicate.ArmeriaRouteDuplicateIndex
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaRouteDuplicateIndexConflictTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerRouteDuplicateIndexStubs()
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
            groups
                .single()
                .routes
                .map { it.routeMatch }
                .toSet(),
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
            groups
                .single()
                .routes
                .map { it.routeMatch }
                .toSet(),
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
}
