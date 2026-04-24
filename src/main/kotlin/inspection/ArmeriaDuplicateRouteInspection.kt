package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaDuplicateRouteInspection : AbstractBaseJavaLocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.duplicate.route.display.name")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                val duplicateRoutes = mutableSetOf<PsiMethod>()
                val seen = mutableMapOf<Pair<String, String>, PsiMethod>()
                for (method in aClass.methods) {
                    val route = ArmeriaMethodRoute.from(method) ?: continue
                    for (path in route.paths) {
                        val key = route.httpMethod to ArmeriaMethodRoute.combinePaths(route.classPrefix, path)
                        val previous = seen.putIfAbsent(key, method)
                        if (previous != null) {
                            duplicateRoutes += previous
                            duplicateRoutes += method
                        }
                    }
                }
                for (method in duplicateRoutes) {
                    holder.registerProblem(
                        method.nameIdentifier ?: method,
                        message("inspection.duplicate.route.problem"),
                    )
                }
            }
        }
    }
}

private data class ArmeriaMethodRoute(
    val httpMethod: String,
    val classPrefix: String,
    val paths: List<String>,
) {
    companion object {
        private val routeAnnotations = mapOf(
            "com.linecorp.armeria.server.annotation.Get" to "GET",
            "com.linecorp.armeria.server.annotation.Head" to "HEAD",
            "com.linecorp.armeria.server.annotation.Post" to "POST",
            "com.linecorp.armeria.server.annotation.Put" to "PUT",
            "com.linecorp.armeria.server.annotation.Delete" to "DELETE",
            "com.linecorp.armeria.server.annotation.Options" to "OPTIONS",
            "com.linecorp.armeria.server.annotation.Patch" to "PATCH",
            "com.linecorp.armeria.server.annotation.Trace" to "TRACE",
        )

        fun from(method: PsiMethod): ArmeriaMethodRoute? {
            val annotation = method.modifierList.annotations.firstNotNullOfOrNull { candidate ->
                val qualifiedName = candidate.qualifiedName ?: return@firstNotNullOfOrNull null
                routeAnnotations[qualifiedName]?.let { candidate to it }
            } ?: return null
            val classPrefix = method.containingClass?.getAnnotation("com.linecorp.armeria.server.annotation.PathPrefix")
                ?.findDeclaredAttributeValue("value")
                ?.text
                ?.removePrefix("\"")
                ?.removeSuffix("\"")
                .orEmpty()
            val paths = sequenceOf("value", "path")
                .mapNotNull { annotation.first.findDeclaredAttributeValue(it) }
                .flatMap {
                    when (it) {
                        is com.intellij.psi.PsiAnnotationArrayInitializerMemberValue -> it.initializers.asSequence().map { initializer -> initializer.text.removePrefix("\"").removeSuffix("\"") }
                        else -> sequenceOf(it.text.removePrefix("\"").removeSuffix("\""))
                    }
                }
                .map { if (it.startsWith("/")) it else "/$it" }
                .toList()
                .ifEmpty { listOf("/") }
            return ArmeriaMethodRoute(annotation.second, classPrefix, paths)
        }

        fun combinePaths(prefix: String, path: String): String {
            if (prefix.isBlank() || prefix == "/") {
                return if (path.startsWith("/")) path else "/$path"
            }
            val normalizedPrefix = if (prefix.startsWith("/")) prefix else "/$prefix"
            val normalizedPath = if (path.startsWith("/")) path else "/$path"
            if (normalizedPath == "/") {
                return normalizedPrefix
            }
            return "${normalizedPrefix.removeSuffix("/")}/${normalizedPath.removePrefix("/")}"
        }
    }
}
