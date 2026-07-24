package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.ImportPath

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
            if (targetClass.containingFile is PsiJavaFile) {
                ArmeriaTestLanguage.JAVA
            } else {
                ArmeriaTestLanguage.KOTLIN
            }
        val classExtensions = ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, targetClass)
        val extension =
            when (classExtensions.size) {
                1 -> classExtensions.single()
                0 ->
                    run {
                        Messages.showWarningDialog(
                            project,
                            message("test.support.insert.noServerExtension"),
                            message("test.support.insert.title"),
                        )
                        return false
                    }
                else ->
                    run {
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
        val kotlinTargetClass =
            if (language == ArmeriaTestLanguage.KOTLIN) {
                resolveKotlinTargetClass(project, targetClass)
            } else {
                null
            }
        if (language == ArmeriaTestLanguage.KOTLIN) {
            val ktClass =
                kotlinTargetClass ?: run {
                    Messages.showWarningDialog(
                        project,
                        message("test.support.insert.noTarget"),
                        message("test.support.insert.title"),
                    )
                    return false
                }
            if (ktClass.declarations.filterIsInstance<KtNamedFunction>().any { it.name == methodName }) {
                Messages.showInfoMessage(
                    project,
                    message("test.support.insert.methodExists", methodName),
                    message("test.support.insert.title"),
                )
                return false
            }
        }
        val methodText = ArmeriaTestMethodGenerator.generateTestMethod(route, extension.variableName, language)
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
                } else {
                    val ktClass = kotlinTargetClass ?: return@runWriteCommandAction
                    insertKotlinMethod(project, ktClass, methodText)
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

    internal fun insertKotlinMethod(
        project: Project,
        ktClass: KtClass,
        methodText: String,
    ) {
        val factory = KtPsiFactory(project)
        val function = factory.createFunction(methodText)
        val added = ktClass.addDeclaration(function)
        val ktFile = ktClass.containingKtFile
        insertKotlinImport(project, ktFile, "org.junit.jupiter.api.Test")
        insertKotlinImport(project, ktFile, "org.junit.jupiter.api.Assertions.assertEquals")
        if (methodText.contains("WebClient")) {
            insertKotlinImport(project, ktFile, ArmeriaJUnitServerExtensionSupport.WEB_CLIENT_CLASS)
        }
        val editor =
            ktFile.virtualFile
                ?.let { FileEditorManager.getInstance(project).openFile(it, true).firstOrNull() as? Editor }
        editor?.caretModel?.moveToOffset(added.textRange.startOffset)
        editor?.scrollingModel?.scrollToCaret(ScrollType.CENTER)
    }

    private fun insertKotlinImport(
        project: Project,
        ktFile: KtFile,
        fqName: String,
    ) {
        val path = FqName(fqName)
        if (ktFile.importDirectives.any { it.importedFqName == path }) {
            return
        }
        val factory = KtPsiFactory(project)
        val directive = factory.createImportDirective(ImportPath.fromString(fqName))
        val importList = ktFile.importList
        if (importList != null) {
            importList.add(directive)
            return
        }
        val packageDirective = ktFile.packageDirective
        if (packageDirective != null) {
            ktFile.addAfter(directive, packageDirective)
        } else {
            ktFile.add(directive)
        }
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
            elementAtCaret?.getParentOfType<KtClass>(true)?.toLightClass()?.let { ktClass ->
                if (ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, ktClass).isNotEmpty()) {
                    return ktClass
                }
            }
            elementAtCaret?.let { PsiTreeUtil.getParentOfType(it, PsiClass::class.java) }?.let { javaClass ->
                if (ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, javaClass).isNotEmpty()) {
                    return javaClass
                }
            }
        }
        val selectedClass =
            when (selectedPsiFile) {
                is PsiJavaFile -> resolveJavaTargetClass(project, selectedPsiFile, elementAtCaret)
                is KtFile -> {
                    val topLevelClasses = selectedPsiFile.declarations.filterIsInstance<KtClass>()
                    topLevelClasses.singleOrNull()?.toLightClass()
                }
                else -> null
            }
        if (selectedClass != null &&
            ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, selectedClass).isNotEmpty()
        ) {
            return selectedClass
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

    private fun resolveKotlinTargetClass(
        project: Project,
        targetClass: PsiClass,
    ): KtClass? {
        val selectedFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        selectedFile
            ?.let { PsiManager.getInstance(project).findFile(it) as? KtFile }
            ?.declarations
            ?.filterIsInstance<KtClass>()
            ?.firstOrNull { it.fqName?.asString() == targetClass.qualifiedName }
            ?.let { return it }
        return ArmeriaJUnitServerExtensionSupport.toKtClass(targetClass)
    }

    private fun resolveJavaTargetClass(
        project: Project,
        javaFile: PsiJavaFile,
        elementAtCaret: com.intellij.psi.PsiElement?,
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
