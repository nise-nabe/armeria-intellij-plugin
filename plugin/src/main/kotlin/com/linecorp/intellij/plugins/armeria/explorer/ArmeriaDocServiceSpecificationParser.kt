package com.linecorp.intellij.plugins.armeria.explorer

/**
 * Parses Armeria DocService [specification.json](https://armeria.dev) payloads.
 */
internal object ArmeriaDocServiceSpecificationParser {
    data class ParsedSpecification(
        val routes: List<ParsedRoute>,
        val docServiceMountPath: String?,
    )

    data class ParsedRoute(
        val httpMethod: String,
        val path: String,
        val serviceName: String,
        val methodName: String,
    )

    fun parse(json: String): ParsedSpecification {
        val trimmed = json.trim()
        if (trimmed.isEmpty() || trimmed.first() != '{') {
            return ParsedSpecification(emptyList(), null)
        }
        val docServiceMountPath = parseDocServiceMount(trimmed)
        val routes = mutableListOf<ParsedRoute>()
        val servicesJson = extractJsonArray(trimmed, "services") ?: return ParsedSpecification(emptyList(), docServiceMountPath)
        for (serviceJson in splitJsonObjects(servicesJson)) {
            val serviceName = extractJsonString(serviceJson, "name") ?: continue
            val methodsJson = extractJsonArray(serviceJson, "methods") ?: continue
            for (methodJson in splitJsonObjects(methodsJson)) {
                routes += parseMethodRoutes(serviceName, methodJson)
            }
        }
        return ParsedSpecification(deduplicate(routes), docServiceMountPath)
    }

    private fun parseMethodRoutes(serviceName: String, methodJson: String): List<ParsedRoute> {
        val methodName = extractJsonString(methodJson, "name").orEmpty()
        val httpMethod = extractJsonString(methodJson, "httpMethod")?.uppercase().orEmpty().ifBlank { "GET" }
        val routes = mutableListOf<ParsedRoute>()
        val endpointsJson = extractJsonArray(methodJson, "endpoints")
        if (endpointsJson != null) {
            for (endpointJson in splitJsonObjects(endpointsJson)) {
                val pathMapping = extractJsonString(endpointJson, "pathMapping") ?: continue
                routes += ParsedRoute(httpMethod = httpMethod, path = pathMapping, serviceName = serviceName, methodName = methodName)
            }
        }
        if (routes.isEmpty()) {
            val examplePathsJson = extractJsonArray(methodJson, "examplePaths")
            if (examplePathsJson != null) {
                for (path in splitJsonStrings(examplePathsJson)) {
                    routes += ParsedRoute(httpMethod = httpMethod, path = path, serviceName = serviceName, methodName = methodName)
                }
            }
        }
        return routes
    }

    private fun parseDocServiceMount(json: String): String? {
        val routeObject = extractJsonObject(json, "docServiceRoute") ?: return null
        val pattern = extractJsonString(routeObject, "patternString") ?: return null
        return normalizeMountPath(pattern)
    }

    private fun normalizeMountPath(path: String): String {
        val withoutWildcard = path.removeSuffix("/*")
        val normalized = withoutWildcard.trimEnd('/')
        return if (normalized.isEmpty()) "/" else normalized
    }

    private fun deduplicate(routes: List<ParsedRoute>): List<ParsedRoute> =
        routes.distinctBy { "${it.httpMethod.uppercase()}|${it.path}|${it.serviceName}|${it.methodName}" }

    private fun extractJsonObject(json: String, fieldName: String): String? {
        val fieldIndex = indexOfJsonField(json, fieldName) ?: return null
        val valueStart = skipWhitespace(json, fieldIndex)
        if (valueStart >= json.length || json[valueStart] != '{') {
            return null
        }
        return extractBalanced(json, valueStart, '{', '}')
    }

    private fun extractJsonArray(json: String, fieldName: String): String? {
        val fieldIndex = indexOfJsonField(json, fieldName) ?: return null
        val valueStart = skipWhitespace(json, fieldIndex)
        if (valueStart >= json.length || json[valueStart] != '[') {
            return null
        }
        return extractBalanced(json, valueStart, '[', ']')
    }

