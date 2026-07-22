package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.protobuf.lang.psi.PbFile
import com.intellij.protobuf.lang.psi.PbServiceMethod
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaProtoRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaProtocolRouteContributor
import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase

class ArmeriaProtoPsiRouteCollectorTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerArmeriaStubs()
        registerProtoRouteCollectorExtensionPoint()
    }

    fun testCollectGrpcRoutesFromProtoPsi() {
        val file =
            myFixture.configureByText(
                "greeter.proto",
                """
                syntax = "proto3";
                package com.example;

                service Greeter {
                  rpc SayHello(HelloRequest) returns (HelloResponse);
                  rpc SayGoodbye(HelloRequest) returns (HelloResponse);
                }
                """.trimIndent(),
            )
        assertTrue("Expected Proto Editor PSI for .proto fixture", file is PbFile)

        val routes = mutableListOf<ArmeriaRoute>()
        assertTrue(ArmeriaProtoPsiRouteCollector().collectFromFile(file, routes, mutableSetOf()))

        assertEquals(
            listOf("/com.example.Greeter/SayGoodbye", "/com.example.Greeter/SayHello"),
            routes.map { it.path }.sorted(),
        )
        assertTrue(routes.all { it.routeMatch == RouteMatch.NON_HTTP })
    }

    fun testCollectGrpcRoutesFromProtoPsiWithoutPackage() {
        val file =
            myFixture.configureByText(
                "greeter.proto",
                """
                syntax = "proto3";

                service Greeter {
                  rpc Ping(PingRequest) returns (PingResponse);
                }
                """.trimIndent(),
            )
        assertTrue(file is PbFile)

        val routes = mutableListOf<ArmeriaRoute>()
        assertTrue(ArmeriaProtoPsiRouteCollector().collectFromFile(file, routes, mutableSetOf()))

        val route = routes.single()
        assertEquals("/Greeter/Ping", route.path)
        assertEquals("Greeter.Ping", route.target)
    }

    fun testCollectGrpcRoutesFromProtoPsiAnchorsRpcMethod() {
        val file =
            myFixture.configureByText(
                "greeter.proto",
                """
                syntax = "proto3";
                package com.example;

                service Greeter {
                  rpc SayHello(HelloRequest) returns (HelloResponse);
                }
                """.trimIndent(),
            ) as PbFile

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaProtoPsiRouteCollector().collectFromFile(file, routes, mutableSetOf())

        val route = routes.single()
        assertTrue(route.pointer.element is PbServiceMethod)
        assertEquals("SayHello", (route.pointer.element as PbServiceMethod).name)
    }

    fun testCommentedRpcIsIgnoredByProtoPsi() {
        val file =
            myFixture.configureByText(
                "greeter.proto",
                """
                syntax = "proto3";
                package com.example;

                service Greeter {
                  // rpc Deprecated(HelloRequest) returns (HelloResponse);
                  rpc SayHello(HelloRequest) returns (HelloResponse);
                }
                """.trimIndent(),
            )
        assertTrue(file is PbFile)

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaProtoPsiRouteCollector().collectFromFile(file, routes, mutableSetOf())

        assertEquals(listOf("/com.example.Greeter/SayHello"), routes.map { it.path })
    }

    fun testProtoPsiReturnsFalseWhenNoServicesToAllowTextFallback() {
        val file =
            myFixture.configureByText(
                "empty.proto",
                """
                syntax = "proto3";
                package com.example;
                """.trimIndent(),
            )
        assertTrue(file is PbFile)

        val routes = mutableListOf<ArmeriaRoute>()
        assertFalse(ArmeriaProtoPsiRouteCollector().collectFromFile(file, routes, mutableSetOf()))
        assertTrue(routes.isEmpty())
    }

    fun testEmptyServiceBodyIsHandledWithoutTextFallback() {
        val file =
            myFixture.configureByText(
                "empty-service.proto",
                """
                syntax = "proto3";
                package com.example;

                service Greeter {
                }
                """.trimIndent(),
            )
        assertTrue(file is PbFile)

        val routes = mutableListOf<ArmeriaRoute>()
        assertTrue(ArmeriaProtoPsiRouteCollector().collectFromFile(file, routes, mutableSetOf()))
        assertTrue(routes.isEmpty())
    }

    fun testCollectGrpcRoutesFromMultipleServicesInOneProto() {
        val file =
            myFixture.configureByText(
                "multi.proto",
                """
                syntax = "proto3";
                package com.example;

                service Greeter {
                  rpc SayHello(HelloRequest) returns (HelloResponse);
                }

                service Echo {
                  rpc Ping(PingRequest) returns (PingResponse);
                }
                """.trimIndent(),
            )
        assertTrue(file is PbFile)

        val routes = mutableListOf<ArmeriaRoute>()
        assertTrue(ArmeriaProtoPsiRouteCollector().collectFromFile(file, routes, mutableSetOf()))

        assertEquals(
            listOf("/com.example.Echo/Ping", "/com.example.Greeter/SayHello"),
            routes.map { it.path }.sorted(),
        )
    }

    fun testJavaPackageOptionDoesNotChangeGrpcPath() {
        val file =
            myFixture.configureByText(
                "greeter.proto",
                """
                syntax = "proto3";
                package com.example;
                option java_package = "com.example.java";

                service Greeter {
                  rpc SayHello(HelloRequest) returns (HelloResponse);
                }
                """.trimIndent(),
            )
        assertTrue(file is PbFile)

        val routes = mutableListOf<ArmeriaRoute>()
        assertTrue(ArmeriaProtoPsiRouteCollector().collectFromFile(file, routes, mutableSetOf()))

        val route = routes.single()
        assertEquals("/com.example.Greeter/SayHello", route.path)
        assertEquals("com.example.Greeter.SayHello", route.target)
    }

    fun testCollectFromProtoFilePrefersPsiCollectorOverTextParsing() {
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              // rpc Deprecated(HelloRequest) returns (HelloResponse);
              rpc SayHello(HelloRequest) returns (HelloResponse);
            }
            """.trimIndent(),
        )
        registerProtoRouteCollectorExtension()

        val routes =
            ArmeriaRouteCollector.collect(
                project,
                includeProtoRoutes = true,
                contributors = listOf(ArmeriaProtocolRouteContributor),
            )

        assertEquals(listOf("/com.example.Greeter/SayHello"), routes.map { it.path })
        assertTrue(routes.single().pointer.element is PbServiceMethod)
    }

    private fun registerProtoRouteCollectorExtensionPoint() {
        val area = ApplicationManager.getApplication().extensionArea
        if (area.hasExtensionPoint(ArmeriaProtoRouteCollector.EP.name)) {
            return
        }
        area.registerExtensionPoint(
            ArmeriaProtoRouteCollector.EP.name,
            ArmeriaProtoRouteCollector::class.java.name,
            ExtensionPoint.Kind.INTERFACE,
            true,
        )
    }

    private fun registerProtoRouteCollectorExtension() {
        ArmeriaProtoRouteCollector.EP.point.registerExtension(
            ArmeriaProtoPsiRouteCollector(),
            testRootDisposable,
        )
    }

    private fun registerArmeriaStubs() {
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
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcServiceBuilder {
                public com.linecorp.armeria.server.grpc.GrpcService build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }
}
