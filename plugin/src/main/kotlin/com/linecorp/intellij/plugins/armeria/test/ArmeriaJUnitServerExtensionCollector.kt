package com.linecorp.intellij.plugins.armeria.test

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

object ArmeriaJUnitServerExtensionCollector {
    private val KOTLIN_PLUGIN_ID = PluginId.getId("org.jetbrains.kotlin")

    fun collect(project: Project): List<ArmeriaJUnitServerExtension> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            compute(project)
        }

    fun extensionsInClass(
        project: Project,
        psiClass: PsiClass,
    ): List<ArmeriaJUnitServerExtension> {
        val scope = GlobalSearchScope.projectScope(project)
        val testClassName =
            psiClass.qualifiedName
                ?: ArmeriaJUnitServerExtensionSupport.toKtClass(psiClass)?.fqName?.asString()
                ?: return emptyList()
        val fromFields = extensionsFromClassHierarchy(psiClass, scope)
        if (fromFields.isNotEmpty()) {
            return fromFields
        }
        return collect(project)
            .filter { extension ->
                ArmeriaJUnitServerExtensionSupport.canAccessServerExtension(
                    testClassName,
                    extension.containingClassName,
                    project,
                    scope,
                )
            }.distinctBy { "${it.containingClassName}#${it.variableName}" }
    }

    private fun extensionsFromClassHierarchy(
        psiClass: PsiClass,
        scope: GlobalSearchScope,
    ): List<ArmeriaJUnitServerExtension> {
        val extensions = mutableListOf<ArmeriaJUnitServerExtension>()
        val seen = mutableSetOf<String>()
        var current: PsiClass? = psiClass
        while (current != null) {
            for (extension in ArmeriaJUnitServerExtensionSupport.serverExtensionsInClass(current, scope)) {
                val key = "${extension.containingClassName}#${extension.variableName}"
                if (seen.add(key)) {
                    extensions += extension
                }
            }
            current = current.superClass
        }
        return extensions
    }

    private fun compute(project: Project): CachedValueProvider.Result<List<ArmeriaJUnitServerExtension>> {
        val scope = GlobalSearchScope.projectScope(project)
        val extensions = mutableListOf<ArmeriaJUnitServerExtension>()
        val seen = mutableSetOf<String>()
        for (virtualFile in FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)) {
            val file = PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile ?: continue
            if (!ArmeriaJUnitServerExtensionSupport.fileMayContainRegisterExtension(file.text)) {
                continue
            }
            file.accept(
                object : JavaRecursiveElementWalkingVisitor() {
                    override fun visitClass(aClass: PsiClass) {
                        for (extension in ArmeriaJUnitServerExtensionSupport.serverExtensionsInClass(aClass, scope)) {
                            add(extension, extensions, seen)
                        }
                        super.visitClass(aClass)
                    }
                },
            )
        }
        if (PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)) {
            ArmeriaKotlinJUnitServerExtensionCollector.collect(project, scope, extensions, seen)
        }
        val sorted = extensions.sortedWith(compareBy({ it.moduleName }, { it.containingClassName }, { it.variableName }))
        return CachedValueProvider.Result.create(sorted, PsiModificationTracker.MODIFICATION_COUNT)
    }

    internal fun add(
        extension: ArmeriaJUnitServerExtension,
        extensions: MutableList<ArmeriaJUnitServerExtension>,
        seen: MutableSet<String>,
    ) {
        val key = "${extension.containingClassName}#${extension.variableName}"
        if (seen.add(key)) {
            extensions += extension
        }
    }
}
