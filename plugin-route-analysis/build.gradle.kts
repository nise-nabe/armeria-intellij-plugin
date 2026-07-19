plugins {
    id("com.linecorp.intellij.route-module")
}

dependencies {
    api(project(":plugin-route-model"))
    api(project(":plugin-route-collectors"))
    api(project(":plugin-route-spring"))
    api(project(":plugin-route-protocol"))
    implementation(project(":plugin-shared"))
    intellijPlatform {
        intellijIdeaUltimate(
            libs.versions.idea.platform
                .get(),
        )
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            dependencies {
                implementation(testFixtures(project(":plugin-route-collectors")))
            }
        }
        named("fastTest") {
            dependencies {
                implementation(testFixtures(project(":plugin-route-collectors")))
            }
        }
    }
}
