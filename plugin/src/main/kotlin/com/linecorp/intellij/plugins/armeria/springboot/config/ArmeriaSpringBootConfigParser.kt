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

    internal fun flattenProperties(text: String): Map<String, String> =
        try {
            val properties = Properties()
            properties.load(StringReader(text))
            properties.stringPropertyNames().associateWith { key -> properties.getProperty(key).orEmpty() }
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
                while (stack.isNotEmpty() && indent <= stack.last().indent) {
                    stack.removeLast()
                }
                when {
                    trimmed.startsWith("- ") -> {
                        val parent = stack.lastOrNull() ?: continue
                        val listPath = "${parent.path}[${parent.nextListIndex()}]"
                        stack.addLast(YamlFrame(indent, listPath))
                        val content = trimmed.removePrefix("- ").trim()
                        if (isInlineMappingListItem(content)) {
                            val ci = content.indexOf(':')
                            val key = content.substring(0, ci).trim()
                            val value = content.substring(ci + 1).trim()
                            if (value.isNotEmpty()) {
                                result["$listPath.$key"] = unquote(value)
                            } else {
                                stack.addLast(YamlFrame(indent + 2, "$listPath.$key"))
                            }
                        } else {
                            result[listPath] = unquote(content)
                            stack.removeLast()
                        }
                    }
                    else -> {
                        val ci = trimmed.indexOf(':')
                        if (ci < 0) {
                            continue
                        }
                        val key = trimmed.substring(0, ci).trim()
                        val value = trimmed.substring(ci + 1).trim()
                        val path = stack.lastOrNull()?.path?.let { "$it.$key" } ?: key
                        if (value.isEmpty()) {
                            stack.addLast(YamlFrame(indent, path))
                        } else {
                            result[path] = unquote(value)
                        }
                    }
                }
            } catch (_: RuntimeException) {
                continue
            }
        }
        return result
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

    private data class YamlFrame(
        val indent: Int,
        val path: String,
        var listItemCount: Int = 0,
    ) {
        fun nextListIndex() = listItemCount++
    }
}
