package com.linecorp.intellij.plugins.armeria.explorer.support

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lazy registry of [RouteContributor] instances. Spring and protocol contributors are
 * registered via [bootstrap], which is set by `ArmeriaRouteContributorBootstrap` in
 * `plugin-route-analysis` before the first collection call. Tests register contributors
 * explicitly with [register] after calling [clearForTests].
 */
object RouteContributorRegistry {
    private val lock = Any()
    private val contributors = CopyOnWriteArrayList<RouteContributor>()

    @Volatile
    private var bootstrapped = false

    /** Set once by analysis bootstrap; invoked at most once on the first [all] call. */
    @Volatile
    var bootstrap: () -> Unit = {}

    fun register(contributor: RouteContributor) {
        synchronized(lock) {
            if (contributors.none { it === contributor }) {
                contributors += contributor
            }
        }
    }

    fun all(): List<RouteContributor> {
        if (!bootstrapped) {
            synchronized(lock) {
                if (!bootstrapped) {
                    bootstrap()
                    bootstrapped = true
                }
            }
        }
        return contributors.toList()
    }

    /** Test-only: clear registered contributors so each test starts from a known state. */
    fun clearForTests() {
        synchronized(lock) {
            contributors.clear()
            bootstrapped = false
        }
    }
}
