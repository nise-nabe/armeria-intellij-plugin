package com.linecorp.intellij.plugins.armeria.springboot.config

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaSpringBootSettingsInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaServerStubs()
        registerSpringAnnotationStubs()
        registerArmeriaSpringStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(
            ArmeriaSpringBootYamlSettingsInspection(),
            ArmeriaSpringBootPropertiesSettingsInspection(),
        )
    }

    @Test
    fun yamlHighlightsPositiveServerPortWithArmeriaPorts() {
        myFixture.configureByText(
            "application.yml",
            """
            server:
              port: 8080
            armeria:
              ports:
                - port: 8080
                  protocols:
                    - http
            """.trimIndent(),
        )
        val highlights = highlights(message("inspection.springboot.settings.port.conflict"))
        assertEquals(1, highlights.size, highlights.toString())
        val highlighted =
            myFixture.file.text.substring(highlights.single().startOffset, highlights.single().endOffset)
        assertTrue(highlighted.contains("8080"), highlighted)
    }

    @Test
    fun yamlDoesNotHighlightWhenServerPortIsMinusOne() {
        myFixture.configureByText(
            "application.yml",
            """
            server:
              port: -1
            armeria:
              ports:
                - port: 8080
            """.trimIndent(),
        )
        assertTrue(highlights(message("inspection.springboot.settings.port.conflict")).isEmpty())
    }

    @Test
    fun yamlDoesNotHighlightWhenWebApplicationTypeIsNone() {
        myFixture.configureByText(
            "application.yml",
            """
            spring:
              main:
                web-application-type: none
            server:
              port: 8080
            armeria:
              ports:
                - port: 8080
            """.trimIndent(),
        )
        assertTrue(highlights(message("inspection.springboot.settings.port.conflict")).isEmpty())
    }

    @Test
    fun yamlHighlightsIncludeDocsWithoutPathOrConfigurator() {
        myFixture.configureByText(
            "application.yml",
            """
            armeria:
              internal-services:
                include: docs
            """.trimIndent(),
        )
        val expected =
            message(
                "inspection.springboot.settings.internal.missing",
                "docs",
                ArmeriaSpringBootSettingsConflict.DOCS_PATH_KEY,
            )
        val highlights = highlights(expected)
        assertEquals(1, highlights.size, myFixture.doHighlighting().map { it.description }.toString())
        val highlighted =
            myFixture.file.text.substring(highlights.single().startOffset, highlights.single().endOffset)
        assertEquals("docs", highlighted)
    }

    @Test
    fun yamlDoesNotHighlightIncludeDocsWhenDocsPathSet() {
        myFixture.configureByText(
            "application.yml",
            """
            armeria:
              docs-path: /docs
              internal-services:
                include: docs
            """.trimIndent(),
        )
        val expected =
            message(
                "inspection.springboot.settings.internal.missing",
                "docs",
                ArmeriaSpringBootSettingsConflict.DOCS_PATH_KEY,
            )
        assertTrue(highlights(expected).isEmpty())
    }

    @Test
    fun yamlDoesNotHighlightIncludeDocsWhenDocServiceConfiguratorPresent() {
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.spring.DocServiceConfigurator;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class ArmeriaConfiguration {
                @Bean
                public DocServiceConfigurator docServiceConfigurator() {
                    return builder -> {};
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "application.yml",
            """
            armeria:
              internal-services:
                include: docs
            """.trimIndent(),
        )
        val expected =
            message(
                "inspection.springboot.settings.internal.missing",
                "docs",
                ArmeriaSpringBootSettingsConflict.DOCS_PATH_KEY,
            )
        assertTrue(highlights(expected).isEmpty(), myFixture.doHighlighting().map { it.description }.toString())
    }

    @Test
    fun yamlHighlightsIncludeSequenceItem() {
        myFixture.configureByText(
            "application.yml",
            """
            armeria:
              internal-services:
                include:
                  - docs
                  - health
            """.trimIndent(),
        )
        val docs =
            message(
                "inspection.springboot.settings.internal.missing",
                "docs",
                ArmeriaSpringBootSettingsConflict.DOCS_PATH_KEY,
            )
        val health =
            message(
                "inspection.springboot.settings.internal.missing",
                "health",
                ArmeriaSpringBootSettingsConflict.HEALTH_PATH_KEY,
            )
        assertEquals(1, highlights(docs).size)
        assertEquals(1, highlights(health).size)
    }

    @Test
    fun propertiesHighlightsPositiveServerPortWithArmeriaPorts() {
        myFixture.configureByText(
            "application.properties",
            """
            server.port=8080
            armeria.ports[0].port=8080
            """.trimIndent(),
        )
        val highlights = highlights(message("inspection.springboot.settings.port.conflict"))
        assertEquals(1, highlights.size, highlights.toString())
    }

    @Test
    fun propertiesDoesNotHighlightWhenServerPortIsMinusOne() {
        myFixture.configureByText(
            "application.properties",
            """
            server.port=-1
            armeria.ports[0].port=8080
            """.trimIndent(),
        )
        assertTrue(highlights(message("inspection.springboot.settings.port.conflict")).isEmpty())
    }

    @Test
    fun propertiesHighlightsIncludeDocsWithoutPathOrConfigurator() {
        myFixture.configureByText(
            "application.properties",
            "armeria.internal-services.include=docs",
        )
        val expected =
            message(
                "inspection.springboot.settings.internal.missing",
                "docs",
                ArmeriaSpringBootSettingsConflict.DOCS_PATH_KEY,
            )
        assertEquals(1, highlights(expected).size, myFixture.doHighlighting().map { it.description }.toString())
    }

    @Test
    fun propertiesDoesNotHighlightIncludeDocsWhenDocsPathSet() {
        myFixture.configureByText(
            "application.properties",
            """
            armeria.docs-path=/docs
            armeria.internal-services.include=docs
            """.trimIndent(),
        )
        val expected =
            message(
                "inspection.springboot.settings.internal.missing",
                "docs",
                ArmeriaSpringBootSettingsConflict.DOCS_PATH_KEY,
            )
        assertTrue(highlights(expected).isEmpty())
    }

    private fun highlights(description: String) = myFixture.doHighlighting().filter { it.description == description }
}
