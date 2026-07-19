package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.duplicate.ArmeriaRouteDuplicateIndex
import com.linecorp.intellij.plugins.armeria.explorer.duplicate.DuplicateRegistrationGroup
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaRouteDuplicateIndexCoreTest : ArmeriaFixtureTestBase() {
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
        assertEquals(
            setOf("/dup"),
            groups
                .single()
                .routes
                .map { it.path }
                .toSet(),
        )
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

    fun testConflictingRoutesExcludesCurrentRegistration() {
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

        val firstMethod = myFixture.findClass("example.First").methods[0]
        val hit = ArmeriaRouteDuplicateIndex.duplicateHitsInFile(project, firstMethod.containingFile).single()

        assertEquals(1, hit.conflictingRoutes.size)
        assertEquals("GET /shared", hit.conflictingRoutes.single().navigationLabel)
    }

    fun testConflictingRouteNavigationLabelsDisambiguateSamePath() {
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
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Third {
                public static void register(com.linecorp.armeria.server.ServerBuilder sb) {
                    sb.service("/dup", new ThirdService());
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class FirstService {}")
        myFixture.addClass("package example; public class SecondService {}")
        myFixture.addClass("package example; public class ThirdService {}")

        val mainFile = myFixture.findClass("example.Main").containingFile
        val hit = ArmeriaRouteDuplicateIndex.duplicateHitsInFile(project, mainFile).single()

        assertEquals(3, hit.registrationCount)
        assertEquals(2, hit.conflictingRoutes.size)
        val labels = hit.conflictingRoutes.map { it.navigationLabel }
        assertEquals(2, labels.distinct().size)
        assertTrue(labels.all { it.startsWith("/dup (") })
    }

    fun testConflictingRoutesDeduplicateSharedPsiElements() {
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

        val group = ArmeriaRouteDuplicateIndex.duplicateGroups(project).single()
        val inflatedGroup = DuplicateRegistrationGroup(group.routes + group.routes.last())
        val mainFile = myFixture.findClass("example.Main").containingFile.virtualFile!!
        val hit = ArmeriaRouteDuplicateIndex.duplicateHitsForGroups(listOf(inflatedGroup))[mainFile]!!.single()

        assertEquals(2, hit.registrationCount)
        assertEquals(1, hit.conflictingRoutes.size)
        assertEquals("/dup", hit.conflictingRoutes.single().navigationLabel)
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
            ArmeriaRouteDuplicateIndex
                .duplicateHitsInFile(project, getRoute.pointer.element!!.containingFile)
                .single { it.registrationLabel == "GET /shared" }
                .registrationCount,
        )
        assertEquals(
            2,
            ArmeriaRouteDuplicateIndex
                .duplicateHitsInFile(project, postRoute.pointer.element!!.containingFile)
                .single { it.registrationLabel == "POST /shared" }
                .registrationCount,
        )
        assertEquals(
            3,
            ArmeriaRouteDuplicateIndex
                .duplicateHitsInFile(project, serviceRoute.pointer.element!!.containingFile)
                .single()
                .registrationCount,
        )
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
        val secondClass =
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
