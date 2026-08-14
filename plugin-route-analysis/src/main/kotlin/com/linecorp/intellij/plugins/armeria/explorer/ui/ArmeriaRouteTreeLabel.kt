package com.linecorp.intellij.plugins.armeria.explorer.ui

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaRouteTreeLabel {
    fun headerMatchSuffix(route: ArmeriaRoute): String? {
        val hints = route.contentHints.filter(::isHeaderMatchHint)
        if (hints.isEmpty()) {
            return null
        }
        return hints.joinToString(" · ")
    }

    private fun isHeaderMatchHint(hint: String): Boolean {
        val marker = "\u0001"
        val sample = message("route.explorer.hint.matchesHeader", marker)
        val prefix = sample.substringBefore(marker)
        val suffix = sample.substringAfter(marker)
        return hint.startsWith(prefix) && hint.endsWith(suffix)
    }
}
