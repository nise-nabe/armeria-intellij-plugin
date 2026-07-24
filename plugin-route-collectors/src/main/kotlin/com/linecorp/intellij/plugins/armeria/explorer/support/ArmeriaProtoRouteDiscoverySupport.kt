package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.openapi.util.registry.Registry
import java.util.MissingResourceException

object ArmeriaProtoRouteDiscoverySupport {
    fun isEnabled(): Boolean =
        try {
            Registry.`is`("armeria.grpc.proto.routes.enabled")
        } catch (_: MissingResourceException) {
            true
        }
}