    private fun extractJsonString(json: String, fieldName: String): String? {
        val fieldIndex = indexOfJsonField(json, fieldName) ?: return null
        val valueStart = skipWhitespace(json, fieldIndex)
        if (valueStart >= json.length || json[valueStart] != '"') {
            return null
        }
        return readJsonString(json, valueStart)?.value
    }

    private data class ParsedJsonString(val value: String, val endIndex: Int)

    private fun indexOfJsonField(json: String, fieldName: String): Int? {
        val pattern = "\"$fieldName\""
        var searchFrom = 0
        while (searchFrom < json.length) {
            val index = json.indexOf(pattern, searchFrom)
            if (index < 0) {
                return null
            }
            var cursor = index + pattern.length
            cursor = skipWhitespace(json, cursor)
            if (cursor < json.length && json[cursor] == ':') {
                return skipWhitespace(json, cursor + 1)
            }
            searchFrom = index + pattern.length
        }
        return null
    }

    private fun splitJsonObjects(arrayJson: String): List<String> {
        val trimmed = arrayJson.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return emptyList()
        }
        val objects = mutableListOf<String>()
        var index = 1
        while (index < trimmed.length - 1) {
            index = skipWhitespace(trimmed, index)
            if (index >= trimmed.length - 1 || trimmed[index] == ']') {
                break
            }
            if (trimmed[index] != '{') {
                index++
                continue
            }
            val objectJson = extractBalanced(trimmed, index, '{', '}') ?: break
            objects += objectJson
            index = skipWhitespace(trimmed, index + objectJson.length)
            if (index < trimmed.length && trimmed[index] == ',') {
                index++
            }
        }
        return objects
    }

    private fun splitJsonStrings(arrayJson: String): List<String> {
        val trimmed = arrayJson.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return emptyList()
        }
        val values = mutableListOf<String>()
        var index = 1
        while (index < trimmed.length - 1) {
            index = skipWhitespace(trimmed, index)
            if (index >= trimmed.length - 1 || trimmed[index] == ']') {
                break
            }
            if (trimmed[index] != '"') {
                index++
                continue
            }
            val parsed = readJsonString(trimmed, index) ?: break
            values += parsed.value
            index = skipWhitespace(trimmed, parsed.endIndex)
            if (index < trimmed.length && trimmed[index] == ',') {
                index++
            }
        }
        return values
    }

    private fun readJsonString(json: String, startIndex: Int): ParsedJsonString? {
        if (startIndex >= json.length || json[startIndex] != '"') {
            return null
        }
        val builder = StringBuilder()
        var index = startIndex + 1
        while (index < json.length) {
            when (val ch = json[index]) {
                '"' -> return ParsedJsonString(builder.toString(), index + 1)
                '\\' -> {
                    if (index + 1 >= json.length) {
                        return null
                    }
                    when (val escaped = json[index + 1]) {
                        '"' -> builder.append('"')
                        '\\' -> builder.append('\\')
                        '/' -> builder.append('/')
                        'b' -> builder.append('\b')
                        'f' -> builder.append('\u000C')
                        'n' -> builder.append('\n')
                        'r' -> builder.append('\r')
                        't' -> builder.append('\t')
                        'u' -> {
                            if (index + 5 >= json.length) {
                                return null
                            }
                            val hex = json.substring(index + 2, index + 6)
                            if (!hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                                return null
                            }
                            builder.append(hex.toInt(16).toChar())
                            index += 6
                            continue
                        }
                        else -> builder.append(escaped)
                    }
                    index += 2
                }
                else -> {
                    builder.append(ch)
                    index++
                }
            }
        }
        return null
    }

    private fun extractBalanced(json: String, startIndex: Int, openChar: Char, closeChar: Char): String? {
        if (startIndex >= json.length || json[startIndex] != openChar) {
            return null
        }
        var depth = 0
        var inString = false
        var escaped = false
        for (index in startIndex until json.length) {
            val ch = json[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                openChar -> depth++
                closeChar -> {
                    depth--
                    if (depth == 0) {
                        return json.substring(startIndex, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun skipWhitespace(json: String, startIndex: Int): Int {
        var index = startIndex
        while (index < json.length && json[index].isWhitespace()) {
            index++
        }
        return index
    }
}
