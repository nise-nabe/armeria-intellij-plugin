package com.linecorp.intellij.plugins.armeria.module

import com.intellij.ide.starters.local.StarterModuleBuilder

/**
 * Minimal stand-in for [com.intellij.ide.starters.local.GeneratorContext] used by J2EE file templates.
 */
class ArmeriaWizardTemplateTestContext(
    val group: String = "com.example",
    val artifact: String = "demo",
    val version: String = "0.0.1-SNAPSHOT",
    val moduleName: String = "demo",
    val language: String = "kotlin",
    val testRunnerId: String = "junit5",
    private val libraries: Set<String> = emptySet(),
    private val versions: Map<Pair<String, String>, String> = defaultVersions(),
) {
    val rootPackage: String = StarterModuleBuilder.suggestPackageName(group, artifact)

    fun hasLibrary(libraryId: String): Boolean = libraryId in libraries

    fun hasLanguage(languageId: String): Boolean = language == languageId

    fun getVersion(groupId: String, artifactId: String): String =
        versions[groupId to artifactId] ?: "UNVERSIONED"

    companion object {
        fun defaultVersions(): Map<Pair<String, String>, String> = mapOf(
            "org.jetbrains.kotlin" to "kotlin-bom" to "2.1.21",
            "com.linecorp.armeria" to "armeria-bom" to "1.38.0",
            "ch.qos.logback" to "logback-classic" to "1.5.18",
            "org.slf4j" to "log4j-over-slf4j" to "2.0.17",
            "org.junit" to "junit-bom" to "5.14.0",
            "junit" to "junit" to "4.13.2",
        )
    }
}
