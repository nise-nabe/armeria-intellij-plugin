package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.codeInsight.completion.CompletionType
import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import org.jetbrains.yaml.psi.YAMLFile
import kotlin.test.assertTrue

class ArmeriaSpringBootConfigCompletionTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    fun testYamlCompletionUnderArmeriaSuggestsDocsHealthMetricsAndInclude() {
        val file =
            myFixture.configureByText(
                "application.yml",
                """
                armeria:
                  i<caret>:
                """.trimIndent(),
            )
        assertTrue(file is YAMLFile, "expected YAML file, got ${file.fileType.name} ${file.javaClass.name}")
        val lookups = lookupStrings()
        assertContainsAll(lookups, "internal-services", "idle-timeout")
    }

    fun testYamlCompletionWithoutColonSuggestsNestedKeys() {
        myFixture.configureByText(
            "application.yml",
            """
            armeria:
              i<caret>
            """.trimIndent(),
        )
        val lookups = lookupStrings()
        assertContainsAll(lookups, "internal-services", "idle-timeout")
    }

    fun testYamlCompletionNestedPathWithoutColon() {
        myFixture.configureByText(
            "application.yml",
            """
            armeria:
              internal-services:
                i<caret>
            """.trimIndent(),
        )
        val lookups = lookupStrings()
        assertContainsAll(lookups, "include")
        assertTrue("docs-path" !in lookups, lookups.toString())
    }

    fun testYamlCompletionSequenceItemKeysWithoutColon() {
        myFixture.configureByText(
            "application.yml",
            """
            armeria:
              ports:
                - p<caret>
            """.trimIndent(),
        )
        val lookups = lookupStrings()
        assertContainsAll(lookups, "port", "protocols")
    }

    fun testYamlCompletionNestedPathInsertsLeafSegment() {
        myFixture.configureByText(
            "application.yml",
            """
            armeria:
              internal-services:
                i<caret>:
            """.trimIndent(),
        )
        val lookups = lookupStrings()
        assertContainsAll(lookups, "include")
        assertTrue("docs-path" !in lookups, lookups.toString())
    }

    fun testYamlIncludeValueCompletionOffersServiceIds() {
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

    fun testYamlCompletionSuggestsDocsPath() {
        myFixture.configureByText(
            "application.yml",
            """
            armeria:
              docs-<caret>:
            """.trimIndent(),
        )
        assertLookupOrInserted("docs-path")
    }

    fun testPropertiesCompletionSuggestsArmeriaSettingsKeys() {
        myFixture.configureByText(
            "application.properties",
            "armeria.d<caret>",
        )
        val lookups = lookupStrings()
        assertContainsAll(lookups, "armeria.docs-path")
    }

    fun testPropertiesIncludeValueCompletionOffersServiceIds() {
        val file =
            myFixture.configureByText(
                "application.properties",
                "armeria.internal-services.include=",
            )
        myFixture.editor.caretModel.moveToOffset(file.textLength)
        val lookups = lookupStrings()
        assertContainsAll(lookups, "docs", "health", "metrics", "actuator", "all")
    }

    fun testPropertiesIncludeValueCompletionAcceptsWhitespaceSeparator() {
        val file =
            myFixture.configureByText(
                "application.properties",
                "armeria.internal-services.include ",
            )
        myFixture.editor.caretModel.moveToOffset(file.textLength)
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

    private fun assertLookupOrInserted(expected: String) {
        val lookups = lookupStrings()
        if (lookups.isEmpty()) {
            assertTrue(
                myFixture.editor.document.text
                    .contains(expected),
                "expected lookup or unique insertion of $expected; fileType=${myFixture.file.fileType.name}",
            )
            return
        }
        assertTrue(expected in lookups, lookups.toString())
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
