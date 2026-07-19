package com.linecorp.intellij.plugins.armeria.explorer.navigation
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import java.io.File

internal class ArmeriaRuntimeRoutePointer private constructor(
    private val projectProvider: () -> Project,
    private val virtualFileProvider: () -> VirtualFile,
) : SmartPsiElementPointer<PsiElement> {
    constructor(project: Project) : this(
        projectProvider = { project },
        virtualFileProvider = { virtualFileForProject(project) },
    )

    override fun getElement(): PsiElement? = null

    override fun getContainingFile(): PsiFile? = null

    override fun getRange(): TextRange? = null

    override fun getProject(): Project = projectProvider()

    override fun getVirtualFile(): VirtualFile = virtualFileProvider()

    override fun getPsiRange(): TextRange? = null

    companion object {
        private val withoutProject =
            ArmeriaRuntimeRoutePointer(
                projectProvider = { ProjectManager.getInstance().defaultProject },
                virtualFileProvider = { virtualFileForProject(ProjectManager.getInstance().defaultProject) },
            )

        fun withoutProject(): SmartPsiElementPointer<PsiElement> = withoutProject

        private fun virtualFileForProject(project: Project): VirtualFile {
            val basePath = project.basePath
            if (basePath != null) {
                LocalFileSystem.getInstance().findFileByPath(basePath)?.let { return it }
            }
            return fallbackVirtualFile()
        }

        private fun fallbackVirtualFile(): VirtualFile {
            val workspaceRoot = File(".").absoluteFile
            return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(workspaceRoot)
                ?: error("Unable to resolve virtual file for $workspaceRoot")
        }
    }
}
