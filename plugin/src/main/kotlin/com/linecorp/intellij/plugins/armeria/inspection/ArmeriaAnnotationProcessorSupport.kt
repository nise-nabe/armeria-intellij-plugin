package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

internal object ArmeriaAnnotationProcessorSupport {
    const val DOCUMENTATION_PROCESSOR_CLASS =
        "com.linecorp.armeria.server.annotation.processor.DocumentationProcessor"
    private const val PROCESSOR_ARTIFACT = "armeria-annotation-processor"
    private const val MAX_GRADLE_SCRIPT_CHARS = 256 * 1024
    private val GRADLE_SCRIPT_NAMES = listOf("build.gradle", "build.gradle.kts")
    private val PROCESSOR_KEY = Key.create<CachedValue<Boolean>>("armeria.annotation.processor.present")

    fun hasDocumentationProcessor(element: PsiElement): Boolean {
        val project = element.project
        val module = ModuleUtilCore.findModuleForPsiElement(element)
        if (processorClassResolves(project, module)) {
            return true
        }
        if (module == null) {
            return gradleScriptsHaveProcessor(project, null)
        }
        return CachedValuesManager.getManager(project).getCachedValue(
            module,
            PROCESSOR_KEY,
            CachedValueProvider {
                CachedValueProvider.Result.create(
                    gradleScriptsHaveProcessor(project, module),
                    PsiModificationTracker.MODIFICATION_COUNT,
                )
            },
            false,
        )
    }

    private fun processorClassResolves(
        project: Project,
        module: Module?,
    ): Boolean =
        try {
            val scope = module?.moduleWithLibrariesScope ?: GlobalSearchScope.allScope(project)
            JavaPsiFacade.getInstance(project).findClass(DOCUMENTATION_PROCESSOR_CLASS, scope) != null
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: IndexNotReadyException) {
            true
        }

    private fun gradleScriptsHaveProcessor(
        project: Project,
        module: Module?,
    ): Boolean {
        for (file in gradleScriptFiles(project, module)) {
            val text = loadScriptText(file) ?: continue
            if (text.contains(PROCESSOR_ARTIFACT)) {
                return true
            }
        }
        return false
    }

    private fun gradleScriptFiles(
        project: Project,
        module: Module?,
    ): Collection<VirtualFile> {
        val files = linkedSetOf<VirtualFile>()
        if (module != null) {
            for (root in ModuleRootManager.getInstance(module).contentRoots) {
                for (name in GRADLE_SCRIPT_NAMES) {
                    root.findChild(name)?.let { files += it }
                }
            }
        }
        try {
            val scope = module?.moduleContentScope ?: GlobalSearchScope.projectScope(project)
            for (name in GRADLE_SCRIPT_NAMES) {
                files += FilenameIndex.getVirtualFilesByName(name, scope)
            }
        } catch (_: IndexNotReadyException) {
            return files
        }
        return files
    }

    private fun loadScriptText(file: VirtualFile): String? {
        if (file.length > MAX_GRADLE_SCRIPT_CHARS) {
            return null
        }
        return try {
            LoadTextUtil.loadText(file).toString()
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: Exception) {
            null
        }
    }
}
