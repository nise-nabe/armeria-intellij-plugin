package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaGrpcRouteCollectorTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerResolvableArmeriaServerStubs()
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

    fun testCollectGrpcRoutesFromProtoWithHttpsInHttpOption() {
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse) {
                option (google.api.http) = {
                  post: "https://api.example.com/v1/hello"
                  body: "*"
                };
              }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project, includeProtoRoutes = true)

        assertEquals(listOf("/com.example.Greeter/SayHello"), routes.map { it.path })
    }

    fun testCollectGrpcRoutesFromProto() {
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

        val routes = ArmeriaRouteCollector.collect(project, includeProtoRoutes = true)
        val protoRoutes = routes.filter { it.path.startsWith("/com.example.Greeter/") }.sortedBy { it.path }

        assertEquals(2, protoRoutes.size)
        assertEquals("/com.example.Greeter/SayGoodbye", protoRoutes[0].path)
        assertEquals("com.example.Greeter.SayGoodbye", protoRoutes[0].target)
        assertEquals(RouteMatch.NON_HTTP, protoRoutes[0].routeMatch)
        assertEquals("", protoRoutes[0].httpMethod)
        assertEquals("/com.example.Greeter/SayHello", protoRoutes[1].path)
        assertEquals("com.example.Greeter.SayHello", protoRoutes[1].target)
    }

    fun testCollectGrpcRoutesFromProtoWithoutPackage() {
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";

            service Greeter {
              rpc Ping(PingRequest) returns (PingResponse);
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project, includeProtoRoutes = true)
        val protoRoute = routes.single { it.path == "/Greeter/Ping" }

        assertEquals("Greeter.Ping", protoRoute.target)
        assertEquals(RouteMatch.NON_HTTP, protoRoute.routeMatch)
    }

    fun testCollectGrpcRoutesFromProtoWithNestedHttpOptions() {
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse) {
                option (google.api.http) = {
                  post: "/v1/hello"
                  body: "*"
                };
              }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project, includeProtoRoutes = true)
        val protoRoute = routes.firstOrNull { it.path == "/com.example.Greeter/SayHello" }

        assertNotNull(protoRoute)
        assertEquals("com.example.Greeter.SayHello", protoRoute!!.target)
    }

    fun testCollectGrpcRoutesFromMultipleServicesInOneProto() {
        myFixture.configureByText(
            "services.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse);
            }

            service Admin {
              rpc Ping(PingRequest) returns (PingResponse);
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project, includeProtoRoutes = true).map { it.path }.sorted()

        assertEquals(
            listOf("/com.example.Admin/Ping", "/com.example.Greeter/SayHello"),
            routes,
        )
    }

    fun testInlineBlockCommentInPackageDoesNotBreakPackageName() {
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com/*c*/.example;

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse);
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project, includeProtoRoutes = true)

        assertEquals(listOf("/com.example.Greeter/SayHello"), routes.map { it.path })
    }

    fun testInlineBlockCommentBetweenTokensDoesNotMergeRpcName() {
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              rpc/*inline*/SayHello(HelloRequest) returns (HelloResponse);
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project, includeProtoRoutes = true)

        assertEquals(listOf("/com.example.Greeter/SayHello"), routes.map { it.path })
    }

    fun testCommentedRpcIsIgnored() {
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

        val routes = ArmeriaRouteCollector.collect(project, includeProtoRoutes = true)

        assertEquals(listOf("/com.example.Greeter/SayHello"), routes.map { it.path })
    }

    fun testUnbalancedBraceDoesNotSkipLaterServices() {
        val file = myFixture.configureByText(
            "broken.proto",
            """
            syntax = "proto3";
            package com.example;

            service Broken {
              rpc MissingBrace(HelloRequest) returns (HelloResponse) {
            }

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse);
            }
            """.trimIndent(),
        )
        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaGrpcRouteCollector.collectFromProtoText(file.text, file, routes)

        assertEquals(listOf("/com.example.Greeter/SayHello"), routes.map { it.path })
    }

    fun testDuplicateProtoRoutesAreDeduplicated() {
        val file = myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse);
            }
            """.trimIndent(),
        )
        val routes = mutableListOf<ArmeriaRoute>()
        val seenProtoRoutes = mutableSetOf<String>()
        val element = file
        ArmeriaGrpcRouteCollector.collectFromProtoText(file.text, element, routes, seenProtoRoutes)
        ArmeriaGrpcRouteCollector.collectFromProtoText(file.text, element, routes, seenProtoRoutes)

        assertEquals(1, routes.size)
        assertEquals("/com.example.Greeter/SayHello", routes.single().path)
    }

    fun testProtoRoutesCoexistWithGrpcServiceRegistration() {
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
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.grpc.GrpcService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/grpc", GrpcService.builder(new HelloGrpcService()).build())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class HelloGrpcService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project, includeProtoRoutes = true)

        assertNotNull(routes.firstOrNull { it.path == "/grpc" })
        assertNotNull(routes.firstOrNull { it.path == "/com.example.Greeter/SayHello" })
    }

    fun testProtoRouteMergeIsCachedAcrossCollectCalls() {
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

        val first = ArmeriaRouteCollector.collect(project, includeProtoRoutes = true)
        val firstScan = ArmeriaRouteCollectionMetrics.lastSnapshot!!.filesScanned
        assertEquals(listOf("/com.example.Greeter/SayHello"), first.map { it.path })
        assertTrue(firstScan > 0)

        val second = ArmeriaRouteCollector.collect(project, includeProtoRoutes = true)
        val secondScan = ArmeriaRouteCollectionMetrics.lastSnapshot!!.filesScanned
        assertEquals(first.map { it.path }, second.map { it.path })
        assertEquals(0, secondScan)

        val withoutProto = ArmeriaRouteCollector.collect(project, includeProtoRoutes = false)
        assertTrue(withoutProto.none { it.path == "/com.example.Greeter/SayHello" })
    }
}
