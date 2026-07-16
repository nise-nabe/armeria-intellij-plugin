package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

object ArmeriaSpringBootConfigSupport {
    fun isApplicationConfigFileName(fileName: String): Boolean =
        fileName in setOf("application.yml", "application.yaml", "application.properties") ||
            (fileName.startsWith("application-") && (fileName.endsWith(".yml") || fileName.endsWith(".yaml") || fileName.endsWith(".properties")))
    fun normalizeIndexedKeyPath(keyPath: String): String = keyPath.replace(Regex("""\[\d+]"""), "")
    fun parseFile(fileName: String, text: String) = when {
        fileName.endsWith(".yml") || fileName.endsWith(".yaml") -> ArmeriaSpringBootConfigParser.parseYaml(text)
        fileName.endsWith(".properties") -> ArmeriaSpringBootConfigParser.parseProperties(text)
        else -> emptyList()
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
            val text = runCatching { String(vf.contentsToByteArray(), Charsets.UTF_8) }.getOrNull() ?: return@mapNotNull null
            val entries = ArmeriaSpringBootConfigSupport.parseFile(vf.name, text)
            if (entries.isEmpty()) null else ArmeriaSpringBootConfigFile(vf.name, vf.path, entries)
        }
    }
}
