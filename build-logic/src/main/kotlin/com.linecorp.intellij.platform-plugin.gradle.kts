import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
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
        val test by getting(JvmTestSuite::class) {
            targets.all {
                testTask.configure {
                    failOnNoDiscoveredTests = false
                }
            }
        }
    }
}
