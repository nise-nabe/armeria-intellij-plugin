package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.reference.EntryPoint
import com.intellij.codeInspection.reference.RefElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.util.xmlb.XmlSerializer
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message
import org.jdom.Element

class ArmeriaMethodEntryPoint(
    @JvmField var wasSelected: Boolean = true,
) : EntryPoint() {
    override fun getDisplayName(): String =
        runCatching { message("inspection.entrypoint.armeria") }
            .getOrDefault(ENTRY_POINT_DISPLAY_NAME)

    override fun isEntryPoint(
        refElement: RefElement,
        psiElement: PsiElement,
    ) = isEntryPoint(psiElement)

    override fun isEntryPoint(psiElement: PsiElement): Boolean =
        psiElement is PsiMethod &&
            AnnotationUtil.isAnnotated(
                psiElement,
                ArmeriaRouteSupport.routeAnnotations.keys,
                AnnotationUtil.CHECK_TYPE,
            )

    override fun isSelected(): Boolean = wasSelected

    override fun setSelected(selected: Boolean) {
        wasSelected = selected
    }

    override fun readExternal(element: Element) {
        XmlSerializer.deserializeInto(this, element)
    }

    override fun writeExternal(element: Element) {
        if (!wasSelected) {
            XmlSerializer.serializeInto(this, element)
        }
    }

    private companion object {
        private const val ENTRY_POINT_DISPLAY_NAME = "Armeria Methods"
    }
}
