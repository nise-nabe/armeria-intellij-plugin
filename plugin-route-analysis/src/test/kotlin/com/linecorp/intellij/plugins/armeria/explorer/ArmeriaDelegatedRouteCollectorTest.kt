package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaDelegatedRouteCollectorTest : ArmeriaFixtureTestBase() {
    override fun setUp() {
        super.setUp()
        registerSpringAnnotationStubs()
        registerArmeriaSpringStubs()
        registerServletServiceStubs()
        registerSpringWebMvcStubs()
    }

    fun testTomcatServiceUnderMountExposesDelegatedSpringMvcRoutes() {
        configureTomcatMount("/spring/")
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
        assertEquals(
            DelegationKind.SPRING_MVC,
            ArmeriaRouteDetailFormatter.delegationKindOf(mountRoute),
        )

        val delegatedRoute = routes.single { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC }
        assertEquals("GET", delegatedRoute.httpMethod)
        assertEquals("/spring/users/{id}", delegatedRoute.path)
        assertEquals("/spring/", delegatedRoute.delegationMountPath)
        assertEquals("example.UserController#getUser()", delegatedRoute.target)
    }

    fun testExactServiceMountIsBadgedWithoutSpringMvcChildren() {
        myFixture.configureByText(
            "ArmeriaConfig.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.tomcat.TomcatService;

            public class ArmeriaConfig {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/spring", TomcatService.of(null))
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
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class UserController {
                @GetMapping("/users")
                public String list() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val mountRoute = routes.single { it.path == "/spring" && it.routeMatch == RouteMatch.SERVICE }
        assertEquals(
            DelegationKind.SPRING_MVC,
            ArmeriaRouteDetailFormatter.delegationKindOf(mountRoute),
        )
        assertTrue(routes.none { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC })
    }

    fun testJettyServiceMountIsBadgedWithoutSpringMvcChildren() {
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
        assertEquals(
            DelegationKind.SERVLET,
            ArmeriaRouteDetailFormatter.delegationKindOf(mountRoute),
        )
        // Jetty is a servlet container mount; do not invent Spring MVC children under it.
        assertTrue(routes.none { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC })
    }

    fun testTomcatMountIsBadgedWithoutSpringControllers() {
        configureTomcatMount("/spring/")

        val routes = ArmeriaRouteCollector.collect(project)

        val mountRoute = routes.single { it.path == "/spring/" && it.routeMatch == RouteMatch.SERVICE_UNDER }
        assertEquals(
            DelegationKind.SPRING_MVC,
            ArmeriaRouteDetailFormatter.delegationKindOf(mountRoute),
        )
        assertTrue(routes.none { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC })
    }

    fun testRequestMappingMethodEnumIsResolved() {
        configureTomcatMount("/spring/")
        myFixture.configureByText(
            "OrderController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RequestMethod;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class OrderController {
                @RequestMapping(path = "/orders", method = RequestMethod.POST)
                public String create() {
                    return "ok";
                }

                @RequestMapping(path = "/orders/{id}", method = {RequestMethod.GET, RequestMethod.HEAD})
                public String read() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val delegated = routes.filter { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC }

        val create = delegated.single { it.path == "/spring/orders" }
        assertEquals("POST", create.httpMethod)

        val readMethods = delegated.filter { it.path == "/spring/orders/{id}" }.map { it.httpMethod }.toSet()
        assertEquals(setOf("GET", "HEAD"), readMethods)
    }

    fun testDelegatedRoutesInheritVirtualHostFromMount() {
        myFixture.configureByText(
            "ArmeriaConfig.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.tomcat.TomcatService;

            public class ArmeriaConfig {
                public static void main(String[] args) {
                    Server.builder()
                        .virtualHost("api.example.com")
                        .serviceUnder("/spring/", TomcatService.of(null))
                        .build();
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
        val mountRoute = routes.single { it.path == "/spring/" && it.routeMatch == RouteMatch.SERVICE_UNDER }
        val delegatedRoute = routes.single { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC }

        assertEquals("api.example.com", mountRoute.virtualHostName)
        assertEquals("api.example.com", delegatedRoute.virtualHostName)
    }

    fun testDelegatedRoutesKeepSeparateChildrenPerVirtualHost() {
        myFixture.configureByText(
            "FirstMain.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.tomcat.TomcatService;

            public class FirstMain {
                public static void main(String[] args) {
                    Server.builder()
                        .virtualHost("a.example.com")
                        .serviceUnder("/spring/", TomcatService.of(null))
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "SecondMain.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.tomcat.TomcatService;

            public class SecondMain {
                public static void main(String[] args) {
                    Server.builder()
                        .virtualHost("b.example.com")
                        .serviceUnder("/spring/", TomcatService.of(null))
                        .build();
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
        val delegated = routes.filter {
            it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC && it.path == "/spring/hello"
        }
        assertEquals(2, delegated.size)
        assertEquals(
            setOf("a.example.com", "b.example.com"),
            delegated.map { it.virtualHostName }.toSet(),
        )
    }

    fun testTomcatServiceBeanMountIsDetected() {
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

        assertNotNull(
            routes.singleOrNull {
                it.path == "/spring/" &&
                    ArmeriaRouteDetailFormatter.delegationKindOf(it) == DelegationKind.SPRING_MVC
            },
        )
        assertNotNull(routes.singleOrNull { it.path == "/spring/hello" && it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC })
    }

    fun testKotlinTomcatServiceBeanMountIsDetected() {
        myFixture.configureByText(
            "ArmeriaConfig.kt",
            """
            package example

            import com.linecorp.armeria.spring.ArmeriaServerConfigurator
            import com.linecorp.armeria.server.tomcat.TomcatService
            import org.springframework.context.annotation.Bean
            import org.springframework.context.annotation.Configuration

            @Configuration
            class ArmeriaConfig {
                @Bean
                fun armeriaServerConfigurator(tomcatService: TomcatService): ArmeriaServerConfigurator =
                    ArmeriaServerConfigurator { serverBuilder ->
                        serverBuilder.serviceUnder("/spring/", tomcatService)
                    }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "HelloController.kt",
            """
            package example

            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RestController

            @RestController
            class HelloController {
                @GetMapping("/hello")
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNotNull(
            routes.singleOrNull {
                it.path == "/spring/" &&
                    ArmeriaRouteDetailFormatter.delegationKindOf(it) == DelegationKind.SPRING_MVC
            },
        )
        assertNotNull(routes.singleOrNull { it.path == "/spring/hello" && it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC })
    }

    fun testClassLevelMultiPathRequestMappingEmitsAllPrefixes() {
        configureTomcatMount("/spring/")
        myFixture.configureByText(
            "UserController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            @RequestMapping({"/api", "/v1"})
            public class UserController {
                @GetMapping("/users")
                public String list() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val delegated = routes.filter { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC }

        assertEquals(
            setOf("/spring/api/users", "/spring/v1/users"),
            delegated.map { it.path }.toSet(),
        )
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
    }

    fun testSpringMvcRoutesForMountFiltersControllersByModule() {
        myFixture.configureByText(
            "AppController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class AppController {
                @GetMapping("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val springMvcRoutes = ArmeriaSpringMvcRouteCollector.collect(project, GlobalSearchScope.allScope(project))
        val helloRoute = springMvcRoutes.single()
        val controllerModule = ArmeriaRouteMetadata.moduleName(helloRoute.element)
        val matchingMount = ArmeriaRoute.create(
            element = helloRoute.element,
            protocol = RouteProtocol.HTTP.presentableName(),
            httpMethod = "",
            path = "/spring/",
            target = "TomcatService",
            routeMatch = RouteMatch.SERVICE_UNDER,
        )
        val unassignedModule = message("route.explorer.module.unassigned")

        val scopedRoutes = ArmeriaDelegatedRouteCollector.springMvcRoutesForMount(
            mountRoute = matchingMount,
            springMvcRoutes = springMvcRoutes,
            unassignedModule = unassignedModule,
        )
        assertEquals(springMvcRoutes, scopedRoutes)

        val otherModuleMount = matchingMount.copy(moduleName = "$controllerModule-other")
        val filteredRoutes = ArmeriaDelegatedRouteCollector.springMvcRoutesForMount(
            mountRoute = otherModuleMount,
            springMvcRoutes = springMvcRoutes,
            unassignedModule = unassignedModule,
        )
        assertTrue(filteredRoutes.isEmpty())
    }

    fun testServletMountSupportDetectsKnownServices() {
        assertEquals(
            DelegationKind.SPRING_MVC,
            ArmeriaServletMountSupport.detectDelegation("TomcatService", RouteMatch.SERVICE_UNDER),
        )
        assertEquals(
            DelegationKind.SPRING_MVC,
            ArmeriaServletMountSupport.detectDelegation("TomcatService", RouteMatch.SERVICE),
        )
        assertEquals(
            DelegationKind.SERVLET,
            ArmeriaServletMountSupport.detectDelegation("JettyService", RouteMatch.SERVICE_UNDER),
        )
        assertNull(ArmeriaServletMountSupport.detectDelegation("HelloService", RouteMatch.SERVICE))
        assertNull(ArmeriaServletMountSupport.detectDelegation("SpringBootService", RouteMatch.SERVICE))
        assertNull(
            ArmeriaServletMountSupport.detectDelegation("FooTomcatService", RouteMatch.SERVICE_UNDER),
        )
        assertEquals(
            DelegationKind.SPRING_MVC,
            ArmeriaServletMountSupport.detectDelegation(
                "com.linecorp.armeria.server.tomcat.TomcatService",
                RouteMatch.SERVICE_UNDER,
            ),
        )
    }

    private fun configureTomcatMount(path: String) {
        myFixture.configureByText(
            "ArmeriaConfig.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.tomcat.TomcatService;

            public class ArmeriaConfig {
                public static void main(String[] args) {
                    Server.builder()
                        .serviceUnder("$path", TomcatService.of(null))
                        .build();
                }
            }
            """.trimIndent(),
        )
    }

    private fun registerServletServiceStubs() {
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

    private fun registerSpringWebMvcStubs() {
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

            public enum RequestMethod {
                GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package org.springframework.web.bind.annotation;

            public @interface RequestMapping {
                String[] value() default {};
                String[] path() default {};
                RequestMethod[] method() default {};
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
