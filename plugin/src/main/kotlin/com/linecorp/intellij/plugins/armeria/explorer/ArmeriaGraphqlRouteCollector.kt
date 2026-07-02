package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaGraphqlRouteCollector {
    private val FIELD_PATTERN = Regex("""^\s*(\w+)\s*:""", RegexOption.MULTILINE)
    private val TYPE_PATTERN = Regex("""type\s+(Query|Mutation)\s*\{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL)

    fun collect(project: Project, scope: GlobalSearchScope, routes: MutableList<ArmeriaRoute>) {
        for (virtualFile in FilenameIndex.getAllFilesByExt(project, "graphql", scope)) {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: continue
            for (match in TYPE_PATTERN.findAll(psiFile.text)) {
                val operationType = match.groupValues[1]
                val body = match.groupValues[2]
                for (field in FIELD_PATTERN.findAll(body)) {
                    val fieldName = field.groupValues[1]
                    routes += ArmeriaRoute.create(
                        element = psiFile,
                        protocol = message("route.explorer.protocol.graphql"),
                        httpMethod = operationType.uppercase(),
                        path = "/graphql/$fieldName",
                        target = "$operationType.$fieldName",
                        routeMatch = RouteMatch.NON_HTTP,
                    )
                }
            }
        }
    }
}
