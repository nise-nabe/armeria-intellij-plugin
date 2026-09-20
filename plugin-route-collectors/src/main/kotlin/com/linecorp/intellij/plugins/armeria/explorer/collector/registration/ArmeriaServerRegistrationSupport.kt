package com.linecorp.intellij.plugins.armeria.explorer.collector.registration

import com.intellij.psi.PsiElement
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaServerRegistrationSupport {
    const val SERVER_LISTENER_METHOD = "serverListener"

    internal val LISTENER_FACTORY_METHODS: Set<String> = setOf("builder", "of")
    internal val SERVICE_NAME_BUILDER_METHODS: Set<String> = setOf("serviceName", "appName", "name")

    enum class DiscoveryRegistry(
        val listenerSimpleName: String,
        val listenerQualifiedName: String,
        val protocol: RouteProtocol,
    ) {
        ZOOKEEPER(
            "ZooKeeperUpdatingListener",
            "com.linecorp.armeria.server.zookeeper.ZooKeeperUpdatingListener",
            RouteProtocol.ZOOKEEPER,
        ),
        EUREKA(
            "EurekaUpdatingListener",
            "com.linecorp.armeria.server.eureka.EurekaUpdatingListener",
            RouteProtocol.EUREKA,
        ),
        CONSUL(
            "ConsulUpdatingListener",
            "com.linecorp.armeria.server.consul.ConsulUpdatingListener",
            RouteProtocol.CONSUL,
        ),
        ;

        fun presentableName(): String = protocol.presentableName()

        companion object {
            fun fromQualifiedName(name: String?): DiscoveryRegistry? = entries.firstOrNull { it.listenerQualifiedName == name }

            fun fromSimpleName(name: String?): DiscoveryRegistry? = entries.firstOrNull { it.listenerSimpleName == name }
        }
    }

    data class DiscoveryRegistration(
        val registry: DiscoveryRegistry,
        val serviceName: String,
        val registryUri: String,
    )

    internal fun discoveryRoute(
        element: PsiElement,
        registration: DiscoveryRegistration,
    ): ArmeriaRoute =
        ArmeriaRoute.create(
            element = element,
            protocol = registration.registry.presentableName(),
            httpMethod = "",
            path = registration.serviceName.ifBlank { message("route.explorer.discovery.unnamedService") },
            target =
                registration.registryUri.ifBlank {
                    message("route.explorer.discovery.unresolvedRegistry", registration.registry.presentableName())
                },
            routeMatch = RouteMatch.DISCOVERY,
            targetUnresolved = registration.registryUri.isBlank(),
            excludeFromDuplicateIndex = true,
        )
}
