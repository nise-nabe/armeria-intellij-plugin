package com.linecorp.intellij.plugins.armeria.test

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

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
        val parent = call.parent as? KtDotQualifiedExpression ?: return false
        if (parent.selectorExpression != call) {
            return false
        }
        return when (val qualifier = parent.receiverExpression) {
            is KtNameReferenceExpression -> {
                if (qualifier.getReferencedName() == serverVariableName) {
                    return methodName in setOf("webClient", "httpUri")
                }
                methodName in HTTP_METHOD_NAMES && isAsyncWebClientReference(qualifier)
            }
            is KtCallExpression -> usesAsyncWebClientOnQualifier(qualifier, serverVariableName, methodName)
            is KtDotQualifiedExpression -> {
                val factoryCall = qualifier.selectorExpression as? KtCallExpression ?: return false
                usesAsyncWebClientOnQualifier(factoryCall, serverVariableName, methodName)
            }
            else -> false
        }
    }

    private fun usesAsyncWebClientOnQualifier(
        qualifier: KtCallExpression,
        serverVariableName: String,
        methodName: String,
    ): Boolean {
        val factoryName = qualifier.calleeExpression?.text ?: return false
        if (factoryName == "blockingWebClient") {
            return false
        }
        if (factoryName == "webClient") {
            val receiver =
                (qualifier.parent as? KtDotQualifiedExpression)
                    ?.takeIf { it.selectorExpression == qualifier }
                    ?.receiverExpression as? KtNameReferenceExpression
            if (receiver?.getReferencedName() == serverVariableName) {
                return methodName in HTTP_METHOD_NAMES
            }
        }
        if (factoryName == "of" && isWebClientOfCall(qualifier)) {
            return methodName in HTTP_METHOD_NAMES
        }
        return false
    }

    private fun isWebClientOfCall(call: KtCallExpression): Boolean {
        val parent = call.parent as? KtDotQualifiedExpression ?: return false
        if (parent.selectorExpression != call) {
            return false
        }
        return isAsyncWebClientReference(parent.receiverExpression as? KtNameReferenceExpression ?: return false)
    }

    private fun isAsyncWebClientReference(reference: KtNameReferenceExpression): Boolean {
        val resolved = reference.reference?.resolve()
        val typeName =
            when (resolved) {
                is PsiVariable -> resolved.type.canonicalText
                is PsiClass -> resolved.qualifiedName
                else -> PsiTreeUtil.getParentOfType(resolved, PsiClass::class.java)?.qualifiedName
            } ?: reference.text
        if (isAsyncWebClientTypeName(typeName)) {
            return true
        }
        val property = kotlinPropertyFor(reference) ?: return false
        val initializer = property.initializer ?: return false
        return isAsyncWebClientInitializer(initializer)
    }

    private fun kotlinPropertyFor(reference: KtNameReferenceExpression): KtProperty? {
        val resolved = reference.reference?.resolve()
        (resolved as? KtProperty)?.let { return it }
        (resolved?.navigationElement as? KtProperty)?.let { return it }
        val name = reference.getReferencedName() ?: return null
        val scope =
            reference.getParentOfType<KtNamedFunction>(true)
                ?: reference.getParentOfType<KtClass>(true)
                ?: return null
        return scope.collectDescendantsOfType<KtProperty>().firstOrNull { it.name == name }
    }

    private fun isAsyncWebClientTypeName(typeName: String): Boolean {
        if (typeName == ArmeriaJUnitServerExtensionSupport.BLOCKING_WEB_CLIENT_CLASS ||
            typeName.endsWith(".BlockingWebClient")
        ) {
            return false
        }
        return typeName == ArmeriaJUnitServerExtensionSupport.WEB_CLIENT_CLASS || typeName.endsWith(".WebClient")
    }

    private fun isAsyncWebClientInitializer(expression: KtExpression): Boolean {
        val call =
            when (expression) {
                is KtCallExpression -> expression
                is KtDotQualifiedExpression -> expression.selectorExpression as? KtCallExpression
                else -> null
            } ?: return false
        val factoryName = call.calleeExpression?.text ?: return false
        if (factoryName == "blockingWebClient") {
            return false
        }
        if (factoryName == "webClient") {
            return true
        }
        return factoryName == "of" && isWebClientOfCall(call)
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
