package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import com.linecorp.intellij.plugins.armeria.test.assertRoute
import com.linecorp.intellij.plugins.armeria.test.singleRoute

class ArmeriaKotlinExtendedRegistrationCollectorBasicTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerKotlinExtendedRegistrationCollectorStubs()
    }

    fun testCollectKotlinFluentRouteRegistration() {
        configureFixture("extendedRegistration/kotlin/basic/fluentRoute/Main.kt")
        collectRoutes().also { it.singleRoute() }.assertRoute(
            RouteMatch.ROUTE_FLUENT,
            path = "/api/items",
            httpMethod = "POST",
        )
    }

    fun testCollectKotlinWithRouteRegistration() {
        configureFixture("extendedRegistration/kotlin/basic/withRoute/Main.kt")
        collectRoutes().also { it.singleRoute() }.assertRoute(
            RouteMatch.ROUTE_FLUENT,
            path = "/wrapped",
            httpMethod = "POST",
        )
    }

    fun testCollectKotlinWithRouteDoesNotBurnDedupKeyOnInvalidLambda() {
        configureFixture("extendedRegistration/kotlin/basic/withRouteDedup/Main.kt")
        collectRoutes().also { it.singleRoute() }.assertRoute(
            RouteMatch.ROUTE_FLUENT,
            path = "/wrapped",
            httpMethod = "POST",
        )
    }

    fun testCollectKotlinFluentRoutePathPrefix() {
        configureFixture("extendedRegistration/kotlin/basic/fluentRoutePathPrefix/Main.kt")
        collectRoutes().also { it.singleRoute() }.assertRoute(
            RouteMatch.ROUTE_FLUENT,
            path = "/api/items",
            httpMethod = "GET",
            pathType = PathType.EXACT,
        )
    }

    fun testCollectKotlinFileServiceFromConstValPath() {
        configureFixture("extendedRegistration/kotlin/basic/fileServiceWithConstVal/Main.kt")
        collectRoutes().also { it.singleRoute() }.assertRoute(RouteMatch.FILE_SERVICE, path = "/files/")
    }
}
