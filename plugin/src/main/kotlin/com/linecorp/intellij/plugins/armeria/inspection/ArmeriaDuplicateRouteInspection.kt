package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaDuplicateRouteInspection : AbstractBaseJavaLocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.duplicate.route.display.name")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                val duplicateRoutes = mutableSetOf<PsiMethod>()
                val seen = mutableMapOf<Pair<String, String>, PsiMethod>()
                for (method in aClass.methods) {
                    for (route in ArmeriaMethodRoute.from(method)) {
                        for (path in route.paths) {
                            val key = route.httpMethod to ArmeriaRouteSupport.combinePaths(route.classPrefix, path)
                            val previous = seen.putIfAbsent(key, method)
                            if (previous != null) {
                                duplicateRoutes += previous
                                duplicateRoutes += method
                            }
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

private data class ArmeriaMethodRoute(
    val httpMethod: String,
    val classPrefix: String,
    val paths: List<String>,
) {
    companion object {
        fun from(method: PsiMethod): List<ArmeriaMethodRoute> {
            val routeAnnotationPaths = ArmeriaRouteSupport.routeAnnotationPaths(method)
            if (routeAnnotationPaths.isEmpty()) {
                return emptyList()
            }
            val classPrefix =
                ArmeriaRouteSupport.extractPrimaryPath(
                    method.containingClass?.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION),
                )
            return routeAnnotationPaths.map { (httpMethod, paths) ->
                ArmeriaMethodRoute(httpMethod, classPrefix, paths)
            }
        }
    }
}
