import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.date
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

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
        id = "com.linecorp.intellij.armeria-intellij-plugin"
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
