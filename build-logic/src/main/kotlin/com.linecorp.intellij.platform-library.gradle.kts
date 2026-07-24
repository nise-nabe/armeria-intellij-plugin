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

val kotlinTest =
    extensions
        .getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
        .named("libs")
        .findLibrary("kotlin-test")
        .get()

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
                implementation(kotlinTest)
            }
            targets.all {
                testTask.configure {
                    failOnNoDiscoveredTests = false
                }
            }
        }
    }
}
