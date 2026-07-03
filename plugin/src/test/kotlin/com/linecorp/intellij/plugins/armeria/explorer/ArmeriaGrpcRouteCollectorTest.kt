package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaGrpcRouteCollectorTest : LightJavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    registerArmeriaStubs()
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

    val routes = ArmeriaRouteCollector.collect(project)
    val protoRoutes = routes.filter { it.path.startsWith("/com.example.Greeter/") }.sortedBy { it.path }

    assertEquals(2, protoRoutes.size)
    assertEquals("/com.example.Greeter/SayGoodbye", protoRoutes[0].path)
    assertEquals("com.example.Greeter.SayGoodbye", protoRoutes[0].target)
    assertEquals(RouteMatch.NON_HTTP, protoRoutes[0].routeMatch)
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

    val routes = ArmeriaRouteCollector.collect(project)
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

    val routes = ArmeriaRouteCollector.collect(project)
    val protoRoute = routes.firstOrNull { it.path == "/com.example.Greeter/SayHello" }

    assertNotNull(protoRoute)
    assertEquals("com.example.Greeter.SayHello", protoRoute!!.target)
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

    val routes = ArmeriaRouteCollector.collect(project)

    assertNotNull(routes.firstOrNull { it.path == "/grpc" })
    assertNotNull(routes.firstOrNull { it.path == "/com.example.Greeter/SayHello" })
  }

  private fun registerArmeriaStubs() {
    myFixture.addClass(
      """
      package com.linecorp.armeria.server;

      public final class Server {
          public static ServerBuilder builder() {
              return new ServerBuilder();
          }
      }
      """.trimIndent(),
    )
    myFixture.addClass(
      """
      package com.linecorp.armeria.server;

      public final class ServerBuilder {
          public ServerBuilder service(String path, Object service) {
              return this;
          }

          public com.linecorp.armeria.server.Server build() {
              return null;
          }
      }
      """.trimIndent(),
    )
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
