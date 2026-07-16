package com.linecorp.intellij.plugins.armeria.springboot.config

import org.junit.Assert.*
import org.junit.Test

class ArmeriaSpringBootConfigParserTest {
    @Test fun parseYaml_extractsArmeriaKeys() {
        val m = ArmeriaSpringBootConfigParser.parseYaml(fixture("springboot/application.yml")).associate { it.key to it.value }
        assertEquals("none", m["spring.main.web-application-type"])
        assertEquals("-1", m["server.port"])
        assertEquals("8080", m["armeria.ports[0].port"])
        assertEquals("http", m["armeria.ports[0].protocols[0]"])
        assertEquals("8090", m["armeria.internal-services.port"])
        assertEquals("8081", m["management.server.port"])
        assertFalse(m.containsKey("logging.level.com.linecorp.armeria"))
    }
    @Test fun parseProperties_extractsIndexedKeys() {
        val m = ArmeriaSpringBootConfigParser.parseProperties(fixture("springboot/application.properties")).associate { it.key to it.value }
        assertEquals("9090", m["armeria.ports[0].port"])
        assertFalse(m.containsKey("unrelated.setting"))
    }
    @Test fun isApplicationConfigFileName_matchesProfiles() {
        assertTrue(ArmeriaSpringBootConfigSupport.isApplicationConfigFileName("application-dev.yaml"))
        assertFalse(ArmeriaSpringBootConfigSupport.isApplicationConfigFileName("bootstrap.yml"))
    }
    @Test fun normalizeIndexedKeyPath_stripsListIndexes() {
        assertEquals("armeria.ports.port", ArmeriaSpringBootConfigSupport.normalizeIndexedKeyPath("armeria.ports[0].port"))
        assertEquals("armeria.ports.protocols", ArmeriaSpringBootConfigSupport.normalizeIndexedKeyPath("armeria.ports[0].protocols[0]"))
    }
    @Test fun documentationFor_resolvesIndexedKeys() {
        assertEquals(
            ArmeriaSpringBootConfigKeys.DOCUMENTATION["armeria.ports"],
            ArmeriaSpringBootConfigKeys.documentationFor("armeria.ports[0].port"),
        )
    }
    private fun fixture(path: String) = javaClass.classLoader.getResource(path)!!.readText()
}
