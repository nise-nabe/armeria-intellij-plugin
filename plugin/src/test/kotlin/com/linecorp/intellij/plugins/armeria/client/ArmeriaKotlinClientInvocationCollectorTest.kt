package com.linecorp.intellij.plugins.armeria.client

import com.linecorp.intellij.plugins.armeria.test.ArmeriaClientFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaKotlinClientInvocationCollectorTest : ArmeriaClientFixtureTestBase() {
    fun testCollectRestClientGetExecuteCallSite() {
        myFixture.addClass(
            """
            package example;

            public class Foo {
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.RestClient

            fun main() {
                val client = RestClient.of("https://api.example.com")
                client.get("/x").execute<Foo>()
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)
        val callSite = endpoints.single { it.isCallSite }
        assertEquals("GET", callSite.httpMethod)
        assertEquals("/x", callSite.requestPath)
        assertEquals("https://api.example.com", callSite.uri)
        assertEquals("RestClient", callSite.clientType)
        assertEquals(1, endpoints.count { it.isCallSite })
    }

    fun testCollectWebClientGetFromKotlin() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.WebClient

            fun main() {
                WebClient.of("https://example.com").get("/users/{id}")
            }
            """.trimIndent(),
        )

        val callSite = ArmeriaClientCollector.collect(project).single { it.isCallSite }
        assertEquals("GET", callSite.httpMethod)
        assertEquals("/users/{id}", callSite.requestPath)
        assertEquals("HTTP", callSite.clientType)
    }

    fun testCollectBlockingWebClientPostFromKotlin() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.WebClient

            fun main() {
                WebClient.of("https://example.com").blocking().post("/upload", "{\"ok\":true}")
            }
            """.trimIndent(),
        )

        val callSite = ArmeriaClientCollector.collect(project).single { it.isCallSite }
        assertEquals("POST", callSite.httpMethod)
        assertEquals("/upload", callSite.requestPath)
        assertEquals("{\"ok\":true}", callSite.requestBody)
        assertEquals("BlockingWebClient", callSite.clientType)
    }

    fun testFactoryOnlyKotlinClientUnchanged() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.RestClient

            fun main() {
                RestClient.of("https://example.com/hello")
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)
        assertEquals(1, endpoints.size)
        assertTrue(!endpoints.single().isCallSite)
    }
}
