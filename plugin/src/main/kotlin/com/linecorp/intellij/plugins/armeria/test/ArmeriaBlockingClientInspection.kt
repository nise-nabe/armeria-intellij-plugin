package com.linecorp.intellij.plugins.armeria.test

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteAnalysisCollector
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaBlockingClientInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.blocking.client.display.name")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor {
        if (!ArmeriaJUnitServerExtensionSupport.fileMayContainRegisterExtension(holder.file.text)) {
            return PsiElementVisitor.EMPTY_VISITOR
        }
        if (!ArmeriaJUnitServerExtensionSupport.isLikelyJUnitTestFile(holder.file)) {
            return PsiElementVisitor.EMPTY_VISITOR
        }
        val scope = GlobalSearchScope.projectScope(holder.project)
        val blockingPaths = ArmeriaBlockingClientInspectionPaths.blockingRoutePaths(holder.project)
        if (blockingPaths.isEmpty()) {
            return PsiElementVisitor.EMPTY_VISITOR
        }
        val extensionCache = mutableMapOf<String, List<ArmeriaJUnitServerExtension>>()
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                val extension =
                    ArmeriaJUnitServerExtensionSupport.enclosingServerExtension(expression, scope, extensionCache) ?: return
                if (!usesAsyncWebClient(expression, extension.variableName)) {
                    return
                }
                val path = extractRequestPath(expression) ?: return
                if (!blockingPaths.contains(ArmeriaRouteSupport.normalizePath(path))) {
                    return
                }
                holder.registerProblem(
                    expression.methodExpression.referenceNameElement ?: expression.methodExpression,
                    message("inspection.blocking.client.problem", path),
                )
            }
        }
    }

    private fun usesAsyncWebClient(
        expression: PsiMethodCallExpression,
        serverVariableName: String,
    ): Boolean {
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
                val ofArgument = qualifier.argumentList.expressions.firstOrNull() ?: return false
                if (!ArmeriaJUnitServerExtensionSupport.referencesServerHttpUri(ofArgument, serverVariableName)) {
                    return false
                }
                return expression.methodExpression.referenceName in HTTP_METHOD_NAMES
            }
        }
        if (qualifier is PsiReferenceExpression && isAsyncWebClientFromServer(qualifier, serverVariableName)) {
            return expression.methodExpression.referenceName in HTTP_METHOD_NAMES
        }
        return false
    }

    private fun isAsyncWebClientFromServer(
        reference: PsiReferenceExpression,
        serverVariableName: String,
    ): Boolean {
        if (!isWebClientType(reference)) {
            return false
        }
        val resolved = reference.resolve() as? com.intellij.psi.PsiVariable ?: return false
        val initializer = resolved.initializer ?: return false
        if (initializer is PsiMethodCallExpression) {
            when (initializer.methodExpression.referenceName) {
                "webClient" -> {
                    val receiver = initializer.methodExpression.qualifierExpression as? PsiReferenceExpression
                    return receiver?.referenceName == serverVariableName
                }
                "of" -> {
                    val ofArgument = initializer.argumentList.expressions.firstOrNull() ?: return false
                    return ArmeriaJUnitServerExtensionSupport.referencesServerHttpUri(ofArgument, serverVariableName)
                }
            }
        }
        return false
    }

    private fun isWebClientType(reference: PsiReferenceExpression): Boolean {
        val typeName =
            when (val resolved = reference.resolve()) {
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
    fun blockingRoutePaths(project: Project): Set<String> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            val paths =
                ArmeriaRouteAnalysisCollector
                    .collect(project)
                    .asSequence()
                    .filter { ArmeriaTestMethodGenerator.isBlockingInspectableRoute(it) }
                    .map { ArmeriaRouteSupport.normalizePath(it.path) }
                    .toSet()
            CachedValueProvider.Result.create(paths, PsiModificationTracker.MODIFICATION_COUNT)
        }
}
