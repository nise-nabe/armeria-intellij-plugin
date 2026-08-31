package com.linecorp.intellij.plugins.armeria.explorer.ui

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.message
import javax.swing.tree.DefaultMutableTreeNode

object ArmeriaRouteTreeBuilder {
    private val PORT_PROTOCOL_SEPARATOR = Regex("""\s*,\s*""")

    fun buildRoot(routes: List<ArmeriaRoute>): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode(RootNode)
        if (routes.isEmpty()) {
            return root
        }
        for ((moduleName, moduleRoutes) in routes.groupBy { it.moduleName }.toSortedMap()) {
            val portBindings = moduleRoutes.filter(::isPortBinding).sortedWith(portComparator)
            val serviceRoutes = moduleRoutes.filterNot(::isPortBinding)
            val groupByVirtualHost = serviceRoutes.any { it.virtualHostName.isNotEmpty() }
            val visibleCount =
                if (groupByVirtualHost) {
                    portBindings.size + serviceRoutes.count { it.routeMatch != RouteMatch.VIRTUAL_HOST }
                } else {
                    moduleRoutes.size
                }
            val moduleNode = DefaultMutableTreeNode(ModuleNode(moduleName, visibleCount))
            for (port in portBindings) {
                moduleNode.add(DefaultMutableTreeNode(RouteNode(port)))
            }
            if (groupByVirtualHost) {
                addVirtualHostGroups(moduleNode, serviceRoutes)
            } else {
                for (route in serviceRoutes) {
                    moduleNode.add(DefaultMutableTreeNode(RouteNode(route)))
                }
            }
            root.add(moduleNode)
        }
        return root
    }

    fun selectedRoute(node: Any?): ArmeriaRoute? =
        when (val userObject = (node as? DefaultMutableTreeNode)?.userObject) {
            is RouteNode -> userObject.route
            is VirtualHostNode -> userObject.navigationRoute
            else -> null
        }

    fun findNode(
        root: DefaultMutableTreeNode,
        route: ArmeriaRoute,
    ): DefaultMutableTreeNode? {
        val nodes = root.preorderEnumeration()
        while (nodes.hasMoreElements()) {
            val node = nodes.nextElement() as? DefaultMutableTreeNode ?: continue
            when (val userObject = node.userObject) {
                is RouteNode ->
                    if (routesMatch(userObject.route, route)) {
                        return node
                    }
                is VirtualHostNode -> {
                    val navigation = userObject.navigationRoute ?: continue
                    if (routesMatch(navigation, route)) {
                        return node
                    }
                }
            }
        }
        return null
    }

    fun isPortBinding(route: ArmeriaRoute): Boolean =
        route.routeMatch == RouteMatch.LISTEN_PORT ||
            (route.routeMatch == RouteMatch.NON_HTTP && route.path.startsWith(":") && route.path.length > 1)

    fun portDisplayLabel(route: ArmeriaRoute): String {
        val port = route.path.removePrefix(":")
        val protocols = PORT_PROTOCOL_SEPARATOR.replace(route.protocol, "+")
        return "$port $protocols"
    }

    fun virtualHostDisplayLabel(node: VirtualHostNode): String =
        if (node.hostname.isEmpty()) {
            message("route.explorer.tree.virtualHost.default", node.routeCount)
        } else {
            message("route.explorer.tree.virtualHost", node.hostname, node.routeCount)
        }

    private fun addVirtualHostGroups(
        moduleNode: DefaultMutableTreeNode,
        serviceRoutes: List<ArmeriaRoute>,
    ) {
        val navigationByHost =
            serviceRoutes
                .filter { it.routeMatch == RouteMatch.VIRTUAL_HOST }
                .groupBy { it.virtualHostName.ifBlank { it.target } }
                .mapValues { (_, hosts) -> hosts.first() }
        val grouped =
            serviceRoutes
                .filter { it.routeMatch != RouteMatch.VIRTUAL_HOST }
                .groupBy { it.virtualHostName }
        val hostnames = (grouped.keys + navigationByHost.keys).distinct()
        val ordered = hostnames.sortedWith(compareBy({ it.isNotEmpty() }, { it }))
        for (hostname in ordered) {
            val children = grouped[hostname].orEmpty()
            if (hostname.isEmpty() && children.isEmpty()) {
                continue
            }
            val hostNode =
                DefaultMutableTreeNode(
                    VirtualHostNode(
                        hostname = hostname,
                        routeCount = children.size,
                        navigationRoute = navigationByHost[hostname],
                    ),
                )
            for (route in children) {
                hostNode.add(DefaultMutableTreeNode(RouteNode(route)))
            }
            moduleNode.add(hostNode)
        }
    }

    private fun routesMatch(
        left: ArmeriaRoute,
        right: ArmeriaRoute,
    ): Boolean =
        left.moduleName == right.moduleName &&
            left.path == right.path &&
            left.target == right.target &&
            left.routeMatch == right.routeMatch &&
            left.httpMethod == right.httpMethod &&
            left.virtualHostName == right.virtualHostName &&
            left.delegationMountPath == right.delegationMountPath

    private val portComparator: Comparator<ArmeriaRoute> =
        compareBy<ArmeriaRoute> { portNumber(it) }.thenBy { it.protocol }.thenBy { it.path }

    private fun portNumber(route: ArmeriaRoute): Int = route.path.removePrefix(":").toIntOrNull() ?: Int.MAX_VALUE

    data object RootNode

    data class ModuleNode(
        val name: String,
        val routeCount: Int,
    )

    data class VirtualHostNode(
        val hostname: String,
        val routeCount: Int,
        val navigationRoute: ArmeriaRoute?,
    )

    data class RouteNode(
        val route: ArmeriaRoute,
    )
}
