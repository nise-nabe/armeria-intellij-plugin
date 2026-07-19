package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.decorator.ArmeriaDecoratorSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import junit.framework.TestCase

class ArmeriaDecoratorSupportTest : TestCase() {
    fun testLabelDecoratorKnownService() {
        assertEquals("Logging", ArmeriaDecoratorSupport.labelDecorator("LoggingService.class"))
        assertEquals("CORS", ArmeriaDecoratorSupport.labelDecorator("com.linecorp.armeria.server.cors.CorsService"))
    }

    fun testLabelDecoratorUnknownService() {
        assertEquals("CustomDecorator", ArmeriaDecoratorSupport.labelDecorator("CustomDecorator.class"))
    }

    fun testLabelDecoratorPathScopedSecondArgument() {
        assertEquals("Logging", ArmeriaDecoratorSupport.labelDecorator("LoggingService"))
    }

    fun testLabelDecoratorKotlinClassReference() {
        assertEquals("Logging", ArmeriaDecoratorSupport.labelDecorator("LoggingService::class"))
    }

    fun testDecoratorPathPatternExactMatch() {
        assertTrue(ArmeriaRouteSupport.decoratorPathPatternAppliesToRoute("/api", "/api"))
        assertFalse(ArmeriaRouteSupport.decoratorPathPatternAppliesToRoute("/api", "/api/v1"))
        assertFalse(ArmeriaRouteSupport.decoratorPathPatternAppliesToRoute("/api", "/other"))
    }

    fun testDecoratorPathPatternDoubleStarMatch() {
        assertTrue(ArmeriaRouteSupport.decoratorPathPatternAppliesToRoute("/api/**", "/api"))
        assertTrue(ArmeriaRouteSupport.decoratorPathPatternAppliesToRoute("/api/**", "/api/v1"))
        assertTrue(ArmeriaRouteSupport.decoratorPathPatternAppliesToRoute("/api/**", "/api/v1/users"))
        assertFalse(ArmeriaRouteSupport.decoratorPathPatternAppliesToRoute("/api/**", "/other"))
    }

    fun testDecoratorPathPatternSingleStarMatch() {
        assertFalse(ArmeriaRouteSupport.decoratorPathPatternAppliesToRoute("/api/*", "/api"))
        assertTrue(ArmeriaRouteSupport.decoratorPathPatternAppliesToRoute("/api/*", "/api/v1"))
        assertFalse(ArmeriaRouteSupport.decoratorPathPatternAppliesToRoute("/api/*", "/api/v1/users"))
        assertFalse(ArmeriaRouteSupport.decoratorPathPatternAppliesToRoute("/api/*", "/other"))
    }
}
