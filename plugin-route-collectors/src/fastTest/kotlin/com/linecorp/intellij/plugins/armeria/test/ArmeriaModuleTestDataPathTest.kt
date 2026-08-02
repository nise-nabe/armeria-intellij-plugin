package com.linecorp.intellij.plugins.armeria.test

import junit.framework.TestCase
import kotlin.test.assertFailsWith

class ArmeriaModuleTestDataPathTest : TestCase() {
    fun testResolveModuleTestDataPathRejectsInvalidProperty() {
        val key = "armeria.moduleTestDataPath"
        val previous = System.getProperty(key)
        try {
            System.setProperty(key, "/nonexistent/armeria-module-test-data-path")
            assertFailsWith<IllegalArgumentException> {
                resolveArmeriaModuleTestDataPath()
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
