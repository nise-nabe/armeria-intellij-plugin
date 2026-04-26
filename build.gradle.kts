import org.jetbrains.changelog.Changelog
import org.jetbrains.gradle.ext.packagePrefix
import org.jetbrains.gradle.ext.settings
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
    id("org.jetbrains.gradle.plugin.idea-ext")
    `maven-publish`
}

group = "com.linecorp.intellij"

idea {
    module {
        settings {
            packagePrefix["src/main/kotlin"] = "com.linecorp.intellij.plugins.armeria"
            packagePrefix["src/test/kotlin"] = "com.linecorp.intellij.plugins.armeria"
        }
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2026.1.1")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.gradle")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        @Suppress("UnstableApiUsage")
        vendor = JvmVendorSpec.JETBRAINS
    }
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        val changelog = project.changelog
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
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

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            targets.all {
                testTask.configure {
                    failOnNoDiscoveredTests = false
                }
            }
        }
    }
}
