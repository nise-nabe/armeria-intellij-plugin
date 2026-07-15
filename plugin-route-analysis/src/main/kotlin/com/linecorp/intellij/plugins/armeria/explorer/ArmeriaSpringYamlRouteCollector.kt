package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
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
    val docsPath: String = "/internal/docs",
    val healthPath: String = "/internal/healthcheck",
    val metricsPath: String = "/internal/metrics",
    val internalServicesPort: String? = null,
)

internal object ArmeriaSpringYamlRouteCollector {
    const val DEFAULT_DOCS_PATH = "/internal/docs"
    const val DEFAULT_HEALTH_PATH = "/internal/healthcheck"
    const val DEFAULT_METRICS_PATH = "/internal/metrics"

    private val APPLICATION_FILE_PATTERN = Regex("""^application(-[\w.-]+)?\.(yml|yaml|properties)$""")
    private val PROPERTIES_PORT_PATTERN = Regex("""\barmeria\.ports\[(\d+)]\.port\s*=\s*(\d+)""")
    private val PROPERTIES_PROTOCOL_PATTERN =
        Regex("""\barmeria\.ports\[(\d+)]\.protocols(?:\[(\d+)])?\s*=\s*(\w+)""")
    private val PROPERTIES_INCLUDE_PATTERN =
        Regex("""\barmeria\.internal-services\.include(?:\[\d+])?\s*=\s*(.+)""")
    private val PROPERTIES_INTERNAL_PORT_PATTERN =
        Regex("""\barmeria\.internal-services\.port\s*=\s*(\d+)""")
    private val PROPERTIES_DOCS_PATH_PATTERN =
        Regex("""\barmeria\.(?:docs-path|docsPath)\s*=\s*(\S+)""")
    private val PROPERTIES_HEALTH_PATH_PATTERN =
        Regex("""\barmeria\.(?:health-check-path|healthCheckPath)\s*=\s*(\S+)""")
    private val PROPERTIES_METRICS_PATH_PATTERN =
        Regex("""\barmeria\.(?:metrics-path|metricsPath)\s*=\s*(\S+)""")

