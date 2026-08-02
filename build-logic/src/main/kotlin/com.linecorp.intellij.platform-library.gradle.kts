import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm")
    `java-test-fixtures`
    id("org.jetbrains.intellij.platform")
    id("com.linecorp.intellij.ktlint")
}

group = "com.linecorp.intellij"

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
    }
}

intellijPlatform {
    buildSearchableOptions = false
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            dependencies {
                implementation(versionCatalogs.named("libs").findLibrary("kotlin-test").get())
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
