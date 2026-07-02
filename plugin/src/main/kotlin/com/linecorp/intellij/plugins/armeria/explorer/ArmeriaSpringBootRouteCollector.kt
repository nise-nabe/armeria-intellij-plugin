package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaSpringBootRouteCollector {
    private val SPRING_BOOT_INDICATORS = setOf(
        "ArmeriaServerConfigurator",
        "ArmeriaAutoConfiguration",
        "spring-boot-starter-armeria",
    )

    fun collect(project: Project, scope: GlobalSearchScope, routes: MutableList<ArmeriaRoute>) {
        for (virtualFile in FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)) {
            val file = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile ?: continue
            if (!referencesSpringBootArmeria(file)) {
                continue
            }
            collectBeanServerRegistrations(file, routes)
        }
    }

    private fun referencesSpringBootArmeria(file: PsiJavaFile): Boolean {
        val text = file.text
        return SPRING_BOOT_INDICATORS.any(text::contains) ||
            file.importList?.allImportStatements?.any { import ->
                import.importReference?.qualifiedName?.contains("armeria.spring") == true
            } == true
    }

    private fun collectBeanServerRegistrations(file: PsiJavaFile, routes: MutableList<ArmeriaRoute>) {
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethod(method: PsiMethod) {
                if (!method.hasAnnotation("org.springframework.context.annotation.Bean")) {
                    super.visitMethod(method)
                    return
                }
                val returnType = method.returnType?.canonicalText.orEmpty()
                if (!returnType.contains("Server") && !returnType.contains("ServerBuilder")) {
                    super.visitMethod(method)
                    return
                }
                PsiTreeUtil.findChildrenOfType(method, PsiMethodCallExpression::class.java).forEach { call ->
                    val methodName = call.methodExpression.referenceName
                    if (methodName in setOf("service", "serviceUnder", "annotatedService")) {
                        ArmeriaRouteCollector.addServiceRegistrationFromCall(call, routes, mutableSetOf())
                    }
                }
                routes += ArmeriaRoute.create(
                    element = method,
                    protocol = message("route.explorer.protocol.http"),
                    httpMethod = "",
                    path = "/",
                    target = method.containingClass?.qualifiedName + "#" + method.name + "()",
                    routeMatch = RouteMatch.ANNOTATED_SERVICE,
                    decorators = listOf("Spring Boot @Bean"),
                )
                super.visitMethod(method)
            }
        })
    }
}
