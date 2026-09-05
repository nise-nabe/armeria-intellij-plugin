package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaDropwizardConfigCollector {
    const val BUNDLE_CLASS = "com.linecorp.armeria.dropwizard.ArmeriaBundle"
    const val DOCS_URL = "https://armeria.dev/docs/advanced/dropwizard-integration/"

    fun collect(project: Project): List<ArmeriaSpringBootConfigFile> {
        if (DumbService.isDumb(project)) {
            return emptyList()
        }
        return try {
            val facade = JavaPsiFacade.getInstance(project)
            if (facade.findClass(BUNDLE_CLASS, GlobalSearchScope.allScope(project)) == null) {
                return emptyList()
            }
            listOf(detectedFile())
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: IndexNotReadyException) {
            emptyList()
        }
    }

    internal fun detectedFile(): ArmeriaSpringBootConfigFile =
        ArmeriaSpringBootConfigFile(
            fileName = message("springboot.config.dropwizard.fileName"),
            filePath = message("springboot.config.dropwizard.filePath"),
            entries =
                listOf(
                    ArmeriaSpringBootConfigEntry(
                        key = message("springboot.config.dropwizard.key"),
                        value = message("springboot.config.dropwizard.value"),
                        externalUrl = DOCS_URL,
                    ),
                ),
            synthetic = true,
            dropwizard = true,
        )
}
