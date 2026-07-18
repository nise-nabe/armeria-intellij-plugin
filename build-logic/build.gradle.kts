plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.intellij.platform.gradle.plugin)
    implementation(libs.changelog.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
}
