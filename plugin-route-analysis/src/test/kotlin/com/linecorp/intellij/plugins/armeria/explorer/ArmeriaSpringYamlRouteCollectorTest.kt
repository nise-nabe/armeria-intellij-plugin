package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

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

        assertTrue(routes.any { it.target.contains("8080") })
        assertTrue(routes.any { it.isDocService && it.path == "/internal/docs" })
        assertTrue(routes.any { it.path == "/internal/healthcheck" })
        assertTrue(routes.any { it.path == "/internal/metrics" })
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

        assertTrue(routes.any { it.target.contains("9090") })
        assertTrue(routes.any { it.isDocService })
        assertTrue(routes.any { it.path == "/actuator" })
    }
}
