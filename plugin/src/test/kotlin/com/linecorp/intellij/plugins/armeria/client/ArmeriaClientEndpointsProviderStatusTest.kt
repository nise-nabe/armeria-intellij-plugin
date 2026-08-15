package com.linecorp.intellij.plugins.armeria.client

import com.intellij.microservices.endpoints.EndpointsProvider
import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import kotlin.test.assertEquals

class ArmeriaClientEndpointsProviderStatusTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    fun testGetStatus_isUnavailableWithoutArmeriaClientOnClasspath() {
        assertEquals(
            EndpointsProvider.Status.UNAVAILABLE,
            ArmeriaClientEndpointsProvider().getStatus(project),
        )
    }
}
