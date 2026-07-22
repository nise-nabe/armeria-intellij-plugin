package com.linecorp.intellij.plugins.armeria.explorer.support

data class ScalaServiceRegistrationMatch(
    val methodName: String,
    val path: String,
    val targetText: String,
    val startOffset: Int,
    val argumentCount: Int,
)

/**
 * Text-based Scala route scanning uses regex over source text (no Scala PSI).
 *
 * Supported shapes:
 * - `.service("/path", new Handler())` / `.service("/path", Handler())`
 * - `.serviceUnder("/prefix", …)` and named `pathPrefix` / `service` argument orders
 * - `.annotatedService(…)` with optional path prefix
 *
 * Non-literal path expressions and dynamic prefixes are not matched.
 * Line/block comments are blanked before matching to avoid phantom routes.
 */
object ArmeriaScalaTextSupport {
    private const val TARGET = """(?:new\s+)?[\w.]+(?:\([^)]*\))?"""

    private data class RegistrationRule(
        val methodName: String,
        val pattern: Regex,
        val path: (MatchResult) -> String,
        val target: (MatchResult) -> String,
        val argumentCount: Int,
    )

    private fun pathFirst(method: String): Regex = Regex("""\.$method\s*\(\s*"([^"]+)"\s*,\s*($TARGET)\s*\)""")

    private val RULES =
        listOf(
            RegistrationRule(
                methodName = "service",
                pattern = pathFirst("service"),
                path = { it.groupValues[1] },
                target = { it.groupValues[2] },
                argumentCount = 2,
            ),
            RegistrationRule(
                methodName = "serviceUnder",
                pattern = pathFirst("serviceUnder"),
                path = { it.groupValues[1] },
                target = { it.groupValues[2] },
                argumentCount = 2,
            ),
            RegistrationRule(
                methodName = "serviceUnder",
                pattern =
                    Regex(
                        """\.serviceUnder\s*\(\s*pathPrefix\s*=\s*"([^"]+)"\s*,\s*service\s*=\s*($TARGET)\s*\)""",
                    ),
                path = { it.groupValues[1] },
                target = { it.groupValues[2] },
                argumentCount = 2,
            ),
            RegistrationRule(
                methodName = "serviceUnder",
                pattern =
                    Regex(
                        """\.serviceUnder\s*\(\s*service\s*=\s*($TARGET)\s*,\s*pathPrefix\s*=\s*"([^"]+)"\s*\)""",
                    ),
                path = { it.groupValues[2] },
                target = { it.groupValues[1] },
                argumentCount = 2,
            ),
            RegistrationRule(
                methodName = "annotatedService",
                pattern = pathFirst("annotatedService"),
                path = { it.groupValues[1] },
                target = { it.groupValues[2] },
                argumentCount = 2,
            ),
            RegistrationRule(
                methodName = "annotatedService",
                pattern = Regex("""\.annotatedService\s*\(\s*($TARGET)\s*\)"""),
                path = { "/" },
                target = { it.groupValues[1] },
                argumentCount = 1,
            ),
        )

    fun referencesArmeriaScalaContent(contents: CharSequence): Boolean = ArmeriaRouteSupport.referencesArmeriaSourceContent(contents)

    fun findServiceRegistrations(text: String): List<ScalaServiceRegistrationMatch> {
        val scanText = stripScalaComments(text)
        if (!looksLikeServerBuilderScalaFile(scanText)) {
            return emptyList()
        }
        return RULES
            .flatMap { rule ->
                rule.pattern.findAll(scanText).map { match ->
                    ScalaServiceRegistrationMatch(
                        methodName = rule.methodName,
                        path = rule.path(match),
                        targetText = rule.target(match).trim(),
                        startOffset = match.range.first,
                        argumentCount = rule.argumentCount,
                    )
                }
            }.sortedBy { it.startOffset }
    }

    fun renderScalaTarget(targetText: String): String {
        val trimmed = targetText.trim()
        val withoutNew = trimmed.removePrefix("new ").trim()
        val withoutParens = withoutNew.substringBefore('(').trim()
        return withoutParens.substringAfterLast('.').ifBlank { withoutParens }
    }

    /**
     * Without Scala PSI resolution, a target is unresolved only when rendering could not
     * improve past the raw expression text (e.g. a bare identifier `handler`).
     * Constructor forms like `new HelloService()` successfully yield a class simple name
     * and are treated as resolved best-effort.
     */
    fun isUnresolvedScalaTarget(
        targetText: String,
        renderedTarget: String,
    ): Boolean = renderedTarget == targetText.trim()

    private fun looksLikeServerBuilderScalaFile(contents: CharSequence): Boolean =
        ArmeriaRouteSupport.referencesArmeriaApplicationInSource(contents) ||
            ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(contents.toString())

    /**
     * Best-effort comment blanking for text scanning. Replaces comment text with spaces so
     * match offsets stay aligned with the original source for PSI navigation.
     * Strings that contain comment-like sequences may be altered; that only affects discovery
     * accuracy, not compilation.
     */
    fun stripScalaComments(text: String): String {
        val chars = text.toCharArray()
        var i = 0
        while (i < chars.size) {
            if (i + 1 < chars.size && chars[i] == '/' && chars[i + 1] == '*') {
                chars[i] = ' '
                chars[i + 1] = ' '
                i += 2
                while (i + 1 < chars.size && !(chars[i] == '*' && chars[i + 1] == '/')) {
                    if (chars[i] != '\n') {
                        chars[i] = ' '
                    }
                    i++
                }
                if (i + 1 < chars.size) {
                    chars[i] = ' '
                    chars[i + 1] = ' '
                    i += 2
                }
                continue
            }
            if (i + 1 < chars.size && chars[i] == '/' && chars[i + 1] == '/') {
                while (i < chars.size && chars[i] != '\n') {
                    chars[i] = ' '
                    i++
                }
                continue
            }
            i++
        }
        return String(chars)
    }
}
