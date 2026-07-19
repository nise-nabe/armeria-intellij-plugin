package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaGrpcRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaProtocolRouteContributor
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteContributorRegistry
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaGrpcRouteCollectorGateTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        // Classpath gate test does not need Armeria stubs.
    }

    override fun setUp() {
        super.setUp()
        RouteContributorRegistry.clearForTests()
        RouteContributorRegistry.register(ArmeriaProtocolRouteContributor)
    }

    override fun tearDown() {
        try {
            RouteContributorRegistry.clearForTests()
        } finally {
            super.tearDown()
        }
    }

    fun testProtoRoutesSkippedWithoutGrpcOnClasspath() {
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse);
            }
            """.trimIndent(),
        )

        assertFalse(ArmeriaGrpcRouteCollector.isGrpcOnClasspath(project, GlobalSearchScope.projectScope(project)))
        assertTrue(
            ArmeriaRouteCollector
                .collect(project, includeProtoRoutes = true)
                .none { it.path == "/com.example.Greeter/SayHello" },
        )
    }
}
