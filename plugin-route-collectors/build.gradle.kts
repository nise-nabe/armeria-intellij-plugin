import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("com.linecorp.intellij.route-module")
}

dependencies {
    api(project(":plugin-route-model"))
    implementation(project(":plugin-shared"))
    intellijPlatform {
        intellijIdeaUltimate(
            libs.versions.idea.platform
                .get(),
        )
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Plugin.Java, configurationName = "testFixturesImplementation")
    }
    testFixturesImplementation(libs.junit4)
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            dependencies {
                implementation(testFixtures(project()))
            }
        }
        named("fastTest") {
            dependencies {
                implementation(testFixtures(project()))
            }
        }
    }
}
