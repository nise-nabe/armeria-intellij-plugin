package com.linecorp.intellij.plugins.armeria.explorer.support

/**
 * Language- or protocol-specific route source that contributes routes into a shared
 * [RouteCollectContext]. Implemented by collectors in `plugin-route-spring` and
 * `plugin-route-protocol`, wired together by `ArmeriaRouteCollector` in
 * `plugin-route-collectors` via [RouteContributorRegistry].
 */
fun interface RouteContributor {
    fun collect(context: RouteCollectContext)
}
