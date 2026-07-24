package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.openapi.util.registry.Registry
import java.util.MissingResourceException

/**
 * Shared gate for gRPC proto route discovery (`armeria.grpc.proto.routes.enabled` in plugin.xml).
 *
 * Defaults to enabled when the registry key is absent (e.g. in lightweight test environments).
 * The registry value is checked outside route caches so toggling the kill-switch takes effect
 * immediately without waiting for PSI or project-root invalidation.
 */
object ArmeriaProtoRouteDiscoverySupport {
    fun isEnabled(): Boolean =
        try {
            Registry.`is`("armeria.grpc.proto.routes.enabled")
        } catch (_: MissingResourceException) {
            true
        }
}
