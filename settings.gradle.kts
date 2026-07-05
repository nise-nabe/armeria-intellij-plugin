import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.17.0"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}

rootProject.name = "armeria-intellij-plugin"
includeBuild("build-logic")
include("plugin-shared")
include("plugin-route-analysis")
include("plugin-wizard")
include("plugin")
