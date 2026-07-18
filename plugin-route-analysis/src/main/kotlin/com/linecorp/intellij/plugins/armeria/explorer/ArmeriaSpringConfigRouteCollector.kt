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

internal data class SpringArmeriaPortBinding(
    val port: String,
    val protocols: List<String>,
    val element: PsiElement? = null,
)

internal data class SpringArmeriaConfig(
    val ports: List<SpringArmeriaPortBinding> = emptyList(),
    val includes: Set<String> = emptySet(),
    val docsPath: String = ArmeriaSpringConfigRouteCollector.DEFAULT_DOCS_PATH,
    val healthPath: String = ArmeriaSpringConfigRouteCollector.DEFAULT_HEALTH_PATH,
    val metricsPath: String = ArmeriaSpringConfigRouteCollector.DEFAULT_METRICS_PATH,
    val internalServicesPort: String? = null,
    val includeElement: PsiElement? = null,
    val docsPathElement: PsiElement? = null,
    val healthPathElement: PsiElement? = null,
    val metricsPathElement: PsiElement? = null,
)

internal object ArmeriaSpringConfigRouteCollector {
    const val DEFAULT_DOCS_PATH = "/internal/docs"
    const val DEFAULT_HEALTH_PATH = "/internal/healthcheck"
    const val DEFAULT_METRICS_PATH = "/internal/metrics"

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

    private val INTERNAL_SERVICE_SPECS = listOf(
        InternalServiceSpec(
            id = "docs",
            path = { it.docsPath },
            pathElement = { it.docsPathElement ?: it.includeElement },
            messageKey = "route.explorer.spring.docService",
            protocol = RouteProtocol.DOC_SERVICE.presentableName(),
            httpMethod = "",
            isDocService = true,
            routeMatch = RouteMatch.NON_HTTP,
        ),
        InternalServiceSpec(
            id = "health",
            path = { it.healthPath },
            pathElement = { it.healthPathElement ?: it.includeElement },
            messageKey = "route.explorer.spring.healthCheck",
            protocol = RouteProtocol.HTTP.presentableName(),
            httpMethod = "GET",
            isDocService = false,
            routeMatch = RouteMatch.CONFIG,
        ),
        InternalServiceSpec(
            id = "metrics",
            path = { it.metricsPath },
            pathElement = { it.metricsPathElement ?: it.includeElement },
            messageKey = "route.explorer.spring.metrics",
            protocol = RouteProtocol.HTTP.presentableName(),
            httpMethod = "GET",
            isDocService = false,
            routeMatch = RouteMatch.CONFIG,
        ),
        InternalServiceSpec(
            id = "actuator",
            path = { "/actuator" },
            pathElement = { it.includeElement },
            messageKey = "route.explorer.spring.actuator",
            protocol = RouteProtocol.HTTP.presentableName(),
            httpMethod = "GET",
            isDocService = false,
            routeMatch = RouteMatch.CONFIG,
        ),
    )

