package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaSpringBootRouteCollectorTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerSpringAnnotationStubs()
    }

    fun testCollectServiceRegistrationFromBeanConfigurator() {
        registerArmeriaStubs()
        registerArmeriaSpringStubs()
        myFixture.configureByText(
            "ArmeriaConfig.java",
            """
            package example;

            import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class ArmeriaConfig {
                @Bean
                public ArmeriaServerConfigurator armeriaServerConfigurator() {
                    return serverBuilder -> serverBuilder.service("/api", new HelloService());
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
        registerArmeriaStubs()
        registerArmeriaSpringStubs()
        myFixture.configureByText(
            "ArmeriaConfig.java",
            """
            package example;

            import com.linecorp.armeria.server.ServerBuilder;
            import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class ArmeriaConfig {
                @Bean
                public ArmeriaServerConfigurator armeriaServerConfigurator() {
                    return serverBuilder -> serverBuilder.service("/api", new HelloService());
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
        registerArmeriaStubs()
        registerArmeriaSpringStubs()
        myFixture.configureByText(
            "ArmeriaConfig.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class ArmeriaConfig {
                @Bean
                public Server armeriaServer() {
                    return Server.builder()
                        .service("/health", new HelloService())
                        .build();
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

        val healthRoutes = routes.filter { it.path == "/health" && it.routeMatch == RouteMatch.SERVICE }
        assertEquals(1, healthRoutes.size)
        assertEquals("example.HelloService", healthRoutes.single().target)
    }

    fun testIgnoresUnrelatedServiceCallInBeanMethod() {
        registerArmeriaStubs()
        registerArmeriaSpringStubs()
        myFixture.configureByText(
            "ArmeriaConfig.java",
            """
            package example;

            import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class ArmeriaConfig {
                @Bean
                public ArmeriaServerConfigurator armeriaServerConfigurator() {
                    return serverBuilder -> {
                        Helper.service("ignored");
                        serverBuilder.service("/api", new HelloService());
                    };
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

    fun testIgnoresBeanMethodWithoutArmeriaReturnType() {
        registerArmeriaStubs()
        registerArmeriaSpringStubs()
        myFixture.configureByText(
            "OtherConfig.java",
            """
            package example;

            import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class OtherConfig {
                @Bean
                public String unrelatedBean() {
                    return "value";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertTrue(routes.isEmpty())
    }

    fun testCollectServiceRegistrationFromBeanServerBuilder() {
        registerArmeriaStubs()
        registerArmeriaSpringStubs()
        myFixture.configureByText(
            "ArmeriaConfig.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.ServerBuilder;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class ArmeriaConfig {
                @Bean
                public ServerBuilder armeriaServerBuilder() {
                    return Server.builder()
                        .service("/builder", new HelloService());
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

        val builderRoutes = routes.filter { it.path == "/builder" && it.routeMatch == RouteMatch.SERVICE }
        assertEquals(1, builderRoutes.size)
        assertEquals("example.HelloService", builderRoutes.single().target)
    }

    fun testCollectServiceUnderAndAnnotatedServiceFromBeanConfigurator() {
        registerArmeriaStubs()
        registerArmeriaSpringStubs()
        myFixture.configureByText(
            "ArmeriaConfig.java",
            """
            package example;

            import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class ArmeriaConfig {
                @Bean
                public ArmeriaServerConfigurator armeriaServerConfigurator() {
                    return serverBuilder -> {
                        serverBuilder.serviceUnder("/api", new HelloService());
                        serverBuilder.annotatedService(new AnnotatedService());
                    };
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
        registerArmeriaStubs()
        registerArmeriaSpringStubs()
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
            "ArmeriaConfig.java",
            """
            package example;

            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class ArmeriaConfig {
                @Bean
                public RoutingConfigurator customConfigurator() {
                    return new RoutingConfigurator();
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

        val subtypeRoutes = routes.filter { it.path == "/subtype" && it.routeMatch == RouteMatch.SERVICE }
        assertEquals(1, subtypeRoutes.size)
    }

    fun testSkipsCollectionWhenArmeriaSpringTypesAbsentOnClasspath() {
        myFixture.configureByText(
            "SpringOnlyConfig.java",
            """
            package example;

            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class SpringOnlyConfig {
                @Bean
                public String unrelatedBean() {
                    return "value";
                }
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

    private fun registerSpringAnnotationStubs() {
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
    }

    private fun registerArmeriaSpringStubs() {
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
