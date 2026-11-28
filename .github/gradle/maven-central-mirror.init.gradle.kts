// Route Maven Central traffic to the Google Cloud Storage mirror
// (https://storage-download.googleapis.com/maven-central/index.html).
// Only Maven Central is affected; other repositories are untouched.
val centralHosts = setOf("repo.maven.apache.org", "repo1.maven.org")
val mirrorUrl = uri("https://maven-central.storage-download.googleapis.com/maven2/")

fun RepositoryHandler.redirectCentral() {
    all {
        if (this is MavenArtifactRepository && url.host in centralHosts) {
            url = mirrorUrl
        }
    }
}

// plugins.gradle.org redirects non-plugin artifacts to Maven Central, so resolve
// them from the mirror first and leave the portal for plugin markers only.
fun RepositoryHandler.mirrorBeforePluginPortal() {
    whenObjectAdded {
        if (this is MavenArtifactRepository && url.host == "plugins.gradle.org" &&
            none { it is MavenArtifactRepository && it.url == mirrorUrl }
        ) {
            val portal = this
            val mirror = maven(mirrorUrl) { name = "GoogleMavenCentralMirror" }
            remove(mirror)
            add(indexOf(portal), mirror)
        }
    }
}

beforeProject {
    repositories.redirectCentral()
}
settingsEvaluated {
    // No explicit plugin repositories means Gradle falls back to the portal implicitly.
    if (pluginManagement.repositories.isEmpty()) {
        pluginManagement.repositories.gradlePluginPortal()
    }
    pluginManagement.repositories.redirectCentral()
    dependencyResolutionManagement.repositories.redirectCentral()
}
beforeSettings {
    pluginManagement.repositories.mirrorBeforePluginPortal()
    pluginManagement.repositories.redirectCentral()
    dependencyResolutionManagement.repositories.mirrorBeforePluginPortal()
    dependencyResolutionManagement.repositories.redirectCentral()
}
