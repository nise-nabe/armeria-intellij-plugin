package com.linecorp.intellij.plugins.armeria.run

import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

object ArmeriaHttpClientEnvironment {
    const val FILE_NAME = "http-client.env.json"
    const val ENV_NAME = "armeria"
    const val HOST_KEY = "host"
    const val PORT_KEY = "port"
    const val SCHEME_KEY = "scheme"
    const val REQUEST_BASE_URL = "{{scheme}}://{{host}}:{{port}}"

    fun content(
        host: String,
        port: Int,
        https: Boolean = false,
    ): String =
        buildString {
            appendLine("{")
            appendLine("  \"$ENV_NAME\": {")
            appendLine("    \"$HOST_KEY\": ${jsonString(host)},")
            appendLine("    \"$PORT_KEY\": \"$port\",")
            appendLine("    \"$SCHEME_KEY\": ${jsonString(scheme(https))}")
            appendLine("  }")
            appendLine("}")
        }

    fun merge(
        project: Project,
        existing: String?,
        host: String,
        port: Int,
        https: Boolean = false,
    ): String {
        if (existing.isNullOrBlank()) {
            return content(host, port, https)
        }
        val root = readRootObject(project, existing)
        if (root == null) {
            return existing
        }
        val armeriaProperty = root.findProperty(ENV_NAME)
        if (armeriaProperty == null) {
            return insertArmeriaEnv(existing, root, host, port, https)
        }
        val armeriaObject = armeriaProperty.value as? JsonObject
        if (armeriaObject == null) {
            val replacement = "\"$ENV_NAME\": ${armeriaObjectJson(host, port, https)}"
            return existing.replaceRange(
                armeriaProperty.textRange.startOffset,
                armeriaProperty.textRange.endOffset,
                replacement,
            )
        }
        return mergeArmeriaObject(existing, armeriaObject, host, port, https)
    }

    fun requestBaseUrl(
        requested: String,
        defaultUrl: String,
        envFileExists: Boolean,
    ): String =
        if (requested == defaultUrl && envFileExists) {
            REQUEST_BASE_URL
        } else {
            requested
        }

    private fun readRootObject(
        project: Project,
        text: String,
    ): JsonObject? {
        val file =
            PsiFileFactory
                .getInstance(project)
                .createFileFromText(FILE_NAME, JsonFileType.INSTANCE, text) as? JsonFile
        if (file == null || PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null) {
            return null
        }
        return file.topLevelValue as? JsonObject
    }

    private fun mergeArmeriaObject(
        text: String,
        armeriaObject: JsonObject,
        host: String,
        port: Int,
        https: Boolean,
    ): String {
        val values =
            linkedMapOf(
                HOST_KEY to host,
                PORT_KEY to port.toString(),
                SCHEME_KEY to scheme(https),
            )
        val edits = mutableListOf<TextEdit>()
        val missing = mutableListOf<Pair<String, String>>()
        for ((key, value) in values) {
            val jsonValue = armeriaObject.findProperty(key)?.value
            if (jsonValue == null) {
                missing += key to value
            } else {
                edits += TextEdit(jsonValue.textRange.startOffset, jsonValue.textRange.endOffset, jsonString(value))
            }
        }
        if (missing.isNotEmpty()) {
            val insertOffset = insertionOffset(armeriaObject)
            val needsComma = needsCommaBeforeInsert(text, insertOffset, armeriaObject.propertyList.isNotEmpty())
            edits +=
                TextEdit(
                    insertOffset,
                    insertOffset,
                    insertedProperties(missing, needsComma),
                )
        }
        return applyEdits(text, edits)
    }

    private fun insertArmeriaEnv(
        text: String,
        root: JsonObject,
        host: String,
        port: Int,
        https: Boolean,
    ): String {
        val insertOffset = insertionOffset(root)
        val needsComma = needsCommaBeforeInsert(text, insertOffset, root.propertyList.isNotEmpty())
        val inserted =
            buildString {
                if (needsComma) {
                    append(',')
                }
                append('\n')
                append("  \"$ENV_NAME\": ")
                append(armeriaObjectJson(host, port, https))
            }
        return text.replaceRange(insertOffset, insertOffset, inserted)
    }

    private fun armeriaObjectJson(
        host: String,
        port: Int,
        https: Boolean,
    ): String =
        buildString {
            appendLine("{")
            appendLine("    \"$HOST_KEY\": ${jsonString(host)},")
            appendLine("    \"$PORT_KEY\": \"$port\",")
            appendLine("    \"$SCHEME_KEY\": ${jsonString(scheme(https))}")
            append("  }")
        }

    private fun insertedProperties(
        properties: List<Pair<String, String>>,
        needsComma: Boolean,
    ): String =
        buildString {
            if (needsComma) {
                append(',')
            }
            properties.forEachIndexed { index, (key, value) ->
                append('\n')
                append("    \"$key\": ${jsonString(value)}")
                if (index < properties.lastIndex) {
                    append(',')
                }
            }
        }

    private fun insertionOffset(jsonObject: JsonObject): Int {
        val closingBrace = jsonObject.lastChild
        var element = closingBrace.prevSibling
        var offset = closingBrace.textRange.startOffset
        while (element is PsiWhiteSpace || element is PsiComment) {
            offset = element.textRange.startOffset
            element = element.prevSibling
        }
        return offset
    }

    private fun needsCommaBeforeInsert(
        text: String,
        insertOffset: Int,
        hasProperties: Boolean,
    ): Boolean {
        if (!hasProperties) {
            return false
        }
        var offset = insertOffset - 1
        while (offset >= 0 && text[offset].isWhitespace()) {
            offset--
        }
        return offset >= 0 && text[offset] != ',' && text[offset] != '{'
    }

    private fun applyEdits(
        text: String,
        edits: List<TextEdit>,
    ): String {
        var updated = text
        for (edit in edits.sortedByDescending { it.startOffset }) {
            updated = updated.replaceRange(edit.startOffset, edit.endOffset, edit.replacement)
        }
        return updated
    }

    private fun scheme(https: Boolean): String = if (https) "https" else "http"

    private fun jsonString(value: String): String =
        buildString {
            append('"')
            for (char in value) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }

    private data class TextEdit(
        val startOffset: Int,
        val endOffset: Int,
        val replacement: String,
    )
}
