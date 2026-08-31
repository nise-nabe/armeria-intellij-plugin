package com.linecorp.intellij.plugins.armeria.explorer.protocol

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRouteMetadata
import com.linecorp.intellij.plugins.armeria.explorer.model.GrpcRoutePath
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaGrpcServiceOptionsSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaProtoRouteDiscoverySupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaGrpcRouteCollector {
    private val SERVICE_HEADER_PATTERN = Regex("""\bservice\s+(\w+)\s*\{""")
    private val RPC_PATTERN = Regex("""\brpc\s+(\w+)\s*\(""")
    private val PACKAGE_PATTERN = Regex("""\bpackage\s+([\w.]+)\s*;?""")

    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
    ) {
        if (!ArmeriaProtoRouteDiscoverySupport.isEnabled() || !ArmeriaProtoRouteDiscoverySupport.isGrpcOnClasspath(project, scope)) {
            return
        }
        val seenProtoRoutes = mutableSetOf<String>()
        val protoStartIndex = routes.size
        for (virtualFile in FilenameIndex.getAllFilesByExt(project, "proto", scope)) {
            ArmeriaRouteCollectionMetrics.current()?.filesScanned?.incrementAndGet()
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: continue
            collectFromProtoFile(psiFile, routes, seenProtoRoutes)
        }
        annotateUnframedProtoRoutes(routes, protoStartIndex)
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
        val area = ApplicationManager.getApplication().extensionArea
        if (!area.hasExtensionPoint(ArmeriaProtoRouteCollector.EP.name)) {
            return emptyList()
        }
        return ArmeriaProtoRouteCollector.EP.extensionList
    }

    internal fun collectFromProtoText(
        text: String,
        element: PsiElement,
        routes: MutableList<ArmeriaRoute>,
        seenProtoRoutes: MutableSet<String> = mutableSetOf(),
    ) {
        val strippedText = ArmeriaProtoTextSupport.stripComments(text)
        val packageName =
            PACKAGE_PATTERN
                .find(strippedText)
                ?.groupValues
                ?.get(1)
                .orEmpty()
        for ((serviceName, body) in findServiceBodies(strippedText)) {
            val fqService = if (packageName.isBlank()) serviceName else "$packageName.$serviceName"
            val rpcMatches = RPC_PATTERN.findAll(body).toList()
            for ((index, rpc) in rpcMatches.withIndex()) {
                val methodName = rpc.groupValues[1]
                val rpcEnd = rpcMatches.getOrNull(index + 1)?.range?.first ?: body.length
                val rpcSource = body.substring(rpc.range.first, rpcEnd)
                addProtoRoute(
                    element,
                    fqService,
                    methodName,
                    routes,
                    seenProtoRoutes,
                    ArmeriaGrpcHttpOptionSupport.contentHints(rpcSource),
                )
            }
        }
    }

    fun addProtoRoute(
        element: PsiElement,
        fqService: String,
        methodName: String,
        routes: MutableList<ArmeriaRoute>,
        seenProtoRoutes: MutableSet<String>,
        contentHints: List<String> = emptyList(),
    ) {
        val path = GrpcRoutePath.path(fqService, methodName)
        val dedupeKey = "${ArmeriaRouteMetadata.moduleName(element)}:$path"
        if (!seenProtoRoutes.add(dedupeKey)) {
            return
        }
        routes +=
            ArmeriaRoute.create(
                element = element,
                protocol = message("route.explorer.protocol.grpc"),
                httpMethod = "",
                path = path,
                target = "$fqService.$methodName",
                routeMatch = RouteMatch.NON_HTTP,
                contentHints = contentHints,
            )
    }

    private fun annotateUnframedProtoRoutes(
        routes: MutableList<ArmeriaRoute>,
        protoStartIndex: Int,
    ) {
        if (protoStartIndex >= routes.size) {
            return
        }
        val unframedEnabled =
            routes.any { route ->
                route.protocol.equals(RouteProtocol.GRPC.presentableName(), ignoreCase = true) &&
                    ArmeriaGrpcServiceOptionsSupport.hasUnframedHint(route.contentHints)
            }
        if (!unframedEnabled) {
            return
        }
        val unframedHint = message("route.explorer.badge.grpcUnframed")
        for (index in protoStartIndex until routes.size) {
            val route = routes[index]
            if (!isGrpcMethodRoute(route) || unframedHint in route.contentHints) {
                continue
            }
            routes[index] = route.copy(contentHints = route.contentHints + unframedHint)
        }
    }

    private fun isGrpcMethodRoute(route: ArmeriaRoute): Boolean =
        route.routeMatch == RouteMatch.NON_HTTP &&
            route.protocol.equals(RouteProtocol.GRPC.presentableName(), ignoreCase = true) &&
            GrpcRoutePath.isMethodPath(route.path)

    fun isProtoRouteDiscoveryEnabled(): Boolean = ArmeriaProtoRouteDiscoverySupport.isEnabled()

    internal fun isGrpcOnClasspath(
        project: Project,
        scope: GlobalSearchScope,
    ): Boolean = ArmeriaProtoRouteDiscoverySupport.isGrpcOnClasspath(project, scope)

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
