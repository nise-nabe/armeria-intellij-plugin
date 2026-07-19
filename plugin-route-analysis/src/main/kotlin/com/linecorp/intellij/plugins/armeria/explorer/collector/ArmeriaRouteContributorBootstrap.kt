package com.linecorp.intellij.plugins.armeria.explorer.collector

import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaProtocolRouteContributor
import com.linecorp.intellij.plugins.armeria.explorer.spring.ArmeriaSpringRouteContributor
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteContributorRegistry

/**
 * Sets up [RouteContributorRegistry.bootstrap] so that Spring and protocol contributors are
 * registered on the first call to [RouteContributorRegistry.all]. This object must be
 * referenced before [ArmeriaRouteCollector.collect] is first called — see
 * [com.linecorp.intellij.plugins.armeria.explorer.duplicate.ArmeriaRouteDuplicateIndex] and
 * [com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteExplorerPanel].
 */
object ArmeriaRouteContributorBootstrap {
    init {
        RouteContributorRegistry.bootstrap = {
            RouteContributorRegistry.register(ArmeriaSpringRouteContributor)
            RouteContributorRegistry.register(ArmeriaProtocolRouteContributor)
        }
    }
}
