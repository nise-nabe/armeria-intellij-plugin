package com.linecorp.intellij.plugins.armeria.module

/**
 * All optional Armeria libraries exposed in the New Project Wizard (server + client starters).
 */
val armeriaWizardLibraryIds: List<String> =
    listOf(
        ArmeriaBrave,
        ArmeriaBucket4j,
        ArmeriaDropwizard2,
        ArmeriaEureka,
        ArmeriaGraphql,
        ArmeriaGrpc,
        ArmeriaJetty,
        ArmeriaJetty11,
        ArmeriaKafka,
        ArmeriaKotlin,
        ArmeriaLogback,
        ArmeriaLogback12,
        ArmeriaPrometheus1,
        ArmeriaProtobuf,
        ArmeriaRetrofit,
        ArmeriaRxJava3,
        ArmeriaSaml,
        ArmeriaScala212,
        ArmeriaScala213,
        ArmeriaScala3,
        ArmeriaScalaPB2_12,
        ArmeriaScalaPB2_13,
        ArmeriaScalaPB3,
        ArmeriaSpringBoot2,
        ArmeriaSpringBoot2WebFlux,
        ArmeriaSpringBoot3,
        ArmeriaSpringBoot3Starter,
        ArmeriaSpringBoot3WebFlux,
        ArmeriaSpringBoot3WebFluxStarter,
        ArmeriaThrift0_13,
        ArmeriaThrift0_18,
        ArmeriaTomcat8,
        ArmeriaTomcat,
        ArmeriaTomcat10,
        ArmeriaZookeeper,
    ).map { it.id }.sorted()
