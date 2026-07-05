package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaTestMethodInserter {
    fun insertFromRouteExplorer(project: Project, route: ArmeriaRoute): Boolean {
        val targetClass = resolveTargetClass(project, route) ?: run {
            Messages.showWarningDialog(
                project,
                message("test.support.insert.noTarget"),
                message("test.support.insert.title"),
            )
            return false
        }
        val language = if (targetClass.containingFile is PsiJavaFile) {
            ArmeriaTestLanguage.JAVA
        } else {
            ArmeriaTestLanguage.KOTLIN
        }
        val extension = ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, targetClass).firstOrNull() ?: run {
            Messages.showWarningDialog(
                project,
                message("test.support.insert.noServerExtension"),
                message("test.support.insert.title"),
            )
            return false
        }
        val methodText = ArmeriaTestMethodGenerator.generateTestMethod(route, extension.variableName, language)
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            WriteCommandAction.runWriteCommandAction(
                project,
                message("route.explorer.action.generateTestMethod"),
                null,
                {
                    if (language == ArmeriaTestLanguage.JAVA) {
                        insertJavaMethod(project, targetClass, methodText)
                    } else {
                        insertKotlinMethod(project, targetClass, methodText)
                    }
                },
            )
        }
        return true
    }

    private fun insertJavaMethod(project: Project, targetClass: PsiClass, methodText: String) {
        val anchor = targetClass.methods.lastOrNull() ?: targetClass
        val added = targetClass.addAfter(
            PsiElementFactory.getInstance(project).createMethodFromText(methodText, targetClass),
            anchor,
        ) as PsiMethod
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(added)
        val editor = targetClass.containingFile.virtualFile
            ?.let { FileEditorManager.getInstance(project).openFile(it, true).firstOrNull() as? Editor }
        editor?.caretModel?.moveToOffset(added.textRange.startOffset)
        editor?.scrollingModel?.scrollToCaret(ScrollType.CENTER)
    }

    private fun insertKotlinMethod(project: Project, targetClass: PsiClass, methodText: String) {
        val rBrace = targetClass.rBrace ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(targetClass.containingFile) ?: return
        document.insertString(rBrace.textRange.startOffset, "\n\n$methodText\n")
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    private fun resolveTargetClass(project: Project, route: ArmeriaRoute): PsiClass? {
        val selectedFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        val selectedClass = selectedFile?.let { virtualFile ->
            PsiTreeUtil.getChildOfType(
                PsiManager.getInstance(project).findFile(virtualFile),
                PsiClass::class.java,
            )
        }
        if (selectedClass != null &&
            ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, selectedClass).isNotEmpty()
        ) {
            return selectedClass
        }
        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)
        val pointer = extensions.firstOrNull {
            it.moduleName == route.moduleName ||
                route.moduleName == message("route.explorer.module.unassigned")
        }?.pointer ?: extensions.firstOrNull()?.pointer
        return pointer?.element?.let { PsiTreeUtil.getParentOfType(it, PsiClass::class.java) }
    }
}
