package com.linecorp.intellij.plugins.armeria.intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaGenerateRouteMethodIntention : PsiElementBaseIntentionAction() {
    override fun getText(): String = message("intention.generate.route.method")

    override fun getFamilyName(): String = message("intention.generate.route.method.family")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        val serviceClass = annotatedServiceClass(element) ?: return false
        if (serviceClass.containingFile !is PsiJavaFile) {
            return false
        }
        return isMemberDeclarationContext(element, serviceClass)
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val serviceClass = annotatedServiceClass(element) ?: return
        val methodName = suggestMethodName(serviceClass, "handler")
        val path = "/$methodName"
        val factory = JavaPsiFacade.getElementFactory(project)
        val method = factory.createMethodFromText(
            """
            @${ArmeriaRouteSupport.GET_ANNOTATION}("$path")
            public String $methodName() {
                return "";
            }
            """.trimIndent(),
            serviceClass,
        )
        WriteCommandAction.runWriteCommandAction(project) {
            val anchor = serviceClass.rBrace ?: return@runWriteCommandAction
            val added = serviceClass.addBefore(method, anchor) as PsiMethod
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(added)
            CodeStyleManager.getInstance(project).reformat(added)
            added.nameIdentifier?.textRange?.let { range ->
                editor.caretModel.moveToOffset(range.startOffset)
            }
        }
    }

    private fun annotatedServiceClass(element: PsiElement): PsiClass? {
        val serviceClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java, false) ?: return null
        return serviceClass.takeIf(::isAnnotatedServiceCandidate)
    }

    private fun isMemberDeclarationContext(element: PsiElement, serviceClass: PsiClass): Boolean {
        val bodyStart = serviceClass.lBrace?.textRange?.startOffset ?: return false
        val bodyEnd = serviceClass.rBrace?.textRange?.endOffset ?: return false
        val offset = element.textRange.startOffset
        if (offset !in bodyStart..bodyEnd) {
            return false
        }
        val codeBlock = PsiTreeUtil.getParentOfType(element, PsiCodeBlock::class.java, false)
        return codeBlock == null || !PsiTreeUtil.isAncestor(serviceClass, codeBlock, true)
    }

    private fun suggestMethodName(serviceClass: PsiClass, baseName: String): String {
        val usedNames = serviceClass.methods.mapTo(linkedSetOf()) { it.name }
        var candidate = baseName
        var suffix = 2
        while (candidate in usedNames) {
            candidate = "$baseName$suffix"
            suffix++
        }
        return candidate
    }

    private fun isAnnotatedServiceCandidate(serviceClass: PsiClass): Boolean {
        if (serviceClass.isInterface || serviceClass.isEnum || serviceClass.isAnnotationType) {
            return false
        }
        if (serviceClass.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION) != null) {
            return true
        }
        return serviceClass.methods.any { ArmeriaRouteSupport.findRouteAnnotation(it) != null }
    }
}
