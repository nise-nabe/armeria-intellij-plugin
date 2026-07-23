package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaDuplicateRegistrationInspectionTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerRouteDuplicateIndexStubs()
    }

    fun testInspectionHighlightsDuplicateAndOffersNavigateQuickFix() {
        configureDuplicateServiceRegistrationFixture()

        myFixture.enableInspections(ArmeriaDuplicateRegistrationInspection())
        val expectedDescription = message("inspection.duplicate.registration.problem", "/dup", 2)
        val duplicateHighlights =
            myFixture.doHighlighting().filter {
                it.description == expectedDescription
            }

        assertEquals(1, duplicateHighlights.size)
        val expectedQuickFixName = message("inspection.duplicate.registration.quickfix.navigate", "/dup")
        val quickFixes =
            myFixture.getAvailableQuickFixes().filter {
                it.text == expectedQuickFixName
            }
        assertEquals(1, quickFixes.size)
    }

    fun testNavigateQuickFixOpensConflictingRegistrationFile() {
        configureDuplicateServiceRegistrationFixture()

        myFixture.enableInspections(ArmeriaDuplicateRegistrationInspection())
        myFixture.doHighlighting()

        val expectedQuickFixName = message("inspection.duplicate.registration.quickfix.navigate", "/dup")
        val quickFix = myFixture.getAvailableQuickFixes().single { it.text == expectedQuickFixName }
        ApplicationManager.getApplication().invokeAndWait {
            myFixture.launchAction(quickFix)
        }
        waitForConflictingFileToOpen("Extra.java")

        val openFileNames =
            FileEditorManager
                .getInstance(project)
                .openFiles
                .map { it.name }
                .toSet()
        assertTrue("Expected Extra.java among open files but found $openFileNames", "Extra.java" in openFileNames)
    }

    fun testJavaCrossFileAnnotatedRoutesAreHighlighted() {
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

        myFixture.enableInspections(ArmeriaDuplicateRegistrationInspection())
        val expectedDescription = message("inspection.duplicate.registration.problem", "GET /shared", 2)
        val duplicateHighlights =
            myFixture.doHighlighting().filter {
                it.description == expectedDescription
            }

        assertEquals(1, duplicateHighlights.size)
    }

    fun testInClassJavaAnnotatedDuplicatesAreNotRegistrationProblems() {
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

        myFixture.enableInspections(ArmeriaDuplicateRegistrationInspection())
        val registrationHighlights =
            myFixture.doHighlighting().filter {
                it.description.startsWith("This Armeria route")
            }

        assertTrue(registrationHighlights.isEmpty())
    }

    fun testKotlinInClassAnnotatedDuplicatesAreNotRegistrationProblems() {
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

        myFixture.enableInspections(ArmeriaDuplicateRegistrationKotlinInspection())
        val registrationHighlights =
            myFixture.doHighlighting().filter {
                it.description.startsWith("This Armeria route")
            }

        assertTrue(registrationHighlights.isEmpty())
    }

    fun testKotlinDistinctPathsAreNotRegistrationProblems() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"

                @Get("/goodbye")
                fun goodbye(): String = "goodbye"
            }
            """.trimIndent(),
        )

        myFixture.enableInspections(ArmeriaDuplicateRegistrationKotlinInspection())
        val registrationHighlights =
            myFixture.doHighlighting().filter {
                it.description.startsWith("This Armeria route")
            }

        assertTrue(registrationHighlights.isEmpty())
    }

    fun testKotlinInspectionHighlightsDuplicateAndOffersNavigateQuickFix() {
        myFixture.configureByText(
            "First.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class First {
                @Get("/shared")
                fun first(): String = "first"
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Second.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class Second {
                @Get("/shared")
                fun second(): String = "second"
            }
            """.trimIndent(),
        )

        myFixture.enableInspections(ArmeriaDuplicateRegistrationKotlinInspection())
        val expectedDescription = message("inspection.duplicate.registration.problem", "GET /shared", 2)
        val duplicateHighlights =
            myFixture.doHighlighting().filter {
                it.description == expectedDescription
            }

        assertEquals(1, duplicateHighlights.size)
        val expectedQuickFixName = message("inspection.duplicate.registration.quickfix.navigate", "GET /shared")
        val quickFixes =
            myFixture.getAvailableQuickFixes().filter {
                it.text == expectedQuickFixName
            }
        assertEquals(1, quickFixes.size)
    }

    private fun waitForConflictingFileToOpen(expectedFileName: String) {
        val deadlineMs = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadlineMs) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            val openFileNames = FileEditorManager.getInstance(project).openFiles.map { it.name }
            if (expectedFileName in openFileNames) {
                return
            }
            Thread.sleep(50)
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        val openFileNames = FileEditorManager.getInstance(project).openFiles.map { it.name }
        assertTrue(
            "Timed out waiting for $expectedFileName to open; open files: $openFileNames",
            expectedFileName in openFileNames,
        )
    }

    private fun configureDuplicateServiceRegistrationFixture() {
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
    }
}
