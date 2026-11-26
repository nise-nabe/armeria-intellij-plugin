package com.linecorp.intellij.plugins.armeria.explorer

import org.junit.Test
import kotlin.test.assertFalse

class ArmeriaKotlinRuntimeIsolationTest {
    @Test
    fun sharedEntryPointsDoNotReferenceKotlinPluginClassesInBytecode() {
        val isolatedClassNames =
            listOf(
                "com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector",
                "com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector\$CoreRegistration",
                "com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics",
                "com.linecorp.intellij.plugins.armeria.explorer.support.CoreServiceRegistrationSupport",
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
