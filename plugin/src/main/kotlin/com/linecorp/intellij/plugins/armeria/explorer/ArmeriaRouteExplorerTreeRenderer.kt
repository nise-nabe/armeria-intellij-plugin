package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.linecorp.intellij.plugins.armeria.message
import javax.swing.JTree

internal class ArmeriaRouteExplorerTreeRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        toolTipText = null
        val node = value as? javax.swing.tree.DefaultMutableTreeNode ?: return
        when (val userObject = node.userObject) {
            is ArmeriaRouteTreeBuilder.ModuleNode -> {
                append(
                    message("route.explorer.tree.module", userObject.name, userObject.routeCount),
                    SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES,
                )
            }
            is ArmeriaRouteTreeBuilder.RouteNode -> renderRoute(userObject.route)
        }
    }

    private fun renderRoute(route: ArmeriaRoute) {
        val pillLabel = ArmeriaHttpMethodPill.pillLabel(route)
        toolTipText = buildString {
            append(route.methodLabel)
            append(' ')
            append(route.path)
            append(" → ")
            append(route.shortTarget)
            if (route.delegationMountPath.isNotEmpty()) {
                append(" (")
                append(message("route.explorer.tooltip.delegatedVia", route.delegationMountPath))
                append(')')
            }
        }
        append(ArmeriaHttpMethodPill.pillText(pillLabel), ArmeriaHttpMethodPill.textAttributes(route))
        append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        append(route.path, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        if (route.delegationMountPath.isNotEmpty()) {
            append(
                message("route.explorer.secondary.delegatedVia", route.delegationMountPath),
                SimpleTextAttributes.GRAYED_ATTRIBUTES,
            )
        } else {
            ArmeriaServletMountSupport.delegationKindOf(route)?.let { delegationKind ->
                append(
                    message("route.explorer.secondary.separator") +
                        ArmeriaRouteDetailFormatter.delegationBadge(delegationKind),
                    SimpleTextAttributes.GRAYED_ATTRIBUTES,
                )
            }
        }
    }
}
