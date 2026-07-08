package com.linecorp.intellij.plugins.armeria.run

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaMainClassResolverTest : LightJavaCodeInsightFixtureTestCase() {

    fun testFindsJavaMainClass() {
        val file = myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder().http(8080).build();
                }
            }
            """.trimIndent(),
        )
        val element = file.findElementAt(indexOf(file.text, "main"))!!

        val mainClass = ArmeriaMainClassResolver.findArmeriaMainClass(element)

        assertNotNull(mainClass)
        assertEquals("example.Main", mainClass!!.qualifiedName)
    }

    fun testFindsKotlinTopLevelMainFacade() {
        val file = myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().http(8080).build()
            }
            """.trimIndent(),
        )
        val element = file.findElementAt(indexOf(file.text, "main"))!!

        val mainClass = ArmeriaMainClassResolver.findArmeriaMainClass(element)

        assertNotNull(mainClass)
        assertEquals("example.MainKt", mainClass!!.qualifiedName)
    }

    fun testIgnoresNonArmeriaMain() {
        val file = myFixture.configureByText(
            "Main.kt",
            """
            package example

            fun main() {
                println("hello")
            }
            """.trimIndent(),
        )
        val element = file.findElementAt(indexOf(file.text, "main"))!!

        assertNull(ArmeriaMainClassResolver.findArmeriaMainClass(element))
    }

    private fun indexOf(text: String, needle: String): Int {
        val index = text.indexOf(needle)
        assertTrue("Expected to find '$needle'", index >= 0)
        return index
    }
}
