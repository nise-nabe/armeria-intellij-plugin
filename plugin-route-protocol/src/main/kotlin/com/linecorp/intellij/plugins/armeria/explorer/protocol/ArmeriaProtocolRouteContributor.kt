package com.linecorp.intellij.plugins.armeria.explorer.protocol

import com.linecorp.intellij.plugins.armeria.explorer.support.RouteCollectContext
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteContributor

/**
 * Collects GraphQL, Thrift, and gRPC-proto routes. Registered in production via
 * `ArmeriaRouteContributorBootstrap` in `plugin-route-analysis`.
 *
 * - When [RouteCollectContext.includeProtoRoutes] is false: collects GraphQL and Thrift routes
 *   (the normal cached-collection pass).
 * - When [RouteCollectContext.includeProtoRoutes] is true: collects gRPC-proto routes only
 *   (the proto-overlay pass that runs on top of already-cached routes).
 */
object ArmeriaProtocolRouteContributor : RouteContributor {
    override fun collect(context: RouteCollectContext) {
        if (context.includeProtoRoutes) {
            ArmeriaGrpcRouteCollector.collect(context.project, context.scope, context.routes)
        } else {
            ArmeriaGraphqlRouteCollector.collect(context.project, context.scope, context.routes)
            ArmeriaThriftRouteCollector.collect(context.project, context.scope, context.routes)
        }
    }
}
