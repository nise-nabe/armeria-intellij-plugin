package com.linecorp.intellij.plugins.armeria.client

import org.junit.Test
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
}
