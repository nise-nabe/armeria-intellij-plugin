package com.linecorp.intellij.plugins.armeria.editor

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorNavigatable
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.lang.reflect.Method

/**
 * Opens editors across the 2026.2/2026.3 API transition.
 *
 * IntelliJ 2026.3 deprecates the synchronous editor-opening methods, while their
 * non-blocking replacements are not present in the 2026.2 compile target. Keep both
 * calls reflective so the plugin remains compatible with both IDE generations without
 * statically referencing deprecated or unavailable APIs.
 */
object ArmeriaFileEditorOpenSupport {
    private val LOG = logger<ArmeriaFileEditorOpenSupport>()

    private val asyncOpenMethods: Triple<Method, Method, Method>? by lazy {
        runCatching {
            val requestClass = Class.forName("com.intellij.openapi.fileEditor.FileEditorOpenRequest")
            val fromDescriptor = requestClass.getMethod("fromDescriptor", FileEditorNavigatable::class.java)
            val withRequestFocus = requestClass.getMethod("withRequestFocus", Boolean::class.javaPrimitiveType)
            val requestOpenEditor =
                FileEditorManager::class.java.getMethod(
                    "requestOpenEditor",
                    FileEditorNavigatable::class.java,
                    requestClass,
                )
            Triple(requestOpenEditor, fromDescriptor, withRequestFocus)
        }.getOrNull()
    }

    private val legacyOpenMethod: Method? by lazy {
        runCatching {
            FileEditorManager::class.java.getMethod(
                "openFileEditor",
                FileEditorNavigatable::class.java,
                Boolean::class.javaPrimitiveType,
            )
        }.getOrNull()
    }

    fun openFile(
        project: Project,
        virtualFile: VirtualFile,
    ) {
        open(project, OpenFileDescriptor(project, virtualFile))
    }

    fun openFileAtOffset(
        project: Project,
        virtualFile: VirtualFile,
        offset: Int,
    ) {
        open(project, OpenFileDescriptor(project, virtualFile, offset))
    }

    private fun open(
        project: Project,
        descriptor: OpenFileDescriptor,
    ) {
        val manager = FileEditorManager.getInstance(project)
        if (openWithAsyncApi(manager, descriptor)) {
            return
        }
        if (openWithLegacyApi(manager, descriptor)) {
            return
        }
        LOG.warn("No compatible FileEditorManager open API is available")
    }

    private fun openWithAsyncApi(
        manager: FileEditorManager,
        descriptor: OpenFileDescriptor,
    ): Boolean {
        val (requestOpenEditor, fromDescriptor, withRequestFocus) =
            asyncOpenMethods ?: return false
        return runCatching {
            // fromDescriptor preserves reuse/preview behavior; withFocus forces reuseOpen off.
            val descriptorRequest = fromDescriptor.invoke(null, descriptor)
            val request = withRequestFocus.invoke(descriptorRequest, true)
            requestOpenEditor.invoke(manager, descriptor, request)
        }.isSuccess
    }

    private fun openWithLegacyApi(
        manager: FileEditorManager,
        descriptor: OpenFileDescriptor,
    ): Boolean {
        val openFileEditor = legacyOpenMethod ?: return false
        return runCatching {
            openFileEditor.invoke(manager, descriptor, true)
        }.isSuccess
    }
}
