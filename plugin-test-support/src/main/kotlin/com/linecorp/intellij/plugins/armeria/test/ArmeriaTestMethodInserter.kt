package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinPluginSupport
import com.linecorp.intellij.plugins.armeria.message

/**
 * Java/Core test-method insertion entry point.
 *
 * Kotlin PSI operations are delegated to [ArmeriaKotlinTestMethodSupport] only after the
 * optional Kotlin plugin availability guard so this class remains loadable without Kotlin.
 */
internal object ArmeriaTestMethodInserter {
    fun insertFromRouteExplorer(
        project: Project,
        route: ArmeriaRoute,
    ): Boolean {
        val targetClass =
            resolveTargetClass(project, route) ?: run {
                Messages.showWarningDialog(
                    project,
                    message("test.support.insert.noTarget"),
                    message("test.support.insert.title"),
                )
                return false
            }
        val language =
            when {
                targetClass.containingFile is PsiJavaFile -> {
                    ArmeriaTestLanguage.JAVA
                }

                isKotlinPluginAvailable() && ArmeriaKotlinTestMethodSupport.isKotlinClass(targetClass) -> {
                    ArmeriaTestLanguage.KOTLIN
                }

                else -> {
                    Messages.showWarningDialog(
                        project,
                        message("test.support.insert.noTarget"),
                        message("test.support.insert.title"),
                    )
                    return false
                }
            }
        val classExtensions = ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, targetClass)
        val extension =
            when (classExtensions.size) {
                1 -> {
                    classExtensions.single()
                }

                0 -> {
                    Messages.showWarningDialog(
                        project,
                        message("test.support.insert.noServerExtension"),
                        message("test.support.insert.title"),
                    )
                    return false
                }

                else -> {
                    Messages.showWarningDialog(
                        project,
                        message("test.support.insert.ambiguousServerExtension"),
                        message("test.support.insert.title"),
                    )
                    return false
                }
            }
        val methodName = ArmeriaTestMethodGenerator.suggestMethodName(route)
        if (targetClass.methods.any { it.name == methodName }) {
            Messages.showInfoMessage(
                project,
                message("test.support.insert.methodExists", methodName),
                message("test.support.insert.title"),
            )
            return false
        }
        if (language == ArmeriaTestLanguage.KOTLIN &&
            isKotlinPluginAvailable() &&
            ArmeriaKotlinTestMethodSupport.hasKotlinMethod(project, targetClass, methodName)
        ) {
            Messages.showInfoMessage(
                project,
                message("test.support.insert.methodExists", methodName),
                message("test.support.insert.title"),
            )
            return false
        }
        val methodText = ArmeriaTestMethodGenerator.generateTestMethod(route, extension.serverReceiver, language)
        if (project.isDisposed) {
            return false
        }
        WriteCommandAction.runWriteCommandAction(
            project,
            message("route.explorer.action.generateTestMethod"),
            null,
            {
                if (language == ArmeriaTestLanguage.JAVA) {
                    insertJavaMethod(project, targetClass, methodText)
                } else if (isKotlinPluginAvailable()) {
                    ArmeriaKotlinTestMethodSupport.insertKotlinMethodFor(project, targetClass, methodText)
                }
            },
        )
        return true
    }

    private fun insertJavaMethod(
        project: Project,
        targetClass: PsiClass,
        methodText: String,
    ) {
        val anchor = targetClass.methods.lastOrNull() ?: targetClass
        val added =
            targetClass.addAfter(
                PsiElementFactory.getInstance(project).createMethodFromText(methodText, targetClass),
                anchor,
            ) as PsiMethod
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(added)
        val editor =
            targetClass.containingFile.virtualFile
                ?.let { FileEditorManager.getInstance(project).openFile(it, true).firstOrNull() as? Editor }
        editor?.caretModel?.moveToOffset(added.textRange.startOffset)
        editor?.scrollingModel?.scrollToCaret(ScrollType.CENTER)
    }

    private fun resolveTargetClass(
        project: Project,
        route: ArmeriaRoute,
    ): PsiClass? = resolveTargetClassInternal(project, route)

    internal fun resolveTargetClassInternal(
        project: Project,
        route: ArmeriaRoute,
    ): PsiClass? {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val selectedFile = fileEditorManager.selectedFiles.firstOrNull()
        val selectedEditor = fileEditorManager.selectedTextEditor
        val selectedPsiFile = selectedFile?.let { PsiManager.getInstance(project).findFile(it) }
        val elementAtCaret =
            if (selectedPsiFile != null && selectedEditor != null) {
                selectedPsiFile.findElementAt(selectedEditor.caretModel.offset)
            } else {
                null
            }
        if (selectedPsiFile != null && selectedEditor != null) {
            if (isKotlinPluginAvailable()) {
                ArmeriaKotlinTestMethodSupport.lightClassAtCaret(elementAtCaret)?.let { kotlinClass ->
                    if (ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, kotlinClass).isNotEmpty()) {
                        return kotlinClass
                    }
                }
            }
            elementAtCaret?.let { PsiTreeUtil.getParentOfType(it, PsiClass::class.java) }?.let { javaClass ->
                if (ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, javaClass).isNotEmpty()) {
                    return javaClass
                }
            }
        }
        val selectedClass =
            when {
                selectedPsiFile is PsiJavaFile -> {
                    resolveJavaTargetClass(project, selectedPsiFile, elementAtCaret)
                }

                isKotlinPluginAvailable() -> {
                    ArmeriaKotlinTestMethodSupport.topLevelLightClass(selectedPsiFile)
                }

                else -> {
                    null
                }
            }
        if (selectedClass != null &&
            ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, selectedClass).isNotEmpty()
        ) {
            return selectedClass
        }
        return resolveModuleFallbackTargetClass(project, route, selectedPsiFile, elementAtCaret, selectedClass)
    }

    private fun resolveModuleFallbackTargetClass(
        project: Project,
        route: ArmeriaRoute,
        selectedPsiFile: PsiFile?,
        elementAtCaret: PsiElement?,
        selectedClass: PsiClass?,
    ): PsiClass? {
        if (selectedPsiFile != null && !ArmeriaJUnitServerExtensionSupport.isInTestSourceContent(selectedPsiFile)) {
            return null
        }
        if (selectedPsiFile != null &&
            editorHasResolvableClass(selectedPsiFile, elementAtCaret, selectedClass)
        ) {
            return null
        }
        val unassignedModule = message("route.explorer.module.unassigned")
        if (route.moduleName == unassignedModule) {
            return null
        }
        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)
        val moduleMatches = extensions.filter { it.moduleName == route.moduleName }
        if (moduleMatches.size != 1) {
            return null
        }
        return moduleMatches
            .single()
            .pointer
            .element
            ?.let { PsiTreeUtil.getParentOfType(it, PsiClass::class.java) }
    }

    private fun editorHasResolvableClass(
        selectedPsiFile: PsiFile,
        elementAtCaret: PsiElement?,
        selectedClass: PsiClass?,
    ): Boolean {
        if (selectedClass != null) {
            return true
        }
        if (elementAtCaret != null) {
            if (PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass::class.java) != null) {
                return true
            }
        }
        if (selectedPsiFile is PsiJavaFile && selectedPsiFile.classes.any { it.containingClass == null }) {
            return true
        }
        return isKotlinPluginAvailable() &&
            ArmeriaKotlinTestMethodSupport.hasResolvableKotlinClass(selectedPsiFile, elementAtCaret)
    }

    private fun isKotlinPluginAvailable(): Boolean = ArmeriaKotlinPluginSupport.isKotlinPluginAvailable()

    private fun resolveJavaTargetClass(
        project: Project,
        javaFile: PsiJavaFile,
        elementAtCaret: PsiElement?,
    ): PsiClass? {
        val topLevelClasses = javaFile.classes.filter { it.containingClass == null }
        elementAtCaret
            ?.let { PsiTreeUtil.getParentOfType(it, PsiClass::class.java) }
            ?.takeIf { candidate -> topLevelClasses.any { it.isEquivalentTo(candidate) } }
            ?.let { return it }
        return topLevelClasses.singleOrNull {
            ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, it).isNotEmpty()
        } ?: topLevelClasses.singleOrNull()
    }
}
