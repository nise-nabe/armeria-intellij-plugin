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
                includeGroup("com.nisecoder.gradle.plugin")
            }
        }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("com.nisecoder.idea-ext-ext")) {
                useModule("com.nisecoder.gradle.plugin:idea-ext-ext:0.0.4")
            }
            if (requested.id.id.startsWith("com.nisecoder.github-pages.asciidoctor")) {
                useModule("com.nisecoder.gradle.plugin:asciidoctor:0.0.4")
            }
        }
    }

    plugins {
        kotlin("jvm") version "1.5.31"
        id("org.jetbrains.intellij") version "1.2.1"
        id("org.jetbrains.gradle.plugin.idea-ext") version "1.1"
        id("org.asciidoctor.jvm.convert") version "3.+"
        id("org.jetbrains.changelog") version "1.3.1"
    }
}
