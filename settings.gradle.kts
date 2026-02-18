import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "armeria-intellij-plugin"


pluginManagement {
    repositories {
        gradlePluginPortal()
    }

    plugins {
        kotlin("jvm") version "2.3.10"
        id("org.jetbrains.intellij.platform") version "2.11.0"
        id("org.jetbrains.gradle.plugin.idea-ext") version "1.4.1"
        id("org.jetbrains.changelog") version "2.5.0"
    }
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.11.0"
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
