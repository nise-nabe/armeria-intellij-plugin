package com.linecorp.intellij.plugins.armeria.springboot.config

import com.linecorp.intellij.plugins.armeria.explorer.spring.SpringArmeriaConfigSemantics
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaSpringBootSettingsConflictTest {
    @Test
    fun portConflict_whenBothPortsPositiveWithoutWebApplicationTypeNone() {
        val findings =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(
                    ArmeriaSpringBootConfigKeys.SERVER_PORT to "8080",
                    "armeria.ports[0].port" to "8080",
                ),
                emptySet(),
            )
        assertEquals(
            listOf(ArmeriaSpringBootSettingsConflict.Kind.PORT_CONFLICT),
            findings.map { it.kind },
        )
        assertEquals(ArmeriaSpringBootConfigKeys.SERVER_PORT, findings.single().highlightPath)
    }

    @Test
    fun portConflict_silentWhenServerPortIsMinusOne() {
        val findings =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(
                    ArmeriaSpringBootConfigKeys.SERVER_PORT to "-1",
                    "armeria.ports[0].port" to "8080",
                ),
                emptySet(),
            )
        assertTrue(findings.none { it.kind == ArmeriaSpringBootSettingsConflict.Kind.PORT_CONFLICT })
    }

    @Test
    fun portConflict_silentWhenWebApplicationTypeIsNone() {
        val findings =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(
                    ArmeriaSpringBootConfigKeys.SERVER_PORT to "8080",
                    ArmeriaSpringBootConfigKeys.SPRING_WEB_APPLICATION_TYPE to "none",
                    "armeria.ports[0].port" to "8080",
                ),
                emptySet(),
            )
        assertTrue(findings.none { it.kind == ArmeriaSpringBootSettingsConflict.Kind.PORT_CONFLICT })
    }

    @Test
    fun portConflict_silentWhenArmeriaServerDisabled() {
        val findings =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(
                    ArmeriaSpringBootConfigKeys.SERVER_PORT to "8080",
                    ArmeriaSpringBootSettingsConflict.SERVER_ENABLED_KEY to "false",
                    "armeria.ports[0].port" to "8080",
                ),
                emptySet(),
            )
        assertTrue(findings.none { it.kind == ArmeriaSpringBootSettingsConflict.Kind.PORT_CONFLICT })
    }

    @Test
    fun portConflict_whenServerEnabledTrueWithoutExplicitPorts() {
        val findings =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(
                    ArmeriaSpringBootConfigKeys.SERVER_PORT to "8080",
                    ArmeriaSpringBootSettingsConflict.SERVER_ENABLED_KEY to "true",
                ),
                emptySet(),
            )
        assertTrue(findings.any { it.kind == ArmeriaSpringBootSettingsConflict.Kind.PORT_CONFLICT })
    }

    @Test
    fun portConflict_silentWhenServerPortIsZeroOrUnresolved() {
        assertFalse(
            ArmeriaSpringBootSettingsConflict.isPortConflict(
                mapOf(
                    ArmeriaSpringBootConfigKeys.SERVER_PORT to "0",
                    "armeria.ports[0].port" to "8080",
                ),
            ),
        )
        assertFalse(
            ArmeriaSpringBootSettingsConflict.isPortConflict(
                mapOf(
                    ArmeriaSpringBootConfigKeys.SERVER_PORT to "\${PORT}",
                    "armeria.ports[0].port" to "8080",
                ),
            ),
        )
    }

    @Test
    fun includeDocs_highlightedWithoutPathOrConfigurator() {
        val findings =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(
                    ArmeriaSpringBootConfigKeys.INTERNAL_SERVICES_INCLUDE to "docs",
                ),
                emptySet(),
            )
        val missing = findings.filter { it.kind == ArmeriaSpringBootSettingsConflict.Kind.MISSING_INTERNAL_SERVICE }
        assertEquals(listOf(SpringArmeriaConfigSemantics.ID_DOCS), missing.map { it.includeId })
        assertEquals(ArmeriaSpringBootSettingsConflict.DOCS_PATH_KEY, missing.single().pathKey)
    }

    @Test
    fun includeDocs_silentWhenDocsPathSet() {
        val findings =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(
                    ArmeriaSpringBootConfigKeys.INTERNAL_SERVICES_INCLUDE to "docs",
                    ArmeriaSpringBootSettingsConflict.DOCS_PATH_KEY to "/docs",
                ),
                emptySet(),
            )
        assertTrue(findings.none { it.kind == ArmeriaSpringBootSettingsConflict.Kind.MISSING_INTERNAL_SERVICE })
    }

    @Test
    fun includeDocs_silentWhenDocServiceConfiguratorPresent() {
        val findings =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(ArmeriaSpringBootConfigKeys.INTERNAL_SERVICES_INCLUDE to "docs"),
                setOf(ArmeriaRouteSupport.DOC_SERVICE_CONFIGURATOR_CLASS),
            )
        assertTrue(findings.none { it.kind == ArmeriaSpringBootSettingsConflict.Kind.MISSING_INTERNAL_SERVICE })
    }

    @Test
    fun includeDocs_silentWhenDocServiceBeanPresent() {
        val findings =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(ArmeriaSpringBootConfigKeys.INTERNAL_SERVICES_INCLUDE to "docs"),
                setOf(ArmeriaRouteSupport.DOC_SERVICE_CLASS),
            )
        assertTrue(findings.none { it.kind == ArmeriaSpringBootSettingsConflict.Kind.MISSING_INTERNAL_SERVICE })
    }

    @Test
    fun includeAll_flagsHealthAndMetricsWithoutPathOrBean() {
        val findings =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(ArmeriaSpringBootConfigKeys.INTERNAL_SERVICES_INCLUDE to "all"),
                emptySet(),
            )
        assertEquals(
            setOf(
                SpringArmeriaConfigSemantics.ID_DOCS,
                SpringArmeriaConfigSemantics.ID_HEALTH,
                SpringArmeriaConfigSemantics.ID_METRICS,
            ),
            findings
                .filter { it.kind == ArmeriaSpringBootSettingsConflict.Kind.MISSING_INTERNAL_SERVICE }
                .mapNotNull { it.includeId }
                .toSet(),
        )
    }

    @Test
    fun includeHealth_silentWhenHealthCheckPathSet() {
        val findings =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(
                    ArmeriaSpringBootConfigKeys.INTERNAL_SERVICES_INCLUDE to "health",
                    ArmeriaSpringBootSettingsConflict.HEALTH_PATH_KEY to "/health",
                ),
                emptySet(),
            )
        assertTrue(findings.none { it.kind == ArmeriaSpringBootSettingsConflict.Kind.MISSING_INTERNAL_SERVICE })
    }

    @Test
    fun missingInternal_skippedWhenBeanFqnsUnknown() {
        val findings =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(ArmeriaSpringBootConfigKeys.INTERNAL_SERVICES_INCLUDE to "docs"),
                presentBeanFqns = null,
            )
        assertTrue(findings.none { it.kind == ArmeriaSpringBootSettingsConflict.Kind.MISSING_INTERNAL_SERVICE })
    }

    @Test
    fun camelCaseKeys_matchRelaxedBinding() {
        val silentPort =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(
                    ArmeriaSpringBootConfigKeys.SERVER_PORT to "8080",
                    "spring.main.webApplicationType" to "none",
                    "armeria.ports[0].port" to "8080",
                ),
                emptySet(),
            )
        assertTrue(silentPort.none { it.kind == ArmeriaSpringBootSettingsConflict.Kind.PORT_CONFLICT })

        val silentDocs =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(
                    "armeria.internalServices.include" to "docs",
                    "armeria.docsPath" to "/docs",
                ),
                emptySet(),
            )
        assertTrue(silentDocs.none { it.kind == ArmeriaSpringBootSettingsConflict.Kind.MISSING_INTERNAL_SERVICE })

        val disabled =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(
                    ArmeriaSpringBootConfigKeys.SERVER_PORT to "8080",
                    "armeria.serverEnabled" to "false",
                    "armeria.ports[0].port" to "8080",
                ),
                emptySet(),
            )
        assertTrue(disabled.none { it.kind == ArmeriaSpringBootSettingsConflict.Kind.PORT_CONFLICT })
    }

    @Test
    fun inlineComments_areStrippedFromValues() {
        assertTrue(
            ArmeriaSpringBootSettingsConflict.isPortConflict(
                mapOf(
                    ArmeriaSpringBootConfigKeys.SERVER_PORT to "8080 # tomcat",
                    "armeria.ports[0].port" to "8080",
                ),
            ),
        )
        assertFalse(
            ArmeriaSpringBootSettingsConflict.isPortConflict(
                mapOf(
                    ArmeriaSpringBootConfigKeys.SERVER_PORT to "8080",
                    ArmeriaSpringBootConfigKeys.SPRING_WEB_APPLICATION_TYPE to "none # armeria only",
                    "armeria.ports[0].port" to "8080",
                ),
            ),
        )
        val docs =
            ArmeriaSpringBootSettingsConflict.findings(
                mapOf(
                    ArmeriaSpringBootConfigKeys.INTERNAL_SERVICES_INCLUDE to "docs # ui",
                    ArmeriaSpringBootSettingsConflict.DOCS_PATH_KEY to "/docs # explicit",
                ),
                emptySet(),
            )
        assertTrue(docs.none { it.kind == ArmeriaSpringBootSettingsConflict.Kind.MISSING_INTERNAL_SERVICE })
    }

    @Test
    fun lastWins_duplicateServerPort() {
        val entries =
            linkedMapOf(
                ArmeriaSpringBootConfigKeys.SERVER_PORT to "8080",
                "armeria.ports[0].port" to "8080",
            )
        // LinkedHashMap associate last-wins: overwrite the port with -1 after ports are set.
        val lastDisabled = entries + (ArmeriaSpringBootConfigKeys.SERVER_PORT to "-1")
        assertFalse(ArmeriaSpringBootSettingsConflict.isPortConflict(lastDisabled))
    }

    @Test
    fun lastWins_aliasOrderNotAlphabeticalSort() {
        val camelThenKebab =
            ArmeriaSpringBootConfigParser.flattenRelatedInOrder(
                "application.yml",
                """
                server:
                  port: 8080
                spring:
                  main:
                    webApplicationType: none
                    web-application-type: servlet
                armeria:
                  ports:
                    - port: 8080
                """.trimIndent(),
            )
        assertTrue(ArmeriaSpringBootSettingsConflict.isPortConflict(camelThenKebab))

        val kebabThenCamel =
            ArmeriaSpringBootConfigParser.flattenRelatedInOrder(
                "application.yml",
                """
                server:
                  port: 8080
                spring:
                  main:
                    web-application-type: servlet
                    webApplicationType: none
                armeria:
                  ports:
                    - port: 8080
                """.trimIndent(),
            )
        assertFalse(ArmeriaSpringBootSettingsConflict.isPortConflict(kebabThenCamel))
    }

    @Test
    fun lastWins_propertiesAliasOrder() {
        val camelThenKebab =
            ArmeriaSpringBootConfigParser.flattenRelatedInOrder(
                "application.properties",
                """
                server.port=8080
                spring.main.webApplicationType=none
                spring.main.web-application-type=servlet
                armeria.ports[0].port=8080
                """.trimIndent(),
            )
        assertTrue(ArmeriaSpringBootSettingsConflict.isPortConflict(camelThenKebab))

        val kebabThenCamel =
            ArmeriaSpringBootConfigParser.flattenRelatedInOrder(
                "application.properties",
                """
                server.port=8080
                spring.main.web-application-type=servlet
                spring.main.webApplicationType=none
                armeria.ports[0].port=8080
                """.trimIndent(),
            )
        assertFalse(ArmeriaSpringBootSettingsConflict.isPortConflict(kebabThenCamel))
    }

    @Test
    fun lastWins_repeatedAliasAfterOtherAlias() {
        val lastCamelNone =
            ArmeriaSpringBootConfigParser.flattenRelatedInOrder(
                "application.properties",
                """
                server.port=8080
                spring.main.webApplicationType=none
                spring.main.web-application-type=servlet
                spring.main.webApplicationType=none
                armeria.ports[0].port=8080
                """.trimIndent(),
            )
        assertFalse(ArmeriaSpringBootSettingsConflict.isPortConflict(lastCamelNone))

        val lastKebabServlet =
            ArmeriaSpringBootConfigParser.flattenRelatedInOrder(
                "application.properties",
                """
                server.port=8080
                spring.main.web-application-type=servlet
                spring.main.webApplicationType=none
                spring.main.web-application-type=servlet
                armeria.ports[0].port=8080
                """.trimIndent(),
            )
        assertTrue(ArmeriaSpringBootSettingsConflict.isPortConflict(lastKebabServlet))

        val yamlLastCamelNone =
            ArmeriaSpringBootConfigParser.flattenRelatedInOrder(
                "application.yml",
                """
                server:
                  port: 8080
                spring:
                  main:
                    webApplicationType: none
                    web-application-type: servlet
                    webApplicationType: none
                armeria:
                  ports:
                    - port: 8080
                """.trimIndent(),
            )
        assertFalse(ArmeriaSpringBootSettingsConflict.isPortConflict(yamlLastCamelNone))
    }

    @Test
    fun canonicalConfigKey_kebabizesCamelSegments() {
        assertEquals(
            "armeria.docs-path",
            ArmeriaSpringBootConfigSupport.canonicalConfigKey("armeria.docsPath"),
        )
        assertEquals(
            "armeria.internal-services.include",
            ArmeriaSpringBootConfigSupport.canonicalConfigKey("armeria.internalServices.include[0]"),
        )
        assertEquals(
            "spring.main.web-application-type",
            ArmeriaSpringBootConfigSupport.canonicalConfigKey("spring.main.webApplicationType"),
        )
    }
}
