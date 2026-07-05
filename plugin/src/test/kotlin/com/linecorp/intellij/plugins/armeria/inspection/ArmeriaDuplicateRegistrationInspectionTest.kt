package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteDuplicateIndex

class ArmeriaDuplicateRegistrationInspectionTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerArmeriaStubs()
    }

    fun testInspectionReportsDuplicateServiceRegistrations() {
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

        myFixture.enableInspections(ArmeriaDuplicateRegistrationInspection())
        val duplicateHighlights = myFixture.doHighlighting().filter {
            it.description?.contains("conflicting registrations") == true
        }
        assertEquals(1, duplicateHighlights.size)
    }

    fun testHighlightingAttachesNavigateQuickFix() {
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

        myFixture.enableInspections(ArmeriaDuplicateRegistrationInspection())
        val duplicateHighlights = myFixture.doHighlighting().filter {
            it.description?.contains("conflicting registrations") == true
        }

        assertEquals(1, duplicateHighlights.size)
        val quickFixes = myFixture.getAvailableQuickFixes().filter {
            it.text.startsWith("Navigate to conflicting route:")
        }
        assertEquals(1, quickFixes.size)
        assertEquals("Navigate to conflicting route: /dup", quickFixes.single().text)
    }

    fun testQuickFixesOfferNavigationToConflictingRoutes() {
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

        val element = ArmeriaRouteDuplicateIndex.duplicateHitsInFile(project, myFixture.file).single().pointer.element!!
        val quickFixes = DuplicateRegistrationQuickFixes.forElement(project, element)

        assertEquals(1, quickFixes.size)
        assertEquals("Navigate to conflicting route: /dup", quickFixes.single().name)
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
        val conflicts = ArmeriaRouteDuplicateIndex.conflictingRoutes(project, firstMethod)

        assertEquals(1, conflicts.size)
        assertEquals("GET /shared", ArmeriaRouteDuplicateIndex.duplicateRegistrationLabel(conflicts.single()))
    }

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
                public ServerBuilder service(String path, Object service) {
                    return this;
                }

                public com.linecorp.armeria.server.Server build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }
}
