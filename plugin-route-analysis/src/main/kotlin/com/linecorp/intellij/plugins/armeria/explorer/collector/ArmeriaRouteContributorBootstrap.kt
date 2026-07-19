package com.linecorp.intellij.plugins.armeria.explorer.collector

import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaProtocolRouteContributor
import com.linecorp.intellij.plugins.armeria.explorer.spring.ArmeriaSpringRouteContributor
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteContributorRegistry

/**
 * Sets up [RouteContributorRegistry.bootstrap] so that Spring and protocol contributors are
 * registered on the first call to [RouteContributorRegistry.all]. Every production caller of
 * [ArmeriaRouteCollector.collect] must invoke [ensureRegistered] first (or otherwise load this
 * object) so the bootstrap lambda is installed before collection.
 */
object ArmeriaRouteContributorBootstrap {
    init {
        RouteContributorRegistry.bootstrap = {
            RouteContributorRegistry.register(ArmeriaSpringRouteContributor)
            RouteContributorRegistry.register(ArmeriaProtocolRouteContributor)
        }
    }

    /** Installs the bootstrap lambda (via class init) so the next [RouteContributorRegistry.all] registers contributors. */
    fun ensureRegistered() {
        // Intentionally empty: referencing this object runs [init] above.
    }
}
