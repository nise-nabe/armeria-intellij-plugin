package com.linecorp.intellij.plugins.armeria.springboot.config

import com.linecorp.intellij.plugins.armeria.message
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        val m =
            ArmeriaSpringBootConfigParser
                .parseProperties(fixture("springboot/application.properties"))
                .associate { it.key to it.value }
        assertEquals("9090", m["armeria.ports[0].port"])
        assertFalse(m.containsKey("unrelated.setting"))
    }

    @Test
    fun parseProperties_acceptsWhitespaceSeparator() {
        val m =
            ArmeriaSpringBootConfigParser
                .parseProperties(
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
        val m =
            ArmeriaSpringBootConfigParser
                .parseProperties(
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
        val m =
            ArmeriaSpringBootConfigParser.flattenYaml(
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
        val m =
            ArmeriaSpringBootConfigParser
                .parseYaml(
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
            message("springboot.config.doc.armeria.ports.port"),
            ArmeriaSpringBootConfigKeys.documentationFor("armeria.ports[0].port"),
        )
        assertEquals(
            message("springboot.config.doc.armeria.ports"),
            ArmeriaSpringBootConfigKeys.documentationFor("armeria.ports[0]"),
        )
    }

    @Test
    fun completionSuggestions_includeArmeriaSettingsPaths() {
        val suggestions = ArmeriaSpringBootConfigKeys.COMPLETION_SUGGESTIONS
        assertTrue("armeria.docs-path" in suggestions)
        assertTrue("armeria.health-check-path" in suggestions)
        assertTrue("armeria.metrics-path" in suggestions)
        assertTrue("armeria.internal-services.include" in suggestions)
        assertTrue("armeria.ssl.key-store" in suggestions)
    }

    @Test
    fun completionInsertText_topLevelArmeriaKeysInsertArmeriaSegment() {
        assertEquals(
            "armeria",
            ArmeriaSpringBootConfigKeys.completionInsertText("", "armeria.docs-path"),
        )
        assertEquals(
            "docs-path",
            ArmeriaSpringBootConfigKeys.completionInsertText("armeria", "armeria.docs-path"),
        )
        assertEquals(
            "include",
            ArmeriaSpringBootConfigKeys.completionInsertText(
                "armeria.internal-services",
                "armeria.internal-services.include",
            ),
        )
    }

    @Test
    fun isIncludeValuePath_matchesIndexedIncludeKeys() {
        assertTrue(ArmeriaSpringBootConfigKeys.isIncludeValuePath("armeria.internal-services.include"))
        assertTrue(ArmeriaSpringBootConfigKeys.isIncludeValuePath("armeria.internal-services.include[0]"))
        assertFalse(ArmeriaSpringBootConfigKeys.isIncludeValuePath("armeria.internal-services.port"))
    }

    @Test
    fun includeValueDocumentation_coversDocumentedServiceIds() {
        assertEquals(
            listOf("docs", "health", "metrics", "actuator", "all"),
            ArmeriaSpringBootConfigKeys.INTERNAL_SERVICE_INCLUDE_VALUES,
        )
        assertEquals(
            message("springboot.config.doc.include.docs"),
            ArmeriaSpringBootConfigKeys.documentationForIncludeValue("docs"),
        )
        assertNull(ArmeriaSpringBootConfigKeys.documentationForIncludeValue("unknown"))
    }

    @Test
    fun lastIncludeToken_usesTextAfterLastComma() {
        assertEquals("he", ArmeriaSpringBootCompletionSupport.lastIncludeToken("docs, he"))
        assertEquals("docs", ArmeriaSpringBootCompletionSupport.lastIncludeToken("docs"))
    }

    @Test
    fun incompleteYamlKeyPrefix_detectsNestedKeyWithoutColon() {
        assertEquals("i", ArmeriaSpringBootCompletionSupport.incompleteYamlKeyPrefix("  i"))
        assertEquals("p", ArmeriaSpringBootCompletionSupport.incompleteYamlKeyPrefix("    - p"))
        assertEquals("", ArmeriaSpringBootCompletionSupport.incompleteYamlKeyPrefix("  "))
        assertNull(ArmeriaSpringBootCompletionSupport.incompleteYamlKeyPrefix("  include: "))
        assertNull(ArmeriaSpringBootCompletionSupport.incompleteYamlKeyPrefix("    - port: 8"))
    }

    @Test
    fun propertiesValueSeparatorIndex_acceptsEqualsColonAndWhitespace() {
        assertEquals(-1, ArmeriaSpringBootCompletionSupport.propertiesValueSeparatorIndex("armeria.docs-path"))
        assertEquals(
            "armeria.internal-services.include=".indexOf('='),
            ArmeriaSpringBootCompletionSupport.propertiesValueSeparatorIndex("armeria.internal-services.include="),
        )
        assertEquals(
            "armeria.internal-services.include:".indexOf(':'),
            ArmeriaSpringBootCompletionSupport.propertiesValueSeparatorIndex("armeria.internal-services.include:"),
        )
        assertEquals(
            "armeria.internal-services.include ".indexOf(' '),
            ArmeriaSpringBootCompletionSupport.propertiesValueSeparatorIndex("armeria.internal-services.include "),
        )
        assertEquals(-1, ArmeriaSpringBootCompletionSupport.propertiesValueSeparatorIndex(" armeria.docs-path"))
        assertEquals(
            "  armeria.docs-path=".indexOf('='),
            ArmeriaSpringBootCompletionSupport.propertiesValueSeparatorIndex("  armeria.docs-path="),
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

    @Test
    fun summaryText_excludesSyntheticConfiguratorBeansFromFileCounts() {
        val config =
            ArmeriaSpringBootConfigFile(
                fileName = "application.yml",
                filePath = "/app/application.yml",
                entries = listOf(ArmeriaSpringBootConfigEntry("armeria.docs-path", "/docs")),
            )
        val beans =
            ArmeriaSpringBootConfigFile(
                fileName = "Configurator beans",
                filePath = "beans",
                entries =
                    listOf(
                        ArmeriaSpringBootConfigEntry("ArmeriaServerConfigurator", "MyConfigurator"),
                    ),
                synthetic = true,
            )

        assertEquals(
            message("springboot.config.summary.entries", 1, 1),
            ArmeriaSpringBootConfigSupport.summaryText(listOf(config)),
        )
        assertEquals(
            message("springboot.config.summary.beans", 1),
            ArmeriaSpringBootConfigSupport.summaryText(listOf(beans)),
        )
        assertEquals(
            "${message("springboot.config.summary.entries", 1, 1)} · ${message("springboot.config.summary.beans", 1)}",
            ArmeriaSpringBootConfigSupport.summaryText(listOf(config, beans)),
        )
    }

    @Test
    fun summaryText_warnsWhenAthenzIsEnabledWithoutZtsUri() {
        val athenz =
            ArmeriaSpringBootConfigFile(
                fileName = "application.yml",
                filePath = "/app/application.yml",
                entries =
                    listOf(
                        ArmeriaSpringBootConfigEntry("armeria.athenz.domains", "my-domain"),
                    ),
            )
        val withZts =
            ArmeriaSpringBootConfigFile(
                fileName = "application.yml",
                filePath = "/app/application.yml",
                entries =
                    listOf(
                        ArmeriaSpringBootConfigEntry("armeria.athenz.domains", "my-domain"),
                        ArmeriaSpringBootConfigEntry("armeria.athenz.zts-uri", "https://zts.example/zts/v1"),
                    ),
            )
        val camelZts =
            ArmeriaSpringBootConfigFile(
                fileName = "application.yml",
                filePath = "/app/application.yml",
                entries =
                    listOf(
                        ArmeriaSpringBootConfigEntry("armeria.athenz.ztsUri", "https://zts.example/zts/v1"),
                    ),
            )

        assertTrue(
            ArmeriaSpringBootConfigSupport.summaryText(listOf(athenz)).contains(
                message("springboot.config.summary.athenzMissingZtsUri"),
            ),
        )
        assertFalse(
            ArmeriaSpringBootConfigSupport.summaryText(listOf(withZts)).contains(
                message("springboot.config.summary.athenzMissingZtsUri"),
            ),
        )
        assertFalse(
            ArmeriaSpringBootConfigSupport.summaryText(listOf(camelZts)).contains(
                message("springboot.config.summary.athenzMissingZtsUri"),
            ),
        )
    }

    @Test
    fun summaryText_mentionsDropwizardWithoutCountingItAsConfigFilesOrBeans() {
        val dropwizard = ArmeriaDropwizardConfigCollector.detectedFile()
        val config =
            ArmeriaSpringBootConfigFile(
                fileName = "application.yml",
                filePath = "/app/application.yml",
                entries = listOf(ArmeriaSpringBootConfigEntry("armeria.docs-path", "/docs")),
            )

        assertEquals(
            message("springboot.config.summary.dropwizardDetected"),
            ArmeriaSpringBootConfigSupport.summaryText(listOf(dropwizard)),
        )
        assertEquals(
            "${message("springboot.config.summary.entries", 1, 1)} · ${message("springboot.config.summary.dropwizardDetected")}",
            ArmeriaSpringBootConfigSupport.summaryText(listOf(config, dropwizard)),
        )
        assertEquals(ArmeriaDropwizardConfigCollector.DOCS_URL, dropwizard.entries.single().externalUrl)
    }

    private fun fixture(path: String) = javaClass.classLoader.getResource(path)!!.readText()
}
