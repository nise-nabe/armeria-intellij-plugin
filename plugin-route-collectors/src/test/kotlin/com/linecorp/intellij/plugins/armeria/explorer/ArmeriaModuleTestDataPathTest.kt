package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import kotlin.test.assertFailsWith

class ArmeriaModuleTestDataPathTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    fun testResolveModuleTestDataPathRejectsInvalidProperty() {
        val key = "armeria.moduleTestDataPath"
        val previous = System.getProperty(key)
        try {
            System.setProperty(key, "/nonexistent/armeria-module-test-data-path")
            assertFailsWith<IllegalArgumentException> {
                resolveModuleTestDataPath()
            }
        } finally {
            if (previous == null) {
                System.clearProperty(key)
            } else {
                System.setProperty(key, previous)
            }
        }
    }
}
