import org.jetbrains.changelog.Changelog

plugins {
    id("com.linecorp.intellij.platform-plugin")
}

group = "com.linecorp.intellij"
version = providers.gradleProperty("pluginVersion").get()

dependencies {
    intellijPlatform {
        intellijIdeaUltimate(libs.versions.idea.platform.get())
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.gradle")
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            dependencies {
                implementation(libs.velocity.engine.core)
                implementation(libs.junit4)
            }
        }
    }
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
