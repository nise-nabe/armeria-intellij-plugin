package com.linecorp.intellij.plugins.armeria.springboot.config

import com.linecorp.intellij.plugins.armeria.message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmeriaSpringBootConfigParserTest {
    @Test
    fun parseYaml_extractsArmeriaKeys() {
        val m = ArmeriaSpringBootConfigParser.parseYaml(fixture("springboot/application.yml")).associate { it.key to it.value }
        assertEquals("none", m["spring.main.web-application-type"])
        assertEquals("-1", m["server.port"])
        assertEquals("8080", m["armeria.ports[0].port"])
        assertEquals("http", m["armeria.ports[0].protocols[0]"])
        assertEquals("8090", m["armeria.internal-services.port"])
        assertEquals("8081", m["management.server.port"])
        assertFalse(m.containsKey("logging.level.com.linecorp.armeria"))
    }

    @Test
    fun parseProperties_extractsIndexedKeys() {
        val m = ArmeriaSpringBootConfigParser.parseProperties(fixture("springboot/application.properties"))
            .associate { it.key to it.value }
        assertEquals("9090", m["armeria.ports[0].port"])
        assertFalse(m.containsKey("unrelated.setting"))
    }

    @Test
    fun parseProperties_acceptsWhitespaceSeparator() {
        val m = ArmeriaSpringBootConfigParser.parseProperties(
            """
            server.port 8080
            armeria.enable-auto-injection true
            """.trimIndent(),
        ).associate { it.key to it.value }
        assertEquals("8080", m["server.port"])
        assertEquals("true", m["armeria.enable-auto-injection"])
    }

    @Test
    fun parseProperties_acceptsSpacesAroundEquals() {
        val m = ArmeriaSpringBootConfigParser.parseProperties(
            """
            server.port = 8080
            armeria.internal-services.port = 8090
            """.trimIndent(),
        ).associate { it.key to it.value }
        assertEquals("8080", m["server.port"])
        assertEquals("8090", m["armeria.internal-services.port"])
    }

    @Test
    fun parseYaml_listItemWithColonInScalar_isNotTreatedAsInlineMapping() {
        val m = ArmeriaSpringBootConfigParser.flattenYaml(
            """
            armeria:
              allowed-origins:
                - http://example.com
                - https://foo.bar:8080/path
            """.trimIndent(),
        )
        assertEquals("http://example.com", m["armeria.allowed-origins[0]"])
        assertEquals("https://foo.bar:8080/path", m["armeria.allowed-origins[1]"])
    }

    @Test
    fun parseYaml_topLevelListDoesNotThrow() {
        val m = ArmeriaSpringBootConfigParser.parseYaml(
            """
            - item
            armeria:
              enable-auto-injection: true
            """.trimIndent(),
        ).associate { it.key to it.value }
        assertEquals("true", m["armeria.enable-auto-injection"])
    }

    @Test
    fun isApplicationConfigFileName_matchesProfiles() {
        assertTrue(ArmeriaSpringBootConfigSupport.isApplicationConfigFileName("application-dev.yaml"))
        assertFalse(ArmeriaSpringBootConfigSupport.isApplicationConfigFileName("bootstrap.yml"))
    }

    @Test
    fun normalizeIndexedKeyPath_stripsListIndexes() {
        assertEquals("armeria.ports.port", ArmeriaSpringBootConfigSupport.normalizeIndexedKeyPath("armeria.ports[0].port"))
        assertEquals(
            "armeria.ports.protocols",
            ArmeriaSpringBootConfigSupport.normalizeIndexedKeyPath("armeria.ports[0].protocols[0]"),
        )
    }

    @Test
    fun completionContextPath_usesParentAfterStrippingLeaf() {
        assertEquals(
            "armeria.internal-services",
            ArmeriaSpringBootConfigSupport.completionContextPath("armeria.internal-services.port"),
        )
        assertEquals(
            "armeria.ports",
            ArmeriaSpringBootConfigSupport.completionContextPath("armeria.ports[0].port"),
        )
        assertEquals("", ArmeriaSpringBootConfigSupport.completionContextPath("server"))
        assertEquals("", ArmeriaSpringBootConfigSupport.completionContextPath(""))
    }

    @Test
    fun documentationFor_resolvesIndexedKeys() {
        assertEquals(
            message("springboot.config.doc.armeria.ports"),
            ArmeriaSpringBootConfigKeys.documentationFor("armeria.ports[0].port"),
        )
    }

    @Test
    fun completionInsertText_usesNextSegmentUnderNestedPath() {
        assertEquals(
            "internal-services",
            ArmeriaSpringBootConfigKeys.completionInsertText("armeria", "armeria.internal-services.port"),
        )
        assertEquals(
            "port",
            ArmeriaSpringBootConfigKeys.completionInsertText("armeria.internal-services", "armeria.internal-services.port"),
        )
        assertEquals(
            "server.port",
            ArmeriaSpringBootConfigKeys.completionInsertText("", "server.port"),
        )
        assertNull(ArmeriaSpringBootConfigKeys.completionInsertText("server", "armeria.ports"))
    }

    @Test
    fun isRelevantCompletionPath_matchesRelatedRootsAndPrefixes() {
        assertTrue(ArmeriaSpringBootConfigKeys.isRelevantCompletionPath(""))
        assertTrue(ArmeriaSpringBootConfigKeys.isRelevantCompletionPath("armeria"))
        assertTrue(ArmeriaSpringBootConfigKeys.isRelevantCompletionPath("server"))
        assertTrue(ArmeriaSpringBootConfigKeys.isRelevantCompletionPath("management.server"))
        assertFalse(ArmeriaSpringBootConfigKeys.isRelevantCompletionPath("logging.level"))
    }

    private fun fixture(path: String) = javaClass.classLoader.getResource(path)!!.readText()
}
