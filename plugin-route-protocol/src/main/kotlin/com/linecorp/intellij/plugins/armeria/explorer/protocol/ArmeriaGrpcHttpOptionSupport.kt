package com.linecorp.intellij.plugins.armeria.explorer.protocol

import com.linecorp.intellij.plugins.armeria.message

/**
 * Parses `google.api.http` method bindings from an RPC body or option block.
 */
object ArmeriaGrpcHttpOptionSupport {
    private val HTTP_OPTION_HEADER =
        Regex("""option\s*\(\s*google\.api\.http\s*\)\s*=\s*\{""", RegexOption.IGNORE_CASE)
    private val HTTP_METHOD_PATH =
        Regex("""\b(get|post|put|patch|delete)\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    private val CUSTOM_BLOCK = Regex("""\bcustom\s*:\s*\{""", RegexOption.IGNORE_CASE)
    private val CUSTOM_KIND = Regex("""\bkind\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    private val CUSTOM_PATH = Regex("""\bpath\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)

    fun contentHints(rpcSource: String): List<String> =
        parseBindings(rpcSource).map { binding ->
            message("route.explorer.hint.grpcHttpPath", binding.display)
        }

    internal fun parseBindings(rpcSource: String): List<GrpcHttpBinding> {
        val stripped = ArmeriaProtoTextSupport.stripComments(rpcSource)
        val bindings = linkedMapOf<String, GrpcHttpBinding>()
        for (block in httpOptionBodies(stripped)) {
            for (match in HTTP_METHOD_PATH.findAll(block)) {
                addBinding(bindings, match.groupValues[1].uppercase(), match.groupValues[2].trim())
            }
            var searchFrom = 0
            while (searchFrom < block.length) {
                val customHeader = CUSTOM_BLOCK.find(block, searchFrom) ?: break
                val openBrace = customHeader.range.last
                val closeBrace = ArmeriaProtoTextSupport.findMatchingCloseBrace(block, openBrace) ?: break
                val customBody = block.substring(openBrace + 1, closeBrace)
                val kind =
                    CUSTOM_KIND
                        .find(customBody)
                        ?.groupValues
                        ?.get(1)
                        ?.trim()
                val path =
                    CUSTOM_PATH
                        .find(customBody)
                        ?.groupValues
                        ?.get(1)
                        ?.trim()
                if (path != null) {
                    val method = kind?.takeIf { it.isNotEmpty() }?.uppercase() ?: "CUSTOM"
                    addBinding(bindings, method, path)
                }
                searchFrom = closeBrace + 1
            }
        }
        return bindings.values.toList()
    }

    private fun httpOptionBodies(text: String): List<String> {
        val bodies = mutableListOf<String>()
        var searchFrom = 0
        while (searchFrom < text.length) {
            val match = HTTP_OPTION_HEADER.find(text, searchFrom) ?: break
            val openBrace = match.range.last
            val closeBrace = ArmeriaProtoTextSupport.findMatchingCloseBrace(text, openBrace) ?: break
            bodies += text.substring(openBrace + 1, closeBrace)
            searchFrom = closeBrace + 1
        }
        return bodies
    }

    private fun addBinding(
        bindings: MutableMap<String, GrpcHttpBinding>,
        method: String,
        path: String,
    ) {
        if (!isHttpPath(path)) {
            return
        }
        val binding = GrpcHttpBinding(method, path)
        bindings.putIfAbsent(binding.display, binding)
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
