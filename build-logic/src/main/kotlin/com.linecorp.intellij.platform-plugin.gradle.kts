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
    }
}

intellijPlatform {
    buildSearchableOptions = false
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            targets.all {
                testTask.configure {
                    failOnNoDiscoveredTests = false
                }
            }
        }
    }
}
