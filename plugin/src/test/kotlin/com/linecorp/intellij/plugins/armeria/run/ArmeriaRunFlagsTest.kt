package com.linecorp.intellij.plugins.armeria.run

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaRunFlagsTest {
    @Test
    fun systemProperties_emptyWhenUnchecked() {
        assertTrue(ArmeriaRunFlags.systemProperties(verboseResponses = false, reportBlockedEventLoop = false).isEmpty())
    }

    @Test
    fun systemProperties_appendsDocumentedFlags() {
        assertEquals(
            listOf(
                "-Dcom.linecorp.armeria.verboseResponses=true",
                "-Dcom.linecorp.armeria.reportBlockedEventLoop=true",
            ),
            ArmeriaRunFlags.systemProperties(verboseResponses = true, reportBlockedEventLoop = true),
        )
    }

    @Test
    fun systemProperties_verboseResponsesOnly() {
        assertEquals(
            listOf("-Dcom.linecorp.armeria.verboseResponses=true"),
            ArmeriaRunFlags.systemProperties(verboseResponses = true, reportBlockedEventLoop = false),
        )
    }
}

class ArmeriaRunProfileStateStartHintTest {
    @Test
    fun looksLikeServerStarted_matchesArmeriaServingLog() {
        assertTrue(ArmeriaRunProfileState.looksLikeServerStarted("Serving HTTP at /0.0.0.0:8080"))
        assertTrue(ArmeriaRunProfileState.looksLikeServerStarted("Serving HTTPS at /0.0.0.0:8443"))
        assertFalse(ArmeriaRunProfileState.looksLikeServerStarted("Loading configuration"))
        assertFalse(ArmeriaRunProfileState.looksLikeServerStarted(null))
    }
}
