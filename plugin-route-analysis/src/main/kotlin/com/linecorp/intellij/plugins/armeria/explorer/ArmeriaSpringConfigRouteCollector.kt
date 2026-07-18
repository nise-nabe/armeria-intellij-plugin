package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaSpringConfigRouteCollector {
    private val YAML_PLUGIN_ID = PluginId.getId("org.jetbrains.plugins.yaml")

    private val APPLICATION_FILE_PATTERN = Regex("""^application(-[\w.-]+)?\.(yml|yaml|properties)$""")
    private val PROPERTIES_DELIMITER = """[:=]"""
    private val PROPERTIES_LINE_START = """^\s*"""
    private val PROPERTIES_MULTILINE = setOf(RegexOption.MULTILINE)
    private val PROPERTIES_PORT_PATTERN =
        Regex(
            """${PROPERTIES_LINE_START}armeria\.ports\[(\d+)]\.port\s*$PROPERTIES_DELIMITER\s*(\S+)""",
            PROPERTIES_MULTILINE,
        )
    private val PROPERTIES_PROTOCOL_PATTERN =
        Regex(
            """${PROPERTIES_LINE_START}armeria\.ports\[(\d+)]\.protocols(?:\[(\d+)])?\s*$PROPERTIES_DELIMITER\s*(.+)""",
            PROPERTIES_MULTILINE,
        )
    private val PROPERTIES_INCLUDE_PATTERN =
        Regex(
            """${PROPERTIES_LINE_START}armeria\.(?:internal-services|internalServices)\.include(?:\[(\d+)])?\s*$PROPERTIES_DELIMITER\s*(.+)""",
            PROPERTIES_MULTILINE,
        )
    private val PROPERTIES_INTERNAL_PORT_PATTERN =
        Regex(
            """${PROPERTIES_LINE_START}armeria\.(?:internal-services|internalServices)\.port\s*$PROPERTIES_DELIMITER\s*(\S+)""",
            PROPERTIES_MULTILINE,
        )
    private val PROPERTIES_DOCS_PATH_PATTERN =
        Regex(
            """${PROPERTIES_LINE_START}armeria\.(?:docs-path|docsPath)\s*$PROPERTIES_DELIMITER\s*(\S+)""",
            PROPERTIES_MULTILINE,
        )
    private val PROPERTIES_HEALTH_PATH_PATTERN =
        Regex(
            """${PROPERTIES_LINE_START}armeria\.(?:health-check-path|healthCheckPath)\s*$PROPERTIES_DELIMITER\s*(\S+)""",
            PROPERTIES_MULTILINE,
        )
    private val PROPERTIES_METRICS_PATH_PATTERN =
        Regex(
            """${PROPERTIES_LINE_START}armeria\.(?:metrics-path|metricsPath)\s*$PROPERTIES_DELIMITER\s*(\S+)""",
            PROPERTIES_MULTILINE,
        )

    private data class InternalServiceSpec(
        val id: String,
        val path: (SpringArmeriaConfig) -> String,
        val pathElement: (SpringArmeriaConfig) -> PsiElement?,
        val messageKey: String,
        val protocol: String,
        val httpMethod: String,
        val isDocService: Boolean,
        val routeMatch: RouteMatch,
    )

    private val INTERNAL_SERVICE_SPECS =
        listOf(
            InternalServiceSpec(
                id = SpringArmeriaConfigSemantics.ID_DOCS,
                path = { it.resolvedDocsPath() },
                pathElement = { it.docsPathElement },
                messageKey = "route.explorer.spring.docService",
                protocol = RouteProtocol.DOC_SERVICE.presentableName(),
                httpMethod = "",
                isDocService = true,
                routeMatch = RouteMatch.NON_HTTP,
            ),
            InternalServiceSpec(
                id = SpringArmeriaConfigSemantics.ID_HEALTH,
                path = { it.resolvedHealthPath() },
                pathElement = { it.healthPathElement },
                messageKey = "route.explorer.spring.healthCheck",
                protocol = RouteProtocol.HTTP.presentableName(),
                httpMethod = "GET",
                isDocService = false,
                routeMatch = RouteMatch.CONFIG,
            ),
            InternalServiceSpec(
                id = SpringArmeriaConfigSemantics.ID_METRICS,
                path = { it.resolvedMetricsPath() },
                pathElement = { it.metricsPathElement },
                messageKey = "route.explorer.spring.metrics",
                protocol = RouteProtocol.HTTP.presentableName(),
                httpMethod = "GET",
                isDocService = false,
                routeMatch = RouteMatch.CONFIG,
            ),
            InternalServiceSpec(
                id = SpringArmeriaConfigSemantics.ID_ACTUATOR,
                path = { "/actuator" },
                pathElement = { null },
                messageKey = "route.explorer.spring.actuator",
                protocol = RouteProtocol.HTTP.presentableName(),
                httpMethod = "GET",
                isDocService = false,
                routeMatch = RouteMatch.CONFIG,
            ),
        )

    /** Exposed for tests — must stay equal to [SpringArmeriaConfigSemantics.INTERNAL_SERVICE_IDS]. */
    internal fun internalServiceSpecIds(): Set<String> = INTERNAL_SERVICE_SPECS.mapTo(linkedSetOf()) { it.id }

    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        seenConfigRoutes: MutableSet<String>,
    ) {
        // Resolve only filenames that match application*.{yml,yaml,properties} instead of
        // enumerating every yml/yaml/properties file in scope (common in large Spring repos).
        val configFiles =
            FilenameIndex
                .getAllFilenames(project)
                .asSequence()
                .filter { isApplicationConfigFile(it) }
                .flatMap { name -> FilenameIndex.getVirtualFilesByName(name, scope) }
                .sortedWith(compareBy({ it.path }, { it.name }))
        val psiManager = PsiManager.getInstance(project)
        for (virtualFile in configFiles) {
            val psiFile = psiManager.findFile(virtualFile) ?: continue
            collectFromPsiFile(psiFile, routes, seenConfigRoutes)
        }
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
        val config =
            when {
                psiFile.name.endsWith(".properties") -> parseProperties(text)
                isYamlPluginAvailable() -> ArmeriaYamlSpringConfigReader.read(psiFile)
                else -> return
            }
        emitRoutes(config, psiFile, profileFromFileName(psiFile.name), routes, seenConfigRoutes)
    }

    internal fun parseProperties(text: String): SpringArmeriaConfig {
        if (!text.contains("armeria.")) {
            return SpringArmeriaConfig()
        }
        val portsByIndex = mutableMapOf<Int, String>()
        PROPERTIES_PORT_PATTERN.findAll(text).forEach { match ->
            val portIndex = match.groupValues[1].toIntOrNull() ?: return@forEach
            portsByIndex[portIndex] = stripPropertiesInlineComment(match.groupValues[2])
        }
        // portIndex -> (protocolIndex -> protocol); later assignments overwrite (last-wins).
        val protocolsByIndex = mutableMapOf<Int, MutableMap<Int, String>>()
        PROPERTIES_PROTOCOL_PATTERN.findAll(text).forEach { match ->
            val portIndex = match.groupValues[1].toIntOrNull() ?: return@forEach
            val protocolIndex = match.groupValues[2].toIntOrNull()
            val protocols =
                SpringArmeriaConfigSemantics
                    .splitScalarList(stripPropertiesInlineComment(match.groupValues[3]))
                    .filter { it.isNotEmpty() }
            if (protocols.isEmpty()) {
                return@forEach
            }
            val bucket = protocolsByIndex.getOrPut(portIndex) { linkedMapOf() }
            if (protocolIndex != null) {
                // Indexed form: armeria.ports[N].protocols[M]=http
                bucket[protocolIndex] = protocols.first()
            } else {
                // Comma / space list replaces any prior protocols for this port index.
                bucket.clear()
                protocols.forEachIndexed { offset, protocol ->
                    bucket[offset] = protocol
                }
            }
        }
        val ports =
            portsByIndex.entries
                .sortedBy { it.key }
                .map { (index, port) ->
                    val protocols =
                        SpringArmeriaConfigSemantics.normalizeProtocols(
                            protocolsByIndex[index]
                                ?.entries
                                ?.sortedBy { it.key }
                                ?.map { it.value }
                                .orEmpty(),
                        )
                    SpringArmeriaPortBinding(port = port, protocols = protocols)
                }

        // Unindexed keys are last-wins; indexed keys (include[N]) are last-wins per index.
        val includesByIndex = linkedMapOf<Int, Set<String>>()
        var unindexedIncludes: Set<String>? = null
        PROPERTIES_INCLUDE_PATTERN.findAll(text).forEach { match ->
            val rawValue = stripPropertiesInlineComment(match.groupValues[2])
            val tokens = SpringArmeriaConfigSemantics.parseIncludeTokens(rawValue)
            val indexGroup = match.groupValues[1]
            if (indexGroup.isEmpty()) {
                unindexedIncludes = tokens
                includesByIndex.clear()
            } else {
                val index = indexGroup.toIntOrNull() ?: return@forEach
                unindexedIncludes = null
                includesByIndex[index] = tokens
            }
        }
        val includes =
            when {
                includesByIndex.isNotEmpty() -> includesByIndex.values.flatten().toSet()
                unindexedIncludes != null -> unindexedIncludes
                else -> emptySet()
            }
        return SpringArmeriaConfig(
            ports = ports,
            includes = SpringArmeriaConfigSemantics.expandIncludes(includes),
            docsPath =
                lastPropertiesMatch(PROPERTIES_DOCS_PATH_PATTERN, text)
                    ?.let { SpringArmeriaConfigSemantics.trimQuotes(stripPropertiesInlineComment(it)) },
            healthPath =
                lastPropertiesMatch(PROPERTIES_HEALTH_PATH_PATTERN, text)
                    ?.let { SpringArmeriaConfigSemantics.trimQuotes(stripPropertiesInlineComment(it)) },
            metricsPath =
                lastPropertiesMatch(PROPERTIES_METRICS_PATH_PATTERN, text)
                    ?.let { SpringArmeriaConfigSemantics.trimQuotes(stripPropertiesInlineComment(it)) },
            internalServicesPort =
                lastPropertiesMatch(PROPERTIES_INTERNAL_PORT_PATTERN, text)
                    ?.let { stripPropertiesInlineComment(it) },
        )
    }

    internal fun emitRoutes(
        config: SpringArmeriaConfig,
        fallbackElement: PsiElement,
        profile: String?,
        routes: MutableList<ArmeriaRoute>,
        seenConfigRoutes: MutableSet<String>,
    ) {
        for (binding in config.ports) {
            val protocolLabel = binding.protocols.joinToString(", ").ifEmpty { "HTTP" }
            // Synthetic path so the tree row (pill + path) shows the port; "/" collides with
            // real root mounts and hides the binding in the explorer list.
            addConfigRoute(
                element = navigationElement(binding.element, fallbackElement),
                path = ":${binding.port}",
                target =
                    profileAwareTarget(
                        message("route.explorer.spring.port", binding.port, protocolLabel),
                        profile,
                    ),
                protocol = protocolLabel,
                httpMethod = "",
                routeMatch = RouteMatch.NON_HTTP,
                isDocService = false,
                dedupeKey = profileAwareKey("port:${binding.port}:$protocolLabel", profile),
                routes = routes,
                seenConfigRoutes = seenConfigRoutes,
            )
        }

        val includes = config.includes
        if (includes.isEmpty()) {
            return
        }
        // Config-sourced internal services are excluded from duplicate-registration analysis
        // (CONFIG and NON_HTTP are not in ArmeriaRouteDuplicateIndex.CHECKED_MATCHES).
        val portSuffix = config.internalServicesPort?.let { " · :$it" }.orEmpty()
        for (spec in INTERNAL_SERVICE_SPECS) {
            if (spec.id !in includes) {
                continue
            }
            val path = spec.path(config)
            addConfigRoute(
                element =
                    navigationElement(
                        spec.pathElement(config) ?: config.includeElement,
                        fallbackElement,
                    ),
                path = path,
                target = profileAwareTarget(message(spec.messageKey) + portSuffix, profile),
                protocol = spec.protocol,
                httpMethod = spec.httpMethod,
                routeMatch = spec.routeMatch,
                isDocService = spec.isDocService,
                dedupeKey =
                    profileAwareKey(
                        "${spec.id}:$path:${config.internalServicesPort.orEmpty()}",
                        profile,
                    ),
                routes = routes,
                seenConfigRoutes = seenConfigRoutes,
            )
        }
    }

    /**
     * Keep key-level YAML PSI only when it lives in the same file the user opened. Dummy trees
     * from Plain Text-typed YAML share no virtual file with [fallback], so navigation falls back.
     */
    private fun navigationElement(
        candidate: PsiElement?,
        fallback: PsiElement,
    ): PsiElement {
        if (candidate == null) {
            return fallback
        }
        val candidateFile = candidate.containingFile
        val fallbackFile = fallback.containingFile
        if (candidateFile === fallbackFile) {
            return candidate
        }
        val candidateVf = candidateFile?.virtualFile
        val fallbackVf = fallbackFile?.virtualFile
        if (candidateVf != null && candidateVf == fallbackVf) {
            return candidate
        }
        return fallback
    }

    private fun isYamlPluginAvailable(): Boolean = PluginManagerCore.isLoaded(YAML_PLUGIN_ID)

    private fun isApplicationConfigFile(name: String): Boolean = APPLICATION_FILE_PATTERN.matches(name)

    private fun profileFromFileName(name: String): String? {
        val match = APPLICATION_FILE_PATTERN.matchEntire(name) ?: return null
        val suffix = match.groupValues[1]
        return suffix.removePrefix("-").takeIf { it.isNotEmpty() }
    }

    private fun addConfigRoute(
        element: PsiElement,
        path: String,
        target: String,
        protocol: String,
        httpMethod: String,
        routeMatch: RouteMatch,
        isDocService: Boolean,
        dedupeKey: String,
        routes: MutableList<ArmeriaRoute>,
        seenConfigRoutes: MutableSet<String>,
    ) {
        val moduleKey = "${ArmeriaRouteMetadata.moduleName(element)}:$dedupeKey"
        if (!seenConfigRoutes.add(moduleKey)) {
            return
        }
        routes +=
            ArmeriaRoute.create(
                element = element,
                protocol = protocol,
                httpMethod = httpMethod,
                path = path,
                target = target,
                routeMatch = routeMatch,
                isDocService = isDocService,
            )
    }

    private fun profileAwareTarget(
        base: String,
        profile: String?,
    ): String = if (profile.isNullOrEmpty()) base else "$base [$profile]"

    private fun profileAwareKey(
        base: String,
        profile: String?,
    ): String = if (profile.isNullOrEmpty()) base else "$base@$profile"

    private val PROPERTIES_INLINE_COMMENT = Regex("""\s+[#!].*$""")

    private fun lastPropertiesMatch(
        pattern: Regex,
        text: String,
    ): String? =
        pattern
            .findAll(text)
            .lastOrNull()
            ?.groupValues
            ?.get(1)

    private fun stripPropertiesInlineComment(raw: String): String = raw.trim().replace(PROPERTIES_INLINE_COMMENT, "").trimEnd()
}
