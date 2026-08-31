package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaRouteTreeBuilder
import com.linecorp.intellij.plugins.armeria.message
import org.junit.Test
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArmeriaRouteTreeBuilderTest {
    @Test
    fun buildRoot_groupsRoutesByModule() {
        val routes =
            listOf(
                testRoute(moduleName = "app", path = "/a"),
                testRoute(moduleName = "app", path = "/b"),
                testRoute(moduleName = "lib", path = "/c"),
            )

        val root = ArmeriaRouteTreeBuilder.buildRoot(routes)

        assertEquals(2, root.childCount)
        val appModule = root.getChildAt(0) as DefaultMutableTreeNode
        val libModule = root.getChildAt(1) as DefaultMutableTreeNode
        assertEquals("app", (appModule.userObject as ArmeriaRouteTreeBuilder.ModuleNode).name)
        assertEquals(2, appModule.childCount)
        assertEquals("lib", (libModule.userObject as ArmeriaRouteTreeBuilder.ModuleNode).name)
        assertEquals(1, libModule.childCount)
    }

    @Test
    fun selectedRoute_returnsRouteNodeOnly() {
        val route = testRoute(moduleName = "app", path = "/health")
        val routeNode = DefaultMutableTreeNode(ArmeriaRouteTreeBuilder.RouteNode(route))
        val moduleNode = DefaultMutableTreeNode(ArmeriaRouteTreeBuilder.ModuleNode("app", 1))

        assertEquals(route, ArmeriaRouteTreeBuilder.selectedRoute(routeNode))
        assertNull(ArmeriaRouteTreeBuilder.selectedRoute(moduleNode))
    }

    @Test
    fun buildRoot_groupsNamedVirtualHostsAndDefaultHost() {
        val defaultRoute = testRoute(moduleName = "app", path = "/default")
        val hostedA = testRoute(moduleName = "app", path = "/a", virtualHostName = "a.example.com")
        val hostedB = testRoute(moduleName = "app", path = "/b", virtualHostName = "b.example.com")
        val hostA = virtualHostRoute(moduleName = "app", hostname = "a.example.com")
        val hostB = virtualHostRoute(moduleName = "app", hostname = "b.example.com")

        val root =
            ArmeriaRouteTreeBuilder.buildRoot(
                listOf(defaultRoute, hostedA, hostedB, hostA, hostB),
            )

        val module = root.getChildAt(0) as DefaultMutableTreeNode
        assertEquals(3, module.childCount)
        val defaultHost = module.getChildAt(0) as DefaultMutableTreeNode
        val aHost = module.getChildAt(1) as DefaultMutableTreeNode
        val bHost = module.getChildAt(2) as DefaultMutableTreeNode
        val defaultNode = defaultHost.userObject as ArmeriaRouteTreeBuilder.VirtualHostNode
        val aNode = aHost.userObject as ArmeriaRouteTreeBuilder.VirtualHostNode
        val bNode = bHost.userObject as ArmeriaRouteTreeBuilder.VirtualHostNode
        assertEquals("", defaultNode.hostname)
        assertEquals(1, defaultNode.routeCount)
        assertNull(defaultNode.navigationRoute)
        assertEquals("a.example.com", aNode.hostname)
        assertEquals(hostA, aNode.navigationRoute)
        assertEquals("b.example.com", bNode.hostname)
        assertEquals("/default", childRoute(defaultHost).path)
        assertEquals("/a", childRoute(aHost).path)
        assertEquals("/b", childRoute(bHost).path)
        assertEquals(hostA, ArmeriaRouteTreeBuilder.selectedRoute(aHost))
        assertEquals(
            message("route.explorer.tree.virtualHost.default", 1),
            ArmeriaRouteTreeBuilder.virtualHostDisplayLabel(defaultNode),
        )
        assertEquals(
            message("route.explorer.tree.virtualHost", "a.example.com", 1),
            ArmeriaRouteTreeBuilder.virtualHostDisplayLabel(aNode),
        )
    }

    @Test
    fun buildRoot_placesPortBindingsBeforeVirtualHostGroups() {
        val hosted = testRoute(moduleName = "app", path = "/api", virtualHostName = "api.example.com")
        val host = virtualHostRoute(moduleName = "app", hostname = "api.example.com")
        val http = listenPortRoute(moduleName = "app", port = 8080, protocol = "HTTP")
        val unified = listenPortRoute(moduleName = "app", port = 8888, protocol = "HTTP+HTTPS")

        val root = ArmeriaRouteTreeBuilder.buildRoot(listOf(hosted, host, unified, http))
        val module = root.getChildAt(0) as DefaultMutableTreeNode
        assertEquals(3, module.childCount)
        val first = (module.getChildAt(0) as DefaultMutableTreeNode).userObject as ArmeriaRouteTreeBuilder.RouteNode
        val second = (module.getChildAt(1) as DefaultMutableTreeNode).userObject as ArmeriaRouteTreeBuilder.RouteNode
        val hostNode =
            (module.getChildAt(2) as DefaultMutableTreeNode).userObject as ArmeriaRouteTreeBuilder.VirtualHostNode
        assertEquals(":8080", first.route.path)
        assertEquals("8080 HTTP", ArmeriaRouteTreeBuilder.portDisplayLabel(first.route))
        assertEquals(":8888", second.route.path)
        assertEquals("8888 HTTP+HTTPS", ArmeriaRouteTreeBuilder.portDisplayLabel(second.route))
        assertEquals("api.example.com", hostNode.hostname)
    }

    @Test
    fun buildRoot_keepsFlatTreeWhenEveryRouteIsOnTheDefaultHost() {
        val routes =
            listOf(
                testRoute(moduleName = "app", path = "/a"),
                testRoute(moduleName = "app", path = "/b"),
            )

        val root = ArmeriaRouteTreeBuilder.buildRoot(routes)
        val module = root.getChildAt(0) as DefaultMutableTreeNode
        assertEquals(2, module.childCount)
        assertTrue(module.getChildAt(0) is DefaultMutableTreeNode)
        assertTrue((module.getChildAt(0) as DefaultMutableTreeNode).userObject is ArmeriaRouteTreeBuilder.RouteNode)
    }

    @Test
    fun findNode_restoresVirtualHostGroupAndNestedRoute() {
        val hosted = testRoute(moduleName = "app", path = "/api", virtualHostName = "api.example.com")
        val host = virtualHostRoute(moduleName = "app", hostname = "api.example.com")
        val root = ArmeriaRouteTreeBuilder.buildRoot(listOf(hosted, host))
        val module = root.getChildAt(0) as DefaultMutableTreeNode

        val hostNode = ArmeriaRouteTreeBuilder.findNode(root, host)
        val routeNode = ArmeriaRouteTreeBuilder.findNode(module, hosted)
        assertEquals(host, ArmeriaRouteTreeBuilder.selectedRoute(hostNode))
        assertEquals(hosted, ArmeriaRouteTreeBuilder.selectedRoute(routeNode))
    }

    @Test
    fun isPortBinding_isListenPortOnly() {
        assertTrue(ArmeriaRouteTreeBuilder.isPortBinding(listenPortRoute(moduleName = "app", port = 8080, protocol = "HTTP")))
        assertTrue(
            !ArmeriaRouteTreeBuilder.isPortBinding(
                testRoute(
                    moduleName = "app",
                    path = ":8080",
                    routeMatch = RouteMatch.NON_HTTP,
                    protocol = "HTTP",
                    httpMethod = "",
                    target = "Spring Boot port 8080 (HTTP)",
                ),
            ),
        )
    }

    @Test
    fun findNode_distinguishesListenPortsByProtocol() {
        val http = listenPortRoute(moduleName = "app", port = 8080, protocol = "HTTP")
        val https = listenPortRoute(moduleName = "app", port = 8080, protocol = "HTTPS")
        val root = ArmeriaRouteTreeBuilder.buildRoot(listOf(http, https))

        val httpNode = ArmeriaRouteTreeBuilder.findNode(root, http)
        val httpsNode = ArmeriaRouteTreeBuilder.findNode(root, https)
        assertEquals(http, ArmeriaRouteTreeBuilder.selectedRoute(httpNode))
        assertEquals(https, ArmeriaRouteTreeBuilder.selectedRoute(httpsNode))
        assertTrue(httpNode !== httpsNode)
    }

    @Test
    fun buildRoot_picksStableVirtualHostNavigation() {
        val later = virtualHostRoute(moduleName = "app", hostname = "a.example.com", sourceOffset = 80)
        val earlier = virtualHostRoute(moduleName = "app", hostname = "a.example.com", sourceOffset = 10)
        val hosted = testRoute(moduleName = "app", path = "/a", virtualHostName = "a.example.com")
        val root = ArmeriaRouteTreeBuilder.buildRoot(listOf(later, earlier, hosted))
        val module = root.getChildAt(0) as DefaultMutableTreeNode
        val hostNode = module.getChildAt(0) as DefaultMutableTreeNode
        val node = hostNode.userObject as ArmeriaRouteTreeBuilder.VirtualHostNode
        assertEquals(earlier, node.navigationRoute)
    }

    @Test
    fun speedSearchText_includesPortLabelAndStableDefaultHost() {
        val port = listenPortRoute(moduleName = "app", port = 8080, protocol = "HTTP")
        val hosted = testRoute(moduleName = "app", path = "/a", virtualHostName = "a.example.com")
        val host = virtualHostRoute(moduleName = "app", hostname = "a.example.com")
        val defaultRoute = testRoute(moduleName = "app", path = "/default")
        val root = ArmeriaRouteTreeBuilder.buildRoot(listOf(port, hosted, host, defaultRoute))
        val module = root.getChildAt(0) as DefaultMutableTreeNode
        val portNode = (module.getChildAt(0) as DefaultMutableTreeNode).userObject
        val defaultHost = (module.getChildAt(1) as DefaultMutableTreeNode).userObject
        val namedHost = (module.getChildAt(2) as DefaultMutableTreeNode).userObject
        val portSearch = ArmeriaRouteTreeBuilder.speedSearchText(portNode)
        assertTrue(portSearch.contains("8080 HTTP"))
        assertTrue(portSearch.contains(port.speedSearchText))
        assertEquals(
            message("route.explorer.tree.virtualHost.default.search"),
            ArmeriaRouteTreeBuilder.speedSearchText(defaultHost),
        )
        assertEquals("a.example.com", ArmeriaRouteTreeBuilder.speedSearchText(namedHost))
    }

    private fun testRoute(
        moduleName: String,
        path: String,
        virtualHostName: String = "",
        routeMatch: RouteMatch = RouteMatch.ANNOTATED_HTTP,
        protocol: String = "HTTP",
        httpMethod: String = "GET",
        target: String = "Handler",
        sourceOffset: Int? = null,
    ): ArmeriaRoute =
        ArmeriaRoute(
            protocol = protocol,
            httpMethod = httpMethod,
            path = path,
            target = target,
            routeMatch = routeMatch,
            moduleName = moduleName,
            targetUnresolved = false,
            isDocService = false,
            virtualHostName = virtualHostName,
            decorators = emptyList(),
            exceptionHandlers = emptyList(),
            pointer = TestPsiPointer,
            sourceOffset = sourceOffset,
        )

    private fun virtualHostRoute(
        moduleName: String,
        hostname: String,
        sourceOffset: Int? = null,
    ): ArmeriaRoute =
        testRoute(
            moduleName = moduleName,
            path = "/",
            virtualHostName = hostname,
            routeMatch = RouteMatch.VIRTUAL_HOST,
            httpMethod = "",
            target = hostname,
            sourceOffset = sourceOffset,
        )

    private fun listenPortRoute(
        moduleName: String,
        port: Int,
        protocol: String,
    ): ArmeriaRoute =
        testRoute(
            moduleName = moduleName,
            path = ":$port",
            routeMatch = RouteMatch.LISTEN_PORT,
            protocol = protocol,
            httpMethod = "",
            target = "Listen port",
        )

    private fun childRoute(hostNode: DefaultMutableTreeNode): ArmeriaRoute {
        val routeNode = hostNode.getChildAt(0) as DefaultMutableTreeNode
        return (routeNode.userObject as ArmeriaRouteTreeBuilder.RouteNode).route
    }

    private object TestPsiPointer : SmartPsiElementPointer<PsiElement> {
        override fun getElement(): PsiElement? = null

        override fun getContainingFile(): PsiFile? = null

        override fun getRange(): TextRange? = null

        override fun getProject(): Project = throw UnsupportedOperationException()

        override fun getVirtualFile(): VirtualFile = throw UnsupportedOperationException()

        override fun getPsiRange(): TextRange? = null
    }
}
