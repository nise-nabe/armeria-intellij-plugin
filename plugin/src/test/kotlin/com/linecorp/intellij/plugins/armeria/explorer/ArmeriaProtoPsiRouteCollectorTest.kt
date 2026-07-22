package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.protobuf.lang.psi.PbFile
import com.intellij.protobuf.lang.psi.PbServiceMethod
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteAnalysisCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaProtoRouteCollector

class ArmeriaProtoPsiRouteCollectorTest : LightJavaCodeInsightFixtureTestCase() {
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

        val routes = ArmeriaRouteAnalysisCollector.collect(project, includeProtoRoutes = true)

        assertEquals(listOf("/com.example.Greeter/SayHello"), routes.map { it.path })
        assertTrue(routes.single().pointer.element is PbServiceMethod)
    }

    private fun registerProtoRouteCollectorExtensionPoint() {
        try {
            ArmeriaProtoRouteCollector.EP.extensionList
            return
        } catch (_: IllegalArgumentException) {
            // Extension point is not registered in this test container yet.
        }
        val extensionArea = ApplicationManager.getApplication().extensionArea
        extensionArea.registerExtensionPoint(
            "com.linecorp.intellij.armeria.protoRouteCollector",
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
