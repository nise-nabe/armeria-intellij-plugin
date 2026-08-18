package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

internal class ArmeriaAddBlockingAnnotationKotlinQuickFix(
    owner: KtModifierListOwner,
    private val classLevel: Boolean,
) : LocalQuickFixOnPsiElement(owner) {
    override fun getFamilyName(): String = message("inspection.missing.blocking.quickfix.family")

    override fun getText(): String =
        if (classLevel) {
            message("inspection.missing.blocking.quickfix.class")
        } else {
            message("inspection.missing.blocking.quickfix.method")
        }

    override fun invoke(
        project: Project,
        file: PsiFile,
        startElement: PsiElement,
        endElement: PsiElement,
    ) {
        val owner = startElement as? KtDeclaration ?: return
        if (owner.annotationEntries.any {
                ArmeriaKotlinAnnotationSupport.qualifiedName(it) == ArmeriaRouteSupport.BLOCKING_ANNOTATION
            }
        ) {
            return
        }
        val factory = KtPsiFactory(project)
        owner.addAnnotationEntry(factory.createAnnotationEntry("@Blocking"))
        val ktFile = file as? KtFile ?: owner.containingFile as? KtFile ?: return
        insertBlockingImport(factory, ktFile)
    }

    private fun insertBlockingImport(
        factory: KtPsiFactory,
        ktFile: KtFile,
    ) {
        val path = FqName(ArmeriaRouteSupport.BLOCKING_ANNOTATION)
        if (ktFile.importDirectives.any { it.importedFqName == path }) {
            return
        }
        val directive = factory.createImportDirective(ImportPath.fromString(ArmeriaRouteSupport.BLOCKING_ANNOTATION))
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

    companion object {
        fun forFunction(function: KtModifierListOwner): ArmeriaAddBlockingAnnotationKotlinQuickFix =
            ArmeriaAddBlockingAnnotationKotlinQuickFix(function, classLevel = false)

        fun forClass(klass: KtModifierListOwner): ArmeriaAddBlockingAnnotationKotlinQuickFix =
            ArmeriaAddBlockingAnnotationKotlinQuickFix(klass, classLevel = true)
    }
}
