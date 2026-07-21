package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.DelegationKind
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.explorer.spring.ArmeriaDelegatedRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.spring.ArmeriaSpringMvcRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.spring.ArmeriaSpringRouteContributor
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaSpringMvcInheritanceRouteCollectorTest : ArmeriaFixtureTestBase() {
    override fun setUp() {
        super.setUp()
        // Tomcat mount + Spring MVC mapping stubs only — no @Bean / ArmeriaServerConfigurator.
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
        assertEquals(
            "example.BaseUserController",
            springMvcRoutes
                .single()
                .element.containingClass
                ?.qualifiedName,
        )

        val routes =
            ArmeriaRouteCollector.collect(
                project,
                contributors = listOf(ArmeriaSpringRouteContributor),
            )

        val delegatedRoute = routes.single { it.routeMatch == RouteMatch.DELEGATED }
        assertEquals("GET", delegatedRoute.httpMethod)
        assertEquals("/spring/users/{id}", delegatedRoute.path)
        assertEquals("example.UserController#getUser()", delegatedRoute.target)
        assertEquals(DelegationKind.SPRING_MVC, delegatedRoute.delegationKind)
        assertEquals(
            springMvcRoutes.single().moduleName(),
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

        val routes =
            ArmeriaRouteCollector.collect(
                project,
                contributors = listOf(ArmeriaSpringRouteContributor),
            )

        val delegatedRoute = routes.single { it.routeMatch == RouteMatch.DELEGATED }
        assertEquals("GET", delegatedRoute.httpMethod)
        assertEquals("/spring/users/{id}", delegatedRoute.path)
        assertEquals("example.UserController#getUser()", delegatedRoute.target)
        assertEquals(
            springMvcRoute.moduleName(),
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

        val routes =
            ArmeriaRouteCollector.collect(
                project,
                contributors = listOf(ArmeriaSpringRouteContributor),
            )

        val delegatedRoute = routes.single { it.routeMatch == RouteMatch.DELEGATED }
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

        val routes =
            ArmeriaRouteCollector.collect(
                project,
                contributors = listOf(ArmeriaSpringRouteContributor),
            )

        val delegatedRoute = routes.single { it.routeMatch == RouteMatch.DELEGATED }
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
        assertEquals(
            "example.HelloController",
            springMvcRoutes
                .single()
                .element.containingClass
                ?.qualifiedName,
        )

        val routes =
            ArmeriaRouteCollector.collect(
                project,
                contributors = listOf(ArmeriaSpringRouteContributor),
            )
        val delegatedRoute = routes.single { it.routeMatch == RouteMatch.DELEGATED }
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

        val routes =
            ArmeriaRouteCollector.collect(
                project,
                contributors = listOf(ArmeriaSpringRouteContributor),
            )
        val delegated = routes.filter { it.routeMatch == RouteMatch.DELEGATED }
        assertEquals(listOf("/spring/hello"), delegated.map { it.path })
    }

    fun testUnannotatedConcreteInheritorOfAbstractStereotypeIsNotDiscovered() {
        configureTomcatMount("/spring/")
        myFixture.addClass(
            """
            package example;

            import org.springframework.stereotype.Controller;
            import org.springframework.web.bind.annotation.GetMapping;

            @Controller
            public abstract class BaseApi {
                @GetMapping("/hello")
                public String hello() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "UsersApi.java",
            """
            package example;

            public class UsersApi extends BaseApi {
            }
            """.trimIndent(),
        )

        // @Controller is not @Inherited; default component scanning does not register UsersApi.
        val springMvcRoutes = ArmeriaSpringMvcRouteCollector.collect(project, GlobalSearchScope.projectScope(project))
        assertTrue(springMvcRoutes.isEmpty())

        val routes =
            ArmeriaRouteCollector.collect(
                project,
                contributors = listOf(ArmeriaSpringRouteContributor),
            )
        assertTrue(routes.none { it.routeMatch == RouteMatch.DELEGATED })
    }

    fun testUnannotatedConcreteImplementorOfStereotypeInterfaceIsNotDiscovered() {
        myFixture.addClass(
            """
            package example;

            import org.springframework.stereotype.Controller;
            import org.springframework.web.bind.annotation.GetMapping;

            @Controller
            public interface GreetingApi {
                @GetMapping("/hello")
                String hello();
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "GreetingService.java",
            """
            package example;

            public class GreetingService implements GreetingApi {
                @Override
                public String hello() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val springMvcRoutes = ArmeriaSpringMvcRouteCollector.collect(project, GlobalSearchScope.projectScope(project))
        assertTrue(springMvcRoutes.isEmpty())
    }

    fun testAbstractStereotypeSubclassDoesNotSuppressConcreteParent() {
        myFixture.addClass(
            """
            package example;

            import org.springframework.stereotype.Controller;
            import org.springframework.web.bind.annotation.GetMapping;

            @Controller
            public class ParentController {
                @GetMapping("/parent")
                public String parent() {
                    return "parent";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "MidController.java",
            """
            package example;

            import org.springframework.stereotype.Controller;

            @Controller
            public abstract class MidController extends ParentController {
            }
            """.trimIndent(),
        )

        val springMvcRoutes = ArmeriaSpringMvcRouteCollector.collect(project, GlobalSearchScope.projectScope(project))
        assertEquals(listOf("/parent"), springMvcRoutes.map { it.path })
        assertEquals(listOf("example.ParentController#parent()"), springMvcRoutes.map { it.target })
        assertEquals("example.ParentController", springMvcRoutes.single().controller.qualifiedName)
    }

    fun testConcreteStereotypeParentAndChildBothRegisterWithDistinctPrefixes() {
        myFixture.addClass(
            """
            package example;

            import org.springframework.stereotype.Controller;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;

            @Controller
            @RequestMapping("/admin")
            public class ParentController {
                @GetMapping("/hello")
                public String hello() {
                    return "parent";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "ChildController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.RestController;
            import org.springframework.web.bind.annotation.RequestMapping;

            @RestController
            @RequestMapping("/api")
            public class ChildController extends ParentController {
            }
            """.trimIndent(),
        )

        val springMvcRoutes =
            ArmeriaSpringMvcRouteCollector
                .collect(project, GlobalSearchScope.projectScope(project))
                .sortedBy { it.path }
        assertEquals(listOf("/admin/hello", "/api/hello"), springMvcRoutes.map { it.path })
        assertEquals(
            listOf("example.ChildController#hello()", "example.ParentController#hello()"),
            springMvcRoutes.map { it.target }.sorted(),
        )
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

    fun testMultiLevelUnannotatedOverrideResolvesGrandparentMapping() {
        myFixture.addClass(
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;

            public abstract class GrandController {
                @GetMapping("/hello")
                public String hello() {
                    return "grand";
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public abstract class MidController extends GrandController {
                @Override
                public String hello() {
                    return "mid";
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
            public class HelloController extends MidController {
                @Override
                public String hello() {
                    return "child";
                }
            }
            """.trimIndent(),
        )

        val springMvcRoutes = ArmeriaSpringMvcRouteCollector.collect(project, GlobalSearchScope.projectScope(project))
        assertEquals(listOf("/hello"), springMvcRoutes.map { it.path })
        assertEquals(listOf("example.HelloController#hello()"), springMvcRoutes.map { it.target })
        assertEquals(
            "example.GrandController",
            springMvcRoutes
                .single()
                .element.containingClass
                ?.qualifiedName,
        )
        assertEquals("example.HelloController", springMvcRoutes.single().controller.qualifiedName)
    }

    fun testInterfaceTypePrefixWinsOverSuperclassPrefix() {
        myFixture.addClass(
            """
            package example;

            import org.springframework.web.bind.annotation.RequestMapping;

            @RequestMapping("/base")
            public abstract class BaseController {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;

            @RequestMapping("/api")
            public interface GreetingApi {
                @GetMapping("/hello")
                String hello();
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "HelloController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class HelloController extends BaseController implements GreetingApi {
                @Override
                public String hello() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val springMvcRoutes = ArmeriaSpringMvcRouteCollector.collect(project, GlobalSearchScope.projectScope(project))
        assertEquals(listOf("/api/hello"), springMvcRoutes.map { it.path })
        assertEquals(listOf("example.HelloController#hello()"), springMvcRoutes.map { it.target })
    }

    fun testInterfaceMethodMappingPreferredOverSuperclassMapping() {
        myFixture.addClass(
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;

            public abstract class BaseController {
                @GetMapping("/base")
                public String hello() {
                    return "base";
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            import org.springframework.web.bind.annotation.GetMapping;

            public interface GreetingApi {
                @GetMapping("/api")
                String hello();
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "HelloController.java",
            """
            package example;

            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class HelloController extends BaseController implements GreetingApi {
                @Override
                public String hello() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val springMvcRoutes = ArmeriaSpringMvcRouteCollector.collect(project, GlobalSearchScope.projectScope(project))
        assertEquals(listOf("/api"), springMvcRoutes.map { it.path })
        assertEquals(
            "example.GreetingApi",
            springMvcRoutes
                .single()
                .element.containingClass
                ?.qualifiedName,
        )
    }
}
