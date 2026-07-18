package com.linecorp.intellij.plugins.armeria.test

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * [LightJavaCodeInsightFixtureTestCase] with 2026.2+ test sandbox root access for plugin runtime libraries.
 */
abstract class ArmeriaLightJavaCodeInsightFixtureTestCase : LightJavaCodeInsightFixtureTestCase() {

    override fun setUp() {
        ArmeriaTestSandboxAccess.allowTestSandboxRoots()
        try {
            super.setUp()
        } catch (t: Throwable) {
            ArmeriaTestSandboxAccess.disallowTestSandboxRoots()
            throw t
        }
    }

    override fun tearDown() {
        try {
            super.tearDown()
        } finally {
            ArmeriaTestSandboxAccess.disallowTestSandboxRoots()
        }
    }
}
