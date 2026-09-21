package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty

internal object ArmeriaKotlinJUnitServerExtensionCollector {
    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        extensions: MutableList<ArmeriaJUnitServerExtension>,
        seen: MutableSet<String>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            val file = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            if (!ArmeriaJUnitServerExtensionSupport.fileMayContainRegisterExtension(file.text)) {
                continue
            }
            collectProperties(file.declarations.filterIsInstance<KtProperty>(), extensions, seen)
            collectFunctions(file.declarations.filterIsInstance<KtNamedFunction>(), extensions, seen)
            for (ktClass in file.declarations.filterIsInstance<KtClass>()) {
                collectFromKotlinClass(ktClass, extensions, seen)
            }
            for (objectDeclaration in file.declarations.filterIsInstance<KtObjectDeclaration>()) {
                if (objectDeclaration.isCompanion()) {
                    continue
                }
                collectProperties(objectDeclaration.declarations.filterIsInstance<KtProperty>(), extensions, seen)
                collectFunctions(objectDeclaration.declarations.filterIsInstance<KtNamedFunction>(), extensions, seen)
            }
        }
    }

    private fun collectProperties(
        properties: List<KtProperty>,
        extensions: MutableList<ArmeriaJUnitServerExtension>,
        seen: MutableSet<String>,
    ) {
        for (property in properties) {
            from(property)?.let { ArmeriaJUnitServerExtensionCollector.add(it, extensions, seen) }
        }
    }

    private fun collectFunctions(
        functions: List<KtNamedFunction>,
        extensions: MutableList<ArmeriaJUnitServerExtension>,
        seen: MutableSet<String>,
    ) {
        for (function in functions) {
            fromFunction(function)?.let { ArmeriaJUnitServerExtensionCollector.add(it, extensions, seen) }
        }
    }

    private fun collectFromKotlinClass(
        ktClass: KtClass,
        extensions: MutableList<ArmeriaJUnitServerExtension>,
        seen: MutableSet<String>,
    ) {
        collectProperties(ktClass.declarations.filterIsInstance<KtProperty>(), extensions, seen)
        collectFunctions(ktClass.declarations.filterIsInstance<KtNamedFunction>(), extensions, seen)
        ktClass.companionObjects.forEach { companion ->
            collectProperties(companion.declarations.filterIsInstance<KtProperty>(), extensions, seen)
            collectFunctions(companion.declarations.filterIsInstance<KtNamedFunction>(), extensions, seen)
        }
        for (nestedClass in ktClass.declarations.filterIsInstance<KtClass>()) {
            collectFromKotlinClass(nestedClass, extensions, seen)
        }
    }

    private fun from(property: KtProperty): ArmeriaJUnitServerExtension? =
        ArmeriaJUnitServerExtensionSupport.serverExtensionFromKotlinProperty(property)

    private fun fromFunction(function: KtNamedFunction): ArmeriaJUnitServerExtension? =
        ArmeriaJUnitServerExtensionSupport.serverExtensionFromKotlinFunction(function)
}
