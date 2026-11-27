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
        val hiddenMembers = mutableListOf<HiddenMember>()
        val directExtensions = extensionsFromClassHierarchy(psiClass, hiddenMembers = hiddenMembers)
        val outerExtensions = extensionsFromOuterClasses(psiClass, hiddenMembers)
        val lexicalExtensions = (directExtensions + outerExtensions).distinctBy { "${it.containingClassName}#${it.variableName}" }
        if (lexicalExtensions.isNotEmpty()) {
            return lexicalExtensions
        }

        val outerClassNames = outerClassNames(psiClass, kotlinPluginLoaded)
        return collect(project)
            .filter { extension ->
                ArmeriaJUnitServerExtensionSupport.canAccessServerExtension(
                    psiClass,
                    extension,
                    project,
                    scope,
                )
            }.filter { extension ->
                extension.containingClassName !in outerClassNames || canAccessOuterExtension(psiClass, extension)
            }.filter { extension ->
                isAccessibleFromScan(extension, psiClass, kotlinPluginLoaded)
            }.filter { extension ->
                !isHidden(extension, hiddenMembers)
            }.distinctBy { "${it.containingClassName}#${it.variableName}" }
    }

    private fun extensionsFromOuterClasses(
        psiClass: PsiClass,
        hiddenMembers: MutableList<HiddenMember>,
    ): List<ArmeriaJUnitServerExtension> {
        val extensions = mutableListOf<ArmeriaJUnitServerExtension>()
        val seen = mutableSetOf<String>()
        generateSequence(psiClass.containingClass) { it.containingClass }.forEach { outerClass ->
            for (extension in extensionsFromClassHierarchy(outerClass, psiClass, hiddenMembers)) {
                if (!canAccessOuterExtension(psiClass, extension, outerClass)) {
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
        outerClass: PsiClass? = null,
    ): Boolean {
        val kotlinPluginLoaded = PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)
        val staticContext =
            psiClass.hasModifierProperty(PsiModifier.STATIC) ||
                (
                    kotlinPluginLoaded &&
                        ArmeriaKotlinJUnitServerExtensionSupport.isKotlinStaticNestedClass(psiClass)
                )
        if (!staticContext) {
            return true
        }
        val element = extension.pointer.element ?: return true
        val owner = element as? PsiModifierListOwner
        if (owner != null && owner.hasModifierProperty(PsiModifier.STATIC)) {
            return true
        }
        if (!kotlinPluginLoaded) {
            return false
        }
        if (ArmeriaKotlinJUnitServerExtensionSupport.isStaticLikeKotlinExtension(element)) {
            return true
        }
        return outerClass != null && ArmeriaKotlinJUnitServerExtensionSupport.isKotlinObjectClass(outerClass)
    }

    private fun extensionsFromClassHierarchy(
        psiClass: PsiClass,
        accessContextClass: PsiClass = psiClass,
        hiddenMembers: MutableList<HiddenMember> = mutableListOf(),
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
            addDirectExtensions(current, accessContextClass, kotlinPluginLoaded, extensions, seen, hiddenMembers)
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
        hiddenMembers: MutableList<HiddenMember>,
    ) {
        val directExtensions = ArmeriaJUnitServerExtensionSupport.serverExtensionsInClass(psiClass)
        val classExtensions =
            if (directExtensions.isEmpty() && kotlinPluginLoaded) {
                ArmeriaKotlinJUnitServerExtensionSupport.serverExtensionsInKotlinClass(psiClass)
            } else {
                directExtensions
            }
        for (extension in classExtensions) {
            val memberKey = hiddenMemberKey(extension)
            if (!isAccessibleFromScan(extension, accessContextClass, kotlinPluginLoaded)) {
                hiddenMembers += HiddenMember(memberKey, psiClass)
                continue
            }
            if (isHidden(
                    memberKey,
                    psiClass,
                    qualifiedName(psiClass, kotlinPluginLoaded),
                    hiddenMembers,
                    kotlinPluginLoaded,
                )
            ) {
                continue
            }
            val key = "${extension.containingClassName}#${extension.variableName}"
            if (seen.add(key)) {
                extensions += extension
            }
        }
    }

    private data class HiddenMember(
        val key: String,
        val hidingClass: PsiClass,
    )

    private fun isHidden(
        extension: ArmeriaJUnitServerExtension,
        hiddenMembers: List<HiddenMember>,
    ): Boolean =
        isHidden(
            hiddenMemberKey(extension),
            (extension.pointer.element as? PsiMember)?.containingClass,
            extension.containingClassName,
            hiddenMembers,
            PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID),
        )

    private fun isHidden(
        memberKey: String,
        ownerClass: PsiClass?,
        ownerClassName: String?,
        hiddenMembers: List<HiddenMember>,
        kotlinPluginLoaded: Boolean,
    ): Boolean =
        hiddenMembers.any { hiddenMember ->
            hiddenMember.key == memberKey &&
                isSameOrSuperclass(hiddenMember.hidingClass, ownerClass, ownerClassName, kotlinPluginLoaded)
        }

    private fun isSameOrSuperclass(
        psiClass: PsiClass,
        ownerClass: PsiClass?,
        ownerClassName: String?,
        kotlinPluginLoaded: Boolean,
    ): Boolean {
        val visited = mutableListOf<PsiClass>()
        val pending = ArrayDeque<PsiClass>()
        pending.add(psiClass)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (visited.any { it.isEquivalentTo(current) }) {
                continue
            }
            visited += current
            if (ownerClass != null && current.isEquivalentTo(ownerClass)) {
                return true
            }
            if (!ownerClassName.isNullOrEmpty() && qualifiedName(current, kotlinPluginLoaded) == ownerClassName) {
                return true
            }
            pending.addAll(current.supers)
        }
        return false
    }

    private fun qualifiedName(
        psiClass: PsiClass,
        kotlinPluginLoaded: Boolean,
    ): String? =
        psiClass.qualifiedName
            ?: if (kotlinPluginLoaded) {
                ArmeriaKotlinJUnitServerExtensionSupport.kotlinClassQualifiedName(psiClass)
            } else {
                null
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
