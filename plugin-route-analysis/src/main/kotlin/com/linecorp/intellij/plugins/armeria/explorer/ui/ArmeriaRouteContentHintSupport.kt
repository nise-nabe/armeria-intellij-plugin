package com.linecorp.intellij.plugins.armeria.explorer.ui

import com.linecorp.intellij.plugins.armeria.message
import java.util.Locale

object ArmeriaRouteContentHintSupport {
    private const val MARKER = "\u0001"
    private const val JSON_MEDIA_TYPE = "application/json"
    private val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")
    private val NEWLINE_CHARACTERS = Regex("[\\r\\n]+")

    // tchar minus a trailing '!' immediately before '=' so `name!=value` is not a header pair.
    private val SIMPLE_EQUALITY_MATCH = Regex("""^([A-Za-z0-9_!#\$%&'*+.^`|~-]+)(?<!!)=([^=].*)$""")

    fun isHint(
        hint: String,
        messageKey: String,
    ): Boolean {
        val sample = message(messageKey, MARKER)
        val prefix = sample.substringBefore(MARKER)
        val suffix = sample.substringAfter(MARKER)
        return hint.startsWith(prefix) && hint.endsWith(suffix) && hint.length >= prefix.length + suffix.length
    }

    fun payloads(
        hints: List<String>,
        messageKey: String,
    ): List<String> {
        val sample = message(messageKey, MARKER)
        val prefix = sample.substringBefore(MARKER)
        val suffix = sample.substringAfter(MARKER)
        return hints.mapNotNull { hint ->
            if (!hint.startsWith(prefix) || !hint.endsWith(suffix) || hint.length < prefix.length + suffix.length) {
                return@mapNotNull null
            }
            hint.substring(prefix.length, hint.length - suffix.length)
        }
    }

    fun mediaTypes(
        hints: List<String>,
        messageKey: String,
    ): List<String> =
        payloads(hints, messageKey).flatMap { value ->
            value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }

    fun equalityMatchFields(
        contentHints: List<String>,
        messageKey: String,
    ): List<Pair<String, String>> =
        payloads(contentHints, messageKey).mapNotNull { condition ->
            val match = SIMPLE_EQUALITY_MATCH.matchEntire(condition.trim()) ?: return@mapNotNull null
            match.groupValues[1] to match.groupValues[2]
        }

    fun isJsonMediaType(mediaType: String): Boolean {
        val normalized = mediaType.substringBefore(';').trim().lowercase(Locale.ROOT)
        return normalized == JSON_MEDIA_TYPE || normalized.endsWith("+json")
    }

    fun hasRequestBody(method: String): Boolean = method.uppercase(Locale.ROOT) in METHODS_WITH_BODY

    fun collapseNewlines(text: String): String = text.replace(NEWLINE_CHARACTERS, " ").trim()
}
