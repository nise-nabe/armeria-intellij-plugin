package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import com.linecorp.intellij.plugins.armeria.test.assertRoute
import com.linecorp.intellij.plugins.armeria.test.singleRoute
import kotlin.test.assertEquals

class ArmeriaExtendedRegistrationCollectorBasicTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerExtendedRegistrationCollectorStubs()
    }

    fun testCollectFileServiceRegistration() {
        configureFixture("extendedRegistration/basic/fileService/Main.java")
        collectRoutes().also { it.singleRoute() }.assertRoute(RouteMatch.FILE_SERVICE, path = "/files/")
    }

    fun testCollectFileServiceWithJavaConstantPath() {
        configureFixture("extendedRegistration/basic/fileServiceWithConstant/Main.java")
        collectRoutes().also { it.singleRoute() }.assertRoute(RouteMatch.FILE_SERVICE, path = "/files/")
    }

    fun testCollectHealthCheckRegistration() {
        configureFixture("extendedRegistration/basic/healthCheck/Main.java")
        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.HEALTH_CHECK, path = "/internal/healthcheck")
            .also { route ->
                assertEquals(RouteProtocol.HEALTH_CHECK.presentableName(), route.protocol)
                assertEquals("GET", route.httpMethod)
            }
    }

    fun testCollectFluentRouteRegistration() {
        configureFixture("extendedRegistration/basic/fluentRoute/Main.java")
        collectRoutes().also { it.singleRoute() }.assertRoute(
            RouteMatch.ROUTE_FLUENT,
            path = "/api/items",
            httpMethod = "POST",
        )
    }

    fun testCollectDecoratorUnderRegistration() {
        configureFixture("extendedRegistration/basic/decoratorUnder/Main.java")
        collectRoutes().also { it.singleRoute() }.assertRoute(RouteMatch.DECORATOR_UNDER, path = "/public")
    }

    fun testCollectPathAnnotationAndPathType() {
        configureFixture("extendedRegistration/basic/pathAnnotation/HelloService.java")
        collectRoutes().also { it.singleRoute() }.assertRoute(path = "/hello", pathType = PathType.PREFIX)
    }

    fun testCollectRegexPathAnnotationTrimsWhitespace() {
        configureFixture("extendedRegistration/basic/regexPathAnnotation/HelloService.java")
        collectRoutes().also { it.singleRoute() }.assertRoute(path = "/foo", pathType = PathType.REGEX)
    }

    fun testCollectGlobPathAnnotationNormalizesLeadingSlash() {
        configureFixture("extendedRegistration/basic/globPathAnnotation/HelloService.java")
        collectRoutes().also { it.singleRoute() }.assertRoute(path = "/foo/**", pathType = PathType.GLOB)
    }

    fun testCollectFluentRoutePathPrefix() {
        configureFixture("extendedRegistration/basic/fluentRoutePathPrefix/Main.java")
        collectRoutes().also { it.singleRoute() }.assertRoute(
            RouteMatch.ROUTE_FLUENT,
            path = "/api/items",
            httpMethod = "GET",
            pathType = PathType.EXACT,
        )
    }
}
