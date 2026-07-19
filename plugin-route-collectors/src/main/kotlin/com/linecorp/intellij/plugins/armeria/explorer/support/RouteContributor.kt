package com.linecorp.intellij.plugins.armeria.explorer.support

/**
 * Language- or protocol-specific route source that contributes routes into a shared
 * [RouteCollectContext]. Implemented by collectors in `plugin-route-spring` and
 * `plugin-route-protocol`, and passed explicitly into [com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector.collect].
 *
 * Production wiring lives in `plugin-route-analysis` (`ArmeriaRouteAnalysisCollector`), which
 * always supplies the Spring and protocol contributors. Tests pass only the contributors they need.
 */
interface RouteContributor {
    /** Contributes routes for the normal (cached) collection pass. */
    fun collect(context: RouteCollectContext)

    /** Contributes proto/gRPC overlay routes on top of already-cached base routes. Default: no-op. */
    fun collectProtoOverlay(context: RouteCollectContext) {}
}
