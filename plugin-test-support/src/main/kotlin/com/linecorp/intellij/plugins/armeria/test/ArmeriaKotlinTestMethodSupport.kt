package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.linecorp.intellij.plugins.armeria.explorer.support.toArmeriaLightClass
import org.jetbrains.kotlin.idea.base.psi.addMemberDeclaration
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.ImportPath

/** Kotlin-only implementation details for test-method insertion. */
internal object ArmeriaKotlinTestMethodSupport {
    fun isKotlinClass(targetClass: PsiClass): Boolean = ArmeriaKotlinJUnitServerExtensionSupport.toKtClassOrObject(targetClass) != null

    fun lightClassAtCaret(elementAtCaret: PsiElement?): PsiClass? = kotlinDeclarationLightClassAtCaret(elementAtCaret)

    fun topLevelLightClass(selectedPsiFile: PsiFile?): PsiClass? =
        (selectedPsiFile as? KtFile)?.singleTopLevelKotlinDeclaration()?.toArmeriaLightClass()

    fun hasResolvableKotlinClass(
        selectedPsiFile: PsiFile,
        elementAtCaret: PsiElement?,
    ): Boolean {
        if (elementAtCaret != null) {
            if (elementAtCaret.getParentOfType<KtClass>(true) != null) {
                return true
            }
            elementAtCaret
                .getParentOfType<KtObjectDeclaration>(true)
                ?.takeUnless { it.isCompanion() }
                ?.let { return true }
        }
        return (selectedPsiFile as? KtFile)?.declarations?.any { declaration ->
            declaration is KtClass ||
                (declaration is KtObjectDeclaration && !declaration.isCompanion())
        } == true
    }

    fun hasKotlinMethod(
        project: Project,
        targetClass: PsiClass,
        methodName: String,
    ): Boolean {
        val target = resolveKotlinTargetClassOrObject(project, targetClass) ?: return false
        return target.declarations.filterIsInstance<KtNamedFunction>().any { it.name == methodName }
    }

    fun insertKotlinMethodFor(
        project: Project,
        targetClass: PsiClass,
        methodText: String,
    ): Boolean {
        val target = resolveKotlinTargetClassOrObject(project, targetClass) ?: return false
        insertKotlinMethod(project, target, methodText)
        return true
    }

    internal fun insertKotlinMethod(
        project: Project,
        ktTarget: KtClassOrObject,
        methodText: String,
    ) {
        val factory = KtPsiFactory(project)
        val function = factory.createFunction(methodText)
        val added = ktTarget.addMemberDeclaration(function)
        val ktFile = ktTarget.containingKtFile
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

    private fun resolveKotlinTargetClassOrObject(
        project: Project,
        targetClass: PsiClass,
    ): KtClassOrObject? {
        val selectedFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        selectedFile
            ?.let { PsiManager.getInstance(project).findFile(it) as? KtFile }
            ?.declarations
            ?.filterIsInstance<KtClassOrObject>()
            ?.firstOrNull { it.fqName?.asString() == targetClass.qualifiedName }
            ?.let { return it }
        return ArmeriaKotlinJUnitServerExtensionSupport.toKtClassOrObject(targetClass)
    }

    private fun kotlinDeclarationLightClassAtCaret(elementAtCaret: PsiElement?): PsiClass? {
        if (elementAtCaret == null) {
            return null
        }
        elementAtCaret.getParentOfType<KtClass>(true)?.toArmeriaLightClass()?.let { return it }
        elementAtCaret
            .getParentOfType<KtObjectDeclaration>(true)
            ?.takeUnless { it.isCompanion() }
            ?.toArmeriaLightClass()
            ?.let { return it }
        return null
    }

    private fun KtFile.singleTopLevelKotlinDeclaration(): KtClassOrObject? {
        val topLevelDeclarations =
            declarations.filter { declaration ->
                declaration is KtClass ||
                    (declaration is KtObjectDeclaration && !declaration.isCompanion())
            }
        return topLevelDeclarations.singleOrNull() as? KtClassOrObject
    }
}
