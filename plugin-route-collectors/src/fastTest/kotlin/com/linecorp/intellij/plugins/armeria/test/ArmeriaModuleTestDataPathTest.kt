package com.linecorp.intellij.plugins.armeria.test

import junit.framework.TestCase
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArmeriaModuleTestDataPathTest : TestCase() {
    fun testResolveModuleTestDataPathRejectsInvalidProperty() {
        withModuleTestDataPathProperty { key ->
            System.setProperty(key, "/nonexistent/armeria-module-test-data-path")
            assertFailsWith<IllegalArgumentException> {
                resolveArmeriaModuleTestDataPath()
            }
        }
    }

    fun testResolveModuleTestDataPathAcceptsValidProperty() {
        val testDataDir = File("src/test/testData")
        require(testDataDir.isDirectory) {
            "Expected fixture testData dir at ${testDataDir.absolutePath}"
        }

        withModuleTestDataPathProperty { key ->
            System.setProperty(key, testDataDir.absolutePath)
            assertEquals(
                testDataDir.absolutePath.replace('\\', '/'),
                resolveArmeriaModuleTestDataPath(),
            )
        }
    }

    fun testResolveModuleTestDataPathFallsBackToWorkingDir() {
        val testDataDir = File("src/test/testData")
        require(testDataDir.isDirectory) {
            "Expected fixture testData dir at ${testDataDir.absolutePath}"
        }

        withModuleTestDataPathProperty { key ->
            System.clearProperty(key)
            assertEquals(
                testDataDir.absolutePath.replace('\\', '/'),
                resolveArmeriaModuleTestDataPath(),
            )
        }
    }

    private inline fun withModuleTestDataPathProperty(block: (String) -> Unit) {
        val key = "armeria.moduleTestDataPath"
        val previous = System.getProperty(key)
        try {
            block(key)
        } finally {
            if (previous == null) {
                System.clearProperty(key)
            } else {
                System.setProperty(key, previous)
            }
        }
    }
}