    private val INTERNAL_SERVICE_IDS = setOf("docs", "health", "metrics", "actuator")

    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        seenConfigRoutes: MutableSet<String>,
    ) {
        for (extension in listOf("yml", "yaml", "properties")) {
            for (virtualFile in FilenameIndex.getAllFilesByExt(project, extension, scope)) {
                if (!isApplicationConfigFile(virtualFile.name)) {
                    continue
                }
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: continue
                collectFromPsiFile(psiFile, routes, seenConfigRoutes)
            }
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
            portsByIndex[match.groupValues[1].toInt()] = match.groupValues[2]
        }
        val protocolsByIndex = mutableMapOf<Int, MutableList<Pair<Int, String>>>()
        PROPERTIES_PROTOCOL_PATTERN.findAll(text).forEach { match ->
            val portIndex = match.groupValues[1].toInt()
            val protocolIndex = match.groupValues[2].toIntOrNull() ?: 0
            val protocol = match.groupValues[3].uppercase()
            protocolsByIndex.getOrPut(portIndex) { mutableListOf() } += protocolIndex to protocol
        }
        val ports = portsByIndex.entries
            .sortedBy { it.key }
            .map { (index, port) ->
                val protocols = protocolsByIndex[index]
                    ?.sortedBy { it.first }
                    ?.map { it.second }
                    ?.distinct()
                    .orEmpty()
                    .ifEmpty { listOf("HTTP") }
                SpringArmeriaPortBinding(port = port, protocols = protocols)
            }

        val includes = mutableSetOf<String>()
        PROPERTIES_INCLUDE_PATTERN.findAll(text).forEach { match ->
            includes += parseIncludeTokens(match.groupValues[1])
        }
        return SpringArmeriaConfig(
            ports = ports,
            includes = expandIncludes(includes),
            docsPath = PROPERTIES_DOCS_PATH_PATTERN.find(text)?.groupValues?.get(1)?.trimQuotes()
                ?: DEFAULT_DOCS_PATH,
            healthPath = PROPERTIES_HEALTH_PATH_PATTERN.find(text)?.groupValues?.get(1)?.trimQuotes()
                ?: DEFAULT_HEALTH_PATH,
            metricsPath = PROPERTIES_METRICS_PATH_PATTERN.find(text)?.groupValues?.get(1)?.trimQuotes()
                ?: DEFAULT_METRICS_PATH,
            internalServicesPort = PROPERTIES_INTERNAL_PORT_PATTERN.find(text)?.groupValues?.get(1),
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
            addConfigRoute(
                element = element,
                path = "/",
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
        val portSuffix = config.internalServicesPort?.let { " · :$it" }.orEmpty()
        if ("docs" in includes) {
            addConfigRoute(
                element = element,
                path = config.docsPath,
                target = profileAwareTarget(
                    message("route.explorer.spring.docService") + portSuffix,
                    profile,
                ),
                protocol = RouteProtocol.DOC_SERVICE.presentableName(),
                httpMethod = "",
                routeMatch = RouteMatch.NON_HTTP,
                isDocService = true,
                dedupeKey = profileAwareKey("docs:${config.docsPath}", profile),
                routes = routes,
                seenConfigRoutes = seenConfigRoutes,
            )
        }
        if ("health" in includes) {
            addConfigRoute(
                element = element,
                path = config.healthPath,
                target = profileAwareTarget(
                    message("route.explorer.spring.healthCheck") + portSuffix,
                    profile,
                ),
                protocol = RouteProtocol.HTTP.presentableName(),
                httpMethod = "GET",
                routeMatch = RouteMatch.HEALTH_CHECK,
                isDocService = false,
                dedupeKey = profileAwareKey("health:${config.healthPath}", profile),
                routes = routes,
                seenConfigRoutes = seenConfigRoutes,
            )
        }
        if ("metrics" in includes) {
            addConfigRoute(
                element = element,
                path = config.metricsPath,
                target = profileAwareTarget(
                    message("route.explorer.spring.metrics") + portSuffix,
                    profile,
                ),
                protocol = RouteProtocol.HTTP.presentableName(),
                httpMethod = "GET",
                routeMatch = RouteMatch.SERVICE,
                isDocService = false,
                dedupeKey = profileAwareKey("metrics:${config.metricsPath}", profile),
                routes = routes,
                seenConfigRoutes = seenConfigRoutes,
            )
        }
        if ("actuator" in includes) {
            addConfigRoute(
                element = element,
                path = "/actuator",
                target = profileAwareTarget(
                    message("route.explorer.spring.actuator") + portSuffix,
                    profile,
                ),
                protocol = RouteProtocol.HTTP.presentableName(),
                httpMethod = "GET",
                routeMatch = RouteMatch.SERVICE,
                isDocService = false,
                dedupeKey = profileAwareKey("actuator", profile),
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
        val includeIndex = lines.indexOfFirst { line ->
            val trimmed = line.trim()
            trimmed == "include:" || trimmed.startsWith("include:")
        }
        if (includeIndex < 0) {
            return emptySet()
        }
        val includeLine = lines[includeIndex]
        val inline = includeLine.substringAfter("include:", missingDelimiterValue = "").trim()
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
                tokens += parseIncludeTokens(trimmed.removePrefix("- ").trim())
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

    private fun parseIncludeTokens(raw: String): Set<String> {
        val normalized = raw.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .trim()
        if (normalized.isEmpty()) {
            return emptySet()
        }
        return normalized.split(',', ' ', '\t')
            .map { it.trim().trimQuotes().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun extractTopLevelBlock(text: String, key: String): String? {
        val lines = text.lineSequence().toList()
        val startIndex = lines.indexOfFirst { line ->
            val trimmed = line.trim()
            !trimmed.startsWith("#") && matchesYamlKey(trimmed, key)
        }
        if (startIndex < 0) {
            return null
        }
        val parentIndent = lines[startIndex].takeWhile { it.isWhitespace() }.length
        return collectIndentedBlock(lines, startIndex, parentIndent)
    }

    private fun extractChildBlock(parentBlock: String, vararg keys: String): String? {
        val lines = parentBlock.lineSequence().toList()
        val startIndex = lines.indexOfFirst { line ->
            val trimmed = line.trim()
            !trimmed.startsWith("#") && keys.any { matchesYamlKey(trimmed, it) }
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
                if (current.isNotEmpty()) {
                    items += current.joinToString("\n")
                }
                current = mutableListOf(line)
                itemIndent = indent
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
        // Normalize the leading "- " so sibling keys share a common indent base.
        val lines = itemText.lineSequence().map { line ->
            val match = LIST_ITEM_PREFIX.find(line) ?: return@map line
            match.groupValues[1] + "  " + line.substring(match.range.last + 1)
        }.toList()
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
            val inlineValue = trimmed.substringAfter(':', missingDelimiterValue = "").trim()
            val keyIndent = line.takeWhile { it.isWhitespace() }.length
            if (inlineValue.isNotEmpty()) {
                values.getOrPut(key) { mutableListOf() } += splitInlineYamlValues(inlineValue)
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
                    nested += nestedTrimmed.removePrefix("- ").trim().trimQuotes()
                } else if (nestedTrimmed.contains(':')) {
                    break
                } else {
                    nested += nestedTrimmed.trimQuotes()
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

    private fun splitInlineYamlValues(raw: String): List<String> {
        val normalized = raw.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .trim()
        if (normalized.isEmpty()) {
            return emptyList()
        }
        if (normalized.contains(',')) {
            return normalized.split(',').map { it.trim().trimQuotes() }.filter { it.isNotEmpty() }
        }
        return listOf(normalized.trimQuotes())
    }

    private fun readYamlScalar(block: String?, vararg keys: String): String? {
        if (block.isNullOrBlank()) {
            return null
        }
        val keySet = keys.toSet()
        for (line in block.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#") || !trimmed.contains(':')) {
                continue
            }
            val key = trimmed.substringBefore(':').trim()
            if (key !in keySet) {
                continue
            }
            val value = trimmed.substringAfter(':', missingDelimiterValue = "").trim()
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
}
