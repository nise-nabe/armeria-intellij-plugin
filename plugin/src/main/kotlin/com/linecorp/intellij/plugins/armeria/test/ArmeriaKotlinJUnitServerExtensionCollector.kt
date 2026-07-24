package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
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
            collectProperties(file.declarations.filterIsInstance<KtProperty>(), scope, extensions, seen)
            for (ktClass in file.declarations.filterIsInstance<KtClass>()) {
                collectFromKotlinClass(ktClass, scope, extensions, seen)
            }
            for (objectDeclaration in file.declarations.filterIsInstance<KtObjectDeclaration>()) {
                if (!objectDeclaration.isCompanion()) {
                    continue
                }
                collectProperties(objectDeclaration.declarations.filterIsInstance<KtProperty>(), scope, extensions, seen)
            }
        }
    }

    private fun collectProperties(
        properties: List<KtProperty>,
        scope: GlobalSearchScope,
        extensions: MutableList<ArmeriaJUnitServerExtension>,
        seen: MutableSet<String>,
    ) {
        for (property in properties) {
            from(property, scope)?.let { ArmeriaJUnitServerExtensionCollector.add(it, extensions, seen) }
        }
    }

    private fun collectFromKotlinClass(
        ktClass: KtClass,
        scope: GlobalSearchScope,
        extensions: MutableList<ArmeriaJUnitServerExtension>,
        seen: MutableSet<String>,
    ) {
        collectProperties(ktClass.declarations.filterIsInstance<KtProperty>(), scope, extensions, seen)
        ktClass.companionObjects.forEach { companion ->
            collectProperties(companion.declarations.filterIsInstance<KtProperty>(), scope, extensions, seen)
        }
        for (nestedClass in ktClass.declarations.filterIsInstance<KtClass>()) {
            collectFromKotlinClass(nestedClass, scope, extensions, seen)
        }
    }

    private fun from(
        property: KtProperty,
        scope: GlobalSearchScope,
    ): ArmeriaJUnitServerExtension? = ArmeriaJUnitServerExtensionSupport.serverExtensionFromKotlinProperty(property, scope)
}
