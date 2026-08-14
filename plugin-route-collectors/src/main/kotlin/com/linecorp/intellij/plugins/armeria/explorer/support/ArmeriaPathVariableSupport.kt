package com.linecorp.intellij.plugins.armeria.explorer.support

import com.linecorp.intellij.plugins.armeria.explorer.model.PathType

object ArmeriaPathVariableSupport {
    private val BRACE_PATH_VARIABLE_PATTERN = Regex("""\{([^{}]+)}""")
    private val COLON_PATH_VARIABLE_PATTERN = Regex(""":([A-Za-z_][A-Za-z0-9_]*)""")
    private val REGEX_NAMED_GROUP_PATTERN = Regex("""\(\?<([A-Za-z_][A-Za-z0-9_]*)>""")

    fun extractPathVariables(rawPath: String): List<String> {
        val (pathType, normalized) = ArmeriaRouteAnnotationSupport.parsePathType(rawPath)
        return extractPathVariables(normalized, pathType)
    }

    fun extractPathVariables(
        path: String,
        pathType: PathType,
    ): List<String> {
        val names = linkedSetOf<String>()
        when (pathType) {
            PathType.GLOB -> return emptyList()
            PathType.REGEX ->
                REGEX_NAMED_GROUP_PATTERN.findAll(path).forEach { names += it.groupValues[1] }
            PathType.EXACT, PathType.PREFIX -> {
                BRACE_PATH_VARIABLE_PATTERN.findAll(path).forEach { match ->
                    braceVariableName(match.groupValues[1])?.let { names += it }
                }
                COLON_PATH_VARIABLE_PATTERN.findAll(path).forEach { names += it.groupValues[1] }
            }
        }
        return names.toList()
    }

    fun replacePathVariableName(
        path: String,
        oldName: String,
        newName: String,
    ): String {
        if (oldName.isEmpty() || oldName == newName) {
            return path
        }
        val quoted = Regex.escape(oldName)
        return path
            .replace(Regex("""\{(\*?)$quoted(:[^{}]+)?}""")) { match ->
                "{${match.groupValues[1]}$newName${match.groupValues[2]}}"
            }.replace(Regex(""":$quoted(?![A-Za-z0-9_])"""), ":$newName")
            .replace(Regex("""\(\?<$quoted>"""), "(?<$newName>")
    }

    fun pathVariableOccurrences(path: String): List<PathVariableOccurrence> {
        val (pathType, normalized) = ArmeriaRouteAnnotationSupport.parsePathType(path)
        return pathVariableOccurrences(normalized, pathType, pathPrefixOffset(path, pathType))
    }

    fun pathVariableOccurrences(
        path: String,
        pathType: PathType,
        offsetInOriginal: Int = 0,
    ): List<PathVariableOccurrence> {
        val occurrences = mutableListOf<PathVariableOccurrence>()
        when (pathType) {
            PathType.GLOB -> return emptyList()
            PathType.REGEX ->
                REGEX_NAMED_GROUP_PATTERN.findAll(path).forEach { match ->
                    val name = match.groupValues[1]
                    val nameStart = match.range.first + 3
                    occurrences +=
                        PathVariableOccurrence(
                            name = name,
                            startOffset = offsetInOriginal + nameStart,
                            endOffset = offsetInOriginal + nameStart + name.length,
                        )
                }
            PathType.EXACT, PathType.PREFIX -> {
                BRACE_PATH_VARIABLE_PATTERN.findAll(path).forEach { match ->
                    val raw = match.groupValues[1]
                    val name = braceVariableName(raw) ?: return@forEach
                    val nameStartInBrace = raw.indexOf(name)
                    if (nameStartInBrace < 0) {
                        return@forEach
                    }
                    val nameStart = match.range.first + 1 + nameStartInBrace
                    occurrences +=
                        PathVariableOccurrence(
                            name = name,
                            startOffset = offsetInOriginal + nameStart,
                            endOffset = offsetInOriginal + nameStart + name.length,
                        )
                }
                COLON_PATH_VARIABLE_PATTERN.findAll(path).forEach { match ->
                    val name = match.groupValues[1]
                    val nameStart = match.range.first + 1
                    occurrences +=
                        PathVariableOccurrence(
                            name = name,
                            startOffset = offsetInOriginal + nameStart,
                            endOffset = offsetInOriginal + nameStart + name.length,
                        )
                }
            }
        }
        return occurrences
    }

    private fun pathPrefixOffset(
        rawPath: String,
        pathType: PathType,
    ): Int {
        val trimmed = rawPath.trim()
        return when (pathType) {
            PathType.PREFIX -> if (trimmed.startsWith("prefix:")) "prefix:".length else 0
            PathType.REGEX -> if (trimmed.startsWith("regex:")) "regex:".length else 0
            PathType.GLOB -> if (trimmed.startsWith("glob:")) "glob:".length else 0
            PathType.EXACT -> if (trimmed.startsWith("exact:")) "exact:".length else 0
        }
    }

    private fun braceVariableName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        val withoutCatchAll = trimmed.removePrefix("*")
        val name = withoutCatchAll.substringBefore(':').trim()
        return name.takeIf { it.isNotEmpty() }
    }

    data class PathVariableOccurrence(
        val name: String,
        val startOffset: Int,
        val endOffset: Int,
    )
}
