package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.codeInsight.completion.CompletionType
import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase5
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ArmeriaSpringBootConfigCompletionTest : ArmeriaLightJavaCodeInsightFixtureTestCase5() {
    @Test
    fun yamlCompletionUnderArmeriaSuggestsDocsHealthMetricsAndInclude() {
        myFixture.configureByText(
            "application.yml",
            """
            armeria:
              <caret>
            """.trimIndent(),
        )
        val lookups = lookupStrings()
        assertContainsAll(
            lookups,
            "docs-path",
            "health-check-path",
            "metrics-path",
            "internal-services",
        )
    }

    @Test
    fun yamlCompletionNestedPathInsertsLeafSegment() {
        myFixture.configureByText(
            "application.yml",
            """
            armeria:
              internal-services:
                <caret>
            """.trimIndent(),
        )
        val lookups = lookupStrings()
        assertContainsAll(lookups, "include", "port")
        assertTrue("docs-path" !in lookups, lookups.toString())
    }

    @Test
    fun yamlIncludeValueCompletionOffersServiceIds() {
        myFixture.configureByText(
            "application.yml",
            """
            armeria:
              internal-services:
                include: <caret>
            """.trimIndent(),
        )
        val lookups = lookupStrings()
        assertContainsAll(lookups, "docs", "health", "metrics", "actuator", "all")
    }

    @Test
    fun propertiesCompletionSuggestsArmeriaSettingsKeys() {
        myFixture.configureByText(
            "application.properties",
            "armeria.<caret>",
        )
        val lookups = lookupStrings()
        assertContainsAll(
            lookups,
            "armeria.docs-path",
            "armeria.health-check-path",
            "armeria.metrics-path",
            "armeria.internal-services.include",
        )
    }

    @Test
    fun propertiesIncludeValueCompletionOffersServiceIds() {
        myFixture.configureByText(
            "application.properties",
            "armeria.internal-services.include=<caret>",
        )
        val lookups = lookupStrings()
        assertContainsAll(lookups, "docs", "health", "metrics", "actuator", "all")
    }

    private fun lookupStrings(): List<String> {
        val elements = myFixture.complete(CompletionType.BASIC)
        if (elements != null) {
            return elements.map { it.lookupString }
        }
        return myFixture.lookupElementStrings.orEmpty()
    }

    private fun assertContainsAll(
        lookups: List<String>,
        vararg expected: String,
    ) {
        for (value in expected) {
            assertTrue(value in lookups, "missing $value in $lookups")
        }
    }
}
