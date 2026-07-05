package com.linecorp.intellij.plugins.armeria.springboot.config

object ArmeriaSpringBootConfigParser {
    fun parseProperties(text: String) = flattenProperties(text).filterKeys(ArmeriaSpringBootConfigKeys::isArmeriaRelatedKey).map { (k, v) -> ArmeriaSpringBootConfigEntry(k, v) }.sortedBy { it.key }
    fun parseYaml(text: String) = flattenYaml(text).filterKeys(ArmeriaSpringBootConfigKeys::isArmeriaRelatedKey).map { (k, v) -> ArmeriaSpringBootConfigEntry(k, v) }.sortedBy { it.key }
    internal fun flattenProperties(text: String) = buildMap {
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('#') || line.startsWith('!')) continue
            val sep = line.indexOfFirst { it == '=' || it == ':' }
            if (sep < 0) continue
            put(line.substring(0, sep).trim(), unquote(line.substring(sep + 1).trim()))
        }
    }
    internal fun flattenYaml(text: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val stack = ArrayDeque<YamlFrame>()
        for (raw in text.lineSequence()) {
            if (raw.isBlank() || raw.trimStart().startsWith('#')) continue
            val indent = raw.takeWhile { it == ' ' || it == '\t' }.length
            val trimmed = raw.trim()
            while (stack.isNotEmpty() && indent <= stack.last().indent) stack.removeLast()
            when {
                trimmed.startsWith("- ") -> {
                    val parent = stack.last()
                    val listPath = "${parent.path}[${parent.nextListIndex()}]"
                    stack.addLast(YamlFrame(indent, listPath))
                    val content = trimmed.removePrefix("- ").trim()
                    if (':' in content) {
                        val ci = content.indexOf(':')
                        val key = content.substring(0, ci).trim()
                        val value = content.substring(ci + 1).trim()
                        if (value.isNotEmpty()) result["$listPath.$key"] = unquote(value) else stack.addLast(YamlFrame(indent + 2, "$listPath.$key"))
                    } else {
                        result[listPath] = unquote(content)
                        stack.removeLast()
                    }
                }
                else -> {
                    val ci = trimmed.indexOf(':')
                    val key = trimmed.substring(0, ci).trim()
                    val value = trimmed.substring(ci + 1).trim()
                    val path = stack.lastOrNull()?.path?.let { "$it.$key" } ?: key
                    if (value.isEmpty()) stack.addLast(YamlFrame(indent, path)) else result[path] = unquote(value)
                }
            }
        }
        return result
    }
    private fun unquote(v: String): String {
        val t = v.trim()
        return if ((t.startsWith('"') && t.endsWith('"')) || (t.startsWith('\'') && t.endsWith('\''))) t.substring(1, t.length - 1) else t
    }
    private data class YamlFrame(val indent: Int, val path: String, var listItemCount: Int = 0) { fun nextListIndex() = listItemCount++ }
}
