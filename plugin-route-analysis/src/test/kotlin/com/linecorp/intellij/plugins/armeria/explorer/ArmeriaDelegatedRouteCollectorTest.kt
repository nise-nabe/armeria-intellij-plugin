package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaDelegatedRouteCollectorTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerArmeriaStubs()
        registerSpringWebStubs()
    }

    fun testTomcatServiceUnderMountExposesDelegatedSpringMvcRoutes() {
        myFixture.configureByText(
            "ArmeriaConfig.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.tomcat.TomcatService;

            public class ArmeriaConfig {
                public static void main(String[] args) {
                    Server.builder()
                        .serviceUnder("/spring/", TomcatService.of(null))
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "UserController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            @RequestMapping("/users")
            public class UserController {
                @GetMapping("/{id}")
                public String getUser() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val mountRoute = routes.single { it.path == "/spring/" && it.routeMatch == RouteMatch.SERVICE_UNDER }
        assertEquals(DelegationKind.SPRING_MVC, mountRoute.delegationKind)

        val delegatedRoute = routes.single { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC }
        assertEquals("GET", delegatedRoute.httpMethod)
        assertEquals("/spring/users/{id}", delegatedRoute.path)
        assertEquals("/spring/", delegatedRoute.delegationMountPath)
        assertEquals("example.UserController#getUser()", delegatedRoute.target)
    }

    fun testJettyServiceMountExposesDelegatedRoutesAsServlet() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.jetty.JettyService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serviceUnder("/legacy", JettyService.of(null))
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "LegacyController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class LegacyController {
                @PostMapping("/submit")
                public String submit() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val mountRoute = routes.single { it.path == "/legacy" && it.routeMatch == RouteMatch.SERVICE_UNDER }
        assertEquals(DelegationKind.SERVLET, mountRoute.delegationKind)

        val delegatedRoute = routes.single { it.routeMatch == RouteMatch.DELEGATED_SERVLET }
        assertEquals("POST", delegatedRoute.httpMethod)
        assertEquals("/legacy/submit", delegatedRoute.path)
    }

    fun testSpringBootServiceBeanMountIsDetected() {
        registerArmeriaSpringStubs()
        myFixture.configureByText(
            "ArmeriaConfig.java",
            """
            package example;

            import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
            import com.linecorp.armeria.server.tomcat.TomcatService;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class ArmeriaConfig {
                @Bean
                public ArmeriaServerConfigurator armeriaServerConfigurator(TomcatService tomcatService) {
                    return serverBuilder -> serverBuilder.serviceUnder("/spring/", tomcatService);
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "HelloController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class HelloController {
                @GetMapping("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNotNull(routes.singleOrNull { it.path == "/spring/" && it.delegationKind == DelegationKind.SPRING_MVC })
        assertNotNull(routes.singleOrNull { it.path == "/spring/hello" && it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC })
    }

    fun testNoDelegatedRoutesWithoutServletMount() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/api", new HelloService())
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
        myFixture.configureByText(
            "HelloController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class HelloController {
                @GetMapping("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertTrue(routes.none { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC })
        assertTrue(routes.none { it.routeMatch == RouteMatch.DELEGATED_SERVLET })
    }

    fun testServletMountSupportDetectsKnownServices() {
        assertEquals(
            DelegationKind.SPRING_MVC,
            ArmeriaServletMountSupport.detectDelegation("TomcatService", RouteMatch.SERVICE_UNDER),
        )
        assertEquals(
            DelegationKind.SPRING_MVC,
            ArmeriaServletMountSupport.detectDelegation("SpringBootService", RouteMatch.SERVICE),
        )
        assertEquals(
            DelegationKind.SERVLET,
            ArmeriaServletMountSupport.detectDelegation("JettyService", RouteMatch.SERVICE_UNDER),
        )
        assertNull(ArmeriaServletMountSupport.detectDelegation("HelloService", RouteMatch.SERVICE))
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

                public Server build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.tomcat;

            public final class TomcatService {
                public static TomcatService of(Object connector) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.jetty;

            public final class JettyService {
                public static JettyService of(Object server) {
                    return null;
                }
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

    private fun registerSpringWebStubs() {
        registerArmeriaSpringStubs()
        myFixture.addClass(
            """
            package org.springframework.stereotype;

            public @interface Controller {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package org.springframework.web.bind.annotation;

            public @interface RestController {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package org.springframework.web.bind.annotation;

            public @interface RequestMapping {
                String[] value() default {};
                String[] path() default {};
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package org.springframework.web.bind.annotation;

            public @interface GetMapping {
                String[] value() default {};
                String[] path() default {};
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package org.springframework.web.bind.annotation;

            public @interface PostMapping {
                String[] value() default {};
                String[] path() default {};
            }
            """.trimIndent(),
        )
    }
}
