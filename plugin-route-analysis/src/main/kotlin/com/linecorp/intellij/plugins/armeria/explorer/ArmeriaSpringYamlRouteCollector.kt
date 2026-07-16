package com.linecorp.intellij.plugins.armeria.explorer

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
)

internal data class SpringArmeriaConfig(
    val ports: List<SpringArmeriaPortBinding> = emptyList(),
    val includes: Set<String> = emptySet(),
    val docsPath: String = ArmeriaSpringYamlRouteCollector.DEFAULT_DOCS_PATH,
    val healthPath: String = ArmeriaSpringYamlRouteCollector.DEFAULT_HEALTH_PATH,
    val metricsPath: String = ArmeriaSpringYamlRouteCollector.DEFAULT_METRICS_PATH,
    val internalServicesPort: String? = null,
)

internal object ArmeriaSpringYamlRouteCollector {
    const val DEFAULT_DOCS_PATH = "/internal/docs"
    const val DEFAULT_HEALTH_PATH = "/internal/healthcheck"
    const val DEFAULT_METRICS_PATH = "/internal/metrics"

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
            """${PROPERTIES_LINE_START}armeria\.(?:internal-services|internalServices)\.include(?:\[\d+])?\s*$PROPERTIES_DELIMITER\s*(.+)""",
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
            messageKey = "route.explorer.spring.docService",
            protocol = RouteProtocol.DOC_SERVICE.presentableName(),
            httpMethod = "",
            isDocService = true,
            routeMatch = RouteMatch.NON_HTTP,
        ),
        InternalServiceSpec(
            id = "health",
            path = { it.healthPath },
            messageKey = "route.explorer.spring.healthCheck",
            protocol = RouteProtocol.HTTP.presentableName(),
            httpMethod = "GET",
            isDocService = false,
            routeMatch = RouteMatch.CONFIG,
        ),
        InternalServiceSpec(
            id = "metrics",
            path = { it.metricsPath },
            messageKey = "route.explorer.spring.metrics",
            protocol = RouteProtocol.HTTP.presentableName(),
            httpMethod = "GET",
            isDocService = false,
            routeMatch = RouteMatch.CONFIG,
        ),
        InternalServiceSpec(
            id = "actuator",
            path = { "/actuator" },
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
        val config = if (psiFile.name.endsWith(".properties")) {
            parseProperties(text)
        } else {
            parseYaml(text)
        }
        emitRoutes(config, psiFile, profileFromFileName(psiFile.name), routes, seenConfigRoutes)
    }

    internal fun parseYaml(text: String): SpringArmeriaConfig {
        val armeriaBlock = extractTopLevelBlock(text, "armeria") ?: return SpringArmeriaConfig()
        val ports = parseYamlPorts(extractChildBlock(armeriaBlock, "ports"))
        val internalServicesBlock = extractChildBlock(armeriaBlock, "internal-services", "internalServices")
        val includes = parseYamlInclude(internalServicesBlock)
        val internalServicesPort = readYamlScalar(internalServicesBlock, "port")
        return SpringArmeriaConfig(
            ports = ports,
            includes = includes,
            docsPath = readYamlScalar(armeriaBlock, "docs-path", "docsPath") ?: DEFAULT_DOCS_PATH,
            healthPath = readYamlScalar(armeriaBlock, "health-check-path", "healthCheckPath")
                ?: DEFAULT_HEALTH_PATH,
            metricsPath = readYamlScalar(armeriaBlock, "metrics-path", "metricsPath") ?: DEFAULT_METRICS_PATH,
            internalServicesPort = internalServicesPort,
        )
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

        val includes = mutableSetOf<String>()
        PROPERTIES_INCLUDE_PATTERN.findAll(text).forEach { match ->
            includes += parseIncludeTokens(stripPropertiesInlineComment(match.groupValues[1]))
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
        element: PsiElement,
        profile: String?,
        routes: MutableList<ArmeriaRoute>,
        seenConfigRoutes: MutableSet<String>,
    ) {
        for (binding in config.ports) {
            val protocolLabel = binding.protocols.joinToString(", ")
            val primaryProtocol = binding.protocols.firstOrNull() ?: "HTTP"
            // Synthetic path so the tree row (pill + path) shows the port; "/" collides with
            // real root mounts and hides the binding in the explorer list.
            addConfigRoute(
                element = element,
                path = ":${binding.port}",
                target = profileAwareTarget(
                    message("route.explorer.spring.port", binding.port, protocolLabel),
                    profile,
                ),
                protocol = primaryProtocol,
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
                element = element,
                path = path,
                target = profileAwareTarget(message(spec.messageKey) + portSuffix, profile),
                protocol = spec.protocol,
                httpMethod = spec.httpMethod,
                routeMatch = spec.routeMatch,
                isDocService = spec.isDocService,
                dedupeKey = profileAwareKey("${spec.id}:$path", profile),
                routes = routes,
                seenConfigRoutes = seenConfigRoutes,
            )
        }
    }

    private fun isApplicationConfigFile(name: String): Boolean = APPLICATION_FILE_PATTERN.matches(name)

    private fun profileFromFileName(name: String): String? {
        val match = APPLICATION_FILE_PATTERN.matchEntire(name) ?: return null
        val suffix = match.groupValues[1]
        return suffix.removePrefix("-").takeIf { it.isNotEmpty() }
    }

    private fun parseYamlPorts(portsBlock: String?): List<SpringArmeriaPortBinding> {
        if (portsBlock.isNullOrBlank()) {
            return emptyList()
        }
        val items = splitYamlListItems(portsBlock)
        val bindings = mutableListOf<SpringArmeriaPortBinding>()
        val seenPorts = mutableSetOf<String>()
        for (item in items) {
            val values = parseYamlMapping(item)
            val port = values["port"]?.firstOrNull() ?: continue
            if (!seenPorts.add(port)) {
                continue
            }
            val protocols = values["protocols"]
                ?.map { it.uppercase() }
                ?.distinct()
                .orEmpty()
                .ifEmpty { listOf("HTTP") }
            bindings += SpringArmeriaPortBinding(port = port, protocols = protocols)
        }
        return bindings
    }

    private fun parseYamlInclude(internalServicesBlock: String?): Set<String> {
        if (internalServicesBlock.isNullOrBlank()) {
            return emptySet()
        }
        val lines = internalServicesBlock.lineSequence().toList()
        var topIndent: Int? = null
        val includeIndex = lines.indexOfFirst { line ->
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                return@indexOfFirst false
            }
            val indent = line.takeWhile { it.isWhitespace() }.length
            if (topIndent == null) {
                topIndent = indent
            } else if (indent > topIndent) {
                // Nested under another child of internal-services — skip.
                return@indexOfFirst false
            }
            val trimmed = line.trim()
            trimmed == "include:" || trimmed.startsWith("include:")
        }
        if (includeIndex < 0) {
            return emptySet()
        }
        val includeLine = lines[includeIndex]
        val inline = stripYamlInlineComment(includeLine.substringAfter("include:", missingDelimiterValue = ""))
        val tokens = mutableListOf<String>()
        if (inline.isNotEmpty()) {
            tokens += parseIncludeTokens(inline)
        }
        val includeIndent = includeLine.takeWhile { it.isWhitespace() }.length
        for (index in includeIndex + 1 until lines.size) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                continue
            }
            val indent = line.takeWhile { it.isWhitespace() }.length
            if (indent <= includeIndent) {
                break
            }
            val trimmed = line.trim()
            if (trimmed.startsWith("- ")) {
                tokens += parseIncludeTokens(stripYamlInlineComment(trimmed.removePrefix("- ")))
            }
        }
        return expandIncludes(tokens.toSet())
    }

    private fun expandIncludes(raw: Set<String>): Set<String> {
        if (raw.any { it.equals("all", ignoreCase = true) }) {
            return INTERNAL_SERVICE_IDS
        }
        return raw.map { it.lowercase() }.filter { it in INTERNAL_SERVICE_IDS }.toSet()
    }

    private fun parseIncludeTokens(raw: String): Set<String> =
        splitScalarList(raw).map { it.lowercase() }.toSet()

    private fun splitScalarList(raw: String): List<String> {
        val normalized = raw.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .trim()
        if (normalized.isEmpty()) {
            return emptyList()
        }
        return normalized.split(',', ' ', '\t')
            .map { stripYamlInlineComment(it).trimQuotes() }
            .filter { it.isNotEmpty() }
    }

    private fun extractTopLevelBlock(text: String, key: String): String? {
        val lines = text.lineSequence().toList()
        val startIndex = lines.indexOfFirst { line ->
            if (line.takeWhile { it.isWhitespace() }.isNotEmpty()) {
                return@indexOfFirst false
            }
            val trimmed = line.trim()
            !trimmed.startsWith("#") && matchesYamlKey(trimmed, key)
        }
        if (startIndex < 0) {
            return null
        }
        return collectIndentedBlock(lines, startIndex, parentIndent = 0)
    }

    private fun extractChildBlock(parentBlock: String, vararg keys: String): String? {
        val lines = parentBlock.lineSequence().toList()
        var topIndent: Int? = null
        val startIndex = lines.indexOfFirst { line ->
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                return@indexOfFirst false
            }
            val indent = line.takeWhile { it.isWhitespace() }.length
            if (topIndent == null) {
                topIndent = indent
            } else if (indent > topIndent) {
                // Nested under another child of the parent — skip (e.g. armeria.foo.ports).
                return@indexOfFirst false
            }
            val trimmed = line.trim()
            keys.any { matchesYamlKey(trimmed, it) }
        }
        if (startIndex < 0) {
            return null
        }
        val childIndent = lines[startIndex].takeWhile { it.isWhitespace() }.length
        return collectIndentedBlock(lines, startIndex, childIndent)
    }

    private fun collectIndentedBlock(lines: List<String>, startIndex: Int, parentIndent: Int): String {
        val childLines = mutableListOf<String>()
        for (index in startIndex + 1 until lines.size) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                if (childLines.isNotEmpty()) {
                    childLines += line
                }
                continue
            }
            val indent = line.takeWhile { it.isWhitespace() }.length
            if (indent <= parentIndent) {
                break
            }
            childLines += line
        }
        return childLines.joinToString("\n")
    }

    private fun splitYamlListItems(block: String): List<String> {
        val lines = block.lineSequence().toList()
        val items = mutableListOf<String>()
        var current = mutableListOf<String>()
        var itemIndent = -1
        for (line in lines) {
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                if (current.isNotEmpty()) {
                    current += line
                }
                continue
            }
            val indent = line.takeWhile { it.isWhitespace() }.length
            val trimmed = line.trimStart()
            if (trimmed.startsWith("- ")) {
                if (itemIndent < 0 || indent <= itemIndent) {
                    if (current.isNotEmpty()) {
                        items += current.joinToString("\n")
                    }
                    current = mutableListOf(line)
                    itemIndent = indent
                } else {
                    current += line
                }
                continue
            }
            if (itemIndent >= 0 && indent > itemIndent) {
                current += line
            }
        }
        if (current.isNotEmpty()) {
            items += current.joinToString("\n")
        }
        return items
    }

    private fun parseYamlMapping(itemText: String): Map<String, List<String>> {
        val values = linkedMapOf<String, MutableList<String>>()
        // Normalize only the leading list marker on the first line so sibling keys share indent.
        val rawLines = itemText.lineSequence().toList()
        val lines = rawLines.mapIndexed { index, line ->
            if (index != 0) {
                return@mapIndexed line
            }
            val match = LIST_ITEM_PREFIX.find(line) ?: return@mapIndexed line
            match.groupValues[1] + "  " + line.substring(match.range.last + 1)
        }
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                index++
                continue
            }
            val trimmed = line.trim()
            if (!trimmed.contains(':')) {
                index++
                continue
            }
            val key = trimmed.substringBefore(':').trim()
            val inlineValue = stripYamlInlineComment(trimmed.substringAfter(':', missingDelimiterValue = ""))
            val keyIndent = line.takeWhile { it.isWhitespace() }.length
            if (inlineValue.isNotEmpty()) {
                values.getOrPut(key) { mutableListOf() } += splitScalarList(inlineValue)
                index++
                continue
            }
            val nested = mutableListOf<String>()
            var nestedIndex = index + 1
            while (nestedIndex < lines.size) {
                val nestedLine = lines[nestedIndex]
                if (nestedLine.isBlank() || nestedLine.trimStart().startsWith("#")) {
                    nestedIndex++
                    continue
                }
                val nestedIndent = nestedLine.takeWhile { it.isWhitespace() }.length
                if (nestedIndent <= keyIndent) {
                    break
                }
                val nestedTrimmed = nestedLine.trim()
                if (nestedTrimmed.startsWith("- ")) {
                    nested += stripYamlInlineComment(nestedTrimmed.removePrefix("- ")).trimQuotes()
                } else if (nestedTrimmed.contains(':')) {
                    break
                } else {
                    nested += stripYamlInlineComment(nestedTrimmed).trimQuotes()
                }
                nestedIndex++
            }
            if (nested.isNotEmpty()) {
                values.getOrPut(key) { mutableListOf() } += nested
            }
            index = nestedIndex
        }
        return values
    }

    private val LIST_ITEM_PREFIX = Regex("""^(\s*)- """)

    private fun readYamlScalar(block: String?, vararg keys: String): String? {
        if (block.isNullOrBlank()) {
            return null
        }
        val keySet = keys.toSet()
        var topIndent: Int? = null
        for (line in block.lineSequence()) {
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                continue
            }
            val indent = line.takeWhile { it.isWhitespace() }.length
            if (topIndent == null) {
                topIndent = indent
            } else if (indent > topIndent) {
                continue
            }
            val trimmed = line.trim()
            if (!trimmed.contains(':')) {
                continue
            }
            val key = trimmed.substringBefore(':').trim()
            if (key !in keySet) {
                continue
            }
            val value = stripYamlInlineComment(trimmed.substringAfter(':', missingDelimiterValue = ""))
            if (value.isNotEmpty()) {
                return value.trimQuotes()
            }
        }
        return null
    }

    private fun matchesYamlKey(trimmed: String, key: String): Boolean =
        trimmed == "$key:" || trimmed.startsWith("$key:")

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

    private val YAML_INLINE_COMMENT = Regex("""\s+#.*$""")
    private val PROPERTIES_INLINE_COMMENT = Regex("""\s+[#!].*$""")

    private fun lastPropertiesMatch(pattern: Regex, text: String): String? =
        pattern.findAll(text).lastOrNull()?.groupValues?.get(1)

    private fun stripPropertiesInlineComment(raw: String): String =
        raw.trim().replace(PROPERTIES_INLINE_COMMENT, "").trimEnd()

    private fun stripYamlInlineComment(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return trimmed
        }
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))
        ) {
            return trimmed
        }
        return trimmed.replace(YAML_INLINE_COMMENT, "").trimEnd()
    }
}
