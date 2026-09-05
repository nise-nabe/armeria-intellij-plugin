package com.linecorp.intellij.plugins.armeria.test

import junit.framework.TestCase
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaUltimateTestErrorSupportTest : TestCase() {
    fun testDetectsUltimateConstructorFailureFromCauseChain() {
        val cause =
            RuntimeException(
                "Cannot find suitable constructor for class B.B.B.B.s, expected (), " +
                    "(CoroutineScope), (Application), or (Application, CoroutineScope)",
            )
        val exception =
            RuntimeException(
                "Cannot create extension (class=B.B.B.B.s) [Plugin: com.intellij.modules.ultimate]",
                cause,
            )

        assertTrue(isUltimatePostStartupConstructorError(exception.message, exception))
    }

    fun testDetectsUltimateExtensionMessageWithoutCause() {
        assertTrue(
            isUltimatePostStartupConstructorError(
                "Cannot create extension (class=B.B.B.B.s) [Plugin: com.intellij.modules.ultimate]",
                null,
            ),
        )
    }

    fun testIgnoresUnrelatedPluginErrors() {
        val exception =
            RuntimeException("Cannot create extension (class=Foo) [Plugin: com.linecorp.armeria]")
        assertFalse(isUltimatePostStartupConstructorError(exception.message, exception))
    }
}
