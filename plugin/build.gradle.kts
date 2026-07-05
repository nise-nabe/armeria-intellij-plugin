import org.gradle.api.tasks.testing.Test
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
        intellijIdeaUltimate(libs.versions.idea.platform.get())
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Plugin.Java)
        testFramework(TestFrameworkType.Plugin.Java, configurationName = "testFixturesImplementation")
    }
    testFixturesImplementation(libs.junit4)
}

private fun JvmTestSuite.configureFilteredSuite(
    description: String,
    configureJUnit: org.gradle.api.tasks.testing.junit.JUnitOptions.() -> Unit,
) {
    useJUnit(libs.versions.junit4.get())
    sources {
        kotlin.setSrcDirs(emptyList<String>())
        resources.setSrcDirs(emptyList<String>())
    }
    targets {
        all {
            testTask.configure {
                this.description = description
                dependsOn("prepareTest", "instrumentTestCode", "testClasses")
                useJUnit(configureJUnit)
                filter {
                    isFailOnNoMatchingTests = false
                }
            }
        }
    }
}

private fun Project.wireFilteredTestSuitesToStandardTest() {
    val standardTest = tasks.named<Test>("test")
    listOf("fastTest", "platformTest").forEach { suiteName ->
        tasks.named<Test>(suiteName).configure {
            notCompatibleWithConfigurationCache(
                "Copies IntelliJ Platform test runtime from the standard test task",
            )
            testClassesDirs = standardTest.get().testClassesDirs
            classpath = standardTest.get().classpath
            jvmArgumentProviders.addAll(standardTest.get().jvmArgumentProviders)
            javaLauncher.convention(standardTest.get().javaLauncher)
            doFirst {
                val source = standardTest.get()
                systemProperties.putAll(source.systemProperties)
                environment.putAll(source.environment)
                jvmArgs = source.jvmArgs
            }
        }
    }
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            dependencies {
                implementation(testFixtures(project()))
                implementation(libs.velocity.engine.core)
                implementation(libs.junit4)
            }
        }

        register("fastTest", JvmTestSuite::class) {
            configureFilteredSuite("Runs unit tests without IntelliJ Platform PSI fixture") {
                includeCategories("com.linecorp.intellij.plugins.armeria.test.FastTest")
            }
        }

        register("platformTest", JvmTestSuite::class) {
            configureFilteredSuite("Runs IntelliJ Platform PSI fixture tests") {
                excludeCategories("com.linecorp.intellij.plugins.armeria.test.FastTest")
            }
        }
    }
}

afterEvaluate {
    wireFilteredTestSuitesToStandardTest()
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
