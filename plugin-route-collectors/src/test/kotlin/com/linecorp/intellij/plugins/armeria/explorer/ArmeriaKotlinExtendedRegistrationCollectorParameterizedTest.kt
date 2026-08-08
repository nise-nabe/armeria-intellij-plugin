package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import com.linecorp.intellij.plugins.armeria.test.assertRoute
import com.linecorp.intellij.plugins.armeria.test.singleRoute
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ArmeriaKotlinExtendedRegistrationCollectorParameterizedTest : ArmeriaFixtureTestBase5() {
    override fun onFixtureSetUp() {
        registerKotlinExtendedRegistrationCollectorStubs()
    }

    @ParameterizedTest
    @CsvSource(
        "extendedRegistration/kotlin/basic/fileService/Main.kt, FILE_SERVICE, /files/",
        "extendedRegistration/kotlin/basic/healthCheck/Main.kt, HEALTH_CHECK, /internal/healthcheck",
        "extendedRegistration/kotlin/basic/decoratorUnder/Main.kt, DECORATOR_UNDER, /public",
    )
    fun collectBasicKotlinRegistrations(
        fixture: String,
        routeMatch: String,
        path: String,
    ) {
        configureFixture(fixture)
        collectRoutes().also { it.singleRoute() }.assertRoute(RouteMatch.valueOf(routeMatch), path = path)
    }
}
