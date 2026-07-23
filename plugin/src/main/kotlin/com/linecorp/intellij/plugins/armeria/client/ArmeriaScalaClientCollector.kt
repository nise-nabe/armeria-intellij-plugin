package com.linecorp.intellij.plugins.armeria.client

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaScalaTextSupport

internal object ArmeriaScalaClientCollector {
    private val CLIENT_SIMPLE_NAMES_PATTERN =
        ArmeriaClientSupport.clientSimpleNames().joinToString("|") { Regex.escape(it) }
    private val FACTORY_METHODS_PATTERN =
        ArmeriaClientSupport.FACTORY_METHOD_NAMES.joinToString("|") { Regex.escape(it) }
    private val QUALIFIED_CLIENT_ENDPOINT_PATTERN =
        Regex(
            """\b(com\.linecorp\.armeria(?:\.[A-Za-z_][\w]*)*)\.($CLIENT_SIMPLE_NAMES_PATTERN)\s*\.\s*($FACTORY_METHODS_PATTERN)\s*\(\s*"([^"]+)"\s*\)""",
        )
    private val SIMPLE_CLIENT_ENDPOINT_PATTERN =
        Regex(
            """\b($CLIENT_SIMPLE_NAMES_PATTERN)\s*\.\s*($FACTORY_METHODS_PATTERN)\s*\(\s*"([^"]+)"\s*\)""",
        )

    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
    ) {
        for (virtualFile in FilenameIndex.getAllFilesByExt(project, "scala", scope)) {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: continue
            val contents = psiFile.text
            if (!ArmeriaRouteSupport.referencesArmeriaSourceContent(contents)) {
                continue
            }
            collectFromFile(psiFile, contents, endpoints, seenEndpoints)
        }
    }

    private fun collectFromFile(
        file: PsiFile,
        contents: String,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
    ) {
        val scan = ArmeriaScalaTextSupport.scanScalaText(contents)
        val filePath = file.virtualFile?.path ?: return
        val importedClientClasses = parseScalaArmeriaClientImports(contents)
        val matches = mutableListOf<ClientEndpointMatch>()
        for (match in QUALIFIED_CLIENT_ENDPOINT_PATTERN.findAll(scan.textWithoutComments)) {
            val offset = match.range.first
            if (scan.isInsideLiteral(offset)) {
                continue
            }
            val fqcn = "${match.groupValues[1]}.${match.groupValues[2]}"
            val protocol = ArmeriaClientSupport.protocolForClass(fqcn) ?: continue
            matches +=
                ClientEndpointMatch(
                    offset = offset,
                    protocol = protocol,
                    target = match.groupValues[2],
                    uri = match.groupValues[4],
                )
        }
        for (match in SIMPLE_CLIENT_ENDPOINT_PATTERN.findAll(scan.textWithoutComments)) {
            val offset = match.range.first
            if (scan.isInsideLiteral(offset)) {
                continue
            }
            val simpleName = match.groupValues[1]
            val fqcn = importedClientClasses[simpleName] ?: continue
            val protocol = ArmeriaClientSupport.protocolForClass(fqcn) ?: continue
            matches +=
                ClientEndpointMatch(
                    offset = offset,
                    protocol = protocol,
                    target = simpleName,
                    uri = match.groupValues[3],
                )
        }
        for (match in matches.sortedBy { it.offset }) {
            if (isNestedInsideScalaClientFactoryArgument(scan.textWithoutComments, match.offset, matches)) {
                continue
            }
            val element = file.findElementAt(match.offset) ?: file
            ArmeriaClientCollector.addEndpoint(
                element = element,
                protocol = match.protocol,
                target = match.target,
                uri = match.uri,
                endpoints = endpoints,
                seenEndpoints = seenEndpoints,
                dedupeKey = "$filePath:${match.offset}",
                sourceOffset = match.offset,
            )
        }
    }

    private data class ClientEndpointMatch(
        val offset: Int,
        val protocol: ClientProtocol,
        val target: String,
        val uri: String,
    )

    private fun parseScalaArmeriaClientImports(contents: String): Map<String, String> {
        val imports = mutableMapOf<String, String>()
        val importPattern = Regex("""import\s+([\w.]+)""")
        for (match in importPattern.findAll(contents)) {
            val fqcn = match.groupValues[1]
            if (fqcn.startsWith(ArmeriaClientSupport.ARMERIA_CLIENT_PACKAGE_PREFIX) &&
                ArmeriaClientSupport.protocolForClass(fqcn) != null
            ) {
                imports[fqcn.substringAfterLast('.')] = fqcn
            }
        }
        return imports
    }

    private fun isNestedInsideScalaClientFactoryArgument(
        text: String,
        matchOffset: Int,
        allMatches: List<ClientEndpointMatch>,
    ): Boolean {
        val ourOpenParen = text.indexOf('(', matchOffset)
        if (ourOpenParen < 0) {
            return false
        }
        for (outer in allMatches) {
            if (outer.offset >= matchOffset) {
                break
            }
            val outerOpenParen = text.indexOf('(', outer.offset)
            if (outerOpenParen < 0 || outerOpenParen >= ourOpenParen) {
                continue
            }
            val outerCloseParen = findMatchingCloseParen(text, outerOpenParen) ?: continue
            if (matchOffset > outerOpenParen && matchOffset < outerCloseParen && ourOpenParen < outerCloseParen) {
                return true
            }
        }
        return false
    }

    private fun findMatchingCloseParen(
        text: String,
        openParenOffset: Int,
    ): Int? {
        var depth = 0
        var index = openParenOffset
        while (index < text.length) {
            when (text[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
            }
            index++
        }
        return null
    }
}
