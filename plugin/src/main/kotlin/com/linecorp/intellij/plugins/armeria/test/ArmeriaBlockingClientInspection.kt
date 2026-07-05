package com.linecorp.intellij.plugins.armeria.test

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaBlockingClientInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.blocking.client.display.name")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val scope = GlobalSearchScope.projectScope(holder.project)
        val blockingPaths = ArmeriaBlockingClientInspectionPaths.blockingRoutePaths(holder.project)
        if (blockingPaths.isEmpty()) {
            return PsiElementVisitor.EMPTY_VISITOR
        }
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                val extension = ArmeriaJUnitServerExtensionSupport.enclosingServerExtension(expression, scope) ?: return
                if (!usesAsyncWebClient(expression, extension.variableName)) {
                    return
                }
                val path = extractRequestPath(expression) ?: return
                if (!blockingPaths.contains(ArmeriaRouteSupport.normalizePath(path))) {
                    return
                }
                holder.registerProblem(
                    expression.methodExpression,
                    message("inspection.blocking.client.problem", path),
                )
            }
        }
    }

    private fun usesAsyncWebClient(expression: PsiMethodCallExpression, serverVariableName: String): Boolean {
        val qualifier = expression.methodExpression.qualifierExpression ?: return false
        if (qualifier is PsiReferenceExpression && qualifier.referenceName == serverVariableName) {
            return expression.methodExpression.referenceName in setOf("webClient", "httpUri")
        }
        if (qualifier is PsiMethodCallExpression) {
            val receiver = qualifier.methodExpression.qualifierExpression as? PsiReferenceExpression
            if (receiver?.referenceName == serverVariableName &&
                qualifier.methodExpression.referenceName == "webClient"
            ) {
                return expression.methodExpression.referenceName in HTTP_METHOD_NAMES
            }
            if (qualifier.methodExpression.referenceName == "of" &&
                qualifier.resolveMethod()?.containingClass?.qualifiedName == ArmeriaJUnitServerExtensionSupport.WEB_CLIENT_CLASS
            ) {
                return expression.methodExpression.referenceName in HTTP_METHOD_NAMES
            }
        }
        if (qualifier is PsiReferenceExpression && isWebClientType(qualifier)) {
            return expression.methodExpression.referenceName in HTTP_METHOD_NAMES
        }
        return false
    }

    private fun isWebClientType(reference: PsiReferenceExpression): Boolean {
        val typeName = when (val resolved = reference.resolve()) {
            is com.intellij.psi.PsiVariable -> resolved.type.canonicalText
            is PsiClass -> resolved.qualifiedName
            else -> PsiTreeUtil.getParentOfType(resolved, PsiClass::class.java)?.qualifiedName
        } ?: reference.text
        return typeName == ArmeriaJUnitServerExtensionSupport.WEB_CLIENT_CLASS || typeName.endsWith(".WebClient")
    }

    private fun extractRequestPath(expression: PsiMethodCallExpression): String? {
        val methodName = expression.methodExpression.referenceName ?: return null
        if (methodName !in HTTP_METHOD_NAMES) {
            return null
        }
        return (expression.argumentList.expressions.firstOrNull() as? PsiLiteralExpression)?.value as? String
    }

    companion object {
        private val HTTP_METHOD_NAMES = setOf("get", "post", "put", "patch", "delete", "head", "options", "trace")
    }
}

internal object ArmeriaBlockingClientInspectionPaths {
    fun blockingRoutePaths(project: com.intellij.openapi.project.Project): Set<String> {
        return ArmeriaRouteCollector.collect(project)
            .asSequence()
            .filter { ArmeriaTestMethodGenerator.supports(it) && ArmeriaTestMethodGenerator.requiresBlockingClient(it) }
            .map { ArmeriaRouteSupport.normalizePath(it.path) }
            .toSet()
    }
}
