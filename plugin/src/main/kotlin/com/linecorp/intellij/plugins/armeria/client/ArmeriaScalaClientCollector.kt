package com.linecorp.intellij.plugins.armeria.client

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaScalaTextSupport

internal object ArmeriaScalaClientCollector {
    private val CLIENT_ENDPOINT_PATTERN =
        Regex(
            """\b(${ArmeriaClientSupport.clientSimpleNames().joinToString("|")})\s*\.\s*(?:${
                ArmeriaClientSupport.FACTORY_METHOD_NAMES.joinToString("|")
            })\s*\(\s*"([^"]+)"\s*\)""",
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
        for (match in CLIENT_ENDPOINT_PATTERN.findAll(scan.textWithoutComments)) {
            val offset = match.range.first
            if (scan.isInsideStringLiteral(offset)) {
                continue
            }
            val clientSimpleName = match.groupValues[1]
            val protocol = ArmeriaClientSupport.protocolForSimpleName(clientSimpleName) ?: continue
            val element = file.findElementAt(offset) ?: file
            ArmeriaClientCollector.addEndpoint(
                element = element,
                protocol = protocol,
                target = clientSimpleName,
                uri = match.groupValues[2],
                endpoints = endpoints,
                seenEndpoints = seenEndpoints,
                dedupeKey = "$filePath:$offset",
                sourceOffset = offset,
            )
        }
    }
}
