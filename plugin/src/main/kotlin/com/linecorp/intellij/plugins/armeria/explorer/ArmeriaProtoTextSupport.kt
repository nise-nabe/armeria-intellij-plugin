package com.linecorp.intellij.plugins.armeria.explorer

internal object ArmeriaProtoTextSupport {
    fun stripComments(text: String): String {
        val result = StringBuilder(text.length)
        var index = 0
        var inDoubleQuote = false
        var inSingleQuote = false
        var escaped = false

        while (index < text.length) {
            if (escaped) {
                result.append(text[index])
                escaped = false
                index++
                continue
            }

            if (inDoubleQuote || inSingleQuote) {
                result.append(text[index])
                when (text[index]) {
                    '\\' -> escaped = true
                    '"' -> if (inDoubleQuote) inDoubleQuote = false
                    '\'' -> if (inSingleQuote) inSingleQuote = false
                }
                index++
                continue
            }

            when {
                text[index] == '"' -> {
                    inDoubleQuote = true
                    result.append(text[index])
                    index++
                }
                text[index] == '\'' -> {
                    inSingleQuote = true
                    result.append(text[index])
                    index++
                }
                text.startsWith("//", index) -> {
                    ensureTrailingWhitespace(result)
                    val lineEnd = text.indexOf('\n', index)
                    index = if (lineEnd < 0) text.length else lineEnd
                }
                text.startsWith("/*", index) -> {
                    ensureTrailingWhitespace(result)
                    val blockEnd = text.indexOf("*/", index + 2)
                    index = if (blockEnd < 0) text.length else blockEnd + 2
                }
                else -> {
                    result.append(text[index])
                    index++
                }
            }
        }
        return result.toString()
    }

    fun findMatchingCloseBrace(text: String, openBraceIndex: Int): Int? {
        if (openBraceIndex !in text.indices || text[openBraceIndex] != '{') {
            return null
        }
        var depth = 0
        var inDoubleQuote = false
        var inSingleQuote = false
        var escaped = false

        for (index in openBraceIndex until text.length) {
            if (escaped) {
                escaped = false
                continue
            }
            if (inDoubleQuote || inSingleQuote) {
                when (text[index]) {
                    '\\' -> escaped = true
                    '"' -> if (inDoubleQuote) inDoubleQuote = false
                    '\'' -> if (inSingleQuote) inSingleQuote = false
                }
                continue
            }
            when (text[index]) {
                '"' -> inDoubleQuote = true
                '\'' -> inSingleQuote = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        return null
    }

    private fun ensureTrailingWhitespace(result: StringBuilder) {
        if (result.isNotEmpty() && !result.last().isWhitespace()) {
            result.append(' ')
        }
    }
}
