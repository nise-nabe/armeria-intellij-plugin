package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaThriftRouteCollector {
    private val SERVICE_PATTERN = Regex("""service\s+(\w+)\s*\{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL)
    private val METHOD_PATTERN = Regex("""(\w+)\s*\(""")

    fun collect(project: Project, scope: GlobalSearchScope, routes: MutableList<ArmeriaRoute>) {
        for (virtualFile in FilenameIndex.getAllFilesByExt(project, "thrift", scope)) {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: continue
            for (match in SERVICE_PATTERN.findAll(psiFile.text)) {
                val serviceName = match.groupValues[1]
                val body = match.groupValues[2]
                for (method in METHOD_PATTERN.findAll(body)) {
                    val methodName = method.groupValues[1]
                    if (methodName in setOf("oneway", "throws")) {
                        continue
                    }
                    routes += ArmeriaRoute.create(
                        element = psiFile,
                        protocol = message("route.explorer.protocol.thrift"),
                        httpMethod = "RPC",
                        path = "/$serviceName/$methodName",
                        target = "$serviceName.$methodName",
                        routeMatch = RouteMatch.NON_HTTP,
                    )
                }
            }
        }
    }
}
