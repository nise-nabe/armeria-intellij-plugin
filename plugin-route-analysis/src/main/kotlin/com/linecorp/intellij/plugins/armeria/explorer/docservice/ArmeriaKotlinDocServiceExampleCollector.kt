package com.linecorp.intellij.plugins.armeria.explorer.docservice

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal object ArmeriaKotlinDocServiceExampleCollector {
    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        builder: ArmeriaDocServiceExampleIndex.Builder,
    ) {
        val builderClass =
            JavaPsiFacade
                .getInstance(project)
                .findClass(ArmeriaDocServiceExampleCollector.DOC_SERVICE_BUILDER_CLASS, scope)
                ?: return
        for (methodName in listOf("exampleRequests", "exampleHeaders")) {
            for (method in builderClass.findMethodsByName(methodName, false)) {
                ReferencesSearch.search(method, scope).forEach { reference ->
                    val call =
                        PsiTreeUtil.getParentOfType(reference.element, KtCallExpression::class.java)
                            ?: return@forEach
                    if (ArmeriaKotlinExpressionSupport.resolveCallName(call) != methodName) {
                        return@forEach
                    }
                    parseCall(call, methodName, builder)
                }
            }
        }
        collectFromKotlinFiles(project, scope, builder)
    }

    private fun collectFromKotlinFiles(
        project: Project,
        scope: GlobalSearchScope,
        builder: ArmeriaDocServiceExampleIndex.Builder,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            val ktFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            val text = ktFile.viewProvider.contents
            if (!text.contains("exampleRequests") && !text.contains("exampleHeaders")) {
                continue
            }
            ktFile.forEachDescendant { element ->
                val call = element as? KtCallExpression ?: return@forEachDescendant
                val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: return@forEachDescendant
                if (methodName != "exampleRequests" && methodName != "exampleHeaders") {
                    return@forEachDescendant
                }
                if (!looksLikeDocServiceBuilder(call)) {
                    return@forEachDescendant
                }
                parseCall(call, methodName, builder)
            }
        }
    }

    private fun looksLikeDocServiceBuilder(call: KtCallExpression): Boolean {
        var current: KtExpression? = call
        while (current != null) {
            when (val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(current) ?: current) {
                is KtCallExpression -> {
                    val callee = unwrapped.calleeExpression
                    current = (callee as? KtDotQualifiedExpression)?.receiverExpression
                }
                is KtDotQualifiedExpression -> current = unwrapped.receiverExpression
                is KtNameReferenceExpression -> {
                    val name = unwrapped.getReferencedName()
                    return name == "DocService" || name == "DocServiceBuilder"
                }
                else -> return false
            }
        }
        return false
    }

    private fun parseCall(
        call: KtCallExpression,
        methodName: String,
        builder: ArmeriaDocServiceExampleIndex.Builder,
    ) {
        val args = call.valueArguments.mapNotNull { it.getArgumentExpression() }
        if (args.isEmpty()) {
            return
        }
        val serviceName = extractServiceName(args[0]) ?: return
        val rest = args.drop(1)
        val method = rest.firstOrNull()?.let { ArmeriaKotlinExpressionSupport.extractKotlinString(it) }
        val valueArgs = if (method != null) rest.drop(1) else rest
        when (methodName) {
            "exampleRequests" -> builder.addRequests(serviceName, method, valueArgs.flatMap(::extractStringValues))
            "exampleHeaders" -> builder.addHeaders(serviceName, method, valueArgs.flatMap(::extractHeaders))
        }
    }

    private fun extractServiceName(expression: KtExpression): String? {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return null
        if (unwrapped is KtDotQualifiedExpression) {
            val selector = unwrapped.selectorExpression as? KtNameReferenceExpression
            if (selector?.getReferencedName() == "java") {
                val receiver = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(unwrapped.receiverExpression)
                if (receiver is KtClassLiteralExpression) {
                    resolveClassLiteral(receiver)?.let { return it }
                }
            }
        }
        if (unwrapped is KtClassLiteralExpression) {
            resolveClassLiteral(unwrapped)?.let { return it }
        }
        return ArmeriaKotlinExpressionSupport.extractKotlinString(unwrapped)
    }

    private fun resolveClassLiteral(literal: KtClassLiteralExpression): String? {
        val typeExpression = literal.receiverExpression ?: return null
        val resolved = typeExpression.references.firstOrNull()?.resolve()
        when (resolved) {
            is PsiClass -> resolved.qualifiedName?.let { return it }
            is KtClassOrObject -> resolved.fqName?.asString()?.let { return it }
        }
        return typeExpression.text
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private fun extractStringValues(expression: KtExpression): List<String> {
        ArmeriaKotlinExpressionSupport.extractKotlinString(expression)?.let { return listOf(it) }
        val call = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) as? KtCallExpression ?: return emptyList()
        return call.valueArguments.mapNotNull {
            ArmeriaKotlinExpressionSupport.extractKotlinString(it.getArgumentExpression())
        }
    }

    private fun extractHeaders(expression: KtExpression): List<String> {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return emptyList()
        val call = unwrapped as? KtCallExpression ?: return emptyList()
        if (ArmeriaKotlinExpressionSupport.resolveCallName(call) == "of") {
            val pairs = headerPairsFromOf(call)
            if (pairs.isNotEmpty()) {
                return pairs
            }
        }
        return call.valueArguments.flatMap { argument ->
            val nested = argument.getArgumentExpression() ?: return@flatMap emptyList()
            extractHeaders(nested)
        }
    }

    private fun headerPairsFromOf(call: KtCallExpression): List<String> {
        val values =
            call.valueArguments.mapNotNull { argument ->
                extractHeaderComponent(argument.getArgumentExpression())
            }
        if (values.size < 2 || values.size % 2 != 0) {
            return emptyList()
        }
        return values.chunked(2).map { (name, value) -> "$name: $value" }
    }

    private fun extractHeaderComponent(expression: KtExpression?): String? {
        if (expression == null) {
            return null
        }
        ArmeriaKotlinExpressionSupport.extractKotlinString(expression)?.let { return it }
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return null
        val reference =
            unwrapped as? KtNameReferenceExpression
                ?: (unwrapped as? KtDotQualifiedExpression)?.selectorExpression as? KtNameReferenceExpression
        val fieldName = reference?.getReferencedName() ?: return null
        val resolved = reference.references.firstOrNull()?.resolve()
        if (resolved is PsiField) {
            val constant = resolved.computeConstantValue()
            if (constant is CharSequence && constant.isNotEmpty()) {
                return constant.toString()
            }
        }
        return fieldName.lowercase().replace('_', '-')
    }
}
