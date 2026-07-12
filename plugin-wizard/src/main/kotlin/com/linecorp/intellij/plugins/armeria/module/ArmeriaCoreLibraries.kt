@file:Suppress("UnstableApiUsage")

package com.linecorp.intellij.plugins.armeria.module

import com.intellij.ide.starters.shared.LibraryLink
import com.intellij.ide.starters.shared.LibraryLinkType
import com.linecorp.intellij.plugins.armeria.message

val ArmeriaBrave = armeriaLibrary(
    id = "armeria-brave",
    title = message("module.library.armeria.brave.title"),
    description = message("module.library.armeria.brave.description"),
    links = listOf(
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/advanced-zipkin/",
            title = message("armeria.website.zipkin.title"),
        ),
    ),
)

val ArmeriaBucket4j = armeriaLibrary(
    id = "armeria-bucket4j",
    title = message("module.library.armeria.bucket4j.title"),
    description = message("module.library.armeria.bucket4j.description"),
    links = listOf(
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/setup",
            title = message("armeria.website.bucket4j.title"),
        ),
    ),
)

val ArmeriaDropwizard2 = armeriaLibrary(
    id = "armeria-dropwizard2",
    title = message("module.library.armeria.dropwizard2.title"),
    description = message("module.library.armeria.dropwizard2.description"),
    links = listOf(
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/advanced-dropwizard-integration/",
            title = message("armeria.website.dropwizard.title")
        ),
    ),
)

val ArmeriaEureka = armeriaLibrary(
    id = "armeria-eureka",
    title = message("module.library.armeria.eureka.title"),
    description = message("module.library.armeria.eureka.description"),
    links = listOf(
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/server-service-registration/#eureka-based-service-registration-with-eurekaupdatinglistener",
            title = message("armeria.website.eureka.service-discovery.title")
        ),
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/server-service-registration/#eureka-based-service-registration-with-eurekaupdatinglistener",
            title = message("armeria.website.eureka.service-register.title")
        ),
    )
)

val ArmeriaGraphql = armeriaLibrary(
    id = "armeria-graphql",
    title = message("module.library.armeria.graphql.title"),
    description = message("module.library.armeria.graphql.description"),
    links = listOf(
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/server-graphql",
            title = message("armeria.website.graphql.title")
        ),
    ),
)

val ArmeriaGrpc = armeriaLibrary(
    id = "armeria-grpc",
    title = message("module.library.armeria.grpc.title"),
    description = message("module.library.armeria.grpc.description"),
    links = listOf(
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/server-grpc",
            title = message("armeria.website.grpc.server.title")
        ),
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/client-grpc",
            title = message("armeria.website.grpc.client.title")
        ),
    )
)

val ArmeriaJetty = armeriaLibrary(
    id = "armeria-jetty9",
    title = message("module.library.armeria.jetty.title"),
    description = message("module.library.armeria.jetty.description"),
    links = listOf(
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/server-servlet",
            title = message("armeria.website.servlet.title")
        ),
    )
)

val ArmeriaJetty11 = armeriaLibrary(
    id = "armeria-jetty11",
    title = message("module.library.armeria.jetty11.title"),
    description = message("module.library.armeria.jetty11.description"),
    links = listOf(
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/server-servlet",
            title = message("armeria.website.servlet.title")
        ),
    )
)

