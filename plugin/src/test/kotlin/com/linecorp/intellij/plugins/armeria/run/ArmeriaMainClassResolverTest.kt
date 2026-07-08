package com.linecorp.intellij.plugins.armeria.run

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaMainClassResolverTest : LightJavaCodeInsightFixtureTestCase() {

    fun testFindsJavaMainClass() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void m<caret>ain(String[] args) {
                    Server.builder().http(8080).build();
                }
            }
            """.trimIndent(),
        )

        val mainClass = ArmeriaMainClassResolver.findArmeriaMainClass(myFixture.file.findElementAt(myFixture.caretOffset))

        assertNotNull(mainClass)
        assertEquals("example.Main", mainClass!!.qualifiedName)
    }

    fun testFindsKotlinTopLevelMainFacade() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun m<caret>ain() {
                Server.builder().http(8080).build()
            }
            """.trimIndent(),
        )

        val mainClass = ArmeriaMainClassResolver.findArmeriaMainClass(myFixture.file.findElementAt(myFixture.caretOffset))

        assertNotNull(mainClass)
        assertEquals("example.MainKt", mainClass!!.qualifiedName)
    }

    fun testIgnoresNonArmeriaMain() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            fun m<caret>ain() {
                println("hello")
            }
            """.trimIndent(),
        )

        assertNull(ArmeriaMainClassResolver.findArmeriaMainClass(myFixture.file.findElementAt(myFixture.caretOffset)))
    }
}
