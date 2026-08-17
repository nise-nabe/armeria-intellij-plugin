package com.linecorp.intellij.plugins.armeria.explorer.ui

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute

object ArmeriaRouteTreeLabel {
    fun matchSuffix(route: ArmeriaRoute): String? {
        val hints =
            route.contentHints.filter { hint ->
                ArmeriaRouteContentHintSupport.isHint(hint, "route.explorer.hint.matchesHeader") ||
                    ArmeriaRouteContentHintSupport.isHint(hint, "route.explorer.hint.matchesParam")
            }
        if (hints.isEmpty()) {
            return null
        }
        return hints.joinToString(" · ")
    }
}
