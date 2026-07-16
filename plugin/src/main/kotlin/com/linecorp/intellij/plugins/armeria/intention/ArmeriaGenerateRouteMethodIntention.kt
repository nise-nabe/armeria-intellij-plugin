package com.linecorp.intellij.plugins.armeria.intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaGenerateRouteMethodIntention : PsiElementBaseIntentionAction() {
    override fun getText(): String = message("intention.generate.route.method")

    override fun getFamilyName(): String = message("intention.generate.route.method.family")

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        val serviceClass = ArmeriaIntentionSupport.annotatedServiceClass(element) ?: return false
        if (serviceClass.containingFile !is PsiJavaFile) {
            return false
        }
        return ArmeriaIntentionSupport.isInsideClassBody(element, serviceClass)
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val serviceClass = ArmeriaIntentionSupport.annotatedServiceClass(element) ?: return
        val methodName = ArmeriaIntentionSupport.suggestMethodName(serviceClass, "handler")
        val path = ArmeriaIntentionSupport.suggestPath(methodName)
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
}
