package com.linecorp.intellij.plugins.armeria.test

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaJUnitServerExtensionLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName(): String = message("test.support.lineMarker.name")

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is PsiIdentifier) {
            return null
        }
        val containingFile = element.containingFile
        if (containingFile == null || !ArmeriaJUnitServerExtensionSupport.isInTestSourceContent(containingFile)) {
            return null
        }
        val scope = GlobalSearchScope.projectScope(element.project)
        val field = element.parent as? PsiField
        if (field != null && element == field.nameIdentifier) {
            if (ArmeriaJUnitServerExtensionSupport.serverExtensionFromField(field, scope) != null) {
                return markerFor(field.nameIdentifier, field.textRange, field.name)
            }
            return null
        }
        val method = element.parent as? PsiMethod
        if (method != null && element == method.nameIdentifier) {
            if (ArmeriaJUnitServerExtensionSupport.serverExtensionFromMethod(method, scope) != null) {
                return markerFor(method.nameIdentifier ?: method, method.textRange, method.name)
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
            { message("test.support.lineMarker.name") },
        )
}
