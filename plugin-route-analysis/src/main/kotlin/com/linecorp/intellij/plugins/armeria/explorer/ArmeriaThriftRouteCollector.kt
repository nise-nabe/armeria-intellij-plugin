package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

internal data class ThriftOperation(
    val serviceName: String,
    val methodName: String,
)

internal object ArmeriaThriftRouteCollector {
    private val SERVICE_HEADER_PATTERN = Regex("""service\s+(\w+)(?:\s+extends\s+[\w.]+)?\s*\{""")
    private val METHOD_PATTERN = Regex("""^\s*[\w.<>,\s]+\s+(\w+)\s*\(""", RegexOption.MULTILINE)
    private val THRIFT_KEYWORDS = setOf("oneway", "throws", "extends", "performs")

    fun collect(project: Project, scope: GlobalSearchScope, routes: MutableList<ArmeriaRoute>) {
        if (!ArmeriaIdlRouteSupport.isThriftOnClasspath(project, scope)) {
            return
        }
        val seenThriftRoutes = mutableSetOf<String>()
        // FilenameIndex iteration order is unstable; sort so first-wins dedupe is deterministic.
        val virtualFiles = FilenameIndex.getAllFilesByExt(project, "thrift", scope)
            .sortedWith(compareBy({ it.path }, { it.name }))
        val psiManager = PsiManager.getInstance(project)
        for (virtualFile in virtualFiles) {
            val psiFile = psiManager.findFile(virtualFile) ?: continue
            for (operation in parseOperations(psiFile.text)) {
                val path = "/${operation.serviceName}"
                val target = "${operation.serviceName}.${operation.methodName}"
                val dedupeKey = "${ArmeriaRouteMetadata.moduleName(psiFile)}:$path:$target"
                if (!seenThriftRoutes.add(dedupeKey)) {
                    continue
                }
                routes += ArmeriaRoute.create(
                    element = psiFile,
                    protocol = RouteProtocol.THRIFT.presentableName(),
                    httpMethod = "",
                    path = path,
                    target = target,
                    routeMatch = RouteMatch.NON_HTTP,
                )
            }
        }
    }

    internal fun parseOperations(thriftText: String): List<ThriftOperation> {
        val normalized = ArmeriaIdlRouteSupport.stripComments(thriftText)
        val operations = mutableListOf<ThriftOperation>()
        for (match in SERVICE_HEADER_PATTERN.findAll(normalized)) {
            val serviceName = match.groupValues[1]
            val openBraceIndex = normalized.indexOf('{', match.range.first)
            val body = ArmeriaIdlRouteSupport.extractBracedBody(normalized, openBraceIndex) ?: continue
            for (method in METHOD_PATTERN.findAll(body)) {
                val methodName = method.groupValues[1]
                if (methodName in THRIFT_KEYWORDS || isOnewayMethod(body, method.range.first)) {
                    continue
                }
                operations += ThriftOperation(serviceName, methodName)
            }
        }
        return operations
    }

    private fun isOnewayMethod(body: String, matchStart: Int): Boolean {
        val lineStart = body.lastIndexOf('\n', matchStart - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd = body.indexOf('\n', matchStart).let { if (it < 0) body.length else it }
        val line = body.substring(lineStart, lineEnd)
        return Regex("""\boneway\b""").containsMatchIn(line)
    }
}
