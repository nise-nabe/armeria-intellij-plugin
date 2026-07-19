package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import junit.framework.TestCase

class ArmeriaMethodEntryPointTest : ArmeriaFixtureTestBase() {
    fun testDisplayNameUsesBundleWhenAvailable() {
        val entryPoint = ArmeriaMethodEntryPoint()
        TestCase.assertEquals(message("inspection.entrypoint.armeria"), entryPoint.displayName)
    }
}
