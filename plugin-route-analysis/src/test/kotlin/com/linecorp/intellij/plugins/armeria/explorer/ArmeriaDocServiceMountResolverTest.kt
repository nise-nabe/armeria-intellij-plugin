package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceMountResolver
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import org.junit.Assert.assertEquals
import org.junit.Test

class ArmeriaDocServiceMountResolverTest {
    @Test
    fun candidateMountPaths_prefersUserAndStaticDocServicePaths() {
        val staticRoutes =
            listOf(
                runtimeLikeRoute(path = "/docs", isDocService = true),
                runtimeLikeRoute(path = "/api"),
            )

        val candidates =
            ArmeriaDocServiceMountResolver.candidateMountPaths(
                staticRoutes = staticRoutes,
                userMountPath = "/custom/docs",
            )

        assertEquals(
            listOf("/custom/docs", "/docs", "/internal/docs"),
            candidates,
        )
    }

    private fun runtimeLikeRoute(
        path: String,
        isDocService: Boolean = false,
    ): ArmeriaRoute =
        ArmeriaRoute(
            protocol = "HTTP",
            httpMethod = "GET",
            path = path,
            target = "Handler",
            routeMatch = RouteMatch.SERVICE,
            moduleName = "app",
            targetUnresolved = false,
            isDocService = isDocService,
            decorators = emptyList(),
            exceptionHandlers = emptyList(),
            pointer = TestPsiPointer,
        )

    private object TestPsiPointer : SmartPsiElementPointer<PsiElement> {
        override fun getElement(): PsiElement? = null

        override fun getContainingFile(): PsiFile? = null

        override fun getRange(): TextRange? = null

        override fun getProject(): Project = throw UnsupportedOperationException()

        override fun getVirtualFile(): VirtualFile = throw UnsupportedOperationException()

        override fun getPsiRange(): TextRange? = null
    }
}
