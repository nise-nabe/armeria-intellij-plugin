package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer

internal object ArmeriaRuntimeRoutePointer : SmartPsiElementPointer<PsiElement> {
    override fun getElement(): PsiElement? = null

    override fun getContainingFile(): PsiFile? = null

    override fun getRange(): TextRange? = null

    override fun getProject(): Project = throw UnsupportedOperationException()

    override fun getVirtualFile(): VirtualFile = throw UnsupportedOperationException()

    override fun getPsiRange(): TextRange? = null
}
