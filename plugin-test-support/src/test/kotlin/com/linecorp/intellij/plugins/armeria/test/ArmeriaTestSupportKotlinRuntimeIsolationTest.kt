package com.linecorp.intellij.plugins.armeria.test

import org.junit.Test
import kotlin.test.assertFalse

class ArmeriaTestSupportKotlinRuntimeIsolationTest {
    @Test
    fun javaEntryPointsDoNotReferenceKotlinPluginClassesInBytecode() {
        val isolatedClassNames =
            listOf(
                "com.linecorp.intellij.plugins.armeria.test.ArmeriaJUnitServerExtensionSupport",
                "com.linecorp.intellij.plugins.armeria.test.ArmeriaJUnitServerExtensionCollector",
                "com.linecorp.intellij.plugins.armeria.test.ArmeriaBlockingClientInspection",
                "com.linecorp.intellij.plugins.armeria.test.ArmeriaJUnitServerExtensionLineMarkerProvider",
                "com.linecorp.intellij.plugins.armeria.test.ArmeriaBlockingClientPathSupport",
                "com.linecorp.intellij.plugins.armeria.test.ArmeriaTestMethodInserter",
                "com.linecorp.intellij.plugins.armeria.test.ArmeriaGenerateTestMethodAction",
            )

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
