package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

object ArmeriaSpringBootConfigSupport {
    private val APPLICATION_CONFIG_NAMES = setOf(
        "application.yml",
        "application.yaml",
        "application.properties",
    )
    private val INDEXED_KEY_PATH = Regex("""\[\d+]""")

    fun isApplicationConfigFileName(fileName: String): Boolean =
        fileName in APPLICATION_CONFIG_NAMES ||
            (fileName.startsWith("application-") &&
                (fileName.endsWith(".yml") || fileName.endsWith(".yaml") || fileName.endsWith(".properties")))

    fun normalizeIndexedKeyPath(keyPath: String): String = keyPath.replace(INDEXED_KEY_PATH, "")

    /**
     * Parent path used for YAML key completion: strip the leaf being edited, then drop list indexes.
     * `armeria.ports[0].port` → `armeria.ports`; top-level `server` → `""`.
     */
    fun completionContextPath(editedKeyPath: String): String {
        val parent = editedKeyPath.substringBeforeLast('.', missingDelimiterValue = "")
        return normalizeIndexedKeyPath(parent)
    }
}

object ArmeriaSpringBootConfigCollector {
    fun collect(project: Project): List<ArmeriaSpringBootConfigFile> {
        val scope = GlobalSearchScope.projectScope(project)
        val files = linkedMapOf<String, VirtualFile>()
        FilenameIndex.processAllFileNames({ name ->
            if (ArmeriaSpringBootConfigSupport.isApplicationConfigFileName(name)) {
                FilenameIndex.getVirtualFilesByName(name, scope).forEach { files.putIfAbsent(it.path, it) }
            }
            true
        }, scope, null)
        return files.values.sortedBy { it.path }.mapNotNull { vf ->
            val text = try {
                String(vf.contentsToByteArray(), vf.charset)
            } catch (exception: ProcessCanceledException) {
                throw exception
            } catch (_: Exception) {
                return@mapNotNull null
            }
            val entries = ArmeriaSpringBootConfigParser.parseFile(vf.name, text)
            if (entries.isEmpty()) {
                null
            } else {
                ArmeriaSpringBootConfigFile(vf.name, vf.path, entries)
            }
        }
    }
}
