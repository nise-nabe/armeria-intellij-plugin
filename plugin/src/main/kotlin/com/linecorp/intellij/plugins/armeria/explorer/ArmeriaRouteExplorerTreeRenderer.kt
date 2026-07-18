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
            ArmeriaRouteDetailFormatter.tooltipDelegationSuffix(route)?.let { suffix ->
                append(" (")
                append(suffix)
                append(')')
            }
        }
        append(ArmeriaHttpMethodPill.pillText(pillLabel), ArmeriaHttpMethodPill.textAttributes(route))
        append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        append(route.path, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        ArmeriaRouteDetailFormatter.secondaryDelegationText(route)?.let { secondary ->
            append(secondary, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }
}
