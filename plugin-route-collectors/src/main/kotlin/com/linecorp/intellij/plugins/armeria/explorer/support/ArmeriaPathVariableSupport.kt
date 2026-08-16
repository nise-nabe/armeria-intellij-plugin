package com.linecorp.intellij.plugins.armeria.explorer.support

import com.linecorp.intellij.plugins.armeria.explorer.model.PathType

object ArmeriaPathVariableSupport {
    private val COLON_PATH_VARIABLE_PATTERN = Regex(""":([A-Za-z_][A-Za-z0-9_]*)""")
    private val REGEX_NAMED_GROUP_PATTERN = Regex("""\(\?<([A-Za-z_][A-Za-z0-9_]*)>""")

    /**
     * Names bound from brace (`{id}`), colon (`:name`), regex named groups, and glob
     * wildcards (`*` / `**` as `"0"`, `"1"`, …). Typed `exact:` paths are literal matches
     * and do not bind parameters. Glob names are positional; the path itself cannot be
     * rewritten to a different identifier.
     */
    fun extractPathVariables(rawPath: String): List<String> {
        val typed = typedPath(rawPath)
        if (!typed.bindVariables) {
            return emptyList()
        }
        return extractPathVariables(rawPath.substring(typed.bodyStart), typed.pathType)
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
        val typed = typedPath(path)
        if (!typed.bindVariables) {
            return path
        }
        val body = path.substring(typed.bodyStart)
        val replaced =
            when (typed.pathType) {
                PathType.GLOB -> body
                PathType.REGEX -> replaceRegexNamedGroup(body, oldName, newName)
                PathType.EXACT, PathType.PREFIX -> replaceExactPathVariables(body, oldName, newName)
            }
        if (replaced == body) {
            return path
        }
        return path.substring(0, typed.bodyStart) + replaced
    }

    fun pathVariableOccurrences(path: String): List<PathVariableOccurrence> {
        val typed = typedPath(path)
        if (!typed.bindVariables) {
            return emptyList()
        }
        return pathVariableOccurrences(path.substring(typed.bodyStart), typed.pathType, typed.bodyStart)
    }

    fun pathVariableOccurrences(
        path: String,
        pathType: PathType,
        offsetInOriginal: Int = 0,
    ): List<PathVariableOccurrence> =
        when (pathType) {
            PathType.GLOB -> globPathVariableOccurrences(path, offsetInOriginal)
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
                val colonIndex = index + match.range.first
                if (!isColonPathParameter(path, colonIndex)) {
                    return@forEach
                }
                val name = match.groupValues[1]
                val nameStart = colonIndex + 1
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
            result.append(replaceColonPathVariables(path, index, gap, oldName, newName))
            index += nextBrace
        }
        return result.toString()
    }

    private fun replaceRegexNamedGroup(
        path: String,
        oldName: String,
        newName: String,
    ): String = path.replace(Regex("""\(\?<${Regex.escape(oldName)}>"""), "(?<$newName>")

    private fun replaceColonPathVariables(
        path: String,
        gapStartInPath: Int,
        gap: String,
        oldName: String,
        newName: String,
    ): String {
        val replaced = StringBuilder()
        var cursor = 0
        COLON_PATH_VARIABLE_PATTERN.findAll(gap).forEach { match ->
            val colonIndex = gapStartInPath + match.range.first
            replaced.append(gap, cursor, match.range.first)
            val name = match.groupValues[1]
            if (isColonPathParameter(path, colonIndex) && name == oldName) {
                replaced.append(':').append(newName)
            } else {
                replaced.append(match.value)
            }
            cursor = match.range.last + 1
        }
        replaced.append(gap, cursor, gap.length)
        return replaced.toString()
    }

    private fun isColonPathParameter(
        path: String,
        colonIndex: Int,
    ): Boolean {
        if (colonIndex < 0 || colonIndex >= path.length || path[colonIndex] != ':') {
            return false
        }
        return colonIndex == 0 || path[colonIndex - 1] == '/'
    }

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

    private fun typedPath(rawPath: String): TypedPath {
        val start = rawPath.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
        val rest = rawPath.substring(start)
        return when {
            rest.startsWith("prefix:") -> TypedPath(PathType.PREFIX, start + 7, bindVariables = true)
            rest.startsWith("regex:") -> TypedPath(PathType.REGEX, start + 6, bindVariables = true)
            rest.startsWith("glob:") -> TypedPath(PathType.GLOB, start + 5, bindVariables = true)
            rest.startsWith("exact:") -> TypedPath(PathType.EXACT, start + 6, bindVariables = false)
            else -> TypedPath(PathType.EXACT, start, bindVariables = true)
        }
    }

    private fun globPathVariableOccurrences(
        path: String,
        offsetInOriginal: Int,
    ): List<PathVariableOccurrence> {
        if ("***" in path) {
            return emptyList()
        }
        val occurrences = mutableListOf<PathVariableOccurrence>()
        var index = 0
        var paramIndex = 0
        while (index < path.length) {
            if (path[index] != '*') {
                index++
                continue
            }
            val start = index
            val length = if (index + 1 < path.length && path[index + 1] == '*') 2 else 1
            occurrences +=
                PathVariableOccurrence(
                    name = paramIndex.toString(),
                    startOffset = offsetInOriginal + start,
                    endOffset = offsetInOriginal + start + length,
                )
            paramIndex++
            index += length
        }
        return occurrences
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

    private data class TypedPath(
        val pathType: PathType,
        val bodyStart: Int,
        val bindVariables: Boolean,
    )
}
