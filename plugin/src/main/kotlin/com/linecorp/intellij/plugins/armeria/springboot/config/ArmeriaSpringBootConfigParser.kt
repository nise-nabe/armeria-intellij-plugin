package com.linecorp.intellij.plugins.armeria.springboot.config

import java.io.StringReader
import java.util.Properties

object ArmeriaSpringBootConfigParser {
    fun parseProperties(text: String): List<ArmeriaSpringBootConfigEntry> =
        flattenProperties(text)
            .filterKeys(ArmeriaSpringBootConfigKeys::isArmeriaRelatedKey)
            .map { (k, v) -> ArmeriaSpringBootConfigEntry(k, v) }
            .sortedBy { it.key }

    fun parseYaml(text: String): List<ArmeriaSpringBootConfigEntry> =
        flattenYaml(text)
            .filterKeys(ArmeriaSpringBootConfigKeys::isArmeriaRelatedKey)
            .map { (k, v) -> ArmeriaSpringBootConfigEntry(k, v) }
            .sortedBy { it.key }

    fun parseFile(
        fileName: String,
        text: String,
    ): List<ArmeriaSpringBootConfigEntry> =
        when {
            fileName.endsWith(".yml") || fileName.endsWith(".yaml") -> parseYaml(text)
            fileName.endsWith(".properties") -> parseProperties(text)
            else -> emptyList()
        }

    /**
     * Armeria-related keys in document order (not sorted). Repeating a key moves it to the
     * last map position so last-wins follows the last occurrence, including when a kebab or
     * camel alias is overridden after another alias.
     */
    fun flattenRelatedInOrder(
        fileName: String,
        text: String,
    ): Map<String, String> {
        val flattened =
            when {
                fileName.endsWith(".yml") || fileName.endsWith(".yaml") -> flattenYaml(text)
                fileName.endsWith(".properties") -> flattenProperties(text)
                else -> emptyMap()
            }
        if (flattened.isEmpty()) {
            return emptyMap()
        }
        return flattened.filterKeys(ArmeriaSpringBootConfigKeys::isArmeriaRelatedKey)
    }

    internal fun flattenProperties(text: String): Map<String, String> =
        try {
            val properties = Properties()
            properties.load(StringReader(text))
            val loaded = properties.stringPropertyNames()
            if (loaded.isEmpty()) {
                emptyMap()
            } else {
                val ordered = linkedMapOf<String, String>()
                for (raw in text.lineSequence()) {
                    val key = matchLoadedPropertyKey(raw, loaded) ?: continue
                    putLast(ordered, key, properties.getProperty(key).orEmpty())
                }
                for (key in loaded) {
                    ordered.putIfAbsent(key, properties.getProperty(key).orEmpty())
                }
                ordered
            }
        } catch (_: Exception) {
            emptyMap()
        }

    internal fun flattenYaml(text: String): Map<String, String> =
        try {
            flattenYamlUnchecked(text)
        } catch (_: RuntimeException) {
            emptyMap()
        }

