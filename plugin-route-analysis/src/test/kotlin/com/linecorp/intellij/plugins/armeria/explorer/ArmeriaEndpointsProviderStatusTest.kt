package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.microservices.endpoints.EndpointsProvider
import com.linecorp.intellij.plugins.armeria.explorer.endpoints.ArmeriaEndpointsProvider
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaEndpointsProviderStatusTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() = Unit

    @Test
    fun getStatus_isUnavailableWithoutArmeriaOnClasspath() {
        assertEquals(
            EndpointsProvider.Status.UNAVAILABLE,
            ArmeriaEndpointsProvider().getStatus(project),
        )
    }
}
