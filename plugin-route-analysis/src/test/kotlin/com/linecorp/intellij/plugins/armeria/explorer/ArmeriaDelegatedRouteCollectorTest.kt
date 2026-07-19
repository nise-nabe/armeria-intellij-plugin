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
            ArmeriaServletMountSupport.delegationKindOf(mountRoute),
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
            ArmeriaServletMountSupport.delegationKindOf(mountRoute),
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
            ArmeriaServletMountSupport.delegationKindOf(mountRoute),
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
            ArmeriaServletMountSupport.delegationKindOf(mountRoute),
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
        configureHelloControllerJava()

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
        configureHelloControllerJava()

        val routes = ArmeriaRouteCollector.collect(project)
        val delegated =
            routes.filter {
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
        configureHelloControllerJava()

        val routes = ArmeriaRouteCollector.collect(project)

        assertNotNull(
            routes.singleOrNull {
                it.path == "/spring/" &&
                    ArmeriaServletMountSupport.delegationKindOf(it) == DelegationKind.SPRING_MVC
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
        configureHelloControllerKotlin()

        val routes = ArmeriaRouteCollector.collect(project)
        val springMounts = routes.filter { it.path == "/spring/" }
        assertTrue(
            "expected Spring MVC–badged /spring/ mount; got: " +
                springMounts.map { "${it.target}/${it.routeMatch}/${ArmeriaServletMountSupport.delegationKindOf(it)}" },
            springMounts.any { ArmeriaServletMountSupport.delegationKindOf(it) == DelegationKind.SPRING_MVC },
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

    fun testRepeatableRequestMappingEmitsAllMappings() {
        configureTomcatMount("/spring/")
        myFixture.configureByText(
            "DualController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RequestMethod;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class DualController {
                @RequestMapping(path = "/a", method = RequestMethod.GET)
                @RequestMapping(path = "/b", method = RequestMethod.POST)
                public String dual() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val delegated = routes.filter { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC }

        assertEquals(
            setOf("GET /spring/a", "POST /spring/b"),
            delegated.map { "${it.httpMethod} ${it.path}" }.toSet(),
        )
    }

    fun testMultipleShortcutMappingsOnSameMethodAreCollected() {
        configureTomcatMount("/spring/")
        myFixture.configureByText(
            "ItemController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class ItemController {
                @GetMapping("/items")
                @PostMapping("/items")
                public String items() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val delegated = routes.filter { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC }

        assertEquals(
            setOf("GET /spring/items", "POST /spring/items"),
            delegated.map { "${it.httpMethod} ${it.path}" }.toSet(),
        )
    }

    fun testClassLevelRepeatableRequestMappingEmitsAllPrefixes() {
        configureTomcatMount("/spring/")
        myFixture.configureByText(
            "UserController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            @RequestMapping("/api")
            @RequestMapping("/v1")
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

    fun testKotlinNullableTomcatServiceBeanMountIsDetected() {
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
                fun armeriaServerConfigurator(tomcatService: TomcatService?): ArmeriaServerConfigurator =
                    ArmeriaServerConfigurator { serverBuilder ->
                        serverBuilder.serviceUnder("/spring/", tomcatService)
                    }
            }
            """.trimIndent(),
        )
        configureHelloControllerKotlin()

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(
            routes.any {
                it.path == "/spring/" &&
                    ArmeriaServletMountSupport.delegationKindOf(it) == DelegationKind.SPRING_MVC
            },
        )
        assertNotNull(routes.singleOrNull { it.path == "/spring/hello" && it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC })
    }

    fun testAmbiguousSameModuleMountsExpandOnlyUnderShortestPath() {
        myFixture.configureByText(
            "ArmeriaConfig.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.tomcat.TomcatService;

            public class ArmeriaConfig {
                public static void main(String[] args) {
                    Server.builder()
                        .serviceUnder("/", TomcatService.of(null))
                        .serviceUnder("/api", TomcatService.of(null))
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "ApiController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class ApiController {
                @GetMapping("/api")
                public String fromRootMount() {
                    return "ok";
                }

                @RequestMapping(path = "", method = org.springframework.web.bind.annotation.RequestMethod.GET)
                public String fromApiMount() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val delegated = routes.filter { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC }
        assertTrue(delegated.isNotEmpty())
        assertEquals(setOf("/"), delegated.map { it.delegationMountPath }.toSet())
        assertNotNull(
            delegated.singleOrNull {
                it.path == "/api" &&
                    it.httpMethod == "GET" &&
                    it.target.contains("fromRootMount") &&
                    it.delegationMountPath == "/"
            },
        )
        // Root mapping "" under preferred "/" becomes "/".
        assertNotNull(
            delegated.singleOrNull {
                it.path == "/" &&
                    it.httpMethod == "GET" &&
                    it.target.contains("fromApiMount") &&
                    it.delegationMountPath == "/"
            },
        )
        assertTrue(delegated.none { it.delegationMountPath == "/api" })
    }

    fun testKotlinCyclicPropertyAliasesDoNotCrashCollection() {
        myFixture.configureByText(
            "ArmeriaConfig.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.tomcat.TomcatService

            object ArmeriaConfig {
                val a = b
                val b = a

                @JvmStatic
                fun main(args: Array<String>) {
                    Server.builder()
                        .serviceUnder("/spring/", a)
                        .build()
                }
            }
            """.trimIndent(),
        )
        configureHelloControllerKotlin()

        // Cycle must not StackOverflowError during Kotlin target resolution.
        val routes = ArmeriaRouteCollector.collect(project)
        assertNotNull(routes)
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
        configureHelloControllerJava()

        val routes = ArmeriaRouteCollector.collect(project)

        assertTrue(routes.none { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC })
    }

    fun testPreferredSpringMvcMountsPicksShortestPathPerModuleAndVirtualHost() {
        val probe = myFixture.addClass("public class PreferredMountProbe {}")
        fun mount(
            path: String,
            moduleName: String = "app",
            virtualHostName: String = "",
        ): ArmeriaRoute =
            ArmeriaRoute.create(
                element = probe,
                protocol = RouteProtocol.HTTP.presentableName(),
                httpMethod = "",
                path = path,
                target = "TomcatService",
                routeMatch = RouteMatch.SERVICE_UNDER,
                virtualHostName = virtualHostName,
                moduleName = moduleName,
            )

        val single = mount("/spring/")
        assertEquals(listOf(single), ArmeriaDelegatedRouteCollector.preferredSpringMvcMounts(listOf(single)))

        val root = mount("/")
        val api = mount("/api")
        assertEquals(
            listOf(root),
            ArmeriaDelegatedRouteCollector.preferredSpringMvcMounts(listOf(api, root)),
        )

        val alpha = mount("/aaa")
        val beta = mount("/bbb")
        assertEquals(
            listOf(alpha),
            ArmeriaDelegatedRouteCollector.preferredSpringMvcMounts(listOf(beta, alpha)),
        )

        val hostA = mount("/spring/", virtualHostName = "a.example.com")
        val hostB = mount("/spring/", virtualHostName = "b.example.com")
        val hostAApi = mount("/api", virtualHostName = "a.example.com")
        assertEquals(
            setOf(hostA, hostB),
            ArmeriaDelegatedRouteCollector
                .preferredSpringMvcMounts(listOf(hostAApi, hostA, hostB))
                .toSet(),
        )
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
        val controllerModule = helloRoute.moduleName()
        val matchingMount =
            ArmeriaRoute.create(
                element = helloRoute.controller,
                protocol = RouteProtocol.HTTP.presentableName(),
                httpMethod = "",
                path = "/spring/",
                target = "TomcatService",
                routeMatch = RouteMatch.SERVICE_UNDER,
            )
        val unassignedModule = message("route.explorer.module.unassigned")

        val scopedRoutes =
            ArmeriaDelegatedRouteCollector.springMvcRoutesForMount(
                mountRoute = matchingMount,
                springMvcRoutes = springMvcRoutes,
                unassignedModule = unassignedModule,
            )
        assertEquals(springMvcRoutes, scopedRoutes)

        val otherModuleMount = matchingMount.copy(moduleName = "$controllerModule-other")
        val filteredRoutes =
            ArmeriaDelegatedRouteCollector.springMvcRoutesForMount(
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
        assertEquals(
            DelegationKind.SPRING_MVC,
            ArmeriaServletMountSupport.detectDelegation("TomcatService?", RouteMatch.SERVICE_UNDER),
        )

        val expandable =
            ArmeriaRoute.create(
                element = myFixture.addClass("public class ExpandableMountProbe {}"),
                protocol = RouteProtocol.HTTP.presentableName(),
                httpMethod = "",
                path = "/spring/",
                target = "TomcatService",
                routeMatch = RouteMatch.SERVICE_UNDER,
            )
        val exactMount = expandable.copy(routeMatch = RouteMatch.SERVICE, path = "/spring")
        assertTrue(ArmeriaServletMountSupport.isExpandableSpringMvcMount(expandable))
        assertFalse(ArmeriaServletMountSupport.isExpandableSpringMvcMount(exactMount))
    }

    fun testSpringMvcCollectorResolvesStereotypesOutsideSearchScope() {
        // Stereotype annotation classes live outside a controller-only search scope (same as
        // library jars vs projectScope). Collection must still resolve them via classpath scope.
        val controllerFile =
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
        val controllerOnlyScope = GlobalSearchScope.fileScope(controllerFile)
        val routes = ArmeriaSpringMvcRouteCollector.collect(project, controllerOnlyScope)
        assertEquals(listOf("/hello"), routes.map { it.path })
        assertEquals(listOf("GET"), routes.map { it.httpMethod })
    }

    private fun configureHelloControllerJava(path: String = "/hello") {
        myFixture.configureByText(
            "HelloController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class HelloController {
                @GetMapping("$path")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )
    }

    private fun configureHelloControllerKotlin(path: String = "/hello") {
        myFixture.configureByText(
            "HelloController.kt",
            """
            package example

            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RestController

            @RestController
            class HelloController {
                @GetMapping("$path")
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )
    }
}
