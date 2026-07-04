package com.linecorp.intellij.plugins.armeria.client

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaKotlinRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport

internal object ArmeriaKotlinClientCollector {
    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            val file = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            if (!ArmeriaKotlinRouteCollector.referencesArmeriaKotlinContent(file)) {
                continue
            }
            file.forEachDescendant { element ->
                val call = element as? KtCallExpression ?: return@forEachDescendant
                collectClientFromCall(call, endpoints, seenEndpoints)
            }
        }
    }

    private fun collectClientFromCall(
        call: KtCallExpression,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
    ) {
        val methodName = resolveCallName(call) ?: return
        if (methodName !in ArmeriaClientSupport.FACTORY_METHOD_NAMES) {
            return
        }
        val resolvedClass = resolveContainingClass(call) ?: return
        val protocol = ArmeriaClientSupport.protocolForClass(resolvedClass) ?: return
        val target = resolveTargetName(call) ?: resolvedClass
        val uri = extractUri(call, methodName) ?: target
        ArmeriaClientCollector.addEndpoint(call, protocol, target, uri, endpoints, seenEndpoints)
    }

    private fun resolveTargetName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        val receiver = when (callee) {
            is KtDotQualifiedExpression -> callee.receiverExpression
            else -> (call.parent as? KtDotQualifiedExpression)?.receiverExpression
        }
        return receiver?.text ?: callee.text
    }

    private fun resolveCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }

    private fun resolveContainingClass(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        val references = when (callee) {
            is KtNameReferenceExpression -> callee.references.toList()
            is KtDotQualifiedExpression -> callee.references.toList()
            else -> emptyList()
        }
        for (reference in references) {
            val resolved = reference.resolve()
            val qualifiedName = when (resolved) {
                is PsiMethod -> resolved.containingClass?.qualifiedName
                is PsiClass -> resolved.qualifiedName
                else -> null
            }
            if (ArmeriaClientSupport.protocolForClass(qualifiedName) != null) {
                return qualifiedName
            }
        }
        val qualifierText = (callee as? KtDotQualifiedExpression)?.receiverExpression?.text.orEmpty()
        return protocolForClassBySimpleName(qualifierText, call.containingFile as? KtFile)
    }

    private fun protocolForClassBySimpleName(qualifierText: String, file: KtFile?): String? {
        if (qualifierText.isBlank()) {
            return null
        }
        val simpleName = qualifierText.substringAfterLast('.')
        return when (simpleName) {
            "WebClient" -> "com.linecorp.armeria.client.WebClient"
            "GrpcClient" -> "com.linecorp.armeria.client.grpc.GrpcClient"
            "GrpcClients" -> "com.linecorp.armeria.client.grpc.GrpcClients"
            "ThriftClient" -> "com.linecorp.armeria.client.thrift.ThriftClient"
            "ThriftClients" -> "com.linecorp.armeria.client.thrift.ThriftClients"
            else -> {
                val importFqcn = file?.importList?.imports?.firstOrNull { import ->
                    import.importedFqName?.shortName()?.asString() == simpleName
                }?.importedFqName?.asString()
                importFqcn?.takeIf { ArmeriaClientSupport.protocolForClass(it) != null }
            }
        }
    }

    private fun extractUri(call: KtCallExpression, methodName: String): String? {
        val arguments = call.valueArguments
        return when (methodName) {
            "newClient", "of" -> extractKotlinString(arguments.firstOrNull()?.getArgumentExpression())
            "builder" -> extractKotlinString(arguments.firstOrNull()?.getArgumentExpression())
            else -> null
        }
    }

    private fun extractKotlinString(expression: KtExpression?): String? {
        val unwrapped = unwrapKotlinExpression(expression) ?: return null
        return when (unwrapped) {
            is KtStringTemplateExpression -> {
                if (unwrapped.entries.size == 1) {
                    unwrapped.entries[0].text.trim('"')
                } else {
                    unwrapped.text.trim('"')
                }
            }
            is KtDotQualifiedExpression -> extractKotlinStringFromReference(unwrapped)
            is KtNameReferenceExpression -> extractKotlinStringFromReference(unwrapped)
            else -> unwrapped.text.trim('"').takeIf { it.isNotEmpty() }
        }
    }

    private fun extractKotlinStringFromReference(expression: KtExpression): String? {
        val resolved = expression.references.firstOrNull()?.resolve()
        when (resolved) {
            is KtProperty -> extractKotlinString(resolved.initializer)?.let { return it }
            is PsiVariable -> ArmeriaRouteSupport.evaluateJavaStringConstant(resolved)?.let { return it }
        }
        if (expression is KtDotQualifiedExpression) {
            val selector = expression.selectorExpression as? KtNameReferenceExpression ?: return null
            val receiver = expression.receiverExpression as? KtNameReferenceExpression ?: return null
            val containingClass = receiver.references.firstOrNull()?.resolve() as? PsiClass ?: return null
            val field = containingClass.findFieldByName(selector.getReferencedName(), true)
            if (field != null) {
                ArmeriaRouteSupport.evaluateJavaStringConstant(field)?.let { return it }
            }
        }
        return expression.text.trim('"').takeIf { it.isNotEmpty() }
    }

    private fun unwrapKotlinExpression(expression: KtExpression?): KtExpression? {
        var current = expression ?: return null
        while (true) {
            current = when (current) {
                is KtParenthesizedExpression -> current.expression ?: return null
                else -> return current
            }
        }
    }

    private inline fun PsiElement.forEachDescendant(action: (PsiElement) -> Unit) {
        val queue = ArrayDeque<PsiElement>()
        queue.add(this)
        while (queue.isNotEmpty()) {
            val element = queue.removeFirst()
            action(element)
            for (child in element.children) {
                queue.add(child)
            }
        }
    }
}
