package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaListenPortSupport
import com.linecorp.intellij.plugins.armeria.message
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArmeriaListenPortSupportTest {
    @Test
    fun protocolLabel_portWithoutExtraArgsDefaultsToHttp() {
        assertEquals(
            message("route.explorer.listenPort.http"),
            ArmeriaListenPortSupport.protocolLabel("port", extraArgsPresent = false, resolvedProtocolNames = emptyList()),
        )
    }

    @Test
    fun protocolLabel_unresolvedExtraArgsOmitsBinding() {
        assertNull(
            ArmeriaListenPortSupport.protocolLabel("port", extraArgsPresent = true, resolvedProtocolNames = emptyList()),
        )
        assertNull(
            ArmeriaListenPortSupport.protocolLabel(
                "port",
                extraArgsPresent = true,
                resolvedProtocolNames = listOf("UNKNOWN"),
            ),
        )
    }

    @Test
    fun protocolLabel_collapsesH1ToHttpsAndUnifiesHttpHttps() {
        assertEquals(
            message("route.explorer.listenPort.https"),
            ArmeriaListenPortSupport.protocolLabel(
                "port",
                extraArgsPresent = true,
                resolvedProtocolNames = listOf("H1"),
            ),
        )
        assertEquals(
            "${message("route.explorer.listenPort.http")}+${message("route.explorer.listenPort.https")}",
            ArmeriaListenPortSupport.protocolLabel(
                "port",
                extraArgsPresent = true,
                resolvedProtocolNames = listOf("HTTP", "HTTPS"),
            ),
        )
    }

    @Test
    fun displayProtocols_joinsWithPlusAndDefaultsEmptyToHttp() {
        assertEquals("HTTP+HTTPS", ArmeriaListenPortSupport.displayProtocols(listOf("HTTP", "HTTPS")))
        assertEquals("HTTP", ArmeriaListenPortSupport.displayProtocols(emptyList()))
        assertEquals("HTTPS", ArmeriaListenPortSupport.displayProtocols(listOf("H1")))
    }

    @Test
    fun parseIntLiteral_readsUnderscoresHexAndBinary() {
        assertEquals(1024, ArmeriaListenPortSupport.parseIntLiteral("1_024"))
        assertEquals(8080, ArmeriaListenPortSupport.parseIntLiteral("0x1F90"))
        assertEquals(10, ArmeriaListenPortSupport.parseIntLiteral("0b1010"))
        assertNull(ArmeriaListenPortSupport.parseIntLiteral("unknown"))
    }
}
