package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.endpoints.ArmeriaEndpointsSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaHttpMethodPill
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaHttpRequestGenerator
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaRouteDetailFormatter
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaRouteTreeBuilder
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull as kotlinAssertNotNull

class ArmeriaRouteTreeDiscoveryGroupingTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerExtendedRegistrationCollectorStubs()
        registerDiscoveryRegistrationStubs()
    }

    fun testDiscoveryRegistrationsGroupUnderDiscoverySection() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.eureka.EurekaUpdatingListener;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .http(8080)
                        .service("/api", new ApiService())
                        .serverListener(EurekaUpdatingListener.of("https://eureka.example.com/eureka/v2", "my-app"))
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class ApiService {}")

        val routes = ArmeriaRouteCollector.collect(project)
        val root = ArmeriaRouteTreeBuilder.buildRoot(routes)
        val module = root.getChildAt(0) as DefaultMutableTreeNode

        val discoveryNodes =
            (0 until module.childCount)
                .map { module.getChildAt(it) as DefaultMutableTreeNode }
                .mapNotNull { it.userObject as? ArmeriaRouteTreeBuilder.DiscoveryNode }
        assertEquals(1, discoveryNodes.size)
        assertEquals(1, discoveryNodes.single().routeCount)

        val discoveryNode =
            (0 until module.childCount)
                .map { module.getChildAt(it) as DefaultMutableTreeNode }
                .first { it.userObject is ArmeriaRouteTreeBuilder.DiscoveryNode }
        val discoveryRoute =
            (discoveryNode.getChildAt(0) as DefaultMutableTreeNode)
                .userObject
                .let { it as ArmeriaRouteTreeBuilder.RouteNode }
                .route
        assertEquals(RouteMatch.DISCOVERY, discoveryRoute.routeMatch)
        assertEquals("my-app", discoveryRoute.path)
        assertEquals("https://eureka.example.com/eureka/v2", discoveryRoute.target)
        assertEquals("Eureka", discoveryRoute.protocol)

        // Discovery registrations must not surface as HTTP endpoints or generate requests.
        assertFalse(ArmeriaEndpointsSupport.isVisibleServerRoute(discoveryRoute))
        assertFalse(ArmeriaHttpRequestGenerator.supports(discoveryRoute))
        assertEquals("EUREKA", ArmeriaHttpMethodPill.pillLabel(discoveryRoute))
        assertEquals("Eureka", discoveryRoute.methodLabel)
        assertTrue(ArmeriaRouteDetailFormatter.registrationSummary(discoveryRoute).isNotBlank())
        assertTrue(ArmeriaRouteTreeBuilder.discoveryDisplayLabel(discoveryNodes.single()).isNotBlank())

        val serviceRoute =
            (0 until module.childCount)
                .map { module.getChildAt(it) as DefaultMutableTreeNode }
                .mapNotNull { it.userObject as? ArmeriaRouteTreeBuilder.RouteNode }
                .map { it.route }
                .first { it.path == "/api" }
        assertEquals(RouteMatch.SERVICE, serviceRoute.routeMatch)
        val selected = ArmeriaRouteTreeBuilder.findNode(root, discoveryRoute)
        kotlinAssertNotNull(selected)
    }
}
