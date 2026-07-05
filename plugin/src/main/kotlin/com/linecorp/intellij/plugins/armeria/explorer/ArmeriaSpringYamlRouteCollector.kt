package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaSpringYamlRouteCollector {
    private val APPLICATION_FILE_NAMES = setOf(
        "application.yml",
        "application.yaml",
        "application.properties",
    )
    private val PORT_PATTERN = Regex("""\bport\s*:\s*(\d+)\b""")
    private val PROTOCOLS_PATTERN = Regex("""\bprotocols\s*:\s*(.+)$""")
    private val INCLUDE_PATTERN = Regex("""\binclude\s*:\s*(.+)$""", RegexOption.MULTILINE)
    private val DOCS_PATH_PATTERN = Regex("""\b(?:docs-path|docsPath)\s*[:=]\s*["']?([^"'\s#]+)["']?""")
    private val HEALTH_PATH_PATTERN = Regex("""\b(?:health-check-path|healthCheckPath)\s*[:=]\s*["']?([^"'\s#]+)["']?""")
    private val METRICS_PATH_PATTERN = Regex("""\b(?:metrics-path|metricsPath)\s*[:=]\s*["']?([^"'\s#]+)["']?""")
    private val PROPERTIES_PORT_PATTERN = Regex("""\barmeria\.ports\[\d+]\.port\s*=\s*(\d+)""")
    private val PROPERTIES_PROTOCOL_PATTERN = Regex("""\barmeria\.ports\[\d+]\.protocols\[\d+]\s*=\s*(\w+)""")
    private val PROPERTIES_INCLUDE_PATTERN = Regex("""\barmeria\.internal-services\.include\s*=\s*(.+)""")

    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        seenConfigRoutes: MutableSet<String>,
    ) {
        collectByExtension(project, scope, "yml", routes, seenConfigRoutes)
        collectByExtension(project, scope, "yaml", routes, seenConfigRoutes)
        collectByExtension(project, scope, "properties", routes, seenConfigRoutes)
    }

    internal fun collectFromPsiFile(
        psiFile: PsiFile,
        routes: MutableList<ArmeriaRoute>,
        seenConfigRoutes: MutableSet<String>,
    ) {
        val text = psiFile.text
        if (!text.contains("armeria")) {
            return
        }
        if (psiFile.name.endsWith(".properties")) {
            collectFromProperties(psiFile, text, routes, seenConfigRoutes)
        } else {
            collectFromYaml(psiFile, text, routes, seenConfigRoutes)
        }
    }

    private fun collectByExtension(
        project: Project,
        scope: GlobalSearchScope,
        extension: String,
        routes: MutableList<ArmeriaRoute>,
        seenConfigRoutes: MutableSet<String>,
    ) {
        for (virtualFile in FilenameIndex.getAllFilesByExt(project, extension, scope)) {
            if (virtualFile.name !in APPLICATION_FILE_NAMES) {
                continue
            }
            collectFromFile(project, virtualFile, routes, seenConfigRoutes)
        }
    }

    private fun collectFromFile(
        project: Project,
        virtualFile: VirtualFile,
        routes: MutableList<ArmeriaRoute>,
        seenConfigRoutes: MutableSet<String>,
    ) {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return
        val text = psiFile.text
        if (!text.contains("armeria")) {
            return
        }
        if (virtualFile.name.endsWith(".properties")) {
            collectFromProperties(psiFile, text, routes, seenConfigRoutes)
        } else {
            collectFromYaml(psiFile, text, routes, seenConfigRoutes)
        }
    }

    private fun collectFromYaml(
        psiFile: PsiFile,
        text: String,
        routes: MutableList<ArmeriaRoute>,
        seenConfigRoutes: MutableSet<String>,
    ) {
        if (!text.contains("armeria:")) {
            return
        }
        val docsPath = DOCS_PATH_PATTERN.find(text)?.groupValues?.get(1) ?: "/internal/docs"
        val healthPath = HEALTH_PATH_PATTERN.find(text)?.groupValues?.get(1) ?: "/internal/healthcheck"
        val metricsPath = METRICS_PATH_PATTERN.find(text)?.groupValues?.get(1) ?: "/internal/metrics"
        val includes = parseIncludeList(INCLUDE_PATTERN.find(text)?.groupValues?.get(1).orEmpty())

        collectPortRoutesFromYaml(psiFile, text, routes, seenConfigRoutes)
        collectInternalServiceRoutes(psiFile, includes, docsPath, healthPath, metricsPath, routes, seenConfigRoutes)
    }

    private fun collectFromProperties(
        psiFile: PsiFile,
        text: String,
        routes: MutableList<ArmeriaRoute>,
        seenConfigRoutes: MutableSet<String>,
    ) {
        if (!text.contains("armeria.")) {
            return
        }
        val docsPath = DOCS_PATH_PATTERN.find(text)?.groupValues?.get(1) ?: "/internal/docs"
        val healthPath = HEALTH_PATH_PATTERN.find(text)?.groupValues?.get(1) ?: "/internal/healthcheck"
        val metricsPath = METRICS_PATH_PATTERN.find(text)?.groupValues?.get(1) ?: "/internal/metrics"
        val includes = parseIncludeList(PROPERTIES_INCLUDE_PATTERN.find(text)?.groupValues?.get(1).orEmpty())

        PROPERTIES_PORT_PATTERN.findAll(text).forEach { match ->
            val port = match.groupValues[1]
            val protocol = PROPERTIES_PROTOCOL_PATTERN.find(text)?.groupValues?.get(1)?.uppercase() ?: "HTTP"
            addConfigRoute(
                element = psiFile,
                path = "/",
                target = message("route.explorer.spring.port", port, protocol),
                protocol = message("route.explorer.protocol.http"),
                isDocService = false,
                dedupeKey = "port:$port:$protocol",
                routes = routes,
                seenConfigRoutes = seenConfigRoutes,
            )
        }
        collectInternalServiceRoutes(psiFile, includes, docsPath, healthPath, metricsPath, routes, seenConfigRoutes)
    }

    private fun collectPortRoutesFromYaml(
        psiFile: PsiFile,
        text: String,
        routes: MutableList<ArmeriaRoute>,
        seenConfigRoutes: MutableSet<String>,
    ) {
        val seenPorts = mutableSetOf<String>()
        PORT_PATTERN.findAll(text).forEach { match ->
            val port = match.groupValues[1]
            if (!seenPorts.add(port)) {
                return@forEach
            }
            val protocol = PROTOCOLS_PATTERN.find(text)?.groupValues?.get(1)
                ?.substringAfter('-')
                ?.trim()
                ?.uppercase()
                ?: "HTTP"
            addConfigRoute(
                element = psiFile,
                path = "/",
                target = message("route.explorer.spring.port", port, protocol),
                protocol = message("route.explorer.protocol.http"),
                isDocService = false,
                dedupeKey = "port:$port:$protocol",
                routes = routes,
                seenConfigRoutes = seenConfigRoutes,
            )
        }
    }

    private fun collectInternalServiceRoutes(
        element: PsiElement,
        includes: Set<String>,
        docsPath: String,
        healthPath: String,
        metricsPath: String,
        routes: MutableList<ArmeriaRoute>,
        seenConfigRoutes: MutableSet<String>,
    ) {
        if (includes.isEmpty()) {
            return
        }
        if ("all" in includes || "docs" in includes) {
            addConfigRoute(
                element = element,
                path = docsPath,
                target = message("route.explorer.spring.docService"),
                protocol = message("route.explorer.protocol.docService"),
                isDocService = true,
                dedupeKey = "docs:$docsPath",
                routes = routes,
                seenConfigRoutes = seenConfigRoutes,
            )
        }
        if ("all" in includes || "health" in includes) {
            addConfigRoute(
                element = element,
                path = healthPath,
                target = message("route.explorer.spring.healthCheck"),
                protocol = message("route.explorer.protocol.http"),
                isDocService = false,
                dedupeKey = "health:$healthPath",
                routes = routes,
                seenConfigRoutes = seenConfigRoutes,
            )
        }
        if ("all" in includes || "metrics" in includes) {
            addConfigRoute(
                element = element,
                path = metricsPath,
                target = message("route.explorer.spring.metrics"),
                protocol = message("route.explorer.protocol.http"),
                isDocService = false,
                dedupeKey = "metrics:$metricsPath",
                routes = routes,
                seenConfigRoutes = seenConfigRoutes,
            )
        }
        if ("actuator" in includes) {
            addConfigRoute(
                element = element,
                path = "/actuator",
                target = message("route.explorer.spring.actuator"),
                protocol = message("route.explorer.protocol.http"),
                isDocService = false,
                dedupeKey = "actuator",
                routes = routes,
                seenConfigRoutes = seenConfigRoutes,
            )
        }
    }

    private fun addConfigRoute(
        element: PsiElement,
        path: String,
        target: String,
        protocol: String,
        isDocService: Boolean,
        dedupeKey: String,
        routes: MutableList<ArmeriaRoute>,
        seenConfigRoutes: MutableSet<String>,
    ) {
        val moduleKey = "${ArmeriaRouteMetadata.moduleName(element)}:$dedupeKey"
        if (!seenConfigRoutes.add(moduleKey)) {
            return
        }
        routes += ArmeriaRoute.create(
            element = element,
            protocol = protocol,
            httpMethod = "",
            path = path,
            target = target,
            routeMatch = RouteMatch.NON_HTTP,
            isDocService = isDocService,
        )
    }

    private fun parseIncludeList(raw: String): Set<String> =
        raw.split(',', ' ', '\t')
            .map { it.trim().lowercase().removeSurrounding("\"", "\"").removeSurrounding("'", "'") }
            .filter { it.isNotEmpty() }
            .toSet()
}
