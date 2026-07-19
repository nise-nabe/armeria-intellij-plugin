import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.date
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.jar.JarInputStream
import java.util.zip.ZipFile

plugins {
    id("com.linecorp.intellij.platform-plugin")
}

group = "com.linecorp.intellij"
version = providers.gradleProperty("pluginVersion").get()

dependencies {
    implementation(project(":plugin-route-analysis"))
    implementation(project(":plugin-wizard"))
    intellijPlatform {
        pluginComposedModule(implementation(project(":plugin-shared")))
        intellijIdeaUltimate(
            libs.versions.idea.platform
                .get(),
        )
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("org.jetbrains.plugins.yaml")
        testFramework(TestFrameworkType.Plugin.Java)
        testFramework(TestFrameworkType.Plugin.Java, configurationName = "testFixturesImplementation")
    }
    testFixturesImplementation(testFixtures(project(":plugin-route-collectors")))
    testFixturesImplementation(libs.junit4)
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            useJUnit(libs.versions.junit4.get())
            dependencies {
                implementation(testFixtures(project()))
                implementation(testFixtures(project(":plugin-route-collectors")))
                implementation(libs.velocity.engine.core)
                implementation(libs.junit4)
            }
        }
    }
}

tasks.named("buildPlugin") {
    val distributionZip =
        layout.buildDirectory.file(
            providers.gradleProperty("pluginVersion").map { "distributions/plugin-$it.zip" },
        )
    doLast {
        val distributionFile = distributionZip.get().asFile
        require(distributionFile.exists()) { "Missing plugin distribution: $distributionFile" }

        ZipFile(distributionFile).use { zip ->
            val mainJarEntry =
                zip
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .firstOrNull { name ->
                        name.startsWith("plugin/lib/plugin-") &&
                            name.endsWith(".jar") &&
                            !name.contains("route") &&
                            !name.contains("wizard") &&
                            !name.contains("shared")
                    }
                    ?: error("Main plugin JAR not found in $distributionFile")

            zip.getInputStream(zip.getEntry(mainJarEntry)).use { input ->
                JarInputStream(input).use { jar ->
                    val hasBundle =
                        generateSequence { jar.nextJarEntry }
                            .any { it.name == "com/linecorp/intellij/plugins/armeria/ArmeriaBundleKt.class" }
                    check(hasBundle) {
                        "Main plugin JAR ($mainJarEntry) must include ArmeriaBundleKt from plugin-shared; use pluginComposedModule"
                    }
                }
            }

            val hasSeparateSharedJar =
                zip
                    .entries()
                    .asSequence()
                    .any { it.name == "plugin/lib/plugin-shared.jar" }
            check(!hasSeparateSharedJar) {
                "plugin-shared must be composed into the main JAR, not packaged as plugin/lib/plugin-shared.jar"
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
        id = "com.linecorp.intellij.armeria-intellij-plugin"
        name = "Armeria"
        version = project.version.toString()
        vendor {
            name = "nise_nabe"
            email = "nise.nabe@gmail.com"
            url = "https://github.com/nise-nabe/armeria-intellij-plugin"
        }
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
