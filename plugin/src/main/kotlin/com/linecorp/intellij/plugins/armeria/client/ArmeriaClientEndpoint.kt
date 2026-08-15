package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRouteMetadata

data class ArmeriaClientEndpoint(
    val clientType: String,
    val target: String,
    val uri: String,
    val moduleName: String,
    val pointer: SmartPsiElementPointer<PsiElement>,
    val decorators: List<String> = emptyList(),
    val endpointGroup: String? = null,
    val transport: String? = null,
    /** Plain-text offset (Scala); null for PSI-backed Java/Kotlin so navigation uses the pointer. */
    val sourceOffset: Int? = null,
    /** File URL captured at collect time for Explorer identity on the EDT. */
    val sourceFileUrl: String? = null,
) {
    companion object {
        fun create(
            element: PsiElement,
            clientType: String,
            target: String,
            uri: String,
            decorators: List<String> = emptyList(),
            endpointGroup: String? = null,
            transport: String? = null,
            sourceOffset: Int? = null,
            sourceFileUrl: String? = null,
        ): ArmeriaClientEndpoint =
            ArmeriaClientEndpoint(
                clientType = clientType,
                target = target,
                uri = uri,
                moduleName = ArmeriaRouteMetadata.moduleName(element),
                pointer = SmartPointerManager.createPointer(element),
                decorators = decorators,
                endpointGroup = endpointGroup,
                transport = transport,
                sourceOffset = sourceOffset,
                sourceFileUrl = sourceFileUrl ?: element.containingFile?.virtualFile?.url,
            )
    }
}
