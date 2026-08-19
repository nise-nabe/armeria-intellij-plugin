package com.linecorp.intellij.plugins.armeria.explorer.docservice

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Builds Armeria DocService method permalinks.
 *
 * Current DocService UI (hash router) uses `/docs/#/methods/{service}/{method}`.
 */
object ArmeriaDocServiceDebugFormUrl {
    private const val SPECIFICATION_SUFFIX = "/specification.json"

    fun build(
        docsBaseUrl: String,
        ref: ArmeriaDocServiceMethodRef,
    ): String {
        val base = docsBaseUrl.trimEnd('/')
        return "$base/#/methods/${encodeSegment(ref.serviceName)}/${encodeSegment(ref.methodName)}"
    }

    fun docsBaseUrlFromSpecificationUrl(specificationUrl: String): String {
        val trimmed = specificationUrl.trim().trimEnd('/')
        return if (trimmed.endsWith(SPECIFICATION_SUFFIX, ignoreCase = true)) {
            trimmed.substring(0, trimmed.length - SPECIFICATION_SUFFIX.length)
        } else {
            trimmed
        }
    }

    private fun encodeSegment(value: String): String =
        URLEncoder
            .encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20")
}
