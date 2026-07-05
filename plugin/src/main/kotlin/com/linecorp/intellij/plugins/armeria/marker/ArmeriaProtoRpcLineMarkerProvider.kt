package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.message

internal class ArmeriaProtoRpcLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val file = element.containingFile ?: return null
        if (!file.name.endsWith(".proto")) {
            return null
        }
        if (element.firstChild != null) {
            return null
        }
        val fileText = file.text
        val elementStart = element.textOffset
        val elementEnd = elementStart + element.textLength
        val rpcIndex = fileText.indexOf("rpc", elementStart)
        if (rpcIndex < 0 || rpcIndex + 3 > elementEnd) {
            return null
        }
        val line = lineAtOffset(fileText, rpcIndex) ?: return null
        val rpcName = RPC_LINE_PATTERN.find(line)?.groupValues?.get(1) ?: return null
        val serviceName = SERVICE_HEADER_PATTERN.findAll(fileText.substring(0, rpcIndex)).lastOrNull()
            ?.groupValues?.get(1) ?: return null
        val packageName = PACKAGE_PATTERN.find(fileText)?.groupValues?.get(1).orEmpty()
        val fqService = if (packageName.isBlank()) serviceName else "$packageName.$serviceName"
        val path = "/$fqService/$rpcName"
        return LineMarkerInfo(
            element,
            element.textRange,
            ArmeriaIcons.Armeria,
            { message("marker.grpc.rpc", rpcName, path) },
            null,
            GutterIconRenderer.Alignment.CENTER,
            { message("marker.grpc.title") },
        )
    }

    companion object {
        private val RPC_LINE_PATTERN = Regex("""\brpc\s+(\w+)\s*\(""")
        private val SERVICE_HEADER_PATTERN = Regex("""\bservice\s+(\w+)\s*\{""")
        private val PACKAGE_PATTERN = Regex("""\bpackage\s+([\w.]+)\s*;?""")

        private fun lineAtOffset(text: String, offset: Int): String? {
            var currentOffset = 0
            for (line in text.lineSequence()) {
                val lineEnd = currentOffset + line.length
                if (offset in currentOffset..lineEnd) {
                    return line
                }
                currentOffset = lineEnd + 1
            }
            return null
        }
    }
}
