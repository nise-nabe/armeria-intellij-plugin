package com.linecorp.intellij.plugins.armeria.run

import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaHttpClientEnvironmentTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    fun testContentWritesHostAndPort() {
        val json = ArmeriaHttpClientEnvironment.content("127.0.0.1", 8080)

        assertTrue(json.contains("\"armeria\""))
        assertTrue(json.contains("\"host\": \"127.0.0.1\""))
        assertTrue(json.contains("\"port\": \"8080\""))
        assertTrue(json.contains("\"scheme\": \"http\""))
    }

    fun testMergeBlankExistingUsesFreshContent() {
        assertEquals(
            ArmeriaHttpClientEnvironment.content("127.0.0.1", 9090),
            ArmeriaHttpClientEnvironment.merge(project, null, "127.0.0.1", 9090),
        )
    }

    fun testMergeUpdatesExistingArmeriaEnv() {
        val existing =
            """
            {
              "armeria": {
                "host": "localhost",
                "port": "8080"
              },
              "prod": {
                "host": "example.com",
                "port": "443"
              }
            }
            """.trimIndent()

        val merged = ArmeriaHttpClientEnvironment.merge(project, existing, "127.0.0.1", 9090)

        assertTrue(merged.contains("\"host\": \"127.0.0.1\""))
        assertTrue(merged.contains("\"port\": \"9090\""))
        assertTrue(merged.contains("\"scheme\": \"http\""))
        assertTrue(merged.contains("\"prod\""))
        assertTrue(merged.contains("example.com"))
        assertTrue(merged.contains("\"port\": \"443\""))
    }

    fun testMergePreservesNestedValuesInsideArmeriaEnv() {
        val existing =
            """
            {
              "armeria": {
                "host": "localhost",
                "retry": {
                  "attempts": 3,
                  "path": "/nested/{value}"
                }
              },
              "prod": {
                "host": "example.com"
              }
            }
            """.trimIndent()

        val merged = ArmeriaHttpClientEnvironment.merge(project, existing, "127.0.0.1", 9090, https = true)

        assertEquals(1, Regex("\\\"armeria\\\"\\s*:").findAll(merged).count(), merged)
        assertTrue(merged.contains("\"host\": \"127.0.0.1\""), merged)
        assertTrue(merged.contains("\"port\": \"9090\""), merged)
        assertTrue(merged.contains("\"scheme\": \"https\""), merged)
        assertTrue(merged.contains("\"attempts\": 3"), merged)
        assertTrue(merged.contains("/nested/{value}"), merged)
        assertTrue(merged.contains("example.com"), merged)
    }

    fun testMergePreservesJsonComments() {
        val existing =
            """
            {
              // Local development server.
              "armeria": {
                "host": "localhost",
                "retry": {
                  "attempts": 3
                }
              }
            }
            """.trimIndent()

        val merged = ArmeriaHttpClientEnvironment.merge(project, existing, "127.0.0.1", 9090)

        assertEquals(1, Regex("\\\"armeria\\\"\\s*:").findAll(merged).count(), merged)
        assertTrue(merged.contains("// Local development server."), merged)
        assertTrue(merged.contains("\"attempts\": 3"), merged)
        assertTrue(merged.contains("\"port\": \"9090\""), merged)
    }

    fun testMergeInsertsMissingValuesBeforeTrailingComment() {
        val existing =
            """
            {
              "armeria": {
                "host": "localhost"
                // Runtime values are generated below.
              }
            }
            """.trimIndent()

        val merged = ArmeriaHttpClientEnvironment.merge(project, existing, "127.0.0.1", 9090)

        assertTrue(merged.contains("\"host\": \"127.0.0.1\",\n    \"port\": \"9090\""), merged)
        assertTrue(merged.contains("// Runtime values are generated below."), merged)
        assertTrue(merged.indexOf("\"port\": \"9090\"") < merged.indexOf("// Runtime values"), merged)
    }

    fun testMergeLeavesUnparseableJsonUnchanged() {
        val existing = "{ \"armeria\": {"

        assertEquals(existing, ArmeriaHttpClientEnvironment.merge(project, existing, "127.0.0.1", 9090))
    }

    fun testMergeInsertsArmeriaEnvWithoutDroppingOthers() {
        val existing =
            """
            {
              "prod": {
                "host": "example.com",
                "port": "443"
              }
            }
            """.trimIndent()

        val merged = ArmeriaHttpClientEnvironment.merge(project, existing, "127.0.0.1", 8080)

        assertTrue(merged.contains("\"armeria\""))
        assertTrue(merged.contains("\"host\": \"127.0.0.1\""))
        assertTrue(merged.contains("example.com"))
    }

    fun testMergeWritesHttpsScheme() {
        val json = ArmeriaHttpClientEnvironment.merge(project, null, "127.0.0.1", 8443, https = true)

        assertTrue(json.contains("\"scheme\": \"https\""))
        assertTrue(json.contains("\"port\": \"8443\""))
    }

    fun testRequestBaseUrlUsesVariablesWhenEnvExists() {
        assertEquals(
            ArmeriaHttpClientEnvironment.REQUEST_BASE_URL,
            ArmeriaHttpClientEnvironment.requestBaseUrl(
                "http://localhost:8080",
                "http://localhost:8080",
                envFileExists = true,
            ),
        )
        assertEquals("{{scheme}}://{{host}}:{{port}}", ArmeriaHttpClientEnvironment.REQUEST_BASE_URL)
        assertEquals(
            "http://localhost:8080",
            ArmeriaHttpClientEnvironment.requestBaseUrl(
                "http://localhost:8080",
                "http://localhost:8080",
                envFileExists = false,
            ),
        )
        assertEquals(
            "http://example.com:80",
            ArmeriaHttpClientEnvironment.requestBaseUrl(
                "http://example.com:80",
                "http://localhost:8080",
                envFileExists = true,
            ),
        )
    }
}
