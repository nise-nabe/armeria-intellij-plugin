package com.linecorp.intellij.plugins.armeria.test

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

class ArmeriaKotlinJUnitServerExtensionLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName(): String = message("test.support.lineMarker.name")

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val containingFile = element.containingFile
        if (containingFile == null || !ArmeriaJUnitServerExtensionSupport.isInTestSourceContent(containingFile)) {
            return null
        }
        val scope = GlobalSearchScope.projectScope(element.project)
        val property = element.parent as? KtProperty
        if (property != null && element == property.nameIdentifier) {
            if (ArmeriaJUnitServerExtensionSupport.serverExtensionFromKotlinProperty(property, scope) != null) {
                return markerFor(property.nameIdentifier ?: property, property.textRange, property.name)
            }
            return null
        }
        val function = element.parent as? KtNamedFunction
        if (function != null && element.node.elementType == KtTokens.IDENTIFIER && element.text == function.name) {
            if (ArmeriaJUnitServerExtensionSupport.serverExtensionFromKotlinFunction(function, scope) != null) {
                return markerFor(function.nameIdentifier ?: function, function.textRange, function.name)
            }
        }
        return null
    }

    private fun markerFor(
        anchor: PsiElement,
        range: com.intellij.openapi.util.TextRange,
        name: String?,
    ): LineMarkerInfo<*> =
        LineMarkerInfo(
            anchor,
            range,
            AllIcons.RunConfigurations.Junit,
            { message("test.support.lineMarker.tooltip", name.orEmpty()) },
            null,
            GutterIconRenderer.Alignment.CENTER,
        )
}
