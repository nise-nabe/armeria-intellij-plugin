package com.linecorp.intellij.plugins.armeria.test

import org.junit.Test
import kotlin.test.assertFalse

class ArmeriaTestSupportKotlinRuntimeIsolationTest {
    @Test
    fun javaEntryPointsDoNotReferenceKotlinPluginClassesInBytecode() {
        val isolatedClassNames =
            listOf(
                "ArmeriaJUnitServerExtensionSupport",
                "ArmeriaJUnitServerExtensionCollector",
                "ArmeriaJUnitServerExtensionCollector\$compute\$1",
                "ArmeriaBlockingClientInspection",
                "ArmeriaBlockingClientInspection\$Companion",
                "ArmeriaBlockingClientInspection\$buildVisitor\$1",
                "ArmeriaJUnitServerExtensionLineMarkerProvider",
                "ArmeriaBlockingClientPathSupport",
                "ArmeriaBlockingClientInspectionPaths",
                "ArmeriaTestMethodInserter",
                "ArmeriaGenerateTestMethodAction",
                "ArmeriaJUnitServerExtension",
                "ArmeriaJUnitServerExtension\$Companion",
                "ArmeriaTestMethodGenerator",
                "ArmeriaTestMethodGenerator\$WhenMappings",
                "ArmeriaTestMetadata",
                "ArmeriaTestLanguage",
            ).map { "com.linecorp.intellij.plugins.armeria.test.$it" }

        for (className in isolatedClassNames) {
            assertFalse(
                classBytecode(className).contains("org/jetbrains/kotlin"),
                "$className must not resolve Kotlin plugin types before the runtime availability guard",
            )
        }
    }

    private fun classBytecode(className: String): String {
        val resource = className.replace('.', '/') + ".class"
        val bytes =
            javaClass.classLoader.getResourceAsStream(resource)?.readBytes()
                ?: error("Missing class resource: $resource")
        return String(bytes, Charsets.ISO_8859_1)
    }
}
