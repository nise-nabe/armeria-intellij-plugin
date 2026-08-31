package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaHttpMethodPill
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaRouteDetailFormatter
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaRouteTreeBuilder
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaRouteTreeLabel
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
            is ArmeriaRouteTreeBuilder.VirtualHostNode -> {
                append(
                    ArmeriaRouteTreeBuilder.virtualHostDisplayLabel(userObject),
                    SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES,
                )
            }
            is ArmeriaRouteTreeBuilder.RouteNode -> {
                if (ArmeriaRouteTreeBuilder.isPortBinding(userObject.route)) {
                    renderPort(userObject.route)
                } else {
                    renderRoute(userObject.route)
                }
            }
        }
    }

    private fun renderRoute(route: ArmeriaRoute) {
        val pillLabel = ArmeriaHttpMethodPill.pillLabel(route)
        toolTipText =
            buildString {
                append(route.methodLabel)
                append(' ')
                append(route.path)
                ArmeriaRouteTreeLabel.matchSuffix(route)?.let { suffix ->
                    append(" · ")
                    append(suffix)
                }
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
        ArmeriaRouteTreeLabel.matchSuffix(route)?.let { suffix ->
            append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append(suffix, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
        ArmeriaRouteDetailFormatter.secondaryDelegationText(route)?.let { secondary ->
            append(secondary, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }

    private fun renderPort(route: ArmeriaRoute) {
        val label = ArmeriaRouteTreeBuilder.portDisplayLabel(route)
        toolTipText = label
        append(label, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
}
