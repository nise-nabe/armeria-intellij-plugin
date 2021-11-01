package com.nisecoder.intellij.plugins.armeria.inspection

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.reference.EntryPoint
import com.intellij.codeInspection.reference.RefElement
import com.intellij.openapi.util.DefaultJDOMExternalizer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.util.xmlb.XmlSerializer
import com.nisecoder.intellij.plugins.armeria.message
import org.jdom.Element

class ArmeriaMethodEntryPoint(
    @JvmField var wasSelected: Boolean = true
): EntryPoint() {
    override fun getDisplayName() = message("inspection.entrypoint.armeria")

    override fun isEntryPoint(refElement: RefElement, psiElement: PsiElement) = isEntryPoint(psiElement)

    override fun isEntryPoint(psiElement: PsiElement): Boolean {
        return psiElement is PsiMethod && AnnotationUtil.isAnnotated(psiElement, listOf(
            "com.linecorp.armeria.server.annotation.Get",
            "com.linecorp.armeria.server.annotation.Head",
            "com.linecorp.armeria.server.annotation.Post",
            "com.linecorp.armeria.server.annotation.Put",
            "com.linecorp.armeria.server.annotation.Delete",
            "com.linecorp.armeria.server.annotation.Options",
            "com.linecorp.armeria.server.annotation.Patch",
            "com.linecorp.armeria.server.annotation.Trace",
        ), AnnotationUtil.CHECK_TYPE)
    }

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

}
