package com.linecorp.intellij.plugins.armeria.explorer.ui

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute

object ArmeriaRouteTreeLabel {
    fun headerMatchSuffix(route: ArmeriaRoute): String? {
        val hints =
            route.contentHints.filter { hint ->
                ArmeriaRouteContentHintSupport.isHint(hint, "route.explorer.hint.matchesHeader")
            }
        if (hints.isEmpty()) {
            return null
        }
        return hints.joinToString(" · ")
    }
}
