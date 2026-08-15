package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport

internal enum class ArmeriaParameterNameMode {
    JAVA,
    KOTLIN,
}

internal object ArmeriaParametersCompilerSupport {
    private val GRADLE_SCRIPT_NAMES = listOf("build.gradle", "build.gradle.kts")
    private const val JAVA_FLAG_TOKEN = "-parameters"
    private const val KOTLIN_FLAG_TOKEN = "-java-parameters"
    private const val MAX_GRADLE_SCRIPT_CHARS = 256 * 1024
    private val JAVA_FLAG_KEY = Key.create<CachedValue<Boolean>>("armeria.parameters.compiler.java")
    private val KOTLIN_FLAG_KEY = Key.create<CachedValue<Boolean>>("armeria.parameters.compiler.kotlin")
    private val KOTLIN_JAVA_PARAMETERS_ENABLED =
        Regex("""javaParameters\s*(?:\.set\s*\(\s*(?:true|Boolean\.TRUE)\s*\)|\s*=\s*true)""")

    fun isArmeriaParamWithoutExplicitName(annotation: PsiAnnotation): Boolean {
        if (annotation.qualifiedName != ArmeriaRouteSupport.PARAM_ANNOTATION) {
            return false
        }
        val explicit =
            ArmeriaRouteSupport
                .extractStrings(annotation.findDeclaredAttributeValue("value"))
                .firstOrNull { it.isNotBlank() }
        return explicit == null
    }

    fun hasParameterNameOption(
        element: PsiElement,
        mode: ArmeriaParameterNameMode,
    ): Boolean {
        val project = element.project
        val module = ModuleUtilCore.findModuleForPsiElement(element)
        if (compilerOptionsHaveFlag(project, module, mode)) {
            return true
        }
        if (module == null) {
            return gradleScriptsHaveFlag(project, null, mode)
        }
        val key = if (mode == ArmeriaParameterNameMode.JAVA) JAVA_FLAG_KEY else KOTLIN_FLAG_KEY
        return CachedValuesManager.getManager(project).getCachedValue(
            module,
            key,
            CachedValueProvider {
                CachedValueProvider.Result.create(
                    gradleScriptsHaveFlag(project, module, mode),
                    PsiModificationTracker.MODIFICATION_COUNT,
                )
            },
            false,
        )
    }

    private fun compilerOptionsHaveFlag(
        project: Project,
        module: Module?,
        mode: ArmeriaParameterNameMode,
    ): Boolean {
        val configuration = CompilerConfiguration.getInstance(project)
        val options =
            buildList {
                addAll(configuration.getAdditionalOptions())
                if (module != null) {
                    addAll(configuration.getAdditionalOptions(module))
                }
            }
        return options.any { option -> optionTokens(option).any { tokenMatches(it, mode) } }
    }

    private fun gradleScriptsHaveFlag(
        project: Project,
        module: Module?,
        mode: ArmeriaParameterNameMode,
    ): Boolean {
        for (file in gradleScriptFiles(project, module)) {
            val text = loadScriptText(file) ?: continue
            if (scriptHasFlag(text, mode)) {
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
        return LoadTextUtil.loadText(file).toString()
    }

    private fun scriptHasFlag(
        text: String,
        mode: ArmeriaParameterNameMode,
    ): Boolean =
        when (mode) {
            ArmeriaParameterNameMode.JAVA -> containsToken(text, JAVA_FLAG_TOKEN)
            ArmeriaParameterNameMode.KOTLIN ->
                containsToken(text, KOTLIN_FLAG_TOKEN) ||
                    KOTLIN_JAVA_PARAMETERS_ENABLED.containsMatchIn(text)
        }

    private fun containsToken(
        text: String,
        token: String,
    ): Boolean {
        var start = 0
        while (true) {
            val index = text.indexOf(token, start)
            if (index < 0) {
                return false
            }
            val before = text.getOrNull(index - 1)
            val after = text.getOrNull(index + token.length)
            val beforeOk = before == null || !(before.isLetterOrDigit() || before == '-')
            val afterOk = after == null || !(after.isLetterOrDigit() || after == '-')
            if (beforeOk && afterOk) {
                return true
            }
            start = index + 1
        }
    }

    private fun optionTokens(option: String): List<String> = option.split(Regex("\\s+")).filter { it.isNotBlank() }

    private fun tokenMatches(
        token: String,
        mode: ArmeriaParameterNameMode,
    ): Boolean =
        when (mode) {
            ArmeriaParameterNameMode.JAVA -> token == JAVA_FLAG_TOKEN
            ArmeriaParameterNameMode.KOTLIN -> token == KOTLIN_FLAG_TOKEN
        }
}
