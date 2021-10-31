@file:Suppress("UnstableApiUsage")

package com.nisecoder.intellij.plugins.armeria.module

import com.intellij.ide.starters.local.Library
import com.intellij.ide.starters.local.LibraryCategory
import com.intellij.ide.starters.shared.LibraryLink
import com.intellij.ide.starters.shared.LibraryLinkType
import com.nisecoder.intellij.plugins.armeria.ArmeriaIcons
import com.nisecoder.intellij.plugins.armeria.message


val ArmeriaCategory = LibraryCategory(
    id = "armeria",
    icon = ArmeriaIcons.Armeria,
    title = message("module.category.armeria.title"),
    description = message("module.category.armeria.description"),
)


fun armeriaLibrary(
    id: String,
    title: String,
    description: String,
    links: List<LibraryLink> = emptyList()
) = Library(
    id = id,
    icon = null,
    title = title,
    description = description,
    group = "com.linecorp.armeria",
    artifact = id,
    category = ArmeriaCategory,
    links = links,
)


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

val ArmeriaKafka = armeriaLibrary(
    id = "armeria-kafka",
    title = message("module.library.armeria.kafka.title"),
    description = message("module.library.armeria.kafka.description"),
)

val ArmeriaKotlin = armeriaLibrary(
    id = "armeria-kotlin",
    title = message("module.library.armeria.kotlin.title"),
    description = message("module.library.armeria.kotlin.description"),
    links = listOf(
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/server-annotated-service#kotlin-coroutines-support",
            title = message("armeria.website.kotlin.coroutine.title")
        ),
    )
)

val ArmeriaLogback = armeriaLibrary(
    id = "armeria-logback",
    title = message("module.library.armeria.logback.title"),
    description = message("module.library.armeria.logback.description"),
    links = listOf(
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/advanced-logging",
            title = message("armeria.website.logback.title")
        ),
    ),
)

val ArmeriaProtobuf = armeriaLibrary(
    id = "armeria-protobuf",
    title = message("module.library.armeria.protobuf.title"),
    description = message("module.library.armeria.protobuf.description"),
)

val ArmeriaRetrofit = armeriaLibrary(
    id = "armeria-retrofit2",
    title = message("module.library.armeria.retrofit.title"),
    description = message("module.library.armeria.retrofit.description"),
    links = listOf(
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/client-retrofit",
            title = message("armeria.website.retrofit.title")
        ),
    ),
)

val ArmeriaRxJava3 = armeriaLibrary(
    id = "armeria-rxjava3",
    title = message("module.library.armeria.rxjava.title"),
    description = message("module.library.armeria.rxjava.description"),
)

val ArmeriaSaml = armeriaLibrary(
    id = "armeria-saml",
    title = message("module.library.armeria.saml.title"),
    description = message("module.library.armeria.saml.description"),
)

val ArmeriaScalaPB2_12 = armeriaLibrary(
    id = "armeria-scalapb_2.12",
    title = message("module.library.armeria.scalapb212.title"),
    description = message("module.library.armeria.scalapb212.description"),
)
val ArmeriaScalaPB2_13 = armeriaLibrary(
    id = "armeria-scalapb_2.13",
    title = message("module.library.armeria.scalapb213.title"),
    description = message("module.library.armeria.scalapb213.description"),
)

val ArmeriaSpringBoot2 = armeriaLibrary(
    id = "armeria-spring-boot2-autoconfigure",
    title = message("module.library.armeria.spring-boot2.title"),
    description = message("module.library.armeria.spring-boot2.description"),
)
val ArmeriaSpringBoot2WebFlux = armeriaLibrary(
    id = "armeria-spring-boot2-webflux-autoconfigure",
    title = message("module.library.armeria.spring-boot2-webflux.title"),
    description = message("module.library.armeria.spring-boot2-webflux.description"),
    links = listOf(
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/advanced-spring-webflux-integration",
            title = message("armeria.website.spring-boto2-webflux.title"),
        ),
    ),
)

val ArmeriaThrift0_13 = armeriaLibrary(
    id = "armeria-thrift0.13",
    title = message("module.library.armeria.thrift.title"),
    description = message("module.library.armeria.thrift.description"),
    links = listOf(
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/server-thrift",
            title = message("armeria.website.thrift.server.title"),
        ),
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/client-thrift",
            title = message("armeria.website.thrift.client.title"),
        ),
    ),
)

val ArmeriaTomcat = armeriaLibrary(
    id = "armeria-tomcat9",
    title = message("module.library.armeria.tomcat.title"),
    description = message("module.library.armeria.tomcat.description"),
    links = listOf(
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/server-servlet",
            title = message("armeria.website.servlet.title"),
        ),
    ),
)

val ArmeriaZookeeper = armeriaLibrary(
    id = "armeria-zookeeper3",
    title = message("module.library.armeria.zookeeper.title"),
    description = message("module.library.armeria.zookeeper.description"),
    links = listOf(
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/client-service-discovery#zookeeper-based-service-discovery-with-zookeeperendpointgroup",
            title = message("armeria.website.zookeeper.service-discovery.title")
        ),
        LibraryLink(
            type = LibraryLinkType.GUIDE,
            url = "https://armeria.dev/docs/server-service-registration#zookeeper-based-service-registration-with-zookeeperupdatinglistener",
            title = message("armeria.website.zookeeper.service-register.title")
        ),
    ),
)
