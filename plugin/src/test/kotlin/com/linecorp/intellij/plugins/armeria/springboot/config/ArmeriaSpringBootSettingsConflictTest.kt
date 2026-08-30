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
}
