package com.linecorp.intellij.plugins.armeria.client

import com.intellij.microservices.endpoints.EndpointsProvider
import com.intellij.microservices.endpoints.HTTP_CLIENT_TYPE
import com.intellij.microservices.endpoints.ModuleEndpointsFilter
import com.intellij.microservices.endpoints.SearchScopeEndpointsFilter
import com.intellij.microservices.endpoints.presentation.HttpMethodPresentation
import com.intellij.microservices.url.UrlPath
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaClientFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArmeriaClientEndpointsProviderTest : ArmeriaClientFixtureTestBase() {
    fun testWebClientOfHttpsAppearsAsHttpClientEndpoint() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;

            public class Main {
                public static void main(String[] args) {
                    WebClient.of("https://api.example.com/v1");
                }
            }
            """.trimIndent(),
        )

        val provider = ArmeriaClientEndpointsProvider()
        assertEquals(HTTP_CLIENT_TYPE, provider.endpointType)
        assertEquals(EndpointsProvider.Status.AVAILABLE, provider.getStatus(project))
        assertEquals(message("endpoints.framework.title"), provider.presentation.title)

        val (group, endpoint) = singleEndpoint(provider)
        assertEquals("https://api.example.com/v1", endpoint.uri)
        assertEquals("HTTP", endpoint.clientType)
        assertTrue(provider.isValidEndpoint(group, endpoint))

        val presentation = provider.getEndpointPresentation(group, endpoint) as HttpMethodPresentation
        assertEquals("https://api.example.com/v1", presentation.presentableText)
        assertTrue(presentation.endpointMethods.isEmpty())

        val navigation = provider.getNavigationElement(group, endpoint)
        assertNotNull(navigation)
        assertTrue(navigation is PsiMethodCallExpression)

        val target = provider.getUrlTargetInfo(group, endpoint).single()
        assertEquals(emptySet(), target.methods)
        assertEquals(navigation, target.resolveToPsiElement())
        assertEquals(
            listOf(
                UrlPath.PathSegment.Exact(""),
                UrlPath.PathSegment.Exact("v1"),
                UrlPath.PathSegment.Undefined,
            ),
            target.path.segments,
        )
    }

    fun testOriginOnlyUriIsPrefixRoot() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;

            public class Main {
                public static void main(String[] args) {
                    WebClient.of("https://api.example.com");
                }
            }
            """.trimIndent(),
        )

        val provider = ArmeriaClientEndpointsProvider()
        val (group, endpoint) = singleEndpoint(provider)
        val target = provider.getUrlTargetInfo(group, endpoint).single()
        assertEquals(listOf(UrlPath.PathSegment.Undefined), target.path.segments)
    }

    fun testRestClientPathIsPrefix() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.RestClient;

            public class Main {
                public static void main(String[] args) {
                    RestClient.of("https://api.example.com/hello");
                }
            }
            """.trimIndent(),
        )

        val provider = ArmeriaClientEndpointsProvider()
        val (group, endpoint) = singleEndpoint(provider)
        assertEquals("https://api.example.com/hello", endpoint.uri)
        val target = provider.getUrlTargetInfo(group, endpoint).single()
        assertEquals(
            listOf(
                UrlPath.PathSegment.Exact(""),
                UrlPath.PathSegment.Exact("hello"),
                UrlPath.PathSegment.Undefined,
            ),
            target.path.segments,
        )
        assertTrue(target.methods.isEmpty())
    }

    fun testGrpcClientUsesPost() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.grpc.GrpcClients;

            public class Main {
                public static void main(String[] args) {
                    GrpcClients.newClient("https://grpc.example.com", MyStub.class);
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class MyStub {
            }
            """.trimIndent(),
        )

        val provider = ArmeriaClientEndpointsProvider()
        val (group, endpoint) = singleEndpoint(provider)
        assertEquals("gRPC", endpoint.clientType)
        val presentation = provider.getEndpointPresentation(group, endpoint) as HttpMethodPresentation
        assertEquals(listOf("POST"), presentation.endpointMethods)
        assertEquals(setOf("POST"), provider.getUrlTargetInfo(group, endpoint).single().methods)
    }

    fun testZkDiscoveryUriIsHidden() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;
            import com.linecorp.armeria.client.endpoint.zookeeper.ZooKeeperEndpointGroup;
            import com.linecorp.armeria.common.SessionProtocol;

            public class Main {
                public static void main(String[] args) {
                    WebClient.of("zk://zk.example.com/armeria");
                    WebClient.builder(SessionProtocol.HTTP, ZooKeeperEndpointGroup.of("zk://zk.example.com/armeria"));
                }
            }
            """.trimIndent(),
        )

        val provider = ArmeriaClientEndpointsProvider()
        assertTrue(provider.getEndpointGroups(project, ModuleEndpointsFilter(module, true, true)).none())
        assertTrue(ArmeriaClientCollector.collect(project).isNotEmpty())
    }

    fun testSearchScopeFilter_emptyScopeHidesClients() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;

            public class Main {
                public static void main(String[] args) {
                    WebClient.of("https://api.example.com/v1");
                }
            }
            """.trimIndent(),
        )

        val provider = ArmeriaClientEndpointsProvider()
        val emptyFilter =
            object : SearchScopeEndpointsFilter {
                override val contentSearchScope = GlobalSearchScope.EMPTY_SCOPE
                override val transitiveSearchScope = GlobalSearchScope.EMPTY_SCOPE
            }
        assertTrue(provider.getEndpointGroups(project, emptyFilter).none())
    }

    private fun singleEndpoint(provider: ArmeriaClientEndpointsProvider): Pair<ArmeriaClientEndpointGroup, ArmeriaClientEndpoint> {
        val groups = provider.getEndpointGroups(project, ModuleEndpointsFilter(module, true, true)).toList()
        val group = groups.single()
        val endpoint = provider.getEndpoints(group).single()
        return group to endpoint
    }
}
