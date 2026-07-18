@file:Suppress("UnstableApiUsage")

package com.linecorp.intellij.plugins.armeria.module

import com.intellij.ide.starters.shared.LibraryLink
import com.intellij.ide.starters.shared.LibraryLinkType
import com.linecorp.intellij.plugins.armeria.message

val ArmeriaKafka =
    armeriaLibrary(
        id = "armeria-kafka",
        title = message("module.library.armeria.kafka.title"),
        description = message("module.library.armeria.kafka.description"),
    )

val ArmeriaKotlin =
    armeriaLibrary(
        id = "armeria-kotlin",
        title = message("module.library.armeria.kotlin.title"),
        description = message("module.library.armeria.kotlin.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/server-annotated-service#kotlin-coroutines-support",
                    title = message("armeria.website.kotlin.coroutine.title"),
                ),
            ),
    )

val ArmeriaLogback =
    armeriaLibrary(
        id = "armeria-logback",
        title = message("module.library.armeria.logback.title"),
        description = message("module.library.armeria.logback.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/advanced-logging",
                    title = message("armeria.website.logback.title"),
                ),
            ),
    )

val ArmeriaLogback12 =
    armeriaLibrary(
        id = "armeria-logback12",
        title = message("module.library.armeria.logback12.title"),
        description = message("module.library.armeria.logback12.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/advanced-logging",
                    title = message("armeria.website.logback.title"),
                ),
            ),
    )

val ArmeriaProtobuf =
    armeriaLibrary(
        id = "armeria-protobuf",
        title = message("module.library.armeria.protobuf.title"),
        description = message("module.library.armeria.protobuf.description"),
    )

val ArmeriaPrometheus1 =
    armeriaLibrary(
        id = "armeria-prometheus1",
        title = message("module.library.armeria.prometheus1.title"),
        description = message("module.library.armeria.prometheus1.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/monitoring-metrics",
                    title = message("armeria.website.prometheus.title"),
                ),
            ),
    )

val ArmeriaRetrofit =
    armeriaLibrary(
        id = "armeria-retrofit2",
        title = message("module.library.armeria.retrofit.title"),
        description = message("module.library.armeria.retrofit.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/client-retrofit",
                    title = message("armeria.website.retrofit.title"),
                ),
            ),
    )

val ArmeriaRxJava3 =
    armeriaLibrary(
        id = "armeria-rxjava3",
        title = message("module.library.armeria.rxjava.title"),
        description = message("module.library.armeria.rxjava.description"),
    )

val ArmeriaSaml =
    armeriaLibrary(
        id = "armeria-saml",
        title = message("module.library.armeria.saml.title"),
        description = message("module.library.armeria.saml.description"),
    )

val ArmeriaScala212 =
    armeriaLibrary(
        id = "armeria-scala_2.12",
        title = message("module.library.armeria.scala212.title"),
        description = message("module.library.armeria.scala212.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/advanced-scala",
                    title = message("armeria.website.scala.title"),
                ),
            ),
    )

val ArmeriaScala213 =
    armeriaLibrary(
        id = "armeria-scala_2.13",
        title = message("module.library.armeria.scala213.title"),
        description = message("module.library.armeria.scala213.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/advanced-scala",
                    title = message("armeria.website.scala.title"),
                ),
            ),
    )

val ArmeriaScala3 =
    armeriaLibrary(
        id = "armeria-scala_3",
        title = message("module.library.armeria.scala3.title"),
        description = message("module.library.armeria.scala3.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/advanced-scala",
                    title = message("armeria.website.scala.title"),
                ),
            ),
    )

val ArmeriaScalaPB2_12 =
    armeriaLibrary(
        id = "armeria-scalapb_2.12",
        title = message("module.library.armeria.scalapb212.title"),
        description = message("module.library.armeria.scalapb212.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/advanced-scalapb",
                    title = message("armeria.website.scalapb.title"),
                ),
            ),
    )
val ArmeriaScalaPB2_13 =
    armeriaLibrary(
        id = "armeria-scalapb_2.13",
        title = message("module.library.armeria.scalapb213.title"),
        description = message("module.library.armeria.scalapb213.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/advanced-scalapb",
                    title = message("armeria.website.scalapb.title"),
                ),
            ),
    )

val ArmeriaScalaPB3 =
    armeriaLibrary(
        id = "armeria-scalapb_3",
        title = message("module.library.armeria.scalapb3.title"),
        description = message("module.library.armeria.scalapb3.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/advanced-scalapb",
                    title = message("armeria.website.scalapb.title"),
                ),
            ),
    )

