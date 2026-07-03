package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message
import java.util.MissingResourceException

internal object ArmeriaGrpcRouteCollector {
    private val SERVICE_HEADER_PATTERN = Regex("""\bservice\s+(\w+)\s*\{""")
    private val RPC_PATTERN = Regex("""\brpc\s+(\w+)\s*\(""")
    private val PACKAGE_PATTERN = Regex("""\bpackage\s+([\w.]+)\s*;?""")

    fun collect(project: Project, scope: GlobalSearchScope, routes: MutableList<ArmeriaRoute>) {
        if (!isProtoRouteDiscoveryEnabled()) {
            return
        }
        for (virtualFile in FilenameIndex.getAllFilesByExt(project, "proto", scope)) {
            ArmeriaRouteCollectionMetrics.current()?.filesScanned?.incrementAndGet()
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: continue
            collectFromProtoText(psiFile.text, psiFile, routes)
        }
    }

    internal fun collectFromProtoText(text: String, element: PsiElement, routes: MutableList<ArmeriaRoute>) {
        val packageName = PACKAGE_PATTERN.find(text)?.groupValues?.get(1).orEmpty()
        for ((serviceName, body) in findServiceBodies(text)) {
            val fqService = if (packageName.isBlank()) serviceName else "$packageName.$serviceName"
            for (rpc in RPC_PATTERN.findAll(body)) {
                val methodName = rpc.groupValues[1]
                routes += ArmeriaRoute.create(
                    element = element,
                    protocol = message("route.explorer.protocol.grpc"),
                    httpMethod = "RPC",
                    path = "/$fqService/$methodName",
                    target = "$fqService.$methodName",
                    routeMatch = RouteMatch.NON_HTTP,
                )
            }
        }
    }

    private fun isProtoRouteDiscoveryEnabled(): Boolean =
        try {
            Registry.`is`("armeria.grpc.proto.routes.enabled")
        } catch (_: MissingResourceException) {
            true
        }

    private fun findServiceBodies(text: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        var searchFrom = 0
        while (searchFrom < text.length) {
            val match = SERVICE_HEADER_PATTERN.find(text, searchFrom) ?: break
            val serviceName = match.groupValues[1]
            val openBraceIndex = match.range.last
            val closeBraceIndex = findMatchingCloseBrace(text, openBraceIndex) ?: break
            val body = text.substring(openBraceIndex + 1, closeBraceIndex)
            results += serviceName to body
            searchFrom = closeBraceIndex + 1
        }
        return results
    }

    private fun findMatchingCloseBrace(text: String, openBraceIndex: Int): Int? {
        if (openBraceIndex !in text.indices || text[openBraceIndex] != '{') {
            return null
        }
        var depth = 0
        for (index in openBraceIndex until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        return null
    }
}
