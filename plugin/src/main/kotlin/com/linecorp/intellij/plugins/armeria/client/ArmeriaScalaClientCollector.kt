package com.linecorp.intellij.plugins.armeria.client

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaScalaTextSupport

internal object ArmeriaScalaClientCollector {
    private val CLIENT_CLASS_PROTOCOLS = mapOf(
        "WebClient" to ClientProtocol.HTTP,
        "GrpcClient" to ClientProtocol.GRPC,
        "GrpcClients" to ClientProtocol.GRPC,
        "ThriftClient" to ClientProtocol.THRIFT,
        "ThriftClients" to ClientProtocol.THRIFT,
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
            if (!ArmeriaScalaTextSupport.referencesArmeriaScalaContent(contents)) {
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
        for (match in ArmeriaScalaTextSupport.findClientEndpoints(contents)) {
            val protocol = CLIENT_CLASS_PROTOCOLS[match.clientSimpleName] ?: continue
            val element = file.findElementAt(match.startOffset) ?: file
            ArmeriaClientCollector.addEndpoint(
                element,
                protocol,
                match.clientSimpleName,
                match.uri,
                endpoints,
                seenEndpoints,
            )
        }
    }
}
