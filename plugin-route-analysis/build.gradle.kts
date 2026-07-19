import org.gradle.api.tasks.testing.Test
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("com.linecorp.intellij.platform-library")
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
        testFramework(TestFrameworkType.Plugin.Java)
    }
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            useJUnit(libs.versions.junit4.get())
            dependencies {
                implementation(testFixtures(project(":plugin-route-collectors")))
                implementation(libs.junit4)
            }
        }

        register("fastTest", JvmTestSuite::class) {
            useJUnit(libs.versions.junit4.get())
            dependencies {
                implementation(project())
                implementation(testFixtures(project(":plugin-route-collectors")))
                implementation(libs.junit4)
            }
            sources {
                compileClasspath += configurations.named("testCompileClasspath").get()
                runtimeClasspath += configurations.named("testRuntimeClasspath").get()
            }
            targets.all {
                testTask.configure {
                    description =
                        "Runs pure unit tests from src/fastTest (IntelliJ Platform test runtime; no PSI fixtures)"
                    dependsOn("prepareTest", "instrumentTestCode", "fastTestClasses")
                    val fastTestClassesDirs =
                        project.sourceSets.named("fastTest").map { it.output.classesDirs }
                    testClassesDirs =
                        project.sourceSets
                            .named("fastTest")
                            .get()
                            .output.classesDirs
                    val platformTestClasspath = project.tasks.named<Test>("test").map { it.classpath }
                    classpath = project.files(fastTestClassesDirs, platformTestClasspath)
                    javaLauncher.set(project.tasks.named<Test>("test").flatMap { it.javaLauncher })
                }
            }
        }
    }
}

dependencies {
    intellijPlatform {
        testFramework(TestFrameworkType.Plugin.Java, configurationName = "fastTestImplementation")
        testFramework(TestFrameworkType.Platform, configurationName = "fastTestImplementation")
    }
}

extensions.configure<KotlinJvmProjectExtension>("kotlin") {
    target.compilations.named("fastTest") {
        associateWith(target.compilations.getByName("main"))
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("fastTest"))
}
