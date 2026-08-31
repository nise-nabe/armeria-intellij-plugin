package com.linecorp.intellij.plugins.armeria.explorer.protocol

import com.linecorp.intellij.plugins.armeria.message

/**
 * Parses `google.api.http` method bindings from an RPC body or option block.
 */
object ArmeriaGrpcHttpOptionSupport {
    private val HTTP_METHOD_PATH =
        Regex("""\b(get|post|put|patch|delete)\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    private val CUSTOM_KIND = Regex("""\bkind\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    private val CUSTOM_PATH = Regex("""\bpath\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)

    fun contentHints(rpcSource: String): List<String> =
        parseBindings(rpcSource).map { binding ->
            message("route.explorer.hint.grpcHttpPath", binding.display)
        }

    internal fun parseBindings(rpcSource: String): List<GrpcHttpBinding> {
        val stripped = ArmeriaProtoTextSupport.stripComments(rpcSource)
        val bindings = linkedMapOf<String, GrpcHttpBinding>()
        for (match in HTTP_METHOD_PATH.findAll(stripped)) {
            val path = match.groupValues[2].trim()
            if (!isHttpPath(path)) {
                continue
            }
            val method = match.groupValues[1].uppercase()
            val binding = GrpcHttpBinding(method, path)
            bindings.putIfAbsent(binding.display, binding)
        }
        val customKind =
            CUSTOM_KIND
                .find(stripped)
                ?.groupValues
                ?.get(1)
                ?.trim()
        for (match in CUSTOM_PATH.findAll(stripped)) {
            val path = match.groupValues[1].trim()
            if (!isHttpPath(path)) {
                continue
            }
            val method = customKind?.takeIf { it.isNotEmpty() }?.uppercase() ?: "CUSTOM"
            val binding = GrpcHttpBinding(method, path)
            bindings.putIfAbsent(binding.display, binding)
        }
        return bindings.values.toList()
    }

    private fun isHttpPath(value: String): Boolean = value.startsWith("/") || "://" in value

    internal data class GrpcHttpBinding(
        val method: String,
        val path: String,
    ) {
        val display: String
            get() = "$method $path"
    }
}
