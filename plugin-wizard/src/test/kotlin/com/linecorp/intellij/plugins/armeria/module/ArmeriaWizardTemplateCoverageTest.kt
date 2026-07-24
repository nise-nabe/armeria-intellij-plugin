package com.linecorp.intellij.plugins.armeria.module

import org.junit.Test
import java.nio.charset.StandardCharsets
import kotlin.test.assertTrue

class ArmeriaWizardTemplateCoverageTest {
    @Test
    fun gradleKtsOptionalLibraryHasConditionalBlock() {
        assertOptionalLibrariesCovered("fileTemplates/j2ee/armeria-build.gradle.kts.ft")
    }

    @Test
    fun gradleGroovyOptionalLibraryHasConditionalBlock() {
        assertOptionalLibrariesCovered("fileTemplates/j2ee/armeria-build.gradle.ft")
    }

    @Test
    fun mavenPomOptionalLibraryHasConditionalBlock() {
        assertOptionalLibrariesCovered("fileTemplates/j2ee/armeria-pom.xml.ft")
    }

    @Test
    fun ktsTemplateStillDeclaresCoreArmeriaDependency() {
        val template = readClasspathResource("fileTemplates/j2ee/armeria-build.gradle.kts.ft")
        assertTrue(template.contains("""implementation("com.linecorp.armeria:armeria")"""))
    }

    private fun assertOptionalLibrariesCovered(templateResource: String) {
        val template = readClasspathResource(templateResource)
        armeriaWizardLibraryIds.forEach { libraryId ->
            assertTrue(
                template.contains("""hasLibrary("$libraryId")"""),
                "$templateResource is missing hasLibrary(\"$libraryId\")",
            )
        }
    }

    private fun readClasspathResource(path: String): String {
        val bytes =
            javaClass.classLoader.getResourceAsStream(path)?.readAllBytes()
                ?: error("Missing resource: $path")
        return bytes.toString(StandardCharsets.UTF_8)
    }
}
