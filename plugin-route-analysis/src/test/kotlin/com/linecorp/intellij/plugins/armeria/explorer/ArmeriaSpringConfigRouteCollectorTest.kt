package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue

class ArmeriaSpringConfigRouteCollectorTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    fun testCollectPortsAndInternalServicesFromYaml() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            armeria:
              ports:
                - port: 8080
                  protocols:
                    - http
              internal-services:
                include: docs, health, metrics
              docs-path: /internal/docs
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        assertTrue(routes.any { it.target.contains("8080") && it.path == ":8080" })
        assertTrue(routes.any { it.isDocService && it.path == "/internal/docs" })
        assertTrue(
            routes.any {
                it.path == "/internal/healthcheck" &&
                    it.routeMatch == RouteMatch.CONFIG &&
                    it.httpMethod == "GET"
            },
        )
        assertTrue(
            routes.any {
                it.path == "/internal/metrics" &&
                    it.routeMatch == RouteMatch.CONFIG &&
                    it.httpMethod == "GET"
            },
        )
    }

    fun testCollectFromPropertiesFile() {
        val psiFile = myFixture.configureByText(
            "application.properties",
            """
            armeria.ports[0].port=9090
            armeria.ports[0].protocols[0]=http
            armeria.internal-services.include=docs,actuator
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        assertTrue(routes.any { it.target.contains("9090") && it.path == ":9090" })
        assertTrue(routes.any { it.isDocService })
        assertTrue(routes.any { it.path == "/actuator" && it.routeMatch == RouteMatch.CONFIG })
    }

    fun testCollectDiscoversApplicationConfigByFilename() {
        myFixture.configureByText(
            "application.yml",
            """
            armeria:
              ports:
                - port: 8080
                  protocols: HTTP
            """.trimIndent(),
        )
        // Unrelated YAML must not be scanned as a config candidate.
        myFixture.addFileToProject(
            "unrelated-config.yml",
            """
            armeria:
              ports:
                - port: 9999
                  protocols: HTTP
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collect(
            project,
            GlobalSearchScope.projectScope(project),
            routes,
            mutableSetOf(),
        )

        assertTrue(routes.any { it.path == ":8080" })
        assertFalse(routes.any { it.path == ":9999" || it.target.contains("9999") })
    }

    fun testCollectFromProfileYaml() {
        val psiFile = myFixture.configureByText(
            "application-dev.yml",
            """
            armeria:
              ports:
                - port: 7070
                  protocols:
                    - http
              internal-services:
                include: docs
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        assertTrue(routes.any { it.target.contains("7070") && it.target.contains("[dev]") })
        assertTrue(routes.any { it.isDocService && it.target.contains("[dev]") })
    }

    fun testIgnoresServerPortFalsePositive() {
        val psiFile = myFixture.configureByText(
            "application.yml",
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

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        assertTrue(routes.any { it.target.contains("8080") })
        assertFalse(routes.any { it.target.contains("9999") })
    }

    fun testIgnoresUnrelatedIncludeKey() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            spring:
              profiles:
                include: dev,local
            armeria:
              internal-services:
                include: docs
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        assertTrue(routes.any { it.isDocService })
        assertFalse(routes.any { it.path == "/actuator" })
        assertFalse(routes.any { it.path == "/internal/healthcheck" })
    }

    fun testIncludeAllEnablesActuator() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            armeria:
              internal-services:
                include: all
                port: 18080
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        assertTrue(routes.any { it.isDocService })
        assertTrue(routes.any { it.path == "/internal/healthcheck" && it.routeMatch == RouteMatch.CONFIG })
        assertTrue(routes.any { it.path == "/internal/metrics" })
        assertTrue(routes.any { it.path == "/actuator" })
        assertTrue(routes.all { !it.isDocService || it.target.contains(":18080") })
    }

    fun testYamlIncludeAllExpandsAndSurfacesInternalPort() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            armeria:
              internal-services:
                include: all
                port: 18080
              docs-path: /custom/docs
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        assertTrue(routes.any { it.isDocService && it.path == "/custom/docs" && it.target.contains(":18080") })
        assertTrue(routes.any { it.path == "/internal/healthcheck" && it.target.contains(":18080") })
        assertTrue(routes.any { it.path == "/internal/metrics" && it.target.contains(":18080") })
        assertTrue(routes.any { it.path == "/actuator" && it.target.contains(":18080") })
    }

    fun testUnrelatedDocsPathDoesNotOverrideArmeriaPath() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            myapp:
              docs-path: /wrong
            armeria:
              docs-path: /internal/docs
              internal-services:
                include: docs
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        assertTrue(routes.any { it.isDocService && it.path == "/internal/docs" })
        assertFalse(routes.any { it.path == "/wrong" })
    }

    fun testHttpsPortUsesHttpsProtocolLabel() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            armeria:
              ports:
                - port: 8443
                  protocols: HTTPS
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        val portRoute = routes.single { it.target.contains("8443") }
        assertEquals("HTTPS", portRoute.protocol)
        assertEquals(":8443", portRoute.path)
        assertEquals(RouteMatch.NON_HTTP, portRoute.routeMatch)
        assertEquals(message("route.explorer.spring.port", "8443", "HTTPS"), portRoute.target)
    }

    fun testPortBindingProtocolLabelIncludesAllProtocols() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            armeria:
              ports:
                - port: 8080
                  protocols:
                    - HTTP
                    - HTTPS
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        val portRoute = routes.single { it.target.contains("8080") }
        assertEquals("HTTP, HTTPS", portRoute.protocol)
        assertEquals("HTTP, HTTPS", portRoute.methodLabel)
        assertEquals(
            message("route.explorer.registration.nonHttp", "HTTP, HTTPS", ":8080"),
            portRoute.resolveRegistrationSummary(),
        )
    }

    fun testConfigInternalServicesDoNotDuplicateWithServiceUnderRoot() {
        val configFile = myFixture.configureByText(
            "application.yml",
            """
            armeria:
              internal-services:
                include: docs, health, metrics, actuator
            """.trimIndent(),
        )
        val configRoutes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(configFile, configRoutes, mutableSetOf())
        assertTrue(configRoutes.isNotEmpty())
        assertTrue(configRoutes.filter { it.isDocService }.all { it.routeMatch == RouteMatch.NON_HTTP })
        assertTrue(
            configRoutes.filter { !it.isDocService && it.httpMethod.isNotBlank() }
                .all { it.routeMatch == RouteMatch.CONFIG },
        )

        val serviceUnder = ArmeriaRoute.create(
            element = configFile,
            protocol = "HTTP",
            httpMethod = "",
            path = "/",
            target = "ServiceHandler",
            routeMatch = RouteMatch.SERVICE_UNDER,
        )
        val groups = ArmeriaRouteDuplicateIndex.findDuplicateGroups(configRoutes + serviceUnder)
        assertTrue(
            "Config-sourced internal services must not conflict with serviceUnder(\"/\")",
            groups.isEmpty(),
        )
    }

    fun testYamlInlineCommentsAreStripped() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            armeria:
              ports:
                - port: 8080
                  protocols:
                    - http # primary
              internal-services:
                include: docs # doc service
              docs-path: /internal/docs # custom docs
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        val portRoute = routes.single { it.target.contains("8080") }
        assertEquals("HTTP", portRoute.protocol)
        assertTrue(routes.any { it.isDocService && it.path == "/internal/docs" })
        assertFalse(routes.any { it.path.contains("#") })
    }

    fun testInternalServiceDedupeIncludesPortAcrossConfigFiles() {
        myFixture.configureByText(
            "application.yml",
            """
            armeria:
              internal-services:
                include: docs
                port: 17070
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "application.properties",
            """
            armeria.internal-services.include=docs
            armeria.internal-services.port=18080
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collect(
            project,
            GlobalSearchScope.projectScope(project),
            routes,
            mutableSetOf(),
        )

        val docRoutes = routes.filter { it.isDocService }
        assertEquals(2, docRoutes.size)
        assertTrue(docRoutes.any { it.target.contains(":17070") })
        assertTrue(docRoutes.any { it.target.contains(":18080") })
    }

    fun testConfigRoutesSupportGenerateHttpWithoutRuntimeSemantics() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            armeria:
              internal-services:
                include: health, metrics
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        val health = routes.single { it.path == "/internal/healthcheck" }
        assertEquals(RouteMatch.CONFIG, health.routeMatch)
        assertTrue(ArmeriaHttpRequestGenerator.supports(health))
        assertEquals("GET", ArmeriaHttpRequestGenerator.httpMethod(health))
        assertFalse(ArmeriaRouteDetailFormatter.statusLine(health).contains(message("route.explorer.badge.runtime")))
        assertTrue(ArmeriaRouteDetailFormatter.statusLine(health).contains(message("route.explorer.badge.staticAnalysis")))
    }

    fun testYamlPortNavigationTargetsKeyElement() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            # preamble
            armeria:
              ports:
                - port: 8080
                  protocols: HTTP
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        val portRoute = routes.single { it.path == ":8080" }
        val element = portRoute.pointer.element
        assertNotNull(element)
        assertTrue(
            "Port route should navigate to the YAML port key, not the whole file",
            element is org.jetbrains.yaml.psi.YAMLKeyValue,
        )
        assertEquals("port", (element as org.jetbrains.yaml.psi.YAMLKeyValue).keyText)
        assertFalse(portRoute.resolveSourceHint().endsWith(":1"))
        assertSame(psiFile, element.containingFile)
    }

    fun testYamlMultiDocumentFindsArmeriaInLaterDocument() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            spring:
              application:
                name: demo
            ---
            armeria:
              ports:
                - port: 8080
                  protocols: HTTP
              internal-services:
                include: docs
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        assertTrue(routes.any { it.path == ":8080" })
        assertTrue(routes.any { it.isDocService })
    }

    fun testYamlAcceptsCamelCaseKeysAndFlowIncludes() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            armeria:
              internalServices:
                include: [docs, metrics]
              docsPath: /docs
              healthCheckPath: /health
              metricsPath: /metrics
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        assertTrue(routes.any { it.isDocService && it.path == "/docs" })
        assertTrue(routes.any { it.path == "/metrics" })
        assertFalse(routes.any { it.path == "/health" || it.path == "/internal/healthcheck" })
    }

    fun testYamlIgnoresNestedArmeriaAndNestedPorts() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            wrapper:
              armeria:
                ports:
                  - port: 9999
                    protocols: HTTP
            armeria:
              foo:
                ports:
                  - port: 8888
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

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        assertTrue(routes.any { it.path == ":8080" && it.protocol == "HTTPS" })
        assertFalse(routes.any { it.path == ":9999" || it.path == ":8888" })
        assertTrue(routes.any { it.path == "/internal/healthcheck" })
        assertFalse(routes.any { it.isDocService })
    }

    fun testYamlBlockIncludeListAndCommentOnlyInlineIgnored() {
        val withBlock = myFixture.configureByText(
            "application.yml",
            """
            armeria:
              internal-services:
                include:
                  - docs
                  - health
            """.trimIndent(),
        )
        val blockRoutes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(withBlock, blockRoutes, mutableSetOf())
        assertTrue(blockRoutes.any { it.isDocService })
        assertTrue(blockRoutes.any { it.path == "/internal/healthcheck" })

        val commentOnly = myFixture.configureByText(
            "application-empty.yml",
            """
            armeria:
              internal-services:
                include: # docs disabled
              ports:
                - port: 8080
                  protocols: HTTP
            """.trimIndent(),
        )
        val emptyIncludeRoutes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(commentOnly, emptyIncludeRoutes, mutableSetOf())
        assertTrue(emptyIncludeRoutes.any { it.path == ":8080" })
        assertFalse(emptyIncludeRoutes.any { it.isDocService })
    }

    fun testYamlProtocolsBeforePortAndMultiProtocol() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            armeria:
              ports:
                - protocols:
                    - http
                    - https
                  port: 8080
                - address: 127.0.0.1
                  port: 8081
                  protocols: HTTP
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        assertEquals("HTTP, HTTPS", routes.single { it.path == ":8080" }.protocol)
        assertEquals("HTTP", routes.single { it.path == ":8081" }.protocol)
    }

    fun testYamlInternalServiceNavigationTargetsKeyElements() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            # preamble
            armeria:
              docs-path: /custom/docs
              health-check-path: /custom/health
              metrics-path: /custom/metrics
              internal-services:
                include: docs, health, metrics
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        val docs = routes.single { it.isDocService }
        val health = routes.single { it.path == "/custom/health" }
        val metrics = routes.single { it.path == "/custom/metrics" }
        assertFalse(docs.pointer.element is PsiFile)
        assertFalse(health.pointer.element is PsiFile)
        assertFalse(metrics.pointer.element is PsiFile)
        assertFalse(docs.resolveSourceHint().endsWith(":1"))
        assertFalse(health.resolveSourceHint().endsWith(":1"))
        assertFalse(metrics.resolveSourceHint().endsWith(":1"))
        assertSame(psiFile, docs.pointer.element!!.containingFile)
    }

    fun testYamlCommentOnlyIncludeThenBlockList() {
        val psiFile = myFixture.configureByText(
            "application.yml",
            """
            armeria:
              internal-services:
                include: # docs disabled
                  - docs
                  - health
            """.trimIndent(),
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        assertTrue(routes.any { it.isDocService })
        assertTrue(routes.any { it.path == "/internal/healthcheck" })
    }

    fun testYamlPlainTextFileTypeStillDiscoversRoutes() {
        val content =
            """
            armeria:
              ports:
                - port: 8080
                  protocols: HTTP
              internal-services:
                include: docs
            """.trimIndent()
        val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
            "application.yml",
            PlainTextFileType.INSTANCE,
            content,
        )
        assertFalse(
            "Fixture must not be YAMLFile for this regression",
            psiFile is YAMLFile,
        )

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaSpringConfigRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        assertTrue(routes.any { it.path == ":8080" })
        assertTrue(routes.any { it.isDocService })
        // Dummy YAML tree is not attached; navigation stays on the original file.
        assertSame(psiFile, routes.single { it.path == ":8080" }.pointer.element)
    }
}
