package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

internal object ArmeriaSpringBootRouteCollector {
    private val SPRING_BOOT_FILE_INDICATORS = setOf(
        "ArmeriaServerConfigurator",
        "ArmeriaAutoConfiguration",
        "spring-boot-starter-armeria",
    )

    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        fallbackScannedFiles: MutableSet<VirtualFile>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)) {
            if (virtualFile in fallbackScannedFiles) {
                continue
            }
            ArmeriaRouteCollectionMetrics.current()?.filesScanned?.incrementAndGet()
            val file = PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile ?: continue
            if (!referencesSpringBootArmeria(file)) {
                continue
            }
            fallbackScannedFiles += virtualFile
            ArmeriaRouteCollectionMetrics.current()?.armeriaFiles?.incrementAndGet()
            collectBeanServerRegistrations(file, routes, seenServiceRegistrations)
        }
    }

    private fun referencesSpringBootArmeria(file: PsiJavaFile): Boolean {
        val hasSpringImports = file.importList
            ?.allImportStatements
            ?.any { statement ->
                statement.importReference?.qualifiedName?.startsWith(ArmeriaRouteSupport.ARMERIA_SPRING_PACKAGE_PREFIX) == true
            } ?: false
        if (hasSpringImports) {
            return true
        }
        val searchWindow = file.text.take(ArmeriaRouteSupport.ARMERIA_HEADER_SCAN_LIMIT)
        return SPRING_BOOT_FILE_INDICATORS.any(searchWindow::contains)
    }

    private fun collectBeanServerRegistrations(
        file: PsiJavaFile,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethod(method: PsiMethod) {
                if (!method.hasAnnotation(ArmeriaRouteSupport.SPRING_BEAN_ANNOTATION)) {
                    super.visitMethod(method)
                    return
                }
                if (!isArmeriaServerBeanReturnType(method)) {
                    super.visitMethod(method)
                    return
                }
                PsiTreeUtil.findChildrenOfType(method, PsiMethodCallExpression::class.java).forEach { call ->
                    ArmeriaRouteCollector.collectServiceRegistrationFromMethodCall(
                        call,
                        routes,
                        seenServiceRegistrations,
                    )
                }
                super.visitMethod(method)
            }
        })
    }

    private fun isArmeriaServerBeanReturnType(method: PsiMethod): Boolean {
        val returnType = method.returnType?.canonicalText.orEmpty()
        if (returnType == ArmeriaRouteSupport.ARMERIA_SERVER_CLASS ||
            ArmeriaRouteSupport.isServerBuilderType(returnType)
        ) {
            return true
        }
        return returnType == ArmeriaRouteSupport.ARMERIA_SERVER_CONFIGURATOR_CLASS ||
            returnType.endsWith(".ArmeriaServerConfigurator")
    }
}
