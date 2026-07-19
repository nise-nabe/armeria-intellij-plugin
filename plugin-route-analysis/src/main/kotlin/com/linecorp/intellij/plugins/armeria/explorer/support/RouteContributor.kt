package com.linecorp.intellij.plugins.armeria.explorer.support

internal fun interface RouteContributor {
    fun collect(context: RouteCollectContext)
}
