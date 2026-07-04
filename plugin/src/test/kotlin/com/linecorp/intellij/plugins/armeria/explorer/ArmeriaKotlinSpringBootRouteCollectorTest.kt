package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.search.GlobalSearchScope
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

    fun testKotlinSpringBootCollectorCollectsRoutesDirectly() {
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
                        serverBuilder.service("/direct", HelloService())
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

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaKotlinSpringBootRouteCollector.collect(
            project,
            GlobalSearchScope.projectScope(project),
            routes,
            linkedSetOf(),
            mutableSetOf(),
        )

        val directRoutes = routes.filter { it.path == "/direct" && it.routeMatch == RouteMatch.SERVICE }
        assertEquals(1, directRoutes.size)
        assertEquals("example.HelloService", directRoutes.single().target)
    }

    fun testKotlinSpringBootCollectorCollectsRoutesWithServerImportOnly() {
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
                        .service("/server-import", HelloService())
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

        val routes = mutableListOf<ArmeriaRoute>()
        ArmeriaKotlinSpringBootRouteCollector.collect(
            project,
            GlobalSearchScope.projectScope(project),
            routes,
            linkedSetOf(),
            mutableSetOf(),
        )

        val serverImportRoutes = routes.filter { it.path == "/server-import" && it.routeMatch == RouteMatch.SERVICE }
        assertEquals(1, serverImportRoutes.size)
        assertEquals("example.HelloService", serverImportRoutes.single().target)
    }

    fun testCollectServiceRegistrationFromBeanServerBuilder() {
        myFixture.configureByText(
            "ArmeriaConfig.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.ServerBuilder
            import org.springframework.context.annotation.Bean
            import org.springframework.context.annotation.Configuration

            @Configuration
            class ArmeriaConfig {
                @Bean
                fun armeriaServerBuilder(): ServerBuilder =
                    Server.builder()
                        .service("/builder", HelloService())
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

        val builderRoutes = routes.filter { it.path == "/builder" && it.routeMatch == RouteMatch.SERVICE }
        assertEquals(1, builderRoutes.size)
        assertEquals("example.HelloService", builderRoutes.single().target)
    }

    fun testCollectInferredReturnTypeFromBeanFunction() {
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
                fun armeriaServer() =
                    Server.builder()
                        .service("/inferred", HelloService())
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

        val inferredRoutes = routes.filter { it.path == "/inferred" && it.routeMatch == RouteMatch.SERVICE }
        assertEquals(1, inferredRoutes.size)
        assertEquals("example.HelloService", inferredRoutes.single().target)
    }

    fun testCollectServiceUnderAndAnnotatedServiceFromBeanConfigurator() {
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
                        serverBuilder.serviceUnder("/api", HelloService())
                        serverBuilder.annotatedService(AnnotatedService())
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

            public class AnnotatedService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val serviceUnderRoutes = routes.filter { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE_UNDER }
        assertEquals(1, serviceUnderRoutes.size)
        val annotatedRoutes = routes.filter { it.routeMatch == RouteMatch.ANNOTATED_SERVICE }
        assertEquals(1, annotatedRoutes.size)
    }

    fun testCollectServiceRegistrationFromConfiguratorSubtype() {
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.ServerBuilder;
            import com.linecorp.armeria.spring.ArmeriaServerConfigurator;

            public final class RoutingConfigurator implements ArmeriaServerConfigurator {
                @Override
                public void configure(ServerBuilder serverBuilder) {
                    serverBuilder.service("/subtype", new HelloService());
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "ArmeriaConfig.kt",
            """
            package example

            import org.springframework.context.annotation.Bean
            import org.springframework.context.annotation.Configuration

            @Configuration
            class ArmeriaConfig {
                @Bean
                fun customConfigurator(): RoutingConfigurator = RoutingConfigurator()
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

        val subtypeRoutes = routes.filter { it.path == "/subtype" && it.routeMatch == RouteMatch.SERVICE }
        assertEquals(1, subtypeRoutes.size)
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
