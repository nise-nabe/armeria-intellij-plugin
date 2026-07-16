package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaSpringYamlRouteCollectorTest : LightJavaCodeInsightFixtureTestCase() {
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
        ArmeriaSpringYamlRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

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
        ArmeriaSpringYamlRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

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
        ArmeriaSpringYamlRouteCollector.collect(
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
        ArmeriaSpringYamlRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

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
        ArmeriaSpringYamlRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

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
        ArmeriaSpringYamlRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

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
        ArmeriaSpringYamlRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        assertTrue(routes.any { it.isDocService })
        assertTrue(routes.any { it.path == "/internal/healthcheck" && it.routeMatch == RouteMatch.CONFIG })
        assertTrue(routes.any { it.path == "/internal/metrics" })
        assertTrue(routes.any { it.path == "/actuator" })
        assertTrue(routes.all { !it.isDocService || it.target.contains(":18080") })
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
        ArmeriaSpringYamlRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

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
        ArmeriaSpringYamlRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        val portRoute = routes.single { it.target.contains("8443") }
        assertEquals("HTTPS", portRoute.protocol)
        assertEquals(":8443", portRoute.path)
        assertEquals(RouteMatch.NON_HTTP, portRoute.routeMatch)
        assertEquals(message("route.explorer.spring.port", "8443", "HTTPS"), portRoute.target)
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
        ArmeriaSpringYamlRouteCollector.collectFromPsiFile(configFile, configRoutes, mutableSetOf())
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
        ArmeriaSpringYamlRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        val portRoute = routes.single { it.target.contains("8080") }
        assertEquals("HTTP", portRoute.protocol)
        assertTrue(routes.any { it.isDocService && it.path == "/internal/docs" })
        assertFalse(routes.any { it.path.contains("#") })
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
        ArmeriaSpringYamlRouteCollector.collectFromPsiFile(psiFile, routes, mutableSetOf())

        val health = routes.single { it.path == "/internal/healthcheck" }
        assertEquals(RouteMatch.CONFIG, health.routeMatch)
        assertTrue(ArmeriaHttpRequestGenerator.supports(health))
        assertEquals("GET", ArmeriaHttpRequestGenerator.httpMethod(health))
        assertFalse(ArmeriaRouteDetailFormatter.statusLine(health).contains(message("route.explorer.badge.runtime")))
        assertTrue(ArmeriaRouteDetailFormatter.statusLine(health).contains(message("route.explorer.badge.staticAnalysis")))
    }
}
