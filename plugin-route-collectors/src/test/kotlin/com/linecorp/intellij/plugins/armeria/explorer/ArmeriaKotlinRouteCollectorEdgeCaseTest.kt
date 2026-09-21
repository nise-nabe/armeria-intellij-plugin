package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertNotNull as kotlinAssertNotNull

class ArmeriaKotlinRouteCollectorEdgeCaseTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerKotlinRouteCollectorStubs()
    }

    fun testNoFalsePositiveForUnrelatedServiceCall() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .build()
                Client.service("/oops", Any())
            }

            object Client {
                fun service(path: String, handler: Any) {}
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
    }

    fun testNoFalsePositiveForUnqualifiedServiceCallInAlsoBlock() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().also {
                    service("/oops", Any())
                }.build()
            }

            private fun service(path: String, handler: Any) {}
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
    }

    fun testNoFalsePositiveForUnqualifiedServiceCallInLetBlock() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().let {
                    service("/oops", Any())
                }.build()
            }

            private fun service(path: String, handler: Any) {}
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
    }

    fun testNoFalsePositiveForMisleadingServerBuilderTypeName() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().build()
                val helper = MyServerBuilderHelper()
                helper.service("/oops", Any())
            }

            class MyServerBuilderHelper {
                fun service(path: String, handler: Any) {}
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
    }

    fun testNoFalsePositiveForMyServerBuilderCall() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().build()
                MyServer.builder()
                    .service("/oops", Any())
                    .build()
            }

            object MyServer {
                fun builder(): FakeBuilder = FakeBuilder()
            }

            class FakeBuilder {
                fun service(path: String, handler: Any): FakeBuilder = this
                fun build() {}
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
    }

    fun testNoFalsePositiveForServerBuilderNamedNonArmeriaVariable() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().build()
                val serverBuilderHelper = FakeBuilder()
                serverBuilderHelper.service("/oops", Any())
            }

            class FakeBuilder {
                fun service(path: String, handler: Any) {}
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
    }

    fun testCollectUnresolvedParenthesizedNewExpressionTarget() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service("/api", (MissingService()))
                    .build()
            }
            """.trimIndent(),
        )

        val serviceRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.path == "/api" }
        kotlinAssertNotNull(serviceRoute)
        assertTrue(serviceRoute.targetUnresolved)
    }

    fun testCollectUnresolvedFactoryMethodTarget() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service("/api", createMissingService())
                    .build()
            }

            private fun createMissingService(): Any = MissingService()
            """.trimIndent(),
        )

        val serviceRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.path == "/api" }
        kotlinAssertNotNull(serviceRoute)
        assertTrue(serviceRoute.targetUnresolved)
    }

    fun testCollectUnresolvedGrpcServiceBuilderTarget() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.grpc.GrpcService

            fun main() {
                Server.builder()
                    .service("/grpc", GrpcService.builder(MissingService()).build())
                    .build()
            }
            """.trimIndent(),
        )

        val serviceRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.path == "/grpc" }
        kotlinAssertNotNull(serviceRoute)
        assertTrue(serviceRoute.targetUnresolved)
    }

    fun testNoFalsePositiveForNonArmeriaQualifiedServerBuilder() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().build()
                com.foo.Server.builder()
                    .service("/oops", Any())
                    .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.foo;

            public final class Server {
                public static FakeBuilder builder() {
                    return null;
                }
            }

            class FakeBuilder {
                public FakeBuilder service(String path, Object handler) {
                    return this;
                }

                public void build() {}
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
    }

    fun testSkipsServiceRegistrationWithNonConstantPath() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun computePath(): String = "/dynamic"

            fun main() {
                val path = computePath()
                Server.builder()
                    .service(path, Any())
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.routeMatch == RouteMatch.SERVICE })
    }

    fun testSkipsServiceRegistrationWithInterpolatedPath() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                val version = "v1"
                Server.builder()
                    .service("/api/${'$'}version", Any())
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.routeMatch == RouteMatch.SERVICE })
    }

    fun testCollectsServiceRegistrationWithConstantPropertyPath() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            const val PATH = "/const"

            fun main() {
                Server.builder()
                    .service(PATH, Any())
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        kotlinAssertNotNull(routes.firstOrNull { it.path == "/const" && it.routeMatch == RouteMatch.SERVICE })
    }
}
