package com.linecorp.intellij.plugins.armeria.explorer.support

import com.linecorp.intellij.plugins.armeria.explorer.model.PathType

object ArmeriaPathVariableSupport {
    private val COLON_PATH_VARIABLE_PATTERN = Regex(""":([A-Za-z_][A-Za-z0-9_]*)""")
    private val REGEX_NAMED_GROUP_PATTERN = Regex("""\(\?<([A-Za-z_][A-Za-z0-9_]*)>""")

    /**
     * Names bound from brace (`{id}`), colon (`:name`), and regex named groups.
     * Glob wildcards (`*` / `**`) are not extracted; Armeria binds those as `"0"`, `"1"`, …
     */
    fun extractPathVariables(rawPath: String): List<String> {
        val (pathType, normalized) = ArmeriaRouteAnnotationSupport.parsePathType(rawPath)
        return extractPathVariables(normalized, pathType)
    }

    fun extractPathVariables(
        path: String,
        pathType: PathType,
    ): List<String> = pathVariableOccurrences(path, pathType).map { it.name }.distinct()

    fun replacePathVariableName(
        path: String,
        oldName: String,
        newName: String,
    ): String {
        if (oldName.isEmpty() || oldName == newName) {
            return path
        }
        val (pathType, normalized) = ArmeriaRouteAnnotationSupport.parsePathType(path)
        val prefixLength = pathPrefixOffset(path, pathType)
        val replaced =
            when (pathType) {
                PathType.GLOB -> normalized
                PathType.REGEX -> replaceRegexNamedGroup(normalized, oldName, newName)
                PathType.EXACT, PathType.PREFIX -> replaceExactPathVariables(normalized, oldName, newName)
            }
        if (replaced == normalized) {
            return path
        }
        return path.substring(0, prefixLength) + replaced
    }

    fun pathVariableOccurrences(path: String): List<PathVariableOccurrence> {
        val (pathType, normalized) = ArmeriaRouteAnnotationSupport.parsePathType(path)
        return pathVariableOccurrences(normalized, pathType, pathPrefixOffset(path, pathType))
    }

    fun pathVariableOccurrences(
        path: String,
        pathType: PathType,
        offsetInOriginal: Int = 0,
    ): List<PathVariableOccurrence> =
        when (pathType) {
            PathType.GLOB -> emptyList()
            PathType.REGEX ->
                REGEX_NAMED_GROUP_PATTERN
                    .findAll(path)
                    .map { match ->
                        val name = match.groupValues[1]
                        val nameStart = match.range.first + 3
                        PathVariableOccurrence(
                            name = name,
                            startOffset = offsetInOriginal + nameStart,
                            endOffset = offsetInOriginal + nameStart + name.length,
                        )
                    }.toList()
            PathType.EXACT, PathType.PREFIX -> exactPathVariableOccurrences(path, offsetInOriginal)
        }

    private fun exactPathVariableOccurrences(
        path: String,
        offsetInOriginal: Int,
    ): List<PathVariableOccurrence> {
        val occurrences = mutableListOf<PathVariableOccurrence>()
        var index = 0
        while (index < path.length) {
            if (path[index] == '{') {
                val end = findMatchingBrace(path, index)
                if (end < 0) {
                    index++
                    continue
                }
                val inner = path.substring(index + 1, end)
                val name = braceVariableName(inner)
                if (name != null) {
                    val nameStartInInner = inner.indexOf(name)
                    if (nameStartInInner >= 0) {
                        val nameStart = index + 1 + nameStartInInner
                        occurrences +=
                            PathVariableOccurrence(
                                name = name,
                                startOffset = offsetInOriginal + nameStart,
                                endOffset = offsetInOriginal + nameStart + name.length,
                            )
                    }
                }
                index = end + 1
                continue
            }
            val remaining = path.substring(index)
            val nextBrace = remaining.indexOf('{').let { if (it < 0) remaining.length else it }
            COLON_PATH_VARIABLE_PATTERN.findAll(remaining.substring(0, nextBrace)).forEach { match ->
                val name = match.groupValues[1]
                val nameStart = index + match.range.first + 1
                occurrences +=
                    PathVariableOccurrence(
                        name = name,
                        startOffset = offsetInOriginal + nameStart,
                        endOffset = offsetInOriginal + nameStart + name.length,
                    )
            }
            index += nextBrace
        }
        return occurrences
    }

    private fun replaceExactPathVariables(
        path: String,
        oldName: String,
        newName: String,
    ): String {
        val result = StringBuilder()
        var index = 0
        while (index < path.length) {
            if (path[index] == '{') {
                val end = findMatchingBrace(path, index)
                if (end < 0) {
                    result.append(path[index])
                    index++
                    continue
                }
                val inner = path.substring(index + 1, end)
                val name = braceVariableName(inner)
                result.append('{')
                if (name == oldName) {
                    result.append(inner.replaceFirst(oldName, newName))
                } else {
                    result.append(inner)
                }
                result.append('}')
                index = end + 1
                continue
            }
            val remaining = path.substring(index)
            val nextBrace = remaining.indexOf('{').let { if (it < 0) remaining.length else it }
            val gap = remaining.substring(0, nextBrace)
            result.append(
                gap.replace(Regex(":" + Regex.escape(oldName) + "(?![A-Za-z0-9_])")) { ":$newName" },
            )
            index += nextBrace
        }
        return result.toString()
    }

    private fun replaceRegexNamedGroup(
        path: String,
        oldName: String,
        newName: String,
    ): String = path.replace(Regex("""\(\?<${Regex.escape(oldName)}>"""), "(?<$newName>")

    private fun findMatchingBrace(
        path: String,
        start: Int,
    ): Int {
        if (start >= path.length || path[start] != '{') {
            return -1
        }
        var depth = 0
        for (index in start until path.length) {
            when (path[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        return -1
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
