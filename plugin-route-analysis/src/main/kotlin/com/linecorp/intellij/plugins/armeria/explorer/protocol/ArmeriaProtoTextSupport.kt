package com.linecorp.intellij.plugins.armeria.explorer.protocol

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
                    val lineEnd = text.indexOf('\n', index)
                    val nextIndex = if (lineEnd < 0) text.length else lineEnd
                    ensureWhitespaceBetweenIdentifiers(result, text.getOrNull(nextIndex))
                    index = nextIndex
                }
                text.startsWith("/*", index) -> {
                    val blockEnd = text.indexOf("*/", index + 2)
                    val nextIndex = if (blockEnd < 0) text.length else blockEnd + 2
                    ensureWhitespaceBetweenIdentifiers(result, text.getOrNull(nextIndex))
                    index = nextIndex
                }
                else -> {
                    result.append(text[index])
                    index++
                }
            }
        }
        return result.toString()
    }

    fun findMatchingCloseBrace(
        text: String,
        openBraceIndex: Int,
    ): Int? {
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

    private fun ensureWhitespaceBetweenIdentifiers(
        result: StringBuilder,
        nextChar: Char?,
    ) {
        if (nextChar == null) {
            return
        }
        val lastChar = result.lastOrNull() ?: return
        if (isIdentifierChar(lastChar) && isIdentifierChar(nextChar)) {
            result.append(' ')
        }
    }

    private fun isIdentifierChar(char: Char): Boolean = char.isLetterOrDigit() || char == '_'
}
