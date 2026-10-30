plugins {
    id("com.linecorp.intellij.platform-library")
}

dependencies {
    implementation(project(":plugin-shared"))
    intellijPlatform {
        intellijIdeaUltimate(
            libs.versions.idea.platform
                .get(),
        ) {
            // 2026.2.4 is in the IntelliJ Maven repository. The products-feed installer
            // listing still stops at 2026.2.3, so the default installer lookup cannot resolve it.
            useInstaller = false
        }
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("org.jetbrains.kotlin")
    }
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            dependencies {
                implementation(libs.velocity.engine.core)
                implementation(libs.junit4)
            }
        }
    }
}