    private fun flattenYamlUnchecked(text: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val stack = ArrayDeque<YamlFrame>()
        for (raw in text.lineSequence()) {
            try {
                if (raw.isBlank() || raw.trimStart().startsWith('#')) {
                    continue
                }
                val indent = raw.takeWhile { it == ' ' || it == '\t' }.length
                val trimmed = raw.trim()
                val isDashItem = trimmed == "-" || trimmed.startsWith("- ")
                if (isDashItem) {
                    // YAML compact sequences allow `-` items at the same column as
                    // the parent key — only sibling list items at that column close.
                    while (closesOnSequenceItem(stack, indent)) {
                        stack.removeLast()
                    }
                } else {
                    while (stack.isNotEmpty() && indent <= stack.last().indent) {
                        stack.removeLast()
                    }
                }
                when {
                    isDashItem -> {
                        val parent = stack.lastOrNull() ?: continue
                        val listPath = "${parent.path}[${parent.nextListIndex()}]"
                        stack.addLast(YamlFrame(indent, listPath, isListItem = true))
                        val content = yamlScalarValue(trimmed.removePrefix("-"))
                        if (content.isEmpty()) {
                            // Bare `-` (or comment/anchor only): nested lines
                            // attach under the new list item frame.
                            continue
                        }
                        if (isInlineMappingListItem(content)) {
                            val ci = content.indexOf(':')
                            val key = content.substring(0, ci).trim()
                            val value = yamlScalarValue(content.substring(ci + 1))
                            if (value.isNotEmpty()) {
                                putLast(result, "$listPath.$key", unquote(value))
                            } else {
                                stack.addLast(YamlFrame(indent + 2, "$listPath.$key"))
                            }
                        } else {
                            putLast(result, listPath, unquote(content))
                            stack.removeLast()
                        }
                    }
                    else -> {
                        val ci = trimmed.indexOf(':')
                        if (ci < 0) {
                            continue
                        }
                        val key = trimmed.substring(0, ci).trim()
                        val value = yamlScalarValue(trimmed.substring(ci + 1))
                        val path = stack.lastOrNull()?.path?.let { "$it.$key" } ?: key
                        if (value.isEmpty()) {
                            stack.addLast(YamlFrame(indent, path))
                        } else {
                            putLast(result, path, unquote(value))
                        }
                    }
                }
            } catch (_: RuntimeException) {
                continue
            }
        }
        return result
    }

    private fun putLast(
        map: MutableMap<String, String>,
        key: String,
        value: String,
    ) {
        map.remove(key)
        map[key] = value
    }

    private fun matchLoadedPropertyKey(
        raw: String,
        keys: Set<String>,
    ): String? {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith('#') || line.startsWith('!')) {
            return null
        }
        return keys
            .filter { key ->
                line.startsWith(key) &&
                    (
                        line.length == key.length ||
                            line[key.length] == '=' ||
                            line[key.length] == ':' ||
                            line[key.length].isWhitespace()
                    )
            }.maxByOrNull { it.length }
    }

    /**
     * Normalizes a YAML scalar-ish value: drops leading anchor/alias/tag tokens
     * (`&a`, `*b`, `!tag`/`!!str`) and trailing `#` comments on plain scalars.
     * An empty result means the node content continues on following lines.
     */
    private fun yamlScalarValue(raw: String): String {
        var v = raw.trim()
        while (v.startsWith('&') || v.startsWith('*') || v.startsWith('!')) {
            val space = v.indexOfFirst { it == ' ' || it == '\t' }
            if (space < 0) {
                return ""
            }
            v = v.substring(space + 1).trim()
        }
        if (v.startsWith('#')) {
            return ""
        }
        if (v.startsWith('"') || v.startsWith('\'')) {
            val quote = v[0]
            var from = 1
            while (true) {
                val end = v.indexOf(quote, from)
                if (end < 0) {
                    return v
                }
                if (v[end - 1] != '\\') {
                    return v.substring(0, end + 1)
                }
                from = end + 1
            }
        }
        val commentIndex =
            v.indices.firstOrNull { i -> v[i] == '#' && i > 0 && v[i - 1].isWhitespace() } ?: -1
        return if (commentIndex > 0) v.substring(0, commentIndex).trimEnd() else v
    }

    private fun isInlineMappingListItem(content: String): Boolean {
        val ci = content.indexOf(':')
        if (ci < 0) {
            return false
        }
        val afterColon = content.substring(ci + 1)
        return afterColon.isEmpty() || afterColon.first().isWhitespace()
    }

    private fun unquote(v: String): String {
        val t = v.trim()
        return if ((t.startsWith('"') && t.endsWith('"')) || (t.startsWith('\'') && t.endsWith('\''))) {
            t.substring(1, t.length - 1)
        } else {
            t
        }
    }

    private fun closesOnSequenceItem(
        stack: ArrayDeque<YamlFrame>,
        indent: Int,
    ): Boolean {
        val top = stack.lastOrNull() ?: return false
        return indent < top.indent || (indent == top.indent && top.isListItem)
    }

    private data class YamlFrame(
        val indent: Int,
        val path: String,
        val isListItem: Boolean = false,
        var listItemCount: Int = 0,
    ) {
        fun nextListIndex() = listItemCount++
    }
}