    private val INTERNAL_SERVICE_IDS: Set<String> =
        INTERNAL_SERVICE_SPECS.mapTo(linkedSetOf()) { it.id }

    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        seenConfigRoutes: MutableSet<String>,
    ) {
        // Resolve only filenames that match application*.{yml,yaml,properties} instead of
        // enumerating every yml/yaml/properties file in scope (common in large Spring repos).
        val configFiles = FilenameIndex.getAllFilenames(project)
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
        val config = when {
            psiFile.name.endsWith(".properties") -> parseProperties(text)
            isYamlPluginAvailable() -> ArmeriaYamlSpringConfigReader.read(psiFile) ?: return
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
            val protocols = splitScalarList(stripPropertiesInlineComment(match.groupValues[3])).map { it.uppercase() }
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
        val ports = portsByIndex.entries
            .sortedBy { it.key }
            .map { (index, port) ->
                val protocols = protocolsByIndex[index]
                    ?.entries
                    ?.sortedBy { it.key }
                    ?.map { it.value }
                    ?.distinct()
                    .orEmpty()
                    .ifEmpty { listOf("HTTP") }
                SpringArmeriaPortBinding(port = port, protocols = protocols)
            }

        // Unindexed keys are last-wins; indexed keys (include[N]) are last-wins per index.
        val includesByIndex = linkedMapOf<Int, Set<String>>()
        var unindexedIncludes: Set<String>? = null
        PROPERTIES_INCLUDE_PATTERN.findAll(text).forEach { match ->
            val rawValue = stripPropertiesInlineComment(match.groupValues[2])
            val tokens = parseIncludeTokens(rawValue)
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
        val includes = when {
            includesByIndex.isNotEmpty() -> includesByIndex.values.flatten().toSet()
            unindexedIncludes != null -> unindexedIncludes
            else -> emptySet()
        }
        return SpringArmeriaConfig(
            ports = ports,
            includes = expandIncludes(includes),
            docsPath = lastPropertiesMatch(PROPERTIES_DOCS_PATH_PATTERN, text)
                ?.let { stripPropertiesInlineComment(it).trimQuotes() }
                ?: DEFAULT_DOCS_PATH,
            healthPath = lastPropertiesMatch(PROPERTIES_HEALTH_PATH_PATTERN, text)
                ?.let { stripPropertiesInlineComment(it).trimQuotes() }
                ?: DEFAULT_HEALTH_PATH,
            metricsPath = lastPropertiesMatch(PROPERTIES_METRICS_PATH_PATTERN, text)
                ?.let { stripPropertiesInlineComment(it).trimQuotes() }
                ?: DEFAULT_METRICS_PATH,
            internalServicesPort = lastPropertiesMatch(PROPERTIES_INTERNAL_PORT_PATTERN, text)
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
                element = binding.element ?: fallbackElement,
                path = ":${binding.port}",
                target = profileAwareTarget(
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
                element = spec.pathElement(config) ?: fallbackElement,
                path = path,
                target = profileAwareTarget(message(spec.messageKey) + portSuffix, profile),
                protocol = spec.protocol,
                httpMethod = spec.httpMethod,
                routeMatch = spec.routeMatch,
                isDocService = spec.isDocService,
                dedupeKey = profileAwareKey(
                    "${spec.id}:$path:${config.internalServicesPort.orEmpty()}",
                    profile,
                ),
                routes = routes,
                seenConfigRoutes = seenConfigRoutes,
            )
        }
    }

    internal fun expandIncludes(raw: Set<String>): Set<String> {
        if (raw.any { it.equals("all", ignoreCase = true) }) {
            return INTERNAL_SERVICE_IDS
        }
        return raw.map { it.lowercase() }.filter { it in INTERNAL_SERVICE_IDS }.toSet()
    }

    internal fun parseIncludeTokens(raw: String): Set<String> =
        splitScalarList(raw).map { it.lowercase() }.toSet()

    internal fun splitScalarList(raw: String): List<String> {
        val normalized = raw.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .trim()
        if (normalized.isEmpty()) {
            return emptyList()
        }
        return normalized.split(',', ' ', '\t')
            .map { stripInlineComment(it).trimQuotes() }
            .filter { it.isNotEmpty() }
    }

    private fun isYamlPluginAvailable(): Boolean =
        PluginManagerCore.isLoaded(YAML_PLUGIN_ID)

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
        routes += ArmeriaRoute.create(
            element = element,
            protocol = protocol,
            httpMethod = httpMethod,
            path = path,
            target = target,
            routeMatch = routeMatch,
            isDocService = isDocService,
        )
    }

    private fun profileAwareTarget(base: String, profile: String?): String =
        if (profile.isNullOrEmpty()) base else "$base [$profile]"

    private fun profileAwareKey(base: String, profile: String?): String =
        if (profile.isNullOrEmpty()) base else "$base@$profile"

    private fun String.trimQuotes(): String =
        trim().removeSurrounding("\"").removeSurrounding("'")

    private val PROPERTIES_INLINE_COMMENT = Regex("""\s+[#!].*$""")
    private val INLINE_COMMENT = Regex("""\s+#.*$""")

    private fun lastPropertiesMatch(pattern: Regex, text: String): String? =
        pattern.findAll(text).lastOrNull()?.groupValues?.get(1)

    private fun stripPropertiesInlineComment(raw: String): String =
        raw.trim().replace(PROPERTIES_INLINE_COMMENT, "").trimEnd()

    private fun stripInlineComment(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return trimmed
        }
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))
        ) {
            return trimmed
        }
        if (trimmed.startsWith("#")) {
            return ""
        }
        return trimmed.replace(INLINE_COMMENT, "").trimEnd()
    }
}
