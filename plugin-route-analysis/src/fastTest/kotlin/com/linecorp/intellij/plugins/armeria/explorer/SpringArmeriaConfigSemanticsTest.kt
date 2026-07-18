package com.linecorp.intellij.plugins.armeria.explorer

import org.junit.Assert.assertEquals
import org.junit.Test

class SpringArmeriaConfigSemanticsTest {
    @Test
    fun expandIncludes_allExpandsToCanonicalIds() {
        assertEquals(
            SpringArmeriaConfigSemantics.INTERNAL_SERVICE_IDS,
            SpringArmeriaConfigSemantics.expandIncludes(setOf("all")),
        )
        assertEquals(
            SpringArmeriaConfigSemantics.INTERNAL_SERVICE_IDS,
            SpringArmeriaConfigSemantics.expandIncludes(setOf("ALL", "docs")),
        )
    }

    @Test
    fun expandIncludes_filtersUnknownAndLowercases() {
        assertEquals(
            setOf("docs", "health"),
            SpringArmeriaConfigSemantics.expandIncludes(setOf("Docs", "HEALTH", "unknown")),
        )
    }

    @Test
    fun parseIncludeTokens_splitsCommaAndSpace() {
        assertEquals(
            setOf("docs", "health", "metrics"),
            SpringArmeriaConfigSemantics.parseIncludeTokens("docs, health metrics"),
        )
    }

    @Test
    fun normalizeProtocols_uppercasesDistinctAndDefaults() {
        assertEquals(
            listOf("HTTP", "HTTPS"),
            SpringArmeriaConfigSemantics.normalizeProtocols(listOf("http", "HTTPS", "http")),
        )
        assertEquals(
            listOf("HTTP"),
            SpringArmeriaConfigSemantics.normalizeProtocols(emptyList()),
        )
    }

    @Test
    fun splitScalarList_stripsBracketsAndQuotes() {
        assertEquals(
            listOf("http", "https"),
            SpringArmeriaConfigSemantics.splitScalarList("[\"http\", 'https']"),
        )
        assertEquals(emptyList<String>(), SpringArmeriaConfigSemantics.splitScalarList("[]"))
    }
}
