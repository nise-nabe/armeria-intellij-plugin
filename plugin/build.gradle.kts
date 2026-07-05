import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.date
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("com.linecorp.intellij.platform-plugin")
}

group = "com.linecorp.intellij"
version = providers.gradleProperty("pluginVersion").get()

dependencies {
    implementation(project(":plugin-shared"))
    implementation(project(":plugin-route-analysis"))
    implementation(project(":plugin-wizard"))
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
    }
}

private val fastTestPatterns = listOf(
    "com.linecorp.intellij.plugins.armeria.client.*",
)

tasks.register<Test>("fastTest") {
    notCompatibleWithConfigurationCache("Copies runtime settings from the standard test task")
    description = "Runs unit tests without IntelliJ Platform PSI fixture"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn("prepareTest", "instrumentTestCode", "testClasses")
    filter {
        isFailOnNoMatchingTests = false
        fastTestPatterns.forEach { includeTestsMatching(it) }
    }
}

tasks.register<Test>("platformTest") {
    notCompatibleWithConfigurationCache("Copies runtime settings from the standard test task")
    description = "Runs IntelliJ Platform PSI fixture tests"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn("prepareTest", "instrumentTestCode", "testClasses")
    filter {
        isFailOnNoMatchingTests = false
        fastTestPatterns.forEach { excludeTestsMatching(it) }
    }
}

afterEvaluate {
    val standardTest = tasks.named<Test>("test").get()
    listOf("fastTest", "platformTest").forEach { suiteName ->
        tasks.named<Test>(suiteName).configure {
            testClassesDirs = standardTest.testClassesDirs
            classpath = standardTest.classpath
            jvmArgumentProviders.addAll(standardTest.jvmArgumentProviders)
            javaLauncher.convention(standardTest.javaLauncher)
            doFirst {
                systemProperties.putAll(standardTest.systemProperties)
                environment.putAll(standardTest.environment)
                jvmArgs = standardTest.jvmArgs
            }
        }
    }
}

tasks.named("check") {
    dependsOn("fastTest", "platformTest")
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
