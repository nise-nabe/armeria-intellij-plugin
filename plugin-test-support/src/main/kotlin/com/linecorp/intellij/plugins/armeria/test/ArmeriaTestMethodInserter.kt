package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.linecorp.intellij.plugins.armeria.editor.ArmeriaFileEditorOpenSupport
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
    ): Boolean = completeInsertion(project, prepareInsertion(project, route, captureEditorContext(project)))

    fun insertFromRouteExplorerAsync(
        project: Project,
        route: ArmeriaRoute,
    ) {
        val editorContext = captureEditorContext(project)
        ReadAction
            .nonBlocking<PreparedInsertion> { prepareInsertion(project, route, editorContext) }
            .inSmartMode(project)
            .expireWith(project)
            .coalesceBy(this, route.httpMethod, route.path, route.target, route.moduleName)
            .finishOnUiThread(ModalityState.defaultModalityState()) { prepared ->
                completeInsertion(project, prepared)
            }.submit(AppExecutorUtil.getAppExecutorService())
    }

    private data class EditorContext(
        val selectedFile: VirtualFile?,
        val caretOffset: Int?,
    )

    private data class PreparedInsertion(
        val targetClassPointer: SmartPsiElementPointer<PsiClass>? = null,
        val language: ArmeriaTestLanguage? = null,
        val methodText: String = "",
        val methodName: String = "",
        val warningMessage: String? = null,
        val infoMessage: String? = null,
    )

    private fun prepareInsertion(
        project: Project,
        route: ArmeriaRoute,
        editorContext: EditorContext,
    ): PreparedInsertion {
        val targetClass =
            resolveTargetClass(project, route, editorContext)
                ?: return PreparedInsertion(warningMessage = message("test.support.insert.noTarget"))
        val language =
            when {
                targetClass.containingFile is PsiJavaFile -> {
                    ArmeriaTestLanguage.JAVA
                }

                isKotlinPluginAvailable() && ArmeriaKotlinTestMethodSupport.isKotlinClass(targetClass) -> {
                    ArmeriaTestLanguage.KOTLIN
                }

                else -> {
                    return PreparedInsertion(warningMessage = message("test.support.insert.noTarget"))
                }
            }
        val classExtensions = ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, targetClass)
        val extension =
            when (classExtensions.size) {
                1 -> {
                    classExtensions.single()
                }

                0 -> {
                    return PreparedInsertion(warningMessage = message("test.support.insert.noServerExtension"))
                }

                else -> {
                    return PreparedInsertion(warningMessage = message("test.support.insert.ambiguousServerExtension"))
                }
            }
        val methodName = ArmeriaTestMethodGenerator.suggestMethodName(route)
        if (targetClass.methods.any { it.name == methodName }) {
            return PreparedInsertion(infoMessage = message("test.support.insert.methodExists", methodName))
        }
        if (language == ArmeriaTestLanguage.KOTLIN &&
            isKotlinPluginAvailable() &&
            ArmeriaKotlinTestMethodSupport.hasKotlinMethod(project, targetClass, methodName)
        ) {
            return PreparedInsertion(infoMessage = message("test.support.insert.methodExists", methodName))
        }
        return PreparedInsertion(
            targetClassPointer = SmartPointerManager.createPointer(targetClass),
            language = language,
            methodText = ArmeriaTestMethodGenerator.generateTestMethod(route, extension.serverReceiver, language),
            methodName = methodName,
        )
    }

    private fun completeInsertion(
        project: Project,
        prepared: PreparedInsertion,
    ): Boolean {
        if (project.isDisposed) {
            return false
        }
        prepared.warningMessage?.let {
            Messages.showWarningDialog(project, it, message("test.support.insert.title"))
            return false
        }
        prepared.infoMessage?.let {
            Messages.showInfoMessage(project, it, message("test.support.insert.title"))
            return false
        }
        val (targetClass, methodExists) =
            ApplicationManager.getApplication().runReadAction<Pair<PsiClass?, Boolean>> {
                val resolvedClass = prepared.targetClassPointer?.element
                val javaMethodExists = resolvedClass?.methods?.any { it.name == prepared.methodName } == true
                val kotlinMethodExists =
                    resolvedClass != null &&
                        prepared.language == ArmeriaTestLanguage.KOTLIN &&
                        isKotlinPluginAvailable() &&
                        ArmeriaKotlinTestMethodSupport.hasKotlinMethod(project, resolvedClass, prepared.methodName)
                resolvedClass to (javaMethodExists || kotlinMethodExists)
            }
        if (targetClass == null) {
            return false
        }
        val language = prepared.language ?: return false
        if (methodExists) {
            Messages.showInfoMessage(
                project,
                message("test.support.insert.methodExists", prepared.methodName),
                message("test.support.insert.title"),
            )
            return false
        }
        WriteCommandAction.runWriteCommandAction(
            project,
            message("route.explorer.action.generateTestMethod"),
            null,
            {
                if (language == ArmeriaTestLanguage.JAVA) {
                    insertJavaMethod(project, targetClass, prepared.methodText)
                } else if (isKotlinPluginAvailable()) {
                    ArmeriaKotlinTestMethodSupport.insertKotlinMethodFor(project, targetClass, prepared.methodText)
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
        targetClass.containingFile.virtualFile?.let { virtualFile ->
            ArmeriaFileEditorOpenSupport.openFileAtOffset(project, virtualFile, added.textRange.startOffset)
        }
    }

    private fun captureEditorContext(project: Project): EditorContext {
        val fileEditorManager = FileEditorManager.getInstance(project)
        return EditorContext(
            selectedFile = fileEditorManager.selectedFiles.firstOrNull(),
            caretOffset = fileEditorManager.selectedTextEditor?.caretModel?.offset,
        )
    }

    private fun resolveTargetClass(
        project: Project,
        route: ArmeriaRoute,
        editorContext: EditorContext,
    ): PsiClass? {
        val selectedPsiFile = editorContext.selectedFile?.let { PsiManager.getInstance(project).findFile(it) }
        val elementAtCaret =
            if (selectedPsiFile != null && editorContext.caretOffset != null) {
                selectedPsiFile.findElementAt(editorContext.caretOffset)
            } else {
                null
            }
        if (selectedPsiFile != null && editorContext.caretOffset != null) {
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

    internal fun resolveTargetClassInternal(
        project: Project,
        route: ArmeriaRoute,
    ): PsiClass? = resolveTargetClass(project, route, captureEditorContext(project))

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
