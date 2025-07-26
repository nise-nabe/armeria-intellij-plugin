import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "armeria-intellij-plugin"


pluginManagement {
    repositories {
        gradlePluginPortal()
    }

    plugins {
        kotlin("jvm") version "2.2.0"
        id("org.jetbrains.intellij.platform") version "2.7.0"
        id("org.jetbrains.gradle.plugin.idea-ext") version "1.2"
        id("org.jetbrains.changelog") version "2.2.1"
    }
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.6.0"
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
