package com.linecorp.intellij.plugins.armeria.explorer.support

data class ScalaServiceRegistrationMatch(
    val methodName: String,
    val path: String,
    val targetText: String,
    val startOffset: Int,
    val endOffset: Int,
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
    private data class RegistrationRule(
        val methodName: String,
        val pattern: Regex,
        val path: (MatchResult) -> String?,
        val targetStart: (MatchResult) -> Int,
        val argumentCount: Int,
        val pathAfterTarget: ((String, Int) -> String?)? = null,
    )

    private fun pathFirstPrefix(method: String): Regex = Regex("""\.$method\s*\(\s*"([^"]+)"\s*,\s*""")

    private val RULES =
        listOf(
            RegistrationRule(
                methodName = "service",
                pattern = pathFirstPrefix("service"),
                path = { it.groupValues[1] },
                targetStart = { it.range.last + 1 },
                argumentCount = 2,
            ),
            RegistrationRule(
                methodName = "serviceUnder",
                pattern = pathFirstPrefix("serviceUnder"),
                path = { it.groupValues[1] },
                targetStart = { it.range.last + 1 },
                argumentCount = 2,
            ),
            RegistrationRule(
                methodName = "serviceUnder",
                pattern =
                    Regex(
                        """\.serviceUnder\s*\(\s*pathPrefix\s*=\s*"([^"]+)"\s*,\s*service\s*=\s*""",
                    ),
                path = { it.groupValues[1] },
                targetStart = { it.range.last + 1 },
                argumentCount = 2,
            ),
            RegistrationRule(
                methodName = "serviceUnder",
                pattern =
                    Regex(
                        """\.serviceUnder\s*\(\s*service\s*=\s*""",
                    ),
                path = { null },
                targetStart = { it.range.last + 1 },
                argumentCount = 2,
                pathAfterTarget = { text, targetEnd ->
                    Regex("""\s*,\s*pathPrefix\s*=\s*"([^"]+)"""")
                        .find(text, targetEnd)
                        ?.groupValues
                        ?.get(1)
                },
            ),
            RegistrationRule(
                methodName = "annotatedService",
                pattern = pathFirstPrefix("annotatedService"),
                path = { it.groupValues[1] },
                targetStart = { it.range.last + 1 },
                argumentCount = 2,
            ),
            RegistrationRule(
                methodName = "annotatedService",
                pattern = Regex("""\.annotatedService\s*\(\s*"""),
                path = { "/" },
                targetStart = { it.range.last + 1 },
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
                    if (scan.isInsideLiteral(startOffset) || !isScalaServerBuilderRegistrationCall(scan.textWithoutComments, startOffset)) {
                        return@mapNotNull null
                    }
                    val targetStart = rule.targetStart(match)
                    val targetExtraction = extractBalancedExpression(scan.textWithoutComments, targetStart) ?: return@mapNotNull null
                    val (targetText, targetEnd) = targetExtraction
                    val path =
                        rule.path(match)
                            ?: rule.pathAfterTarget?.invoke(scan.textWithoutComments, targetEnd)
                            ?: return@mapNotNull null
                    val registrationEnd = findRegistrationCallEnd(scan.textWithoutComments, targetEnd) ?: return@mapNotNull null
                    ScalaServiceRegistrationMatch(
                        methodName = rule.methodName,
                        path = path,
                        targetText = targetText.trim(),
                        startOffset = startOffset,
                        endOffset = registrationEnd,
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

    internal fun isScalaServerBuilderRegistrationCall(
        text: String,
        registrationDotOffset: Int,
    ): Boolean {
        if (registrationDotOffset <= 0 || text[registrationDotOffset] != '.') {
            return false
        }
        var position = registrationDotOffset - 1
        while (position >= 0 && text[position].isWhitespace()) {
            position--
        }
        if (position < 0) {
            return false
        }
        return when (text[position]) {
            ')' -> isScalaServerBuilderChainReceiver(text, position)
            else -> isScalaServerBuilderIdentifierReceiver(text, position)
        }
    }

    private fun isScalaServerBuilderChainReceiver(
        text: String,
        closeParenOffset: Int,
    ): Boolean {
        val openParenOffset = findMatchingOpenParen(text, closeParenOffset) ?: return false
        var position = openParenOffset - 1
        while (position >= 0 && text[position].isWhitespace()) {
            position--
        }
        val methodEnd = position + 1
        while (position >= 0 && (text[position].isLetterOrDigit() || text[position] == '_')) {
            position--
        }
        val methodName = text.substring(position + 1, methodEnd)
        if (methodName == "builder") {
            while (position >= 0 && text[position].isWhitespace()) {
                position--
            }
            if (position < 0 || text[position] != '.') {
                return false
            }
            position--
            val qualifierEnd = position + 1
            while (position >= 0 && (text[position].isLetterOrDigit() || text[position] == '_' || text[position] == '.')) {
                position--
            }
            val qualifier = text.substring(position + 1, qualifierEnd)
            return isArmeriaServerBuilderQualifier(qualifier)
        }
        while (position >= 0 && text[position].isWhitespace()) {
            position--
        }
        if (position < 0 || text[position] != '.') {
            return false
        }
        return isScalaServerBuilderRegistrationCall(text, position)
    }

    private fun isScalaServerBuilderIdentifierReceiver(
        text: String,
        identifierEndOffset: Int,
    ): Boolean {
        var position = identifierEndOffset
        val identifierEnd = position + 1
        while (position >= 0 && (text[position].isLetterOrDigit() || text[position] == '_')) {
            position--
        }
        if (position >= 0 && (text[position].isLetterOrDigit() || text[position] == '_')) {
            return false
        }
        val identifier = text.substring(position + 1, identifierEnd)
        return identifier == "serverBuilder"
    }

    private fun isArmeriaServerBuilderQualifier(qualifier: String): Boolean =
        qualifier == "Server" ||
            qualifier == ArmeriaRouteSupport.ARMERIA_SERVER_CLASS ||
            (
                qualifier.endsWith(".Server") &&
                    qualifier.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX)
            )

    private fun extractBalancedExpression(
        text: String,
        start: Int,
    ): Pair<String, Int>? {
        var index = start
        while (index < text.length && text[index].isWhitespace()) {
            index++
        }
        if (index >= text.length) {
            return null
        }
        val expressionStart = index
        var parenDepth = 0
        var hasOpenParen = false
        while (index < text.length) {
            when (text[index]) {
                '(' -> {
                    parenDepth++
                    hasOpenParen = true
                }
                ')' -> {
                    if (!hasOpenParen) {
                        return text.substring(expressionStart, index).trim() to index
                    }
                    parenDepth--
                    if (parenDepth == 0) {
                        return text.substring(expressionStart, index + 1).trim() to (index + 1)
                    }
                }
                ',' -> {
                    if (!hasOpenParen && parenDepth == 0) {
                        return text.substring(expressionStart, index).trim() to index
                    }
                }
            }
            index++
        }
        return if (!hasOpenParen) {
            text.substring(expressionStart).trim() to index
        } else {
            null
        }
    }

    private fun findRegistrationCallEnd(
        text: String,
        targetEndOffset: Int,
    ): Int? {
        var depth = 0
        var index = targetEndOffset
        while (index < text.length) {
            when (text[index]) {
                '(' -> depth++
                ')' -> {
                    if (depth == 0) {
                        return index + 1
                    }
                    depth--
                }
            }
            index++
        }
        return null
    }

    private fun findMatchingOpenParen(
        text: String,
        closeParenOffset: Int,
    ): Int? {
        var depth = 0
        var index = closeParenOffset
        while (index >= 0) {
            when (text[index]) {
                ')' -> depth++
                '(' -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
            }
            index--
        }
        return null
    }

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
    ): Boolean = scanScalaText(text).isInsideLiteral(offset)

    fun scanScalaText(text: String): ScalaTextScan = scanScalaSource(text)

    class ScalaTextScan internal constructor(
        val textWithoutComments: String,
        private val literalRanges: List<IntRange>,
    ) {
        fun isInsideLiteral(offset: Int): Boolean = literalRanges.any { offset in it }

        fun isInsideStringLiteral(offset: Int): Boolean = isInsideLiteral(offset)
    }

    private fun scanScalaSource(text: String): ScalaTextScan {
        val chars = text.toCharArray()
        val literalRanges = mutableListOf<IntRange>()
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
                literalRanges += start until i
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
                literalRanges += start until i
                continue
            }
            if (chars[i] == '\'') {
                val start = i
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
                literalRanges += start until i
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
        return ScalaTextScan(String(chars), literalRanges)
    }
}
