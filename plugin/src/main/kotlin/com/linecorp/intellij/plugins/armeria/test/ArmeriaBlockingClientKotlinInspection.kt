package com.linecorp.intellij.plugins.armeria.test

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

class ArmeriaBlockingClientKotlinInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.blocking.client.kotlin.display.name")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor {
        val scope = GlobalSearchScope.projectScope(holder.project)
        val blockingPaths = ArmeriaBlockingClientInspectionPaths.blockingRoutePaths(holder.project)
        if (blockingPaths.isEmpty()) {
            return PsiElementVisitor.EMPTY_VISITOR
        }
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                val extension = ArmeriaJUnitServerExtensionSupport.enclosingServerExtension(expression, scope) ?: return
                if (!usesAsyncWebClient(expression, extension.variableName)) {
                    return
                }
                val path = extractRequestPath(expression) ?: return
                if (!blockingPaths.contains(ArmeriaRouteSupport.normalizePath(path))) {
                    return
                }
                holder.registerProblem(
                    expression.calleeExpression ?: expression,
                    message("inspection.blocking.client.problem", path),
                )
            }
        }
    }

    private fun usesAsyncWebClient(
        call: KtCallExpression,
        serverVariableName: String,
    ): Boolean {
        val methodName = call.calleeExpression?.text ?: return false
        if (!referencesServerExtension(call, serverVariableName)) {
            return false
        }
        if (methodName in HTTP_METHOD_NAMES) {
            return true
        }
        return methodName in setOf("webClient", "httpUri")
    }

    private fun referencesServerExtension(
        call: KtCallExpression,
        serverVariableName: String,
    ): Boolean {
        var current: PsiElement? = call
        while (current != null) {
            val parent = current.parent
            if (parent is KtDotQualifiedExpression && parent.selectorExpression == current) {
                when (val receiver = parent.receiverExpression) {
                    is KtNameReferenceExpression -> {
                        if (receiver.getReferencedName() == serverVariableName) {
                            return true
                        }
                    }
                    is KtCallExpression -> {
                        if (isWebClientFactoryCall(receiver, serverVariableName)) {
                            return true
                        }
                    }
                }
                current = parent
                continue
            }
            if (parent is KtNamedFunction || parent is KtBlockExpression || parent is KtClass) {
                break
            }
            current = parent
        }
        return false
    }

    private fun isWebClientFactoryCall(
        call: KtCallExpression,
        serverVariableName: String,
    ): Boolean {
        val callee = call.calleeExpression?.text ?: return false
        if (callee != "of") {
            return false
        }
        return call.valueArguments.any { argument ->
            argument.getArgumentExpression()?.text?.contains(serverVariableName) == true
        }
    }

    private fun extractRequestPath(call: KtCallExpression): String? {
        val methodName = call.calleeExpression?.text ?: return null
        if (methodName !in HTTP_METHOD_NAMES) {
            return null
        }
        val argument = call.valueArguments.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression ?: return null
        if (argument.entries.size != 1) {
            return null
        }
        return argument.entries
            .single()
            .text
            .removeSurrounding("\"")
    }

    companion object {
        private val HTTP_METHOD_NAMES = setOf("get", "post", "put", "patch", "delete", "head", "options", "trace")
    }
}
