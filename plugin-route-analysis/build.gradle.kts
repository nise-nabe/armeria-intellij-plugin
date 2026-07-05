plugins {
    id("com.linecorp.intellij.platform-library")
}

import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

private val fastTestPatterns = listOf(
    "com.linecorp.intellij.plugins.armeria.explorer.ArmeriaDecoratorSupportTest",
    "com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteTreeBuilderTest",
    "com.linecorp.intellij.plugins.armeria.explorer.ArmeriaHttpRequestGeneratorTest",
    "com.linecorp.intellij.plugins.armeria.explorer.ArmeriaHttpMethodPillTest",
    "com.linecorp.intellij.plugins.armeria.explorer.ArmeriaProtoTextSupportTest",
    "com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupportApplicationDetectionTest",
    "com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteExplorerTest",
)

dependencies {
    implementation(project(":plugin-shared"))
    intellijPlatform {
        intellijIdeaUltimate(libs.versions.idea.platform.get())
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Plugin.Java)
    }
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            dependencies {
                implementation(libs.junit4)
            }
        }
    }
}

tasks.register<Test>("fastTest") {
    notCompatibleWithConfigurationCache("Copies runtime settings from the standard test task")
    description = "Runs pure route model unit tests"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn("prepareTest", "instrumentTestCode", "testClasses")
    filter {
        isFailOnNoMatchingTests = false
        fastTestPatterns.forEach { includeTestsMatching(it) }
    }
}

tasks.register<Test>("platformTest") {
    notCompatibleWithConfigurationCache("Copies runtime settings from the standard test task")
    description = "Runs route PSI collector tests"
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
