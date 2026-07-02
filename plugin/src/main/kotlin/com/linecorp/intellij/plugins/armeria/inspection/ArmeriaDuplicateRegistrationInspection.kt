package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.RouteMatch
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaDuplicateRegistrationInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.duplicate.registration.display.name")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val duplicates = findProjectDuplicates(holder.project)
        if (duplicates.isEmpty()) {
            return PsiElementVisitor.EMPTY_VISITOR
        }
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                for ((_, routes) in duplicates) {
                    for (route in routes) {
                        val element = route.pointer.element ?: continue
                        if (element.containingFile != file) {
                            continue
                        }
                        holder.registerProblem(
                            element,
                            message("inspection.duplicate.registration.problem", route.path, routes.size),
                        )
                    }
                }
            }
        }
    }

    companion object {
        private val CHECKED_MATCHES = setOf(
            RouteMatch.ANNOTATED_HTTP,
            RouteMatch.SERVICE,
            RouteMatch.SERVICE_UNDER,
        )

        internal fun findProjectDuplicates(project: com.intellij.openapi.project.Project): Map<RouteKey, List<ArmeriaRoute>> {
            return ArmeriaRouteCollector.collect(project)
                .filter { it.routeMatch in CHECKED_MATCHES }
                .groupBy { RouteKey(it.httpMethod, it.path, it.routeMatch) }
                .filter { it.value.size > 1 }
        }
    }
}

internal data class RouteKey(
    val httpMethod: String,
    val path: String,
    val routeMatch: RouteMatch,
)
