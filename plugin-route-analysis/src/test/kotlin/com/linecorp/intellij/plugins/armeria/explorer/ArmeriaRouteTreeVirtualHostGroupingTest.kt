package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaRouteTreeBuilder
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArmeriaRouteTreeVirtualHostGroupingTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerExtendedRegistrationCollectorStubs()
    }

    fun testTwoVirtualHostBlocksGroupRoutesUnderTheCorrectHost() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.common.SessionProtocol;
            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .http(8080)
                        .port(8888, SessionProtocol.HTTP, SessionProtocol.HTTPS)
                        .service("/default", new ApiService())
                        .virtualHost("a.example.com")
                        .service("/a", new ApiService())
                        .virtualHost("b.example.com")
                        .service("/b", new ApiService())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class ApiService {}")

        val routes = ArmeriaRouteCollector.collect(project)
        val root = ArmeriaRouteTreeBuilder.buildRoot(routes)
        val module = root.getChildAt(0) as DefaultMutableTreeNode

        val ports =
            (0 until module.childCount)
                .map { module.getChildAt(it) as DefaultMutableTreeNode }
                .mapNotNull { it.userObject as? ArmeriaRouteTreeBuilder.RouteNode }
                .map { ArmeriaRouteTreeBuilder.portDisplayLabel(it.route) }
        assertEquals(listOf("8080 HTTP", "8888 HTTP+HTTPS"), ports)

        val hosts =
            (0 until module.childCount)
                .map { module.getChildAt(it) as DefaultMutableTreeNode }
                .mapNotNull { it.userObject as? ArmeriaRouteTreeBuilder.VirtualHostNode }
        assertEquals(listOf("", "a.example.com", "b.example.com"), hosts.map { it.hostname })

        val defaultHost = module.getChildAt(2) as DefaultMutableTreeNode
        val aHost = module.getChildAt(3) as DefaultMutableTreeNode
        val bHost = module.getChildAt(4) as DefaultMutableTreeNode
        assertEquals("/default", childRoute(defaultHost).path)
        assertEquals("", childRoute(defaultHost).virtualHostName)
        assertEquals("/a", childRoute(aHost).path)
        assertEquals("a.example.com", childRoute(aHost).virtualHostName)
        assertEquals("/b", childRoute(bHost).path)
        assertEquals("b.example.com", childRoute(bHost).virtualHostName)
        assertEquals("a.example.com", (aHost.userObject as ArmeriaRouteTreeBuilder.VirtualHostNode).navigationRoute?.target)
        assertNull((defaultHost.userObject as ArmeriaRouteTreeBuilder.VirtualHostNode).navigationRoute)
        assertEquals(RouteMatch.VIRTUAL_HOST, ArmeriaRouteTreeBuilder.selectedRoute(aHost)?.routeMatch)
    }

    private fun childRoute(hostNode: DefaultMutableTreeNode) =
        (hostNode.getChildAt(0) as DefaultMutableTreeNode).userObject.let { it as ArmeriaRouteTreeBuilder.RouteNode }.route
}
