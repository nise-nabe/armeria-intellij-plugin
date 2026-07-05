import org.gradle.api.tasks.testing.Test
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.date
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

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
        getByName<JvmTestSuite>("test") {
            useJUnit(libs.versions.junit4.get())
            dependencies {
                implementation(testFixtures(project()))
                implementation(libs.velocity.engine.core)
                implementation(libs.junit4)
            }
        }

        register("fastTest", JvmTestSuite::class) {
            useJUnit(libs.versions.junit4.get())
            dependencies {
                implementation(project())
                implementation(testFixtures(project()))
                implementation(libs.velocity.engine.core)
                implementation(libs.junit4)
            }
            sources {
                compileClasspath += configurations.named("testCompileClasspath").get()
                runtimeClasspath += configurations.named("testRuntimeClasspath").get()
            }
            targets.all {
                testTask.configure {
                    description = "Runs unit tests without IntelliJ Platform PSI fixture"
                    dependsOn("prepareTest", "instrumentTestCode", "fastTestClasses")
                    testClassesDirs = project.sourceSets.named("fastTest").get().output.classesDirs
                }
            }
        }
    }
}

val standardTest = tasks.named<Test>("test")
tasks.named<Test>("fastTest").configure {
    val fastTestClassesDirs = sourceSets.named("fastTest").get().output.classesDirs
    notCompatibleWithConfigurationCache(
        "Copies IntelliJ Platform test runtime from the standard test task",
    )
    classpath = fastTestClassesDirs + standardTest.get().classpath
    jvmArgumentProviders.addAll(standardTest.get().jvmArgumentProviders)
    javaLauncher.convention(standardTest.get().javaLauncher)
    systemProperties.putAll(standardTest.get().systemProperties)
    environment.putAll(standardTest.get().environment)
    jvmArgs = standardTest.get().jvmArgs
}

dependencies {
    intellijPlatform {
        testFramework(TestFrameworkType.Plugin.Java, configurationName = "fastTestImplementation")
        testFramework(TestFrameworkType.Platform, configurationName = "fastTestImplementation")
    }
}

extensions.configure<KotlinJvmProjectExtension>("kotlin") {
    target.compilations.named("fastTest") {
        associateWith(target.compilations.getByName("main"))
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("fastTest"))
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
