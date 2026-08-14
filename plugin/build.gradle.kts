import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.date
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

plugins {
    id("com.linecorp.intellij.platform-plugin")
}

group = "com.linecorp.intellij"
version = providers.gradleProperty("pluginVersion").get()

dependencies {
    intellijPlatform {
        pluginComposedModule(implementation(project(":plugin-shared")))
        pluginComposedModule(implementation(project(":plugin-wizard")))
        pluginComposedModule(implementation(project(":plugin-route-model")))
        pluginComposedModule(implementation(project(":plugin-route-collectors")))
        pluginComposedModule(implementation(project(":plugin-route-spring")))
        pluginComposedModule(implementation(project(":plugin-route-protocol")))
        pluginComposedModule(implementation(project(":plugin-route-analysis")))
        intellijIdeaUltimate(
            libs.versions.idea.platform
                .get(),
        )
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("com.intellij.properties")
        bundledPlugin("idea.plugin.protoeditor")
        testFramework(TestFrameworkType.Plugin.Java)
        testFramework(TestFrameworkType.Plugin.Java, configurationName = "testFixturesImplementation")
        testFramework(TestFrameworkType.JUnit5)
    }
    testFixturesImplementation(testFixtures(project(":plugin-route-collectors")))
    testFixturesImplementation(libs.junit4)
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            dependencies {
                implementation(testFixtures(project()))
                implementation(testFixtures(project(":plugin-route-collectors")))
                implementation(libs.velocity.engine.core)
                implementation(libs.junit4)
            }
        }
    }
}

changelog {
    version.set(providers.gradleProperty("pluginVersion"))
    path.set(file("CHANGELOG.md").canonicalPath)
    header.set(provider { "[${version.get()}] - ${date()}" })
    unreleasedTerm.set("[Unreleased]")
    keepUnreleasedSection.set(true)
    groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
}

intellijPlatform {
    pluginConfiguration {
        id = "com.linecorp.armeria"
        name = "Armeria"
        version = project.version.toString()
        vendor {
            name = "nise_nabe"
            email = "nise.nabe@gmail.com"
            url = "https://github.com/nise-nabe/armeria-intellij-plugin"
        }
        description =
            """
            IntelliJ IDEA plugin for the Armeria microservice framework.
            Discover annotated services and client calls, inspect duplicate routes,
            generate Armeria projects, and run Armeria servers from the IDE.
            """.trimIndent()
        val changelog = project.changelog
        changeNotes =
            providers.gradleProperty("pluginVersion").map { pluginVersion ->
                with(changelog) {
                    renderItem(
                        (getOrNull(pluginVersion) ?: getUnreleased())
                            .withHeader(false)
                            .withEmptySections(false),
                        Changelog.OutputType.HTML,
                    )
                }
            }
    }
}

tasks.register("verifyPluginPackaging") {
    group = "verification"
    description =
        "Fails if the plugin ZIP lib/ contains Kotlin stdlib, JetBrains annotations, or sibling module JARs."
    dependsOn("buildPlugin")
    val zipFile =
        layout.buildDirectory.file(
            providers.gradleProperty("pluginVersion").map { version ->
                "distributions/plugin-$version.zip"
            },
        )
    val expectedJarName =
        providers.gradleProperty("pluginVersion").map { version ->
            "plugin-$version.jar"
        }
    inputs.file(zipFile)
    doLast {
        val zipPath = zipFile.get().asFile
        check(zipPath.isFile) { "Plugin ZIP not found: $zipPath" }
        val expectedJar = expectedJarName.get()
        ZipFile(zipPath).use { zip ->
            val jarEntries =
                zip
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".jar") }
                    .toList()
            check(jarEntries.size == 1 && jarEntries.single().endsWith("/$expectedJar")) {
                "Expected a single lib/$expectedJar, found $jarEntries in $zipPath"
            }
            val pluginJar =
                checkNotNull(zip.getEntry(jarEntries.single())) {
                    "ZIP entry missing: ${jarEntries.single()}"
                }
            val descriptor =
                zip.getInputStream(pluginJar).use { jarStream ->
                    ZipInputStream(jarStream).use { nested ->
                        generateSequence { nested.nextEntry }
                            .firstOrNull { it.name == "META-INF/plugin.xml" }
                            ?.let { nested.readBytes().toString(Charsets.UTF_8) }
                    }
                }
            check(!descriptor.isNullOrBlank()) {
                "META-INF/plugin.xml missing from $expectedJar"
            }
            check("<id>com.linecorp.armeria</id>" in descriptor) {
                "Patched plugin.xml must use id com.linecorp.armeria"
            }
            val description =
                Regex(
                    "<description(?:\\s[^>]*)?>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</description>",
                    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
                ).find(descriptor)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
            check(!description.isNullOrBlank()) {
                "Patched plugin.xml is missing a non-empty <description>"
            }
        }
    }
}

tasks.named("check") {
    dependsOn("verifyPluginPackaging")
}
