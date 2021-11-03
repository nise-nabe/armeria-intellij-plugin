rootProject.name = "armeria-intellij-plugin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        exclusiveContent {
            forRepository {
                maven {
                    name = "gitHubPackages"
                    url = uri("https://maven.pkg.github.com/nise-nabe/gradle-plugins")
                    credentials(PasswordCredentials::class)
                }
            }
            filter {
                includeGroupByRegex("com.nisecoder.*")
            }
        }
    }

    plugins {
        kotlin("jvm") version "1.5.31"
        id("org.jetbrains.intellij") version "1.2.1"
        id("org.jetbrains.gradle.plugin.idea-ext") version "1.1"
        id("org.asciidoctor.jvm.convert") version "3.+"
        id("org.jetbrains.changelog") version "1.3.1"
        id("com.nisecoder.idea-ext-ext") version "0.0.4"
        id("com.nisecoder.github-pages.asciidoctor") version "0.0.4"
    }
}
