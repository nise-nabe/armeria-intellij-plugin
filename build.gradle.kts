import org.jetbrains.changelog.ChangelogPluginExtension
import org.jetbrains.gradle.ext.packagePrefix
import org.jetbrains.gradle.ext.settings
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
    id("org.jetbrains.gradle.plugin.idea-ext")
    `maven-publish`
}

group = "com.linecorp.intellij"

idea {
    module {
        settings {
            packagePrefix["src/main/kotlin"] = "com.linecorp.intellij.plugins.armeria"
            packagePrefix["src/test/kotlin"] = "com.linecorp.intellij.plugins.armeria"
        }
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2025.1.3")
        bundledPlugin("org.jetbrains.plugins.gradle")

        testFramework(TestFrameworkType.JUnit5)
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

tasks {
    val changelog: ChangelogPluginExtension = extensions.getByType()
    patchPluginXml {
        changeNotes.set(provider { changelog.getLatest().toHTML()} )
    }
}


testing {
    @Suppress("UNUSED_VARIABLE")
    suites {
        @Suppress("UnstableApiUsage") val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()

            dependencies {
                implementation(gradleTestKit())
                implementation("org.jetbrains.kotlin:kotlin-test-junit5")
                implementation("io.mockk:mockk:1.14.4")
            }
        }
    }
}
