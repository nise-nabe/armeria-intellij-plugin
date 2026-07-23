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
 * Paths must be string literals; non-literal / dynamic path expressions are not matched.
 * Targets may be constructor forms or bare identifiers (the latter are marked unresolved).
 * Line/block comments are blanked before matching to avoid phantom routes; string literals
 * (including Scala triple-quoted strings) are preserved so comment markers inside them do
 * not blank following code.
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

    fun findServiceRegistrations(text: String): List<ScalaServiceRegistrationMatch> {
        val scan = scanScalaSource(text)
        if (!looksLikeServerBuilderScalaFile(scan.textWithoutComments)) {
            return emptyList()
        }
        return RULES
            .flatMap { rule ->
                rule.pattern.findAll(scan.textWithoutComments).mapNotNull { match ->
                    val startOffset = match.range.first
                    if (scan.isInsideStringLiteral(startOffset)) {
                        return@mapNotNull null
                    }
                    ScalaServiceRegistrationMatch(
                        methodName = rule.methodName,
                        path = rule.path(match),
                        targetText = rule.target(match).trim(),
                        startOffset = startOffset,
                        argumentCount = rule.argumentCount,
                    )
                }
            }.distinctBy { it.startOffset }
            .sortedBy { it.startOffset }
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
     * match offsets stay aligned with the original source for navigation.
     * Skips contents of `"…"`, `"""…"""`, and `'…'` char literals (including escapes).
     * Nested block comments are not supported.
     */
    fun stripScalaComments(text: String): String = scanScalaText(text).textWithoutComments

    fun isOffsetInsideStringLiteral(
        text: String,
        offset: Int,
    ): Boolean = scanScalaText(text).isInsideStringLiteral(offset)

    fun scanScalaText(text: String): ScalaTextScan = scanScalaSource(text)

    class ScalaTextScan internal constructor(
        val textWithoutComments: String,
        private val stringLiteralRanges: List<IntRange>,
    ) {
        fun isInsideStringLiteral(offset: Int): Boolean = stringLiteralRanges.any { offset in it }
    }

    private fun scanScalaSource(text: String): ScalaTextScan {
        val chars = text.toCharArray()
        val stringLiteralRanges = mutableListOf<IntRange>()
        var i = 0
        while (i < chars.size) {
            if (i + 2 < chars.size && chars[i] == '"' && chars[i + 1] == '"' && chars[i + 2] == '"') {
                val start = i
                i += 3
                while (i + 2 < chars.size && !(chars[i] == '"' && chars[i + 1] == '"' && chars[i + 2] == '"')) {
                    i++
                }
                if (i + 2 < chars.size) {
                    i += 3
                }
                stringLiteralRanges += start until i
                continue
            }
            if (chars[i] == '"') {
                val start = i
                i++
                while (i < chars.size && chars[i] != '"') {
                    if (chars[i] == '\\' && i + 1 < chars.size) {
                        i += 2
                    } else {
                        i++
                    }
                }
                if (i < chars.size) {
                    i++
                }
                stringLiteralRanges += start until i
                continue
            }
            if (chars[i] == '\'') {
                i++
                while (i < chars.size) {
                    if (chars[i] == '\\' && i + 1 < chars.size) {
                        i += 2
                        continue
                    }
                    if (chars[i] == '\'') {
                        i++
                        break
                    }
                    i++
                }
                continue
            }
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
        return ScalaTextScan(String(chars), stringLiteralRanges)
    }
}
