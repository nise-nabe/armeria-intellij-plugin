package com.linecorp.intellij.plugins.armeria.test

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaReferenceFileIndex
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCacheSupport

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
                ?: (
                    if (PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)) {
                        ArmeriaKotlinJUnitServerExtensionSupport.kotlinClassQualifiedName(psiClass)
                    } else {
                        null
                    }
                )
                ?: return emptyList()
        val fromFields = extensionsFromClassHierarchy(psiClass)
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

    private fun extensionsFromClassHierarchy(psiClass: PsiClass): List<ArmeriaJUnitServerExtension> {
        val extensions = mutableListOf<ArmeriaJUnitServerExtension>()
        val seen = mutableSetOf<String>()
        val kotlinPluginLoaded = PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)
        var current: PsiClass? = psiClass
        while (current != null) {
            addDirectExtensions(current, kotlinPluginLoaded, extensions, seen)
            current = current.superClass
        }
        var containing = psiClass.containingClass
        while (containing != null) {
            addDirectExtensions(containing, kotlinPluginLoaded, extensions, seen)
            containing = containing.containingClass
        }
        return extensions
    }

    private fun addDirectExtensions(
        psiClass: PsiClass,
        kotlinPluginLoaded: Boolean,
        extensions: MutableList<ArmeriaJUnitServerExtension>,
        seen: MutableSet<String>,
    ) {
        val directExtensions = ArmeriaJUnitServerExtensionSupport.serverExtensionsInClass(psiClass)
        val classExtensions =
            if (directExtensions.isEmpty() && kotlinPluginLoaded) {
                ArmeriaKotlinJUnitServerExtensionSupport.serverExtensionsInKotlinClass(psiClass)
            } else {
                directExtensions
            }
        for (extension in classExtensions) {
            val key = "${extension.containingClassName}#${extension.variableName}"
            if (seen.add(key)) {
                extensions += extension
            }
        }
    }

    private fun compute(project: Project): CachedValueProvider.Result<List<ArmeriaJUnitServerExtension>> {
        val scope = GlobalSearchScope.projectScope(project)
        val extensions = mutableListOf<ArmeriaJUnitServerExtension>()
        val seen = mutableSetOf<String>()
        for (virtualFile in ArmeriaReferenceFileIndex.findJUnitServerExtensionFiles(project, scope)) {
            if (virtualFile.extension != "java") continue
            if (!ArmeriaJUnitServerExtensionSupport.isInTestSourceContent(virtualFile, project)) continue
            val file = PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile ?: continue
            if (!ArmeriaJUnitServerExtensionSupport.fileMayContainRegisterExtension(file.text)) {
                continue
            }
            file.accept(
                object : JavaRecursiveElementWalkingVisitor() {
                    override fun visitClass(aClass: PsiClass) {
                        for (extension in ArmeriaJUnitServerExtensionSupport.serverExtensionsInClass(aClass)) {
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
        return CachedValueProvider.Result.create(sorted, *ArmeriaRouteCacheSupport.invalidators(project))
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
