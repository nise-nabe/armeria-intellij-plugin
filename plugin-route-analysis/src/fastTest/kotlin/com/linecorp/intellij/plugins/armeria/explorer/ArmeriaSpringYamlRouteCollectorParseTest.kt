package com.linecorp.intellij.plugins.armeria.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmeriaSpringYamlRouteCollectorParseTest {
    @Test
    fun parseYaml_scalarProtocolsAndAddressFirst() {
        val config = ArmeriaSpringYamlRouteCollector.parseYaml(
            """
            armeria:
              ports:
                - port: 8080
                  protocols: HTTP
                - address: 127.0.0.1
                  port: 8081
                  protocols: HTTP
                - port: 8443
                  protocols: HTTPS
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                SpringArmeriaPortBinding("8080", listOf("HTTP")),
                SpringArmeriaPortBinding("8081", listOf("HTTP")),
                SpringArmeriaPortBinding("8443", listOf("HTTPS")),
            ),
            config.ports,
        )
    }

    @Test
    fun parseYaml_protocolsBeforePortAndMultiProtocol() {
        val config = ArmeriaSpringYamlRouteCollector.parseYaml(
            """
            armeria:
              ports:
                - protocols:
                    - http
                    - https
                  port: 8080
            """.trimIndent(),
        )

        assertEquals(
            listOf(SpringArmeriaPortBinding("8080", listOf("HTTP", "HTTPS"))),
            config.ports,
        )
    }

    @Test
    fun parseYaml_blockAndFlowIncludeLists() {
        val block = ArmeriaSpringYamlRouteCollector.parseYaml(
            """
            armeria:
              internal-services:
                include:
                  - docs
                  - health
            """.trimIndent(),
        )
        assertEquals(setOf("docs", "health"), block.includes)

        val flow = ArmeriaSpringYamlRouteCollector.parseYaml(
            """
            armeria:
              internal-services:
                include: [docs, metrics]
            """.trimIndent(),
        )
        assertEquals(setOf("docs", "metrics"), flow.includes)
    }

    @Test
    fun parseYaml_includeAllExpandsActuator() {
        val config = ArmeriaSpringYamlRouteCollector.parseYaml(
            """
            armeria:
              internal-services:
                include: all
                port: 18080
              docs-path: /custom/docs
            """.trimIndent(),
        )

        assertEquals(setOf("docs", "health", "metrics", "actuator"), config.includes)
        assertEquals("18080", config.internalServicesPort)
        assertEquals("/custom/docs", config.docsPath)
    }

    @Test
    fun parseYaml_scopesPathKeysToArmeriaBlock() {
        val config = ArmeriaSpringYamlRouteCollector.parseYaml(
            """
            other:
              docs-path: /wrong
              health-check-path: /also-wrong
            armeria:
              docs-path: /internal/docs
              health-check-path: /internal/healthcheck
              metrics-path: /internal/metrics
            """.trimIndent(),
        )

        assertEquals("/internal/docs", config.docsPath)
        assertEquals("/internal/healthcheck", config.healthPath)
        assertEquals("/internal/metrics", config.metricsPath)
    }

    @Test
    fun parseYaml_acceptsCamelCaseInternalServices() {
        val config = ArmeriaSpringYamlRouteCollector.parseYaml(
            """
            armeria:
              internalServices:
                include: docs
              docsPath: /docs
              healthCheckPath: /health
              metricsPath: /metrics
            """.trimIndent(),
        )

        assertEquals(setOf("docs"), config.includes)
        assertEquals("/docs", config.docsPath)
        assertEquals("/health", config.healthPath)
        assertEquals("/metrics", config.metricsPath)
    }

    @Test
    fun parseProperties_sparsePortIndexes() {
        val config = ArmeriaSpringYamlRouteCollector.parseProperties(
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
        val config = ArmeriaSpringYamlRouteCollector.parseProperties(
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
        val config = ArmeriaSpringYamlRouteCollector.parseProperties(
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
        val config = ArmeriaSpringYamlRouteCollector.parseProperties(
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
        val all = ArmeriaSpringYamlRouteCollector.parseProperties(
            "armeria.internal-services.include=all",
        )
        assertEquals(setOf("docs", "health", "metrics", "actuator"), all.includes)

        val indexed = ArmeriaSpringYamlRouteCollector.parseProperties(
            """
            armeria.internal-services.include[0]=docs
            armeria.internal-services.include[1]=actuator
            """.trimIndent(),
        )
        assertEquals(setOf("docs", "actuator"), indexed.includes)
    }

    @Test
    fun parseProperties_acceptsColonDelimiterAndPlaceholders() {
        val config = ArmeriaSpringYamlRouteCollector.parseProperties(
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
    fun parseYaml_ignoresServerPortOutsideArmeria() {
        val config = ArmeriaSpringYamlRouteCollector.parseYaml(
            """
            server:
              port: 9999
            armeria:
              ports:
                - port: 8080
                  protocols:
                    - http
            """.trimIndent(),
        )

        assertEquals(listOf(SpringArmeriaPortBinding("8080", listOf("HTTP"))), config.ports)
        assertTrue(config.ports.none { it.port == "9999" })
    }

    @Test
    fun parseProperties_lastDuplicateKeyWins() {
        val config = ArmeriaSpringYamlRouteCollector.parseProperties(
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
    fun parseProperties_ignoresCommentedOutKeys() {
        val config = ArmeriaSpringYamlRouteCollector.parseProperties(
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
    fun parseYaml_inlineScalarCommentsStrippedBeforeTokenization() {
        val config = ArmeriaSpringYamlRouteCollector.parseYaml(
            """
            armeria:
              ports:
                - port: 8080
                  protocols: http # primary
            """.trimIndent(),
        )

        assertEquals(
            listOf(SpringArmeriaPortBinding("8080", listOf("HTTP"))),
            config.ports,
        )
    }

    @Test
    fun parseYaml_ignoresNestedArmeriaKey() {
        val config = ArmeriaSpringYamlRouteCollector.parseYaml(
            """
            wrapper:
              armeria:
                ports:
                  - port: 9999
                    protocols: HTTP
            armeria:
              ports:
                - port: 8080
                  protocols: HTTP
            """.trimIndent(),
        )

        assertEquals(listOf(SpringArmeriaPortBinding("8080", listOf("HTTP"))), config.ports)
    }

    @Test
    fun parseYaml_ignoresNestedPortsUnderArmeriaChild() {
        val config = ArmeriaSpringYamlRouteCollector.parseYaml(
            """
            armeria:
              foo:
                ports:
                  - port: 9999
                    protocols: HTTP
              ports:
                - port: 8080
                  protocols: HTTPS
              bar:
                internal-services:
                  include: docs
              internal-services:
                include: health
            """.trimIndent(),
        )

        assertEquals(listOf(SpringArmeriaPortBinding("8080", listOf("HTTPS"))), config.ports)
        assertEquals(setOf("health"), config.includes)
    }

    @Test
    fun parseProperties_protocolIndexLastWins() {
        val config = ArmeriaSpringYamlRouteCollector.parseProperties(
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
        val config = ArmeriaSpringYamlRouteCollector.parseProperties(
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
}
