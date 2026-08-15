package com.linecorp.intellij.plugins.armeria.explorer.ui

import com.linecorp.intellij.plugins.armeria.message

object ArmeriaRouteContentHintSupport {
    private const val MARKER = "\u0001"

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
}
