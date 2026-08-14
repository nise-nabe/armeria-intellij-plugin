package com.linecorp.intellij.plugins.armeria.intention

import com.intellij.psi.PsiClass
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport

enum class ArmeriaRouteStubKind {
    GET,
    POST_JSON,
}

internal object ArmeriaRouteMethodStub {
    fun javaMethodText(
        kind: ArmeriaRouteStubKind,
        methodName: String,
        path: String,
    ): String {
        val annotations = javaAnnotations(kind, path)
        return """
            $annotations
            public String $methodName() {
                return "";
            }
            """.trimIndent()
    }

    fun kotlinFunctionText(
        kind: ArmeriaRouteStubKind,
        methodName: String,
        path: String,
        suspend: Boolean,
    ): String {
        val annotations = kotlinAnnotations(kind, path)
        val modifier = if (suspend) "suspend " else ""
        return """
            $annotations
            ${modifier}fun $methodName(): String {
                return ""
            }
            """.trimIndent()
    }

    fun kotlinImports(kind: ArmeriaRouteStubKind): List<String> =
        when (kind) {
            ArmeriaRouteStubKind.GET -> listOf(ArmeriaRouteSupport.GET_ANNOTATION)
            ArmeriaRouteStubKind.POST_JSON ->
                listOf(
                    ArmeriaRouteSupport.POST_ANNOTATION,
                    ArmeriaRouteSupport.CONSUMES_JSON_ANNOTATION,
                    ArmeriaRouteSupport.PRODUCES_JSON_ANNOTATION,
                )
        }

    fun httpMethod(kind: ArmeriaRouteStubKind): String =
        when (kind) {
            ArmeriaRouteStubKind.GET -> "GET"
            ArmeriaRouteStubKind.POST_JSON -> "POST"
        }

    fun suggestMethodName(
        usedMethodNames: Set<String>,
        usedPathsForHttpMethod: Set<String>,
        baseName: String = "handler",
    ): String {
        var candidate = baseName
        var suffix = 2
        while (candidate in usedMethodNames || "/$candidate" in usedPathsForHttpMethod) {
            candidate = "$baseName$suffix"
            suffix++
        }
        return candidate
    }

    fun usedJavaRoutePaths(
        serviceClass: PsiClass,
        httpMethod: String,
    ): Set<String> =
        serviceClass.methods
            .filter { it.containingClass == serviceClass }
            .mapNotNullTo(linkedSetOf()) { method ->
                val (annotation, methodName) = ArmeriaRouteSupport.findRouteAnnotation(method) ?: return@mapNotNullTo null
                if (methodName != httpMethod) {
                    return@mapNotNullTo null
                }
                ArmeriaRouteSupport.extractPrimaryPath(annotation).takeIf { it.isNotEmpty() }
            }

    private fun javaAnnotations(
        kind: ArmeriaRouteStubKind,
        path: String,
    ): String =
        when (kind) {
            ArmeriaRouteStubKind.GET -> "@${ArmeriaRouteSupport.GET_ANNOTATION}(\"$path\")"
            ArmeriaRouteStubKind.POST_JSON ->
                """
                @${ArmeriaRouteSupport.POST_ANNOTATION}("$path")
                @${ArmeriaRouteSupport.CONSUMES_JSON_ANNOTATION}
                @${ArmeriaRouteSupport.PRODUCES_JSON_ANNOTATION}
                """.trimIndent()
        }

    private fun kotlinAnnotations(
        kind: ArmeriaRouteStubKind,
        path: String,
    ): String =
        when (kind) {
            ArmeriaRouteStubKind.GET -> "@Get(\"$path\")"
            ArmeriaRouteStubKind.POST_JSON ->
                """
                @Post("$path")
                @ConsumesJson
                @ProducesJson
                """.trimIndent()
        }
}
