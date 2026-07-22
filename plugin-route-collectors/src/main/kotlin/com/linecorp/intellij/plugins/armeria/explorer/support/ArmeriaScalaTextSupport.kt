package com.linecorp.intellij.plugins.armeria.explorer.support

data class ScalaServiceRegistrationMatch(
    val methodName: String,
    val path: String,
    val targetText: String,
    val startOffset: Int,
)

data class ScalaClientEndpointMatch(
    val clientSimpleName: String,
    val uri: String,
    val startOffset: Int,
)

object ArmeriaScalaTextSupport {
    /**
     * Text-based Scala route scanning uses regex over source text (no Scala PSI).
     *
     * Supported `serviceUnder` shapes:
     * - `.serviceUnder("/prefix", new Handler())` and `.serviceUnder("/prefix", Handler())`
     * - `.serviceUnder(pathPrefix = "/prefix", service = new Handler())`
     *
     * Non-literal path expressions, multiline argument lists, and dynamic prefixes are not matched.
     */
    private val SERVICE_PATTERN =
        Regex("""\.service\s*\(\s*"([^"]+)"\s*,\s*((?:new\s+)?[\w.]+(?:\([^)]*\))?)\s*\)""")
    private val SERVICE_UNDER_PATTERN =
        Regex("""\.serviceUnder\s*\(\s*"([^"]+)"\s*,\s*((?:new\s+)?[\w.]+(?:\([^)]*\))?)\s*\)""")
    private val SERVICE_UNDER_NAMED_PREFIX_FIRST_PATTERN =
        Regex(
            """\.serviceUnder\s*\(\s*pathPrefix\s*=\s*"([^"]+)"\s*,\s*service\s*=\s*((?:new\s+)?[\w.]+(?:\([^)]*\))?)\s*\)""",
        )
    private val SERVICE_UNDER_NAMED_SERVICE_FIRST_PATTERN =
        Regex(
            """\.serviceUnder\s*\(\s*service\s*=\s*((?:new\s+)?[\w.]+(?:\([^)]*\))?)\s*,\s*pathPrefix\s*=\s*"([^"]+)"\s*\)""",
        )
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
        collectPatternMatches(text, SERVICE_UNDER_NAMED_PREFIX_FIRST_PATTERN, "serviceUnder", matches) { groups ->
            groups[1] to groups[2]
        }
        for (match in SERVICE_UNDER_NAMED_SERVICE_FIRST_PATTERN.findAll(text)) {
            val target = match.groupValues[1].trim()
            val path = match.groupValues[2]
            val overlapping =
                matches.any { existing ->
                    existing.startOffset in match.range.first..match.range.last
                }
            if (overlapping) {
                continue
            }
            matches +=
                ScalaServiceRegistrationMatch(
                    methodName = "serviceUnder",
                    path = path,
                    targetText = target,
                    startOffset = match.range.first,
                )
        }
        collectPatternMatches(text, ANNOTATED_SERVICE_WITH_PREFIX_PATTERN, "annotatedService", matches) { groups ->
            groups[1] to groups[2]
        }
        for (match in ANNOTATED_SERVICE_PATTERN.findAll(text)) {
            val serviceText = match.groupValues[1].trim()
            if (serviceText.startsWith("\"")) {
                continue
            }
            val overlapping =
                matches.any { existing ->
                    existing.startOffset in match.range.first..match.range.last
                }
            if (overlapping) {
                continue
            }
            matches +=
                ScalaServiceRegistrationMatch(
                    methodName = "annotatedService",
                    path = "/",
                    targetText = serviceText,
                    startOffset = match.range.first,
                )
        }
        return matches.sortedBy { it.startOffset }
    }

    fun findClientEndpoints(text: String): List<ScalaClientEndpointMatch> =
        CLIENT_ENDPOINT_PATTERN
            .findAll(text)
            .map { match ->
                ScalaClientEndpointMatch(
                    clientSimpleName = match.groupValues[1],
                    uri = match.groupValues[2],
                    startOffset = match.range.first,
                )
            }.toList()

    fun renderScalaTarget(targetText: String): String {
        val trimmed = targetText.trim()
        val withoutNew = trimmed.removePrefix("new ").trim()
        val withoutParens = withoutNew.substringBefore('(').trim()
        return withoutParens.substringAfterLast('.').ifBlank { withoutParens }
    }

    fun isUnresolvedScalaTarget(
        targetText: String,
        renderedTarget: String,
    ): Boolean {
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
            matches +=
                ScalaServiceRegistrationMatch(
                    methodName = methodName,
                    path = path,
                    targetText = target.trim(),
                    startOffset = match.range.first,
                )
        }
    }
}
