plugins {
    id("com.linecorp.intellij.platform-library")
}

dependencies {
    implementation(project(":plugin-shared"))
    intellijPlatform {
        intellijIdeaUltimate(libs.versions.idea.platform.get())
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
