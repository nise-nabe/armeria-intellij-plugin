package com.linecorp.intellij.plugins.armeria.explorer.support

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lazy registry of [RouteContributor] instances. Spring and protocol contributors are
 * registered via [bootstrap], which is set by `ArmeriaRouteContributorBootstrap` in
 * `plugin-route-analysis` before the first collection call. Tests register contributors
 * explicitly with [register] after calling [clearForTests].
 */
object RouteContributorRegistry {
    private val contributors = CopyOnWriteArrayList<RouteContributor>()

    /** Set once by analysis bootstrap; called on every [all] invocation (no-op after first use). */
    @Volatile
    var bootstrap: () -> Unit = {}

    fun register(contributor: RouteContributor) {
        if (contributors.none { it === contributor }) {
            contributors += contributor
        }
    }

    fun all(): List<RouteContributor> {
        bootstrap()
        return contributors.toList()
    }

    /** Test-only: clear registered contributors so each test starts from a known state. */
    fun clearForTests() {
        contributors.clear()
    }
}
