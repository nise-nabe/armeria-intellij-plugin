package com.linecorp.intellij.plugins.armeria.test

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaJUnitServerExtensionLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName(): String = message("test.support.lineMarker.name")

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is PsiIdentifier) {
            return null
        }
        val field = element.parent as? PsiField ?: return null
        if (element != field.nameIdentifier) {
            return null
        }
        val scope = GlobalSearchScope.projectScope(field.project)
        if (ArmeriaJUnitServerExtensionSupport.serverExtensionFromField(field, scope) == null) {
            return null
        }
        return LineMarkerInfo(
            field.nameIdentifier ?: field,
            field.textRange,
            AllIcons.RunConfigurations.Junit,
            { message("test.support.lineMarker.tooltip", field.name) },
            null,
            GutterIconRenderer.Alignment.CENTER,
        )
    }
}
