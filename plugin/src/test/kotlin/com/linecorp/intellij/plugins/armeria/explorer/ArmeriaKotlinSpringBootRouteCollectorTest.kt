package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaKotlinSpringBootRouteCollectorTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerArmeriaStubs()
        registerSpringBootStubs()
    }

    fun testCollectServiceRegistrationFromBeanConfigurator() {
        myFixture.configureByText(
            "ArmeriaConfig.kt",
            """
            package example

            import com.linecorp.armeria.spring.ArmeriaServerConfigurator
            import org.springframework.context.annotation.Bean
            import org.springframework.context.annotation.Configuration

            @Configuration
            class ArmeriaConfig {
                @Bean
                fun armeriaServerConfigurator(): ArmeriaServerConfigurator =
                    ArmeriaServerConfigurator { serverBuilder ->
                        serverBuilder.service("/api", HelloService())
                    }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class HelloService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val apiRoutes = routes.filter { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertEquals(1, apiRoutes.size)
        assertEquals("example.HelloService", apiRoutes.single().target)
        assertFalse(routes.any { it.path == "/" && it.target.contains("armeriaServerConfigurator") })
    }

    fun testDoesNotDuplicateIndexedServiceRegistration() {
        myFixture.configureByText(
            "ArmeriaConfig.kt",
            """
            package example

            import com.linecorp.armeria.server.ServerBuilder
            import com.linecorp.armeria.spring.ArmeriaServerConfigurator
            import org.springframework.context.annotation.Bean
            import org.springframework.context.annotation.Configuration

            @Configuration
            class ArmeriaConfig {
                @Bean
                fun armeriaServerConfigurator(): ArmeriaServerConfigurator =
                    ArmeriaServerConfigurator { serverBuilder ->
                        serverBuilder.service("/api", HelloService())
                    }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class HelloService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val apiRoutes = routes.filter { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertEquals(1, apiRoutes.size)
    }

    fun testCollectServiceRegistrationFromBeanServer() {
        myFixture.configureByText(
            "ArmeriaConfig.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import org.springframework.context.annotation.Bean
            import org.springframework.context.annotation.Configuration

            @Configuration
            class ArmeriaConfig {
                @Bean
                fun armeriaServer(): Server =
                    Server.builder()
                        .service("/health", HelloService())
                        .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class HelloService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val healthRoutes = routes.filter { it.path == "/health" && it.routeMatch == RouteMatch.SERVICE }
        assertEquals(1, healthRoutes.size)
        assertEquals("example.HelloService", healthRoutes.single().target)
    }

    fun testIgnoresUnrelatedServiceCallInBeanMethod() {
        myFixture.configureByText(
            "ArmeriaConfig.kt",
            """
            package example

            import com.linecorp.armeria.spring.ArmeriaServerConfigurator
            import org.springframework.context.annotation.Bean
            import org.springframework.context.annotation.Configuration

            @Configuration
            class ArmeriaConfig {
                @Bean
                fun armeriaServerConfigurator(): ArmeriaServerConfigurator =
                    ArmeriaServerConfigurator { serverBuilder ->
                        Helper.service("ignored")
                        serverBuilder.service("/api", HelloService())
                    }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class HelloService {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public final class Helper {
                public static void service(String ignored) {
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val apiRoutes = routes.filter { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertEquals(1, apiRoutes.size)
        assertFalse(routes.any { it.path == "ignored" })
    }

    fun testIgnoresBeanFunctionWithoutArmeriaReturnType() {
        myFixture.configureByText(
            "OtherConfig.kt",
            """
            package example

            import com.linecorp.armeria.spring.ArmeriaServerConfigurator
            import org.springframework.context.annotation.Bean
            import org.springframework.context.annotation.Configuration

            @Configuration
            class OtherConfig {
                @Bean
                fun unrelatedBean(): String = "value"
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertTrue(routes.isEmpty())
    }

    private fun registerArmeriaStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;

            public final class Server {
                public static ServerBuilder builder() {
                    return null;
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

                public ServerBuilder serviceUnder(String prefix, Object service) {
                    return this;
                }

                public ServerBuilder annotatedService(Object service) {
                    return this;
                }

                public Server build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }

    private fun registerSpringBootStubs() {
        myFixture.addClass(
            """
            package org.springframework.context.annotation;

            public @interface Bean {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package org.springframework.context.annotation;

            public @interface Configuration {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.spring;

            @FunctionalInterface
            public interface ArmeriaServerConfigurator {
                void configure(com.linecorp.armeria.server.ServerBuilder serverBuilder);
            }
            """.trimIndent(),
        )
    }
}
