package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message
import java.util.MissingResourceException

internal object ArmeriaGrpcRouteCollector {
    private const val GRPC_SERVICE_CLASS = "com.linecorp.armeria.server.grpc.GrpcService"
    private val SERVICE_HEADER_PATTERN = Regex("""\bservice\s+(\w+)\s*\{""")
    private val RPC_PATTERN = Regex("""\brpc\s+(\w+)\s*\(""")
    private val PACKAGE_PATTERN = Regex("""\bpackage\s+([\w.]+)\s*;?""")

    fun collect(project: Project, scope: GlobalSearchScope, routes: MutableList<ArmeriaRoute>) {
        if (!isProtoRouteDiscoveryEnabled() || !isGrpcOnClasspath(project, scope)) {
            return
        }
        val seenProtoRoutes = mutableSetOf<String>()
        for (virtualFile in FilenameIndex.getAllFilesByExt(project, "proto", scope)) {
            ArmeriaRouteCollectionMetrics.current()?.filesScanned?.incrementAndGet()
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: continue
            collectFromProtoFile(psiFile, routes, seenProtoRoutes)
        }
    }

    private fun collectFromProtoFile(
        psiFile: PsiFile,
        routes: MutableList<ArmeriaRoute>,
        seenProtoRoutes: MutableSet<String>,
    ) {
        for (collector in protoRouteCollectors()) {
            if (collector.collectFromFile(psiFile, routes, seenProtoRoutes)) {
                return
            }
        }
        collectFromProtoText(psiFile.text, psiFile, routes, seenProtoRoutes)
    }

    private fun protoRouteCollectors(): List<ArmeriaProtoRouteCollector> {
        return try {
            ArmeriaProtoRouteCollector.EP.extensionList
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    internal fun collectFromProtoText(
        text: String,
        element: PsiElement,
        routes: MutableList<ArmeriaRoute>,
        seenProtoRoutes: MutableSet<String> = mutableSetOf(),
    ) {
        val strippedText = ArmeriaProtoTextSupport.stripComments(text)
        val packageName = PACKAGE_PATTERN.find(strippedText)?.groupValues?.get(1).orEmpty()
        for ((serviceName, body) in findServiceBodies(strippedText)) {
            val fqService = if (packageName.isBlank()) serviceName else "$packageName.$serviceName"
            for (rpc in RPC_PATTERN.findAll(body)) {
                val methodName = rpc.groupValues[1]
                addProtoRoute(element, fqService, methodName, routes, seenProtoRoutes)
            }
        }
    }

    internal fun addProtoRoute(
        element: PsiElement,
        fqService: String,
        methodName: String,
        routes: MutableList<ArmeriaRoute>,
        seenProtoRoutes: MutableSet<String>,
    ) {
        val path = "/$fqService/$methodName"
        val dedupeKey = "${ArmeriaRouteMetadata.moduleName(element)}:$path"
        if (!seenProtoRoutes.add(dedupeKey)) {
            return
        }
        routes += ArmeriaRoute.create(
            element = element,
            protocol = message("route.explorer.protocol.grpc"),
            httpMethod = "",
            path = path,
            target = "$fqService.$methodName",
            routeMatch = RouteMatch.NON_HTTP,
        )
    }

    internal fun isProtoRouteDiscoveryEnabled(): Boolean =
        try {
            Registry.`is`("armeria.grpc.proto.routes.enabled")
        } catch (_: MissingResourceException) {
            true
        }

    internal fun isGrpcOnClasspath(project: Project, scope: GlobalSearchScope): Boolean =
        JavaPsiFacade.getInstance(project).findClass(GRPC_SERVICE_CLASS, scope) != null

    private fun findServiceBodies(text: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        var searchFrom = 0
        while (searchFrom < text.length) {
            val match = SERVICE_HEADER_PATTERN.find(text, searchFrom) ?: break
            val serviceName = match.groupValues[1]
            val openBraceIndex = match.range.last
            val closeBraceIndex = ArmeriaProtoTextSupport.findMatchingCloseBrace(text, openBraceIndex)
            if (closeBraceIndex == null) {
                searchFrom = openBraceIndex + 1
                continue
            }
            val body = text.substring(openBraceIndex + 1, closeBraceIndex)
            results += serviceName to body
            searchFrom = closeBraceIndex + 1
        }
        return results
    }
}

fun registerArmeriaGrpcProtoRoute(
    element: PsiElement,
    fqService: String,
    methodName: String,
    routes: MutableList<ArmeriaRoute>,
    seenProtoRoutes: MutableSet<String>,
) {
    ArmeriaGrpcRouteCollector.addProtoRoute(element, fqService, methodName, routes, seenProtoRoutes)
}
