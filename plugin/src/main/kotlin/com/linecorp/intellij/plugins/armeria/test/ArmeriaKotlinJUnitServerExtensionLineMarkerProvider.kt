package com.linecorp.intellij.plugins.armeria.test

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtProperty

class ArmeriaKotlinJUnitServerExtensionLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName(): String = message("test.support.lineMarker.name")

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val property = element as? KtProperty ?: return null
        if (element != property.nameIdentifier && element != property) {
            return null
        }
        val scope = GlobalSearchScope.projectScope(property.project)
        if (ArmeriaJUnitServerExtensionSupport.serverExtensionFromKotlinProperty(property, scope) == null) {
            return null
        }
        return LineMarkerInfo(
            property,
            property.textRange,
            AllIcons.RunConfigurations.Junit,
            { message("test.support.lineMarker.tooltip", property.name.orEmpty()) },
            null,
            GutterIconRenderer.Alignment.CENTER,
        )
    }
}
