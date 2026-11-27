package com.linecorp.intellij.plugins.armeria.test

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
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
        val kotlinPluginLoaded = PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)
        val hiddenMemberKeys = mutableSetOf<String>()
        val directExtensions = extensionsFromClassHierarchy(psiClass, hiddenMemberKeys = hiddenMemberKeys)
        val outerExtensions = extensionsFromOuterClasses(psiClass, hiddenMemberKeys)
        val lexicalExtensions = (directExtensions + outerExtensions).distinctBy { "${it.containingClassName}#${it.variableName}" }
        if (lexicalExtensions.isNotEmpty()) {
            return lexicalExtensions
        }

        val outerClassNames = outerClassNames(psiClass, kotlinPluginLoaded)
        return collect(project)
            .filter { extension ->
                ArmeriaJUnitServerExtensionSupport.canAccessServerExtension(
                    testClassName,
                    extension.containingClassName,
                    project,
                    scope,
                )
            }.filter { extension ->
                extension.containingClassName !in outerClassNames || canAccessOuterExtension(psiClass, extension)
            }.filter { extension ->
                isAccessibleFromScan(extension, psiClass, kotlinPluginLoaded)
            }.filter { extension ->
                hiddenMemberKey(extension) !in hiddenMemberKeys
            }.distinctBy { "${it.containingClassName}#${it.variableName}" }
    }

    private fun extensionsFromOuterClasses(
        psiClass: PsiClass,
        hiddenMemberKeys: MutableSet<String>,
    ): List<ArmeriaJUnitServerExtension> {
        val extensions = mutableListOf<ArmeriaJUnitServerExtension>()
        val seen = mutableSetOf<String>()
        generateSequence(psiClass.containingClass) { it.containingClass }.forEach { outerClass ->
            for (extension in extensionsFromClassHierarchy(outerClass, psiClass, hiddenMemberKeys)) {
                if (!canAccessOuterExtension(psiClass, extension)) {
                    continue
                }
                val key = "${extension.containingClassName}#${extension.variableName}"
                if (seen.add(key)) {
                    extensions += extension
                }
            }
        }
        return extensions
    }

    private fun outerClassNames(
        psiClass: PsiClass,
        kotlinPluginLoaded: Boolean,
    ): Set<String> =
        generateSequence(psiClass.containingClass) { it.containingClass }
            .mapNotNull { outerClass ->
                outerClass.qualifiedName
                    ?: if (kotlinPluginLoaded) {
                        ArmeriaKotlinJUnitServerExtensionSupport.kotlinClassQualifiedName(outerClass)
                    } else {
                        null
                    }
            }.toSet()

    private fun canAccessOuterExtension(
        psiClass: PsiClass,
        extension: ArmeriaJUnitServerExtension,
    ): Boolean {
        if (!psiClass.hasModifierProperty(PsiModifier.STATIC)) {
            return true
        }
        val owner = extension.pointer.element as? PsiModifierListOwner ?: return true
        return owner.hasModifierProperty(PsiModifier.STATIC)
    }

    private fun extensionsFromClassHierarchy(
        psiClass: PsiClass,
        accessContextClass: PsiClass = psiClass,
        hiddenMemberKeys: MutableSet<String> = mutableSetOf(),
    ): List<ArmeriaJUnitServerExtension> {
        val extensions = mutableListOf<ArmeriaJUnitServerExtension>()
        val seen = mutableSetOf<String>()
        val visited = mutableListOf<PsiClass>()
        val pending = ArrayDeque<PsiClass>()
        val kotlinPluginLoaded = PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)
        pending.add(psiClass)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (visited.any { it.isEquivalentTo(current) }) {
                continue
            }
            visited += current
            addDirectExtensions(current, accessContextClass, kotlinPluginLoaded, extensions, seen, hiddenMemberKeys)
            pending.addAll(current.supers)
        }
        return extensions
    }

    private fun addDirectExtensions(
        psiClass: PsiClass,
        accessContextClass: PsiClass,
        kotlinPluginLoaded: Boolean,
        extensions: MutableList<ArmeriaJUnitServerExtension>,
        seen: MutableSet<String>,
        hiddenMemberKeys: MutableSet<String>,
    ) {
        val directExtensions = ArmeriaJUnitServerExtensionSupport.serverExtensionsInClass(psiClass)
        val classExtensions =
            if (directExtensions.isEmpty() && kotlinPluginLoaded) {
                ArmeriaKotlinJUnitServerExtensionSupport.serverExtensionsInKotlinClass(psiClass)
            } else {
                directExtensions
            }
        for (extension in classExtensions) {
            val hiddenMemberKey = hiddenMemberKey(extension)
            if (!isAccessibleFromScan(extension, accessContextClass, kotlinPluginLoaded)) {
                hiddenMemberKeys += hiddenMemberKey
                continue
            }
            if (hiddenMemberKey in hiddenMemberKeys) {
                continue
            }
            val key = "${extension.containingClassName}#${extension.variableName}"
            if (seen.add(key)) {
                extensions += extension
            }
        }
    }

    private fun hiddenMemberKey(extension: ArmeriaJUnitServerExtension): String = "${extension.variableName}#${extension.isFactoryMethod}"

    private fun isAccessibleFromScan(
        extension: ArmeriaJUnitServerExtension,
        accessContextClass: PsiClass,
        kotlinPluginLoaded: Boolean,
    ): Boolean {
        val owner = extension.pointer.element as? PsiModifierListOwner ?: return true
        if (owner.hasModifierProperty(PsiModifier.PRIVATE)) {
            val ownerClass = (owner as? PsiMember)?.containingClass ?: return true
            return generateSequence(accessContextClass) { it.containingClass }.any { it.isEquivalentTo(ownerClass) }
        }
        if (owner !is PsiMember) {
            return true
        }
        if (owner !is PsiField && owner !is PsiMethod) {
            return true
        }
        if (owner.hasModifierProperty(PsiModifier.PUBLIC) ||
            owner.hasModifierProperty(PsiModifier.PROTECTED) ||
            owner.containingClass?.isInterface == true
        ) {
            return true
        }
        val ownerPackage = (owner.containingClass?.containingFile as? PsiJavaFile)?.packageName
        val accessPackage = packageName(accessContextClass, kotlinPluginLoaded)
        return ownerPackage == null || accessPackage == null || ownerPackage == accessPackage
    }

    private fun packageName(
        psiClass: PsiClass,
        kotlinPluginLoaded: Boolean,
    ): String? {
        (psiClass.containingFile as? PsiJavaFile)?.packageName?.let { return it }
        return if (kotlinPluginLoaded) {
            ArmeriaKotlinJUnitServerExtensionSupport.kotlinClassPackageName(psiClass)
        } else {
            null
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
