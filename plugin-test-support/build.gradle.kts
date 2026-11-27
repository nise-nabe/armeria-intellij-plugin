import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("com.linecorp.intellij.platform-library")
}

dependencies {
    add("armeriaTestFixtures", testFixtures(project(":plugin-route-collectors")))
    implementation(project(":plugin-route-analysis"))
    api(project(":plugin-route-model"))
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
