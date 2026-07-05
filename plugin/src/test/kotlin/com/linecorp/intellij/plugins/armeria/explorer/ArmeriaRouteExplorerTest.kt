package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.FastTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(FastTest::class)
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
