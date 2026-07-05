package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.PsiElement
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteMetadata

data class ArmeriaClientEndpoint(
    val clientType: String,
    val target: String,
    val uri: String,
    val moduleName: String,
    val features: List<String>,
    val pointer: SmartPsiElementPointer<PsiElement>,
) {
    companion object {
        fun create(
            element: PsiElement,
            clientType: String,
            target: String,
            uri: String,
            features: List<String> = emptyList(),
        ): ArmeriaClientEndpoint {
            return ArmeriaClientEndpoint(
                clientType = clientType,
                target = target,
                uri = uri,
                moduleName = ArmeriaRouteMetadata.moduleName(element),
                features = features,
                pointer = SmartPointerManager.createPointer(element),
            )
        }
    }
}
