package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.util.registry.Registry
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaProtocolRouteContributor
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaProtoRouteRegistryGateTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcService {
                public static GrpcServiceBuilder builder(Object bindableService) {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }

    fun testProtoRoutesRespectRegistryKillSwitchWithCache() {
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

        val registryKey = Registry.get("armeria.grpc.proto.routes.enabled")
        val original = registryKey.asBoolean()
        try {
            registryKey.setValue(true)
            val withProto =
                ArmeriaRouteCollector.collect(
                    project,
                    includeProtoRoutes = true,
                    contributors = listOf(ArmeriaProtocolRouteContributor),
                )
            assertTrue(withProto.any { it.path == "/com.example.Greeter/SayHello" })

            registryKey.setValue(false)
            val disabled =
                ArmeriaRouteCollector.collect(
                    project,
                    includeProtoRoutes = true,
                    contributors = listOf(ArmeriaProtocolRouteContributor),
                )
            assertTrue(disabled.none { it.path == "/com.example.Greeter/SayHello" })

            registryKey.setValue(true)
            val reenabled =
                ArmeriaRouteCollector.collect(
                    project,
                    includeProtoRoutes = true,
                    contributors = listOf(ArmeriaProtocolRouteContributor),
                )
            assertTrue(reenabled.any { it.path == "/com.example.Greeter/SayHello" })
            assertEquals(0, ArmeriaRouteCollectionMetrics.lastSnapshot!!.filesScanned)
        } finally {
            registryKey.setValue(original)
        }
    }

    fun testProtoEditWhileRegistryDisabledIsVisibleAfterReenable() {
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

        val registryKey = Registry.get("armeria.grpc.proto.routes.enabled")
        val original = registryKey.asBoolean()
        try {
            registryKey.setValue(true)
            ArmeriaRouteCollector.collect(
                project,
                includeProtoRoutes = true,
                contributors = listOf(ArmeriaProtocolRouteContributor),
            )

            registryKey.setValue(false)
            myFixture.configureByText(
                "greeter.proto",
                """
                syntax = "proto3";
                package com.example;

                service Greeter {
                  rpc SayHello(HelloRequest) returns (HelloResponse);
                  rpc SayGoodbye(GoodbyeRequest) returns (GoodbyeResponse);
                }
                """.trimIndent(),
            )

            registryKey.setValue(true)
            val reenabled =
                ArmeriaRouteCollector.collect(
                    project,
                    includeProtoRoutes = true,
                    contributors = listOf(ArmeriaProtocolRouteContributor),
                )
            assertEquals(
                listOf("/com.example.Greeter/SayGoodbye", "/com.example.Greeter/SayHello"),
                reenabled.map { it.path },
            )
        } finally {
            registryKey.setValue(original)
        }
    }
}
