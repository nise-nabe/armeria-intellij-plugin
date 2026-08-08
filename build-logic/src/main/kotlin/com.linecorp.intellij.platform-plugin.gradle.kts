import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm")
    `java-test-fixtures`
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
    id("com.linecorp.intellij.ktlint")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        @Suppress("UnstableApiUsage")
        vendor = JvmVendorSpec.JETBRAINS
    }
}

dependencies {
    intellijPlatform {
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }
}

intellijPlatform {
    buildSearchableOptions = false
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            useJUnitJupiter()
            dependencies {
                implementation(versionCatalogs.named("libs").findLibrary("kotlin-test").get())
                implementation(versionCatalogs.named("libs").findLibrary("junit-jupiter").get())
                runtimeOnly(versionCatalogs.named("libs").findLibrary("junit4").get())
                runtimeOnly(versionCatalogs.named("libs").findLibrary("junit-vintage").get())
            }
            targets.all {
                testTask.configure {
                    failOnNoDiscoveredTests = false
                    val testDataDir = project.file("src/test/testData")
                    if (testDataDir.isDirectory) {
                        systemProperty("armeria.moduleTestDataPath", testDataDir.absolutePath)
                    }
                }
            }
        }
    }
}
