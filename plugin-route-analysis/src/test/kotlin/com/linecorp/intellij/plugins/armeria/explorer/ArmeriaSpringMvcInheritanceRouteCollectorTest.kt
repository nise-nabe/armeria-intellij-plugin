package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaSpringMvcInheritanceRouteCollectorTest : ArmeriaFixtureTestBase() {
    override fun setUp() {
        super.setUp()
        registerSpringAnnotationStubs()
        registerArmeriaSpringStubs()
        registerServletServiceStubs()
        registerSpringWebMvcStubs()
    }

    fun testBaseClassMappingIsDiscoveredUnderConcreteController() {
        configureTomcatMount("/spring/")
        myFixture.addClass(
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;

            public abstract class BaseUserController {
                @GetMapping("/{id}")
                public String getUser() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "UserController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            @RequestMapping("/users")
            public class UserController extends BaseUserController {
            }
            """.trimIndent(),
        )

        val springMvcRoutes = ArmeriaSpringMvcRouteCollector.collect(project, GlobalSearchScope.projectScope(project))
        assertEquals(
            listOf("example.UserController#getUser()"),
            springMvcRoutes.map { it.target },
        )
        assertEquals(listOf("/users/{id}"), springMvcRoutes.map { it.path })
        assertEquals("example.UserController", springMvcRoutes.single().controller.qualifiedName)
        assertEquals("example.BaseUserController", springMvcRoutes.single().element.containingClass?.qualifiedName)

        val routes = ArmeriaRouteCollector.collect(project)

        val delegatedRoute = routes.single { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC }
        assertEquals("GET", delegatedRoute.httpMethod)
        assertEquals("/spring/users/{id}", delegatedRoute.path)
        assertEquals("example.UserController#getUser()", delegatedRoute.target)
        assertEquals(
            ArmeriaRouteMetadata.moduleName(springMvcRoutes.single().controller),
            delegatedRoute.moduleName,
        )
    }

    fun testInterfaceMappingIsDiscoveredUnderConcreteController() {
        configureTomcatMount("/spring/")
        myFixture.addClass(
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;

            @RequestMapping("/users")
            public interface UserApi {
                @GetMapping("/{id}")
                String getUser();
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "UserController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class UserController implements UserApi {
                @Override
                public String getUser() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val springMvcRoutes = ArmeriaSpringMvcRouteCollector.collect(project, GlobalSearchScope.projectScope(project))
        val springMvcRoute = springMvcRoutes.single()
        assertEquals("example.UserController", springMvcRoute.controller.qualifiedName)
        assertEquals("example.UserApi", springMvcRoute.element.containingClass?.qualifiedName)

        val routes = ArmeriaRouteCollector.collect(project)

        val delegatedRoute = routes.single { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC }
        assertEquals("GET", delegatedRoute.httpMethod)
        assertEquals("/spring/users/{id}", delegatedRoute.path)
        assertEquals("example.UserController#getUser()", delegatedRoute.target)
        assertEquals(
            ArmeriaRouteMetadata.moduleName(springMvcRoute.controller),
            delegatedRoute.moduleName,
        )
    }

    fun testBaseClassRequestMappingPrefixAppliesToInheritedMethod() {
        configureTomcatMount("/spring/")
        myFixture.addClass(
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;

            @RequestMapping("/api")
            public abstract class BaseApiController {
                @GetMapping("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "HelloController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class HelloController extends BaseApiController {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val delegatedRoute = routes.single { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC }
        assertEquals("GET", delegatedRoute.httpMethod)
        assertEquals("/spring/api/hello", delegatedRoute.path)
        assertEquals("example.HelloController#hello()", delegatedRoute.target)
    }

    fun testKotlinControllerInheritsJavaBaseClassMapping() {
        configureTomcatMount("/spring/")
        myFixture.addClass(
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;

            public abstract class BaseGreetingController {
                @GetMapping("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "HelloController.kt",
            """
            package example

            import org.springframework.web.bind.annotation.RestController

            @RestController
            class HelloController : BaseGreetingController()
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val delegatedRoute = routes.single { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC }
        assertEquals("GET", delegatedRoute.httpMethod)
        assertEquals("/spring/hello", delegatedRoute.path)
        assertEquals("example.HelloController#hello()", delegatedRoute.target)
    }

    fun testChildMappingOverrideWinsOverBaseClassMapping() {
        configureTomcatMount("/spring/")
        myFixture.addClass(
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;

            public abstract class BaseGreetingController {
                @GetMapping("/hello")
                public String hello() {
                    return "base";
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
            public class HelloController extends BaseGreetingController {
                @Override
                @GetMapping("/greet")
                public String hello() {
                    return "child";
                }
            }
            """.trimIndent(),
        )

        val springMvcRoutes = ArmeriaSpringMvcRouteCollector.collect(project, GlobalSearchScope.projectScope(project))
        assertEquals(listOf("/greet"), springMvcRoutes.map { it.path })
        assertEquals(listOf("example.HelloController#hello()"), springMvcRoutes.map { it.target })
        assertEquals("example.HelloController", springMvcRoutes.single().element.containingClass?.qualifiedName)

        val routes = ArmeriaRouteCollector.collect(project)
        val delegatedRoute = routes.single { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC }
        assertEquals("/spring/greet", delegatedRoute.path)
    }

    fun testAbstractStereotypeBaseDoesNotDoubleRegisterWithConcreteSubclass() {
        configureTomcatMount("/spring/")
        myFixture.addClass(
            """
            package example;

            import org.springframework.stereotype.Controller;
            import org.springframework.web.bind.annotation.GetMapping;

            @Controller
            public abstract class BaseGreetingController {
                @GetMapping("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "HelloController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class HelloController extends BaseGreetingController {
            }
            """.trimIndent(),
        )

        val springMvcRoutes = ArmeriaSpringMvcRouteCollector.collect(project, GlobalSearchScope.projectScope(project))
        assertEquals(listOf("/hello"), springMvcRoutes.map { it.path })
        assertEquals(listOf("example.HelloController#hello()"), springMvcRoutes.map { it.target })

        val routes = ArmeriaRouteCollector.collect(project)
        val delegated = routes.filter { it.routeMatch == RouteMatch.DELEGATED_SPRING_MVC }
        assertEquals(listOf("/spring/hello"), delegated.map { it.path })
    }

    fun testMountFilterUsesControllerModuleNotMappingElement() {
        myFixture.addClass(
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;

            public abstract class BaseGreetingController {
                @GetMapping("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "HelloController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class HelloController extends BaseGreetingController {
            }
            """.trimIndent(),
        )

        val springMvcRoutes = ArmeriaSpringMvcRouteCollector.collect(project, GlobalSearchScope.allScope(project))
        val helloRoute = springMvcRoutes.single()
        assertEquals("example.BaseGreetingController", helloRoute.element.containingClass?.qualifiedName)
        assertEquals("example.HelloController", helloRoute.controller.qualifiedName)

        val controllerModule = ArmeriaRouteMetadata.moduleName(helloRoute.controller)
        val matchingMount = ArmeriaRoute.create(
            element = helloRoute.controller,
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

            public @interface RequestMappings {
                RequestMapping[] value();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package org.springframework.web.bind.annotation;

            @java.lang.annotation.Repeatable(RequestMappings.class)
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
