package com.linecorp.intellij.plugins.armeria.client

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaClientEndpointsSupportTest {
    @Test
    fun isVisibleHttpClientUri_allowsHttpLikeSchemes() {
        assertTrue(ArmeriaClientEndpointsSupport.isVisibleHttpClientUri("https://api.example.com/v1"))
        assertTrue(ArmeriaClientEndpointsSupport.isVisibleHttpClientUri("http://example.com"))
        assertTrue(ArmeriaClientEndpointsSupport.isVisibleHttpClientUri("h2c://example.com"))
        assertTrue(ArmeriaClientEndpointsSupport.isVisibleHttpClientUri("none+h2c://example.com"))
        assertTrue(ArmeriaClientEndpointsSupport.isVisibleHttpClientUri("/hello"))
        assertTrue(ArmeriaClientEndpointsSupport.isVisibleHttpClientUri("example.com"))
    }

    @Test
    fun isVisibleHttpClientUri_rejectsDiscoveryAndBlank() {
        assertFalse(ArmeriaClientEndpointsSupport.isVisibleHttpClientUri("zk://zk.example.com/armeria"))
        assertFalse(ArmeriaClientEndpointsSupport.isVisibleHttpClientUri("ZooKeeper (zk://zk.example.com/armeria)"))
        assertFalse(ArmeriaClientEndpointsSupport.isVisibleHttpClientUri(""))
        assertFalse(ArmeriaClientEndpointsSupport.isVisibleHttpClientUri("   "))
    }

    @Test
    fun authorityText_keepsExplicitPortAndIpv6Brackets() {
        assertEquals("localhost:8080", ArmeriaClientEndpointsSupport.authorityText("http://localhost:8080"))
        assertEquals("api.example.com", ArmeriaClientEndpointsSupport.authorityText("https://api.example.com/v1"))
        assertEquals("example.com:8443", ArmeriaClientEndpointsSupport.authorityText("https://example.com:8443/hello"))
        assertEquals("[::1]:8443", ArmeriaClientEndpointsSupport.authorityText("https://[::1]:8443/"))
        assertEquals("example.com", ArmeriaClientEndpointsSupport.authorityText("example.com"))
    }
}
