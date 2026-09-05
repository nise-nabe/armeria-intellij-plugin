package com.linecorp.intellij.plugins.armeria.springboot.config

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaDropwizardConfigCollectorTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaServerStubs()
    }

    @Test
    fun collect_emptyWhenDropwizardIsNotOnClasspath() {
        assertTrue(ArmeriaDropwizardConfigCollector.collect(project).isEmpty())
    }

    @Test
    fun collect_emitsDocsLinkedRowWhenArmeriaBundleIsPresent() {
        registerDropwizardStubs()

        val files = ArmeriaDropwizardConfigCollector.collect(project)
        val file = files.single()
        assertTrue(file.synthetic)
        assertTrue(file.dropwizard)
        assertEquals(message("springboot.config.dropwizard.fileName"), file.fileName)
        assertEquals(message("springboot.config.dropwizard.key"), file.entries.single().key)
        assertEquals(ArmeriaDropwizardConfigCollector.DOCS_URL, file.entries.single().externalUrl)
    }
}
