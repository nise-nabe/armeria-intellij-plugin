import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junit.JUnitOptions
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

private val pluginTestSuiteNames = setOf("test", "fastTest", "platformTest")
private val filteredTestSuiteNames = setOf("fastTest", "platformTest")

private val configurePluginTestSuite = { suite: JvmTestSuite ->
    suite.useJUnit(libs.versions.junit4.get())
    suite.dependencies {
        if (suite.name != "test") {
            implementation(project())
        }
        implementation(testFixtures(project()))
        implementation(libs.velocity.engine.core)
        implementation(libs.junit4)
    }
}

private fun JvmTestSuite.configureFilteredTestTarget(
    description: String,
    configureJUnit: JUnitOptions.() -> Unit,
) {
    targets {
        all {
            testTask.configure {
                this.description = description
                dependsOn("prepareTest", "instrumentTestCode", "testClasses")
                testClassesDirs = project.sourceSets.named("test").get().output.classesDirs
                options {
                    configureJUnit(this as JUnitOptions)
                }
                filter {
                    isFailOnNoMatchingTests = false
                }
            }
        }
    }
}

private fun Project.wireFilteredTestSuitesToStandardTest() {
    val standardTest = tasks.named<Test>("test")
    filteredTestSuiteNames.forEach { suiteName ->
        tasks.named<Test>(suiteName).configure {
            notCompatibleWithConfigurationCache(
                "Copies IntelliJ Platform test runtime from the standard test task",
            )
            classpath = standardTest.get().classpath
            jvmArgumentProviders.addAll(standardTest.get().jvmArgumentProviders)
            javaLauncher.convention(standardTest.get().javaLauncher)
            systemProperties.putAll(standardTest.get().systemProperties)
            environment.putAll(standardTest.get().environment)
            jvmArgs = standardTest.get().jvmArgs
        }
    }
}

testing {
    suites {
        withType<JvmTestSuite>().matching { it.name in pluginTestSuiteNames }.configureEach(configurePluginTestSuite)

        register("fastTest", JvmTestSuite::class) {
            configureFilteredTestTarget("Runs unit tests without IntelliJ Platform PSI fixture") {
                includeCategories("com.linecorp.intellij.plugins.armeria.test.FastTest")
            }
        }

        register("platformTest", JvmTestSuite::class) {
            configureFilteredTestTarget("Runs IntelliJ Platform PSI fixture tests") {
                excludeCategories("com.linecorp.intellij.plugins.armeria.test.FastTest")
            }
        }
    }
}

dependencies {
    intellijPlatform {
        testFramework(TestFrameworkType.Plugin.Java, configurationName = "fastTestImplementation")
        testFramework(TestFrameworkType.Plugin.Java, configurationName = "platformTestImplementation")
    }
}

wireFilteredTestSuitesToStandardTest()

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
