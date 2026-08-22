package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import com.linecorp.intellij.plugins.armeria.test.assertRoute
import com.linecorp.intellij.plugins.armeria.test.singleRoute
import kotlin.test.assertEquals

class ArmeriaKotlinExtendedRegistrationCollectorBasicTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerKotlinExtendedRegistrationCollectorStubs()
    }

    fun testCollectKotlinFileServiceRegistration() {
        configureFixture("extendedRegistration/kotlin/basic/fileService/Main.kt")
        collectRoutes().also { it.singleRoute() }.assertRoute(RouteMatch.FILE_SERVICE, path = "/files/")
    }

    fun testCollectKotlinFluentRouteRegistration() {
        configureFixture("extendedRegistration/kotlin/basic/fluentRoute/Main.kt")
        collectRoutes().also { it.singleRoute() }.assertRoute(
            RouteMatch.ROUTE_FLUENT,
            path = "/api/items",
            httpMethod = "POST",
        )
    }

    fun testCollectKotlinDecoratorUnderRegistration() {
        configureFixture("extendedRegistration/kotlin/basic/decoratorUnder/Main.kt")
        collectRoutes().also { it.singleRoute() }.assertRoute(RouteMatch.DECORATOR_UNDER, path = "/public")
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

    fun testCollectKotlinHealthCheckRegistration() {
        configureFixture("extendedRegistration/kotlin/basic/healthCheck/Main.kt")
        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.HEALTH_CHECK, path = "/internal/healthcheck")
            .also { route ->
                assertEquals(RouteProtocol.HEALTH_CHECK.presentableName(), route.protocol)
                assertEquals("GET", route.httpMethod)
            }
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
