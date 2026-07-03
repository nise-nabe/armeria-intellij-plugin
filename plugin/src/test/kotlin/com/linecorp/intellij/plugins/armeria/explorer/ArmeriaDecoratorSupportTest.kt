package com.linecorp.intellij.plugins.armeria.explorer

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
}
