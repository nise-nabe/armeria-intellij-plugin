package com.linecorp.intellij.plugins.armeria.explorer

internal data class ScalaServiceRegistrationMatch(
    val methodName: String,
    val path: String,
    val targetText: String,
    val startOffset: Int,
)

internal data class ScalaClientEndpointMatch(
    val clientSimpleName: String,
    val uri: String,
    val startOffset: Int,
)

internal object ArmeriaScalaTextSupport {
    private val SERVICE_PATTERN =
        Regex("""\.service\s*\(\s*"([^"]+)"\s*,\s*((?:new\s+)?[\w.]+(?:\([^)]*\))?)\s*\)""")
    private val SERVICE_UNDER_PATTERN =
        Regex("""\.serviceUnder\s*\(\s*"([^"]+)"\s*,\s*((?:new\s+)?[\w.]+(?:\([^)]*\))?)\s*\)""")
    private val ANNOTATED_SERVICE_WITH_PREFIX_PATTERN =
        Regex("""\.annotatedService\s*\(\s*"([^"]+)"\s*,\s*((?:new\s+)?[\w.]+(?:\([^)]*\))?)\s*\)""")
    private val ANNOTATED_SERVICE_PATTERN =
        Regex("""\.annotatedService\s*\(\s*((?:new\s+)?[\w.]+(?:\([^)]*\))?)\s*\)""")
    private val CLIENT_ENDPOINT_PATTERN =
        Regex(
            """\b(WebClient|GrpcClient|GrpcClients|ThriftClient|ThriftClients)\s*\.\s*(?:of|builder|newClient)\s*\(\s*"([^"]+)"\s*\)""",
        )

    fun referencesArmeriaScalaContent(contents: CharSequence): Boolean {
        val header = contents.subSequence(0, minOf(contents.length, ArmeriaRouteSupport.ARMERIA_HEADER_SCAN_LIMIT))
        if (header.contains("import com.linecorp.armeria")) {
            return true
        }
        return ArmeriaRouteSupport.referencesArmeriaInText(contents)
    }

    fun looksLikeServerBuilderScalaFile(contents: CharSequence): Boolean {
        val text = contents.toString()
        return ArmeriaRouteSupport.referencesArmeriaApplicationInSource(contents) ||
            ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(text)
    }

    fun findServiceRegistrations(text: String): List<ScalaServiceRegistrationMatch> {
        if (!looksLikeServerBuilderScalaFile(text)) {
            return emptyList()
        }
        val matches = mutableListOf<ScalaServiceRegistrationMatch>()
        collectPatternMatches(text, SERVICE_PATTERN, "service", matches) { groups ->
            groups[1] to groups[2]
        }
        collectPatternMatches(text, SERVICE_UNDER_PATTERN, "serviceUnder", matches) { groups ->
            groups[1] to groups[2]
        }
        collectPatternMatches(text, ANNOTATED_SERVICE_WITH_PREFIX_PATTERN, "annotatedService", matches) { groups ->
            groups[1] to groups[2]
        }
        for (match in ANNOTATED_SERVICE_PATTERN.findAll(text)) {
            val serviceText = match.groupValues[1].trim()
            if (serviceText.startsWith("\"")) {
                continue
            }
            val overlapping = matches.any { existing ->
                existing.startOffset in match.range.first..match.range.last
            }
            if (overlapping) {
                continue
            }
            matches += ScalaServiceRegistrationMatch(
                methodName = "annotatedService",
                path = "/",
                targetText = serviceText,
                startOffset = match.range.first,
            )
        }
        return matches.sortedBy { it.startOffset }
    }

    fun findClientEndpoints(text: String): List<ScalaClientEndpointMatch> {
        return CLIENT_ENDPOINT_PATTERN.findAll(text).map { match ->
            ScalaClientEndpointMatch(
                clientSimpleName = match.groupValues[1],
                uri = match.groupValues[2],
                startOffset = match.range.first,
            )
        }.toList()
    }

    fun renderScalaTarget(targetText: String): String {
        val trimmed = targetText.trim()
        val withoutNew = trimmed.removePrefix("new ").trim()
        val withoutParens = withoutNew.substringBefore('(').trim()
        return withoutParens.substringAfterLast('.').ifBlank { withoutParens }
    }

    fun isUnresolvedScalaTarget(targetText: String, renderedTarget: String): Boolean {
        val trimmed = targetText.trim()
        return renderedTarget == trimmed ||
            renderedTarget == trimmed.removePrefix("new ").substringBefore('(').trim()
    }

    private inline fun collectPatternMatches(
        text: String,
        pattern: Regex,
        methodName: String,
        matches: MutableList<ScalaServiceRegistrationMatch>,
        pathAndTarget: (List<String>) -> Pair<String, String>,
    ) {
        for (match in pattern.findAll(text)) {
            val (path, target) = pathAndTarget(match.groupValues)
            matches += ScalaServiceRegistrationMatch(
                methodName = methodName,
                path = path,
                targetText = target.trim(),
                startOffset = match.range.first,
            )
        }
    }
}
