package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.microservices.endpoints.EndpointsProvider
import com.intellij.microservices.endpoints.ModuleEndpointsFilter
import com.intellij.microservices.endpoints.SearchScopeEndpointsFilter
import com.intellij.microservices.endpoints.presentation.HttpMethodPresentation
import com.intellij.microservices.url.UrlPath
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.endpoints.ArmeriaEndpointGroup
import com.linecorp.intellij.plugins.armeria.explorer.endpoints.ArmeriaEndpointsProvider
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArmeriaEndpointsProviderTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerRouteCollectorStubs()
    }

    @Test
    fun annotatedGet_appearsAsHttpServerEndpoint() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val provider = ArmeriaEndpointsProvider()
        assertEquals(EndpointsProvider.Status.AVAILABLE, provider.getStatus(project))
        assertEquals(message("endpoints.framework.title"), provider.presentation.title)

        val (group, route) = singleEndpoint(provider)
        assertEquals("/hello", route.path)
        assertEquals("GET", route.httpMethod)
        assertEquals(RouteMatch.ANNOTATED_HTTP, route.routeMatch)
        assertTrue(provider.isValidEndpoint(group, route))

        val presentation = provider.getEndpointPresentation(group, route) as HttpMethodPresentation
        assertEquals("/hello", presentation.presentableText)
        assertEquals(listOf("GET"), presentation.endpointMethods)

        val navigation = provider.getNavigationElement(group, route)
        assertNotNull(navigation)
        assertEquals("hello", (navigation as PsiMethod).name)

        val target = provider.getUrlTargetInfo(group, route).single()
        assertEquals(setOf("GET"), target.methods)
        assertEquals(navigation, target.resolveToPsiElement())
    }

    @Test
    fun serviceRegistration_appears_andAnnotatedServiceNodeDoesNot() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/api", new HelloService())
                        .annotatedService(new HelloService())
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = collectedRoutes()
        assertTrue(routes.any { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE })
        assertTrue(routes.any { it.path == "/hello" && it.routeMatch == RouteMatch.ANNOTATED_HTTP })
        assertTrue(routes.none { it.routeMatch == RouteMatch.ANNOTATED_SERVICE })
    }

    @Test
    fun springMvcGetMapping_isNotEmittedAsArmeriaEndpoint() {
        registerSpringAnnotationStubs()
        registerArmeriaSpringStubs()
        registerServletServiceStubs()
        registerSpringWebMvcStubs()
        configureTomcatMount("/spring/")
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
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

        val routes = collectedRoutes()
        assertTrue(routes.any { it.path == "/hello" && it.routeMatch == RouteMatch.ANNOTATED_HTTP })
        assertTrue(routes.none { it.routeMatch == RouteMatch.DELEGATED })
        assertTrue(routes.none { it.path == "/spring/users/{id}" })
    }

    @Test
    fun moduleFilter_returnsRoutesFromModule() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val provider = ArmeriaEndpointsProvider()
        val filter = ModuleEndpointsFilter(module, true, true)
        val routes =
            provider.getEndpointGroups(project, filter).flatMap { provider.getEndpoints(it) }.toList()
        assertEquals(listOf("/hello"), routes.map { it.path })
    }

    @Test
    fun searchScopeFilter_emptyScopeHidesRoutes() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val provider = ArmeriaEndpointsProvider()
        val emptyFilter =
            object : SearchScopeEndpointsFilter {
                override val contentSearchScope = GlobalSearchScope.EMPTY_SCOPE
                override val transitiveSearchScope = GlobalSearchScope.EMPTY_SCOPE
            }
        assertTrue(provider.getEndpointGroups(project, emptyFilter).none())
    }

    @Test
    fun serviceUnder_urlPathIsPrefix() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            public class HelloService {
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serviceUnder("/v1", new HelloService())
                        .build();
                }
            }
            """.trimIndent(),
        )

        val provider = ArmeriaEndpointsProvider()
        val groups = provider.getEndpointGroups(project, ModuleEndpointsFilter(module, true, true))
        val (group, route) =
            groups
                .asSequence()
                .flatMap { group -> provider.getEndpoints(group).map { group to it } }
                .first { (_, route) -> route.routeMatch == RouteMatch.SERVICE_UNDER && route.path == "/v1" }
        val target = provider.getUrlTargetInfo(group, route).single()
        assertEquals(
            listOf(
                UrlPath.PathSegment.Exact(""),
                UrlPath.PathSegment.Exact("v1"),
                UrlPath.PathSegment.Undefined,
            ),
            target.path.segments,
        )
    }

    private fun collectedRoutes(): List<ArmeriaRoute> {
        val provider = ArmeriaEndpointsProvider()
        return provider
            .getEndpointGroups(project, ModuleEndpointsFilter(module, true, true))
            .flatMap { provider.getEndpoints(it) }
            .toList()
    }

    private fun singleEndpoint(provider: ArmeriaEndpointsProvider): Pair<ArmeriaEndpointGroup, ArmeriaRoute> {
        val groups = provider.getEndpointGroups(project, ModuleEndpointsFilter(module, true, true)).toList()
        val group = groups.single()
        val route = provider.getEndpoints(group).single()
        return group to route
    }
}
