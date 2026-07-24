package com.linecorp.intellij.plugins.armeria.test

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
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
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
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
                methodName in HTTP_METHOD_NAMES && isAsyncWebClientReference(qualifier, serverVariableName)
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
        if (isWebClientOfFactoryCall(qualifier, serverVariableName)) {
            return methodName in HTTP_METHOD_NAMES
        }
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
        return false
    }

    private fun isWebClientOfFactoryCall(
        call: KtCallExpression,
        serverVariableName: String,
    ): Boolean {
        val ofCall: KtCallExpression
        val webClientReceiver: KtExpression
        when {
            call.calleeExpression?.text == "of" -> {
                val host = call.parent as? KtDotQualifiedExpression ?: return false
                if (host.selectorExpression != call) {
                    return false
                }
                ofCall = call
                webClientReceiver = host.receiverExpression
            }
            call.calleeExpression is KtDotQualifiedExpression -> {
                val host = call.calleeExpression as KtDotQualifiedExpression
                ofCall = host.selectorExpression as? KtCallExpression ?: return false
                if (ofCall.calleeExpression?.text != "of") {
                    return false
                }
                webClientReceiver = host.receiverExpression
            }
            else -> return false
        }
        if (!isWebClientReceiver(webClientReceiver)) {
            return false
        }
        val ofArgument = ofCall.valueArguments.firstOrNull()?.getArgumentExpression() ?: return false
        return ArmeriaJUnitServerExtensionSupport.referencesServerHttpUri(ofArgument, serverVariableName)
    }

    private fun isWebClientReceiver(expression: KtExpression): Boolean =
        when (expression) {
            is KtNameReferenceExpression -> isWebClientTypeReference(expression)
            is KtDotQualifiedExpression -> {
                val selector = expression.selectorExpression as? KtNameReferenceExpression ?: return false
                selector.getReferencedName() == "WebClient"
            }
            else -> false
        }

    private fun isWebClientTypeReference(reference: KtNameReferenceExpression): Boolean {
        val resolved = reference.reference?.resolve()
        val typeName =
            when (resolved) {
                is PsiClass -> resolved.qualifiedName
                else -> reference.text
            } ?: reference.text
        return typeName == ArmeriaJUnitServerExtensionSupport.WEB_CLIENT_CLASS || typeName.endsWith(".WebClient")
    }

    private fun isAsyncWebClientReference(
        reference: KtNameReferenceExpression,
        serverVariableName: String,
    ): Boolean {
        val resolved = reference.reference?.resolve()
        if (resolved is PsiClass) {
            val qualifiedName = resolved.qualifiedName ?: return false
            return isAsyncWebClientTypeName(qualifiedName)
        }
        val property = kotlinPropertyFor(reference) ?: return false
        val initializer = property.initializer ?: return false
        return isAsyncWebClientInitializer(initializer, serverVariableName)
    }

    private fun kotlinPropertyFor(reference: KtNameReferenceExpression): KtProperty? {
        val resolved = reference.reference?.resolve()
        (resolved as? KtProperty)?.let { return it }
        (resolved?.navigationElement as? KtProperty)?.let { return it }
        val name = reference.getReferencedName()
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

    private fun isAsyncWebClientInitializer(
        expression: KtExpression,
        serverVariableName: String,
    ): Boolean {
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
            val receiver =
                (call.parent as? KtDotQualifiedExpression)
                    ?.takeIf { it.selectorExpression == call }
                    ?.receiverExpression as? KtNameReferenceExpression
            return receiver?.getReferencedName() == serverVariableName
        }
        if (factoryName == "of") {
            val ofArgument = call.valueArguments.firstOrNull()?.getArgumentExpression() ?: return false
            return ArmeriaJUnitServerExtensionSupport.referencesServerHttpUri(ofArgument, serverVariableName)
        }
        if (call.calleeExpression is KtDotQualifiedExpression) {
            return isWebClientOfFactoryCall(call, serverVariableName)
        }
        return false
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
