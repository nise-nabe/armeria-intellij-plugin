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
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaKotlinJUnitServerExtensionCollector {
    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        extensions: MutableList<ArmeriaJUnitServerExtension>,
        seen: MutableSet<String>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            val file = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            if (!file.text.contains(ArmeriaJUnitServerExtensionSupport.REGISTER_EXTENSION_ANNOTATION)) {
                continue
            }
            collectProperties(file.declarations.filterIsInstance<KtProperty>(), scope, extensions, seen)
            for (ktClass in file.declarations.filterIsInstance<KtClass>()) {
                collectProperties(ktClass.declarations.filterIsInstance<KtProperty>(), scope, extensions, seen)
                ktClass.companionObjects.forEach { companion ->
                    collectProperties(companion.declarations.filterIsInstance<KtProperty>(), scope, extensions, seen)
                }
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

    private fun from(property: KtProperty, scope: GlobalSearchScope): ArmeriaJUnitServerExtension? {
        if (!property.annotationEntries.any { it.shortName?.asString() == "RegisterExtension" }) {
            return null
        }
        val typeText = property.typeReference?.text ?: return null
        if (!typeText.contains("ServerExtension")) {
            return null
        }
        val variableName = property.name ?: return null
        val containingClass = property.getParentOfType<KtClass>(true) ?: return null
        return ArmeriaJUnitServerExtension.create(
            element = property,
            variableName = variableName,
            containingClassName = containingClass.fqName?.asString().orEmpty(),
            moduleName = ArmeriaTestMetadata.moduleName(property),
        )
    }
}
