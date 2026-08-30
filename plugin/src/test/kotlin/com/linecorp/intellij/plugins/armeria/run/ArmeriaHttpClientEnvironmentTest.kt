package com.linecorp.intellij.plugins.armeria.run

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaHttpClientEnvironmentTest {
    @Test
    fun content_writesHostAndPort() {
        val json = ArmeriaHttpClientEnvironment.content("127.0.0.1", 8080)

        assertTrue(json.contains("\"armeria\""))
        assertTrue(json.contains("\"host\": \"127.0.0.1\""))
        assertTrue(json.contains("\"port\": \"8080\""))
        assertTrue(json.contains("\"scheme\": \"http\""))
    }

    @Test
    fun merge_blankExistingUsesFreshContent() {
        assertEquals(
            ArmeriaHttpClientEnvironment.content("127.0.0.1", 9090),
            ArmeriaHttpClientEnvironment.merge(null, "127.0.0.1", 9090),
        )
    }

    @Test
    fun merge_updatesExistingArmeriaEnv() {
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

        val merged = ArmeriaHttpClientEnvironment.merge(existing, "127.0.0.1", 9090)

        assertTrue(merged.contains("\"host\": \"127.0.0.1\""))
        assertTrue(merged.contains("\"port\": \"9090\""))
        assertTrue(merged.contains("\"scheme\": \"http\""))
        assertTrue(merged.contains("\"prod\""))
        assertTrue(merged.contains("example.com"))
        assertTrue(merged.contains("\"port\": \"443\""))
    }

    @Test
    fun merge_insertsArmeriaEnvWithoutDroppingOthers() {
        val existing =
            """
            {
              "prod": {
                "host": "example.com",
                "port": "443"
              }
            }
            """.trimIndent()

        val merged = ArmeriaHttpClientEnvironment.merge(existing, "127.0.0.1", 8080)

        assertTrue(merged.contains("\"armeria\""))
        assertTrue(merged.contains("\"host\": \"127.0.0.1\""))
        assertTrue(merged.contains("example.com"))
    }

    @Test
    fun merge_writesHttpsScheme() {
        val json = ArmeriaHttpClientEnvironment.merge(null, "127.0.0.1", 8443, https = true)

        assertTrue(json.contains("\"scheme\": \"https\""))
        assertTrue(json.contains("\"port\": \"8443\""))
    }

    @Test
    fun requestBaseUrl_usesVariablesWhenEnvExists() {
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
