package com.linecorp.intellij.plugins.armeria.junit5

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.OpenProjectTaskBuilder
import com.intellij.testFramework.TestApplicationManager
import com.linecorp.intellij.plugins.armeria.junit5.utils.findFields
import com.linecorp.intellij.plugins.armeria.junit5.utils.isAssignableTo
import com.linecorp.intellij.plugins.armeria.junit5.utils.toAccessible
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import kotlin.io.path.createTempDirectory

class IntellijProjectExtension: ParameterResolver, BeforeEachCallback {
    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.isAssignableTo<IntellijProject>()
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        return extensionContext.getProject()
    }

    override fun beforeEach(context: ExtensionContext) {
        context.requiredTestInstances.allInstances.forEach { instance ->
            injectFields(context, instance)
        }
    }

    private fun injectFields(context: ExtensionContext, instance: Any) {
        instance::class.findFields<IntellijProject>().forEach {
            it.toAccessible().set(instance, context.getProject())
        }
    }

    private fun ExtensionContext.getProject(): IntellijProject {
        TestApplicationManager.getInstance()
        return getStore(NAMESPACE).getOrComputeIfAbsent(KEY) {
            val tempDir = createTempDirectory(TEMP_DIR_PREFIX).toFile()
            val projectOptions = OpenProjectTaskBuilder().projectName("test")
            val intellijProject = requireNotNull(ProjectManagerEx.getInstanceEx().openProject(tempDir.toPath(), projectOptions.build()))
            IntellijProject(tempDir, FileTemplateManager.getInstance(intellijProject))
        } as IntellijProject
    }

    companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(IntellijProject::class)
        private const val KEY = "intellij.project.dir"
        private const val TEMP_DIR_PREFIX = "armeria-intellij-plugin"
    }
}
