package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaGrpcRouteCollectorGateTest : LightJavaCodeInsightFixtureTestCase() {
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
            ArmeriaRouteCollector.collect(project, includeProtoRoutes = true)
                .none { it.path == "/com.example.Greeter/SayHello" },
        )
    }
}
