package com.linecorp.intellij.plugins.armeria.run

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaRunProfileStateStartHintTest {
    @Test
    fun looksLikeServerStarted_matchesArmeriaServingLog() {
        assertTrue(ArmeriaRunProfileState.looksLikeServerStarted("Serving HTTP at /0.0.0.0:8080"))
        assertTrue(ArmeriaRunProfileState.looksLikeServerStarted("Serving HTTPS at /0.0.0.0:8443"))
        assertFalse(ArmeriaRunProfileState.looksLikeServerStarted("Loading configuration"))
        assertFalse(ArmeriaRunProfileState.looksLikeServerStarted(null))
    }

    @Test
    fun appendStartupOutput_detectsMarkerSplitAcrossChunks() {
        val buffer = StringBuilder()
        assertFalse(ArmeriaRunProfileState.looksLikeServerStarted(ArmeriaRunProfileState.appendStartupOutput(buffer, "Serv")))
        assertTrue(
            ArmeriaRunProfileState.looksLikeServerStarted(ArmeriaRunProfileState.appendStartupOutput(buffer, "ing HTTP at /0.0.0.0:8080")),
        )
    }
}
