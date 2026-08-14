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
        val (pathType, bodyStart) = typedPathBodyStart(rawPath)
        return extractPathVariables(rawPath.substring(bodyStart), pathType)
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
        val (pathType, bodyStart) = typedPathBodyStart(path)
        val body = path.substring(bodyStart)
        val replaced =
            when (pathType) {
                PathType.GLOB -> body
                PathType.REGEX -> replaceRegexNamedGroup(body, oldName, newName)
                PathType.EXACT, PathType.PREFIX -> replaceExactPathVariables(body, oldName, newName)
            }
        if (replaced == body) {
            return path
        }
        return path.substring(0, bodyStart) + replaced
    }

    fun pathVariableOccurrences(path: String): List<PathVariableOccurrence> {
        val (pathType, bodyStart) = typedPathBodyStart(path)
        return pathVariableOccurrences(path.substring(bodyStart), pathType, bodyStart)
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

    private fun typedPathBodyStart(rawPath: String): Pair<PathType, Int> {
        val start = rawPath.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
        val rest = rawPath.substring(start)
        return when {
            rest.startsWith("prefix:") -> PathType.PREFIX to start + 7
            rest.startsWith("regex:") -> PathType.REGEX to start + 6
            rest.startsWith("glob:") -> PathType.GLOB to start + 5
            rest.startsWith("exact:") -> PathType.EXACT to start + 6
            else -> PathType.EXACT to start
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
