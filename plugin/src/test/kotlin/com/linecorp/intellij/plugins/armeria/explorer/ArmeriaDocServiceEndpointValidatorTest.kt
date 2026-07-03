package com.linecorp.intellij.plugins.armeria.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArmeriaDocServiceEndpointValidatorTest {
    @Test
    fun validateHost_acceptsCommonValues() {
        assertNull(ArmeriaDocServiceEndpointValidator.validateHost("localhost"))
        assertNull(ArmeriaDocServiceEndpointValidator.validateHost("127.0.0.1"))
        assertNull(ArmeriaDocServiceEndpointValidator.validateHost("[::1]"))
    }

    @Test
    fun validateHost_rejectsUnsafeValues() {
        assertEquals("invalid", ArmeriaDocServiceEndpointValidator.validateHost("evil.com@127.0.0.1"))
        assertEquals("empty", ArmeriaDocServiceEndpointValidator.validateHost("   "))
    }

    @Test
    fun validatePort_acceptsValidRange() {
        assertEquals(8080, ArmeriaDocServiceEndpointValidator.validatePort("8080"))
        assertNull(ArmeriaDocServiceEndpointValidator.validatePort("abc"))
        assertNull(ArmeriaDocServiceEndpointValidator.validatePort("70000"))
    }

    @Test
    fun buildSpecificationUrl_normalizesMountPath() {
        assertEquals(
            "http://localhost:8080/docs/specification.json",
            ArmeriaDocServiceEndpointValidator.buildSpecificationUrl("localhost", 8080, false, "/docs"),
        )
        assertEquals(
            "https://localhost:8443/internal/docs/specification.json",
            ArmeriaDocServiceEndpointValidator.buildSpecificationUrl("localhost", 8443, true, "internal/docs/"),
        )
    }
}
