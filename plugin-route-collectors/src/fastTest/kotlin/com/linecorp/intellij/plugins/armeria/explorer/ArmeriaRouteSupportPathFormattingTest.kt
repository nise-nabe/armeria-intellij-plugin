package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import org.junit.Assert.assertEquals
import org.junit.Test

class ArmeriaRouteSupportPathFormattingTest {
    @Test
    fun formatAnnotatedHandlerPath_combinesExactPaths() {
        assertEquals("/api/hello", ArmeriaRouteSupport.formatAnnotatedHandlerPath("/api", "/hello"))
    }

    @Test
    fun formatAnnotatedHandlerPath_preservesPrefixType() {
        assertEquals("prefix:/api/hello", ArmeriaRouteSupport.formatAnnotatedHandlerPath("/api", "prefix:/hello"))
    }

    @Test
    fun formatAnnotatedHandlerPath_preservesRegexTypeWithoutNormalizingPatterns() {
        assertEquals(
            "regex:^/api/hello$",
            ArmeriaRouteSupport.formatAnnotatedHandlerPath("regex:^/api", "regex:^/hello$"),
        )
    }

    @Test
    fun formatAnnotatedHandlerPath_preservesRegexClassPrefixWithExactHandler() {
        assertEquals(
            "regex:^/api/hello",
            ArmeriaRouteSupport.formatAnnotatedHandlerPath("regex:^/api", "/hello"),
        )
    }

    @Test
    fun formatAnnotatedHandlerPath_usesRegexDisplayTypeWhenRegexPrefixCombinesWithPrefixHandler() {
        assertEquals(
            "regex:^/api/hello",
            ArmeriaRouteSupport.formatAnnotatedHandlerPath("regex:^/api", "prefix:/hello"),
        )
    }

    @Test
    fun formatAnnotatedHandlerPath_preservesRegexStartAnchorWhenExactPrefixCombinesWithRegexHandler() {
        assertEquals(
            "regex:^/api/hello$",
            ArmeriaRouteSupport.formatAnnotatedHandlerPath("/api", "regex:^/hello$"),
        )
    }
}
