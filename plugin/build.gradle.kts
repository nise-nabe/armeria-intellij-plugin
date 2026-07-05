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

testing {
    suites {
        withType<JvmTestSuite>().matching {
            it.name in setOf("test", "fastTest", "platformTest")
        }.configureEach {
            useJUnit(libs.versions.junit4.get())
            dependencies {
                if (name != "test") {
                    implementation(project())
                }
                implementation(testFixtures(project()))
                implementation(libs.velocity.engine.core)
                implementation(libs.junit4)
            }
        }

        register("fastTest", JvmTestSuite::class) {
            targets {
                all {
                    testTask.configure {
                        description = "Runs unit tests without IntelliJ Platform PSI fixture"
                        dependsOn("prepareTest", "instrumentTestCode", "testClasses")
                        testClassesDirs = project.sourceSets.named("test").get().output.classesDirs
                        options {
                            (this as JUnitOptions).includeCategories(
                                "com.linecorp.intellij.plugins.armeria.test.FastTest",
                            )
                        }
                        filter {
                            isFailOnNoMatchingTests = false
                        }
                    }
                }
            }
        }

        register("platformTest", JvmTestSuite::class) {
            targets {
                all {
                    testTask.configure {
                        description = "Runs IntelliJ Platform PSI fixture tests"
                        dependsOn("prepareTest", "instrumentTestCode", "testClasses")
                        testClassesDirs = project.sourceSets.named("test").get().output.classesDirs
                        options {
                            (this as JUnitOptions).excludeCategories(
                                "com.linecorp.intellij.plugins.armeria.test.FastTest",
                            )
                        }
                        filter {
                            isFailOnNoMatchingTests = false
                        }
                    }
                }
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

val standardTest = tasks.named<Test>("test")
listOf("fastTest", "platformTest").forEach { suiteName ->
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
