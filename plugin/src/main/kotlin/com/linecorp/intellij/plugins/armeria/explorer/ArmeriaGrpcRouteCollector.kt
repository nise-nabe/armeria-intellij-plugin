package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaGrpcRouteCollector {
    private val SERVICE_PATTERN = Regex("""service\s+(\w+)\s*\{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL)
    private val RPC_PATTERN = Regex("""rpc\s+(\w+)\s*\(""")

    fun collect(project: Project, scope: GlobalSearchScope, routes: MutableList<ArmeriaRoute>) {
        for (virtualFile in FilenameIndex.getAllFilesByExt(project, "proto", scope)) {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: continue
            val packageName = PACKAGE_PATTERN.find(psiFile.text)?.groupValues?.get(1).orEmpty()
            for (match in SERVICE_PATTERN.findAll(psiFile.text)) {
                val serviceName = match.groupValues[1]
                val body = match.groupValues[2]
                val fqService = if (packageName.isBlank()) serviceName else "$packageName.$serviceName"
                for (rpc in RPC_PATTERN.findAll(body)) {
                    val methodName = rpc.groupValues[1]
                    routes += ArmeriaRoute.create(
                        element = psiFile,
                        protocol = message("route.explorer.protocol.grpc"),
                        httpMethod = "RPC",
                        path = "/$fqService/$methodName",
                        target = "$fqService.$methodName",
                        routeMatch = RouteMatch.NON_HTTP,
                    )
                }
            }
        }
    }

    private val PACKAGE_PATTERN = Regex("""package\s+([\w.]+)\s*;""")
}
