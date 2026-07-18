package com.linecorp.intellij.plugins.armeria.explorer

import org.junit.Assert.assertEquals
import org.junit.Test

class ArmeriaSpringConfigRouteCollectorParseTest {
    @Test
    fun parseProperties_sparsePortIndexes() {
        val config = ArmeriaSpringConfigRouteCollector.parseProperties(
            """
            armeria.ports[0].port=8080
            armeria.ports[0].protocols[0]=http
            armeria.ports[2].port=8443
            armeria.ports[2].protocols[0]=https
            armeria.ports[2].protocols[1]=http
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                SpringArmeriaPortBinding("8080", listOf("HTTP")),
                SpringArmeriaPortBinding("8443", listOf("HTTPS", "HTTP")),
            ),
            config.ports,
        )
    }

    @Test
    fun parseProperties_includeAndPathsScopedToArmeria() {
        val config = ArmeriaSpringConfigRouteCollector.parseProperties(
            """
            other.docs-path=/wrong
            armeria.docs-path=/internal/docs
            armeria.internal-services.include=docs,health
            armeria.internal-services.port=18080
            """.trimIndent(),
        )

        assertEquals("/internal/docs", config.docsPath)
        assertEquals(setOf("docs", "health"), config.includes)
        assertEquals("18080", config.internalServicesPort)
    }

    @Test
    fun parseProperties_acceptsCamelCaseInternalServices() {
        val config = ArmeriaSpringConfigRouteCollector.parseProperties(
            """
            armeria.internalServices.include=docs,metrics
            armeria.internalServices.port=19090
            """.trimIndent(),
        )

        assertEquals(setOf("docs", "metrics"), config.includes)
        assertEquals("19090", config.internalServicesPort)
    }

    @Test
    fun parseProperties_commaSeparatedProtocols() {
        val config = ArmeriaSpringConfigRouteCollector.parseProperties(
            """
            armeria.ports[0].port=8080
            armeria.ports[0].protocols=http,https
            """.trimIndent(),
        )

        assertEquals(
            listOf(SpringArmeriaPortBinding("8080", listOf("HTTP", "HTTPS"))),
            config.ports,
        )
    }

    @Test
    fun parseProperties_includeAllAndIndexedIncludeEntries() {
        val all = ArmeriaSpringConfigRouteCollector.parseProperties(
            "armeria.internal-services.include=all",
        )
        assertEquals(setOf("docs", "health", "metrics", "actuator"), all.includes)

        val indexed = ArmeriaSpringConfigRouteCollector.parseProperties(
            """
            armeria.internal-services.include[0]=docs
            armeria.internal-services.include[1]=actuator
            """.trimIndent(),
        )
        assertEquals(setOf("docs", "actuator"), indexed.includes)
    }

    @Test
    fun parseProperties_acceptsColonDelimiterAndPlaceholders() {
        val config = ArmeriaSpringConfigRouteCollector.parseProperties(
            """
            armeria.ports[0].port: ${'$'}{SERVER_PORT:8080}
            armeria.ports[0].protocols: http,https
            armeria.internal-services.port: ${'$'}{INTERNAL_PORT:18080}
            armeria.internal-services.include: docs,health
            armeria.docs-path: ${'$'}{DOCS_PATH:/internal/docs}
            armeria.health-check-path: /internal/healthcheck # comment
            """.trimIndent(),
        )

        assertEquals(
            listOf(SpringArmeriaPortBinding("\${SERVER_PORT:8080}", listOf("HTTP", "HTTPS"))),
            config.ports,
        )
        assertEquals("\${INTERNAL_PORT:18080}", config.internalServicesPort)
        assertEquals(setOf("docs", "health"), config.includes)
        assertEquals("\${DOCS_PATH:/internal/docs}", config.docsPath)
        assertEquals("/internal/healthcheck", config.healthPath)
    }

    @Test
    fun parseProperties_lastDuplicateKeyWins() {
        val config = ArmeriaSpringConfigRouteCollector.parseProperties(
            """
            armeria.docs-path=/stale/docs
            armeria.health-check-path=/stale/health
            armeria.metrics-path=/stale/metrics
            armeria.internal-services.port=17070
            armeria.docs-path=/internal/docs
            armeria.health-check-path=/internal/healthcheck
            armeria.metrics-path=/internal/metrics
            armeria.internal-services.port=18080
            """.trimIndent(),
        )

        assertEquals("/internal/docs", config.docsPath)
        assertEquals("/internal/healthcheck", config.healthPath)
        assertEquals("/internal/metrics", config.metricsPath)
        assertEquals("18080", config.internalServicesPort)
    }

    @Test
    fun parseProperties_includeLastDuplicateKeyWins() {
        val unindexed = ArmeriaSpringConfigRouteCollector.parseProperties(
            """
            armeria.internal-services.include=docs
            armeria.internal-services.include=health
            """.trimIndent(),
        )
        assertEquals(setOf("health"), unindexed.includes)

        val indexed = ArmeriaSpringConfigRouteCollector.parseProperties(
            """
            armeria.internal-services.include[0]=docs
            armeria.internal-services.include[0]=health
            armeria.internal-services.include[1]=metrics
            """.trimIndent(),
        )
        assertEquals(setOf("health", "metrics"), indexed.includes)
    }

    @Test
    fun parseProperties_ignoresCommentedOutKeys() {
        val config = ArmeriaSpringConfigRouteCollector.parseProperties(
            """
            # armeria.ports[0].port=9999
            ! armeria.ports[0].port=8888
            armeria.ports[0].port=8080
            # armeria.ports[0].protocols=http
            armeria.ports[0].protocols=https
            # armeria.internal-services.include=docs
            armeria.internal-services.include=health
            # armeria.docs-path=/stale/docs
            armeria.docs-path=/internal/docs
            """.trimIndent(),
        )

        assertEquals(listOf(SpringArmeriaPortBinding("8080", listOf("HTTPS"))), config.ports)
        assertEquals(setOf("health"), config.includes)
        assertEquals("/internal/docs", config.docsPath)
    }

    @Test
    fun parseProperties_protocolIndexLastWins() {
        val config = ArmeriaSpringConfigRouteCollector.parseProperties(
            """
            armeria.ports[0].port=8080
            armeria.ports[0].protocols[0]=http
            armeria.ports[0].protocols[0]=https
            armeria.ports[0].protocols[1]=http
            """.trimIndent(),
        )

        assertEquals(
            listOf(SpringArmeriaPortBinding("8080", listOf("HTTPS", "HTTP"))),
            config.ports,
        )
    }

    @Test
    fun parseProperties_commaListReplacesPriorIndexedProtocols() {
        val config = ArmeriaSpringConfigRouteCollector.parseProperties(
            """
            armeria.ports[0].port=8080
            armeria.ports[0].protocols[0]=http
            armeria.ports[0].protocols[1]=https
            armeria.ports[0].protocols=h2c
            """.trimIndent(),
        )

        assertEquals(
            listOf(SpringArmeriaPortBinding("8080", listOf("H2C"))),
            config.ports,
        )
    }

    @Test
    fun internalServiceIdsStayAlignedWithSemantics() {
        assertEquals(
            SpringArmeriaConfigSemantics.INTERNAL_SERVICE_IDS,
            ArmeriaSpringConfigRouteCollector.internalServiceSpecIds(),
        )
        assertEquals(
            SpringArmeriaConfigSemantics.INTERNAL_SERVICE_IDS,
            SpringArmeriaConfigSemantics.expandIncludes(setOf("all")),
        )
    }
}
