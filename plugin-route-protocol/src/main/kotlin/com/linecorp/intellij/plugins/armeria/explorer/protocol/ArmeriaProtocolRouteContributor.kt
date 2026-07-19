package com.linecorp.intellij.plugins.armeria.explorer.protocol

import com.linecorp.intellij.plugins.armeria.explorer.support.RouteCollectContext
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteContributor

/**
 * Collects GraphQL, Thrift, and gRPC-proto routes. Wired in production via
 * `ArmeriaRouteAnalysisCollector` in `plugin-route-analysis`.
 */
object ArmeriaProtocolRouteContributor : RouteContributor {
    override fun collect(context: RouteCollectContext) {
        ArmeriaGraphqlRouteCollector.collect(context.project, context.scope, context.routes)
        ArmeriaThriftRouteCollector.collect(context.project, context.scope, context.routes)
    }

    override fun collectProtoOverlay(context: RouteCollectContext) {
        ArmeriaGrpcRouteCollector.collect(context.project, context.scope, context.routes)
    }
}