val ArmeriaSpringBoot2 =
    armeriaLibrary(
        id = "armeria-spring-boot2-autoconfigure",
        title = message("module.library.armeria.spring-boot2.title"),
        description = message("module.library.armeria.spring-boot2.description"),
    )
val ArmeriaSpringBoot2WebFlux =
    armeriaLibrary(
        id = "armeria-spring-boot2-webflux-autoconfigure",
        title = message("module.library.armeria.spring-boot2-webflux.title"),
        description = message("module.library.armeria.spring-boot2-webflux.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/advanced-spring-webflux-integration",
                    title = message("armeria.website.spring-boot2-webflux.title"),
                ),
            ),
    )

val ArmeriaSpringBoot3 =
    armeriaLibrary(
        id = "armeria-spring-boot3-autoconfigure",
        title = message("module.library.armeria.spring-boot3.title"),
        description = message("module.library.armeria.spring-boot3.description"),
    )

val ArmeriaSpringBoot3Starter =
    armeriaLibrary(
        id = "armeria-spring-boot3-starter",
        title = message("module.library.armeria.spring-boot3-starter.title"),
        description = message("module.library.armeria.spring-boot3-starter.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/setup",
                    title = message("armeria.website.setup.title"),
                ),
            ),
    )

val ArmeriaSpringBoot3WebFlux =
    armeriaLibrary(
        id = "armeria-spring-boot3-webflux-autoconfigure",
        title = message("module.library.armeria.spring-boot3-webflux.title"),
        description = message("module.library.armeria.spring-boot3-webflux.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/advanced-spring-webflux-integration",
                    title = message("armeria.website.spring-boot3-webflux.title"),
                ),
            ),
    )

val ArmeriaSpringBoot3WebFluxStarter =
    armeriaLibrary(
        id = "armeria-spring-boot3-webflux-starter",
        title = message("module.library.armeria.spring-boot3-webflux-starter.title"),
        description = message("module.library.armeria.spring-boot3-webflux-starter.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/advanced-spring-webflux-integration",
                    title = message("armeria.website.spring-boot3-webflux.title"),
                ),
            ),
    )

val ArmeriaThrift0_13 =
    armeriaLibrary(
        id = "armeria-thrift0.13",
        title = message("module.library.armeria.thrift.title"),
        description = message("module.library.armeria.thrift.description"),
        links =
            listOf(
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

val ArmeriaThrift0_18 =
    armeriaLibrary(
        id = "armeria-thrift0.18",
        title = message("module.library.armeria.thrift018.title"),
        description = message("module.library.armeria.thrift018.description"),
        links =
            listOf(
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

val ArmeriaTomcat8 =
    armeriaLibrary(
        id = "armeria-tomcat8",
        title = message("module.library.armeria.tomcat8.title"),
        description = message("module.library.armeria.tomcat8.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/server-servlet",
                    title = message("armeria.website.servlet.title"),
                ),
            ),
    )

val ArmeriaTomcat =
    armeriaLibrary(
        id = "armeria-tomcat9",
        title = message("module.library.armeria.tomcat.title"),
        description = message("module.library.armeria.tomcat.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/server-servlet",
                    title = message("armeria.website.servlet.title"),
                ),
            ),
    )

val ArmeriaTomcat10 =
    armeriaLibrary(
        id = "armeria-tomcat10",
        title = message("module.library.armeria.tomcat10.title"),
        description = message("module.library.armeria.tomcat10.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/server-servlet",
                    title = message("armeria.website.servlet.title"),
                ),
            ),
    )

val ArmeriaZookeeper =
    armeriaLibrary(
        id = "armeria-zookeeper3",
        title = message("module.library.armeria.zookeeper.title"),
        description = message("module.library.armeria.zookeeper.description"),
        links =
            listOf(
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url = "https://armeria.dev/docs/client-service-discovery#zookeeper-based-service-discovery-with-zookeeperendpointgroup",
                    title = message("armeria.website.zookeeper.service-discovery.title"),
                ),
                LibraryLink(
                    type = LibraryLinkType.GUIDE,
                    url =
                        "https://armeria.dev/docs/server-service-registration" +
                            "#zookeeper-based-service-registration-with-zookeeperupdatinglistener",
                    title = message("armeria.website.zookeeper.service-register.title"),
                ),
            ),
    )
