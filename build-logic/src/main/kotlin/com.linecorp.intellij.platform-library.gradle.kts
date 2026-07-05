import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
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
            targets.all {
                testTask.configure {
                    failOnNoDiscoveredTests = false
                }
            }
        }
    }
}
