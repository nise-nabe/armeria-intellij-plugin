package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaSpringBootConfigSupport {
    private val APPLICATION_CONFIG_NAMES =
        setOf(
            "application.yml",
            "application.yaml",
            "application.properties",
        )
    private val INDEXED_KEY_PATH = Regex("""\[\d+]""")
    private val CAMEL_BOUNDARY = Regex("([a-z0-9])([A-Z])")
    private val INLINE_COMMENT = Regex("""\s+[#!].*$""")

    fun isApplicationConfigFileName(fileName: String): Boolean =
        fileName in APPLICATION_CONFIG_NAMES ||
            (
                fileName.startsWith("application-") &&
                    (fileName.endsWith(".yml") || fileName.endsWith(".yaml") || fileName.endsWith(".properties"))
            )

    fun normalizeIndexedKeyPath(keyPath: String): String = keyPath.replace(INDEXED_KEY_PATH, "")

    /**
     * Spring Boot relaxed binding: `docsPath` / `internalServices` → `docs-path` / `internal-services`.
     * List indexes are stripped first so `armeria.ports[0].port` → `armeria.ports.port`.
     */
    fun canonicalConfigKey(keyPath: String): String =
        normalizeIndexedKeyPath(keyPath)
            .split('.')
            .joinToString(".") { segment ->
                segment.replace(CAMEL_BOUNDARY, "$1-$2").lowercase()
            }

    /** Drop unquoted trailing `#` / `!` comments left by the lightweight YAML/properties flatteners. */
    fun stripInlineComment(raw: String): String = raw.trim().replace(INLINE_COMMENT, "").trimEnd()

    fun summaryText(files: List<ArmeriaSpringBootConfigFile>): String {
        if (files.isEmpty()) {
            return message("springboot.config.summary.empty")
        }
        val configFiles = files.filterNot { it.synthetic }
        val beanCount = files.filter { it.synthetic }.sumOf { it.entries.size }
        val parts = mutableListOf<String>()
        if (configFiles.isNotEmpty()) {
            parts +=
                message(
                    "springboot.config.summary.entries",
                    configFiles.size,
                    configFiles.sumOf { it.entries.size },
                )
        }
        if (beanCount > 0) {
            parts += message("springboot.config.summary.beans", beanCount)
        }
        return parts.joinToString(" · ").ifEmpty { message("springboot.config.summary.empty") }
    }

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
            val text =
                try {
                    LoadTextUtil.loadText(vf).toString()
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
        } + ArmeriaSpringBootConfiguratorBeanCollector.collect(project)
    }
}
