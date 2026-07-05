package com.linecorp.intellij.plugins.armeria.explorer

import org.junit.Assert.assertEquals
import org.junit.Test

class ArmeriaRouteExplorerTest {
    @Test
    fun truncateTarget_shortensLongValues() {
        val value = "a".repeat(100)
        assertEquals("a".repeat(60) + "…", ArmeriaRoute.truncateTarget(value))
    }

    @Test
    fun truncateTarget_keepsShortValues() {
        assertEquals("handler", ArmeriaRoute.truncateTarget("handler"))
    }
}
