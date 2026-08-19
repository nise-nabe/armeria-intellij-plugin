package com.linecorp.intellij.plugins.armeria.explorer.docservice

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinPluginSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant

object ArmeriaDocServiceExampleCollector {
    const val DOC_SERVICE_BUILDER_CLASS = "com.linecorp.armeria.server.docs.DocServiceBuilder"

    private val EXAMPLE_METHOD_NAMES = listOf("exampleRequests", "exampleHeaders")
    private val CACHE_KEY =
        Key.create<CachedValue<ArmeriaDocServiceExampleIndex>>("armeria.docService.exampleIndex")

    fun collect(project: Project): ArmeriaDocServiceExampleIndex =
        CachedValuesManager.getManager(project).getCachedValue(
            project,
            CACHE_KEY,
            {
                CachedValueProvider.Result.create(
                    doCollect(project, GlobalSearchScope.projectScope(project)),
                    PsiModificationTracker.MODIFICATION_COUNT,
                )
            },
            false,
        )

    internal fun collectUncached(
        project: Project,
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project),
    ): ArmeriaDocServiceExampleIndex = doCollect(project, scope)

    private fun doCollect(
        project: Project,
        scope: GlobalSearchScope,
    ): ArmeriaDocServiceExampleIndex {
        val builder = ArmeriaDocServiceExampleIndex.Builder()
        collectJava(project, scope, builder)
        if (ArmeriaKotlinPluginSupport.isKotlinPluginAvailable()) {
            ArmeriaKotlinDocServiceExampleCollector.collect(project, scope, builder)
        }
        return builder.build()
    }

    private fun collectJava(
        project: Project,
        scope: GlobalSearchScope,
        builder: ArmeriaDocServiceExampleIndex.Builder,
    ) {
        val builderClass = JavaPsiFacade.getInstance(project).findClass(DOC_SERVICE_BUILDER_CLASS, scope)
        if (builderClass != null) {
            for (methodName in EXAMPLE_METHOD_NAMES) {
                for (method in builderClass.findMethodsByName(methodName, false)) {
                    ReferencesSearch.search(method, scope).forEach { reference ->
                        val call =
                            PsiTreeUtil.getParentOfType(reference.element, PsiMethodCallExpression::class.java)
                                ?: return@forEach
                        if (call.methodExpression.referenceName != methodName) {
                            return@forEach
                        }
                        parseJavaCall(call, methodName, builder)
                    }
                }
            }
        }
        collectFromJavaFiles(project, scope, builder)
    }

    private fun collectFromJavaFiles(
        project: Project,
        scope: GlobalSearchScope,
        builder: ArmeriaDocServiceExampleIndex.Builder,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)) {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile ?: continue
            val text = psiFile.viewProvider.contents
            if (!text.contains("exampleRequests") && !text.contains("exampleHeaders")) {
                continue
            }
            psiFile.forEachDescendant { element ->
                val call = element as? PsiMethodCallExpression ?: return@forEachDescendant
                val methodName = call.methodExpression.referenceName ?: return@forEachDescendant
                if (methodName !in EXAMPLE_METHOD_NAMES) {
                    return@forEachDescendant
                }
                if (!looksLikeDocServiceBuilder(call)) {
                    return@forEachDescendant
                }
                parseJavaCall(call, methodName, builder)
            }
        }
    }

    private fun looksLikeDocServiceBuilder(call: PsiMethodCallExpression): Boolean {
        val resolvedClass = call.resolveMethod()?.containingClass?.qualifiedName
        if (resolvedClass == DOC_SERVICE_BUILDER_CLASS) {
            return true
        }
        var current: PsiExpression? = call
        while (current != null) {
            when (val unwrapped = PsiUtil.skipParenthesizedExprDown(current) ?: current) {
                is PsiMethodCallExpression -> {
                    val qualifier = unwrapped.methodExpression.qualifierExpression
                    val qualifierName = (qualifier as? PsiReferenceExpression)?.referenceName
                    if (qualifierName == "DocService" || qualifierName == "DocServiceBuilder") {
                        return true
                    }
                    current = qualifier
                }
                is PsiReferenceExpression -> {
                    val name = unwrapped.referenceName
                    return name == "DocService" || name == "DocServiceBuilder"
                }
                else -> return false
            }
        }
        return false
    }

    private fun parseJavaCall(
        call: PsiMethodCallExpression,
        methodName: String,
        builder: ArmeriaDocServiceExampleIndex.Builder,
    ) {
        val args = call.argumentList.expressions
        if (args.isEmpty()) {
            return
        }
        val serviceName = extractServiceName(args[0]) ?: return
        val rest = args.drop(1)
        val method = rest.firstOrNull()?.let(::extractMethodName)
        val valueArgs = if (method != null) rest.drop(1) else rest
        when (methodName) {
            "exampleRequests" -> builder.addRequests(serviceName, method, valueArgs.flatMap(::extractStringValues))
            "exampleHeaders" -> builder.addHeaders(serviceName, method, valueArgs.flatMap(::extractHeaders))
        }
    }

    internal fun extractServiceName(expression: PsiExpression): String? {
        val unwrapped = PsiUtil.skipParenthesizedExprDown(expression) ?: expression
        when (unwrapped) {
            is PsiClassObjectAccessExpression -> {
                val typeElement = unwrapped.operand
                val resolved =
                    (typeElement.type as? PsiClassType)?.resolve()
                        ?: typeElement.innermostComponentReferenceElement?.resolve() as? PsiClass
                resolved?.qualifiedName?.takeIf { it.isNotBlank() }?.let { return it }
                val reference = typeElement.innermostComponentReferenceElement
                val fromReference = reference?.qualifiedName ?: reference?.referenceName
                if (!fromReference.isNullOrBlank()) {
                    return fromReference
                }
                return typeElement.type
                    ?.canonicalText
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && it != "null" }
            }
            is PsiLiteralExpression -> return unwrapped.value as? String
            is PsiReferenceExpression -> return ArmeriaRouteSupport.extractJavaStringConstant(unwrapped)
            else ->
                return JavaPsiFacade
                    .getInstance(unwrapped.project)
                    .constantEvaluationHelper
                    .computeConstantExpression(unwrapped) as? String
        }
    }

    private fun extractMethodName(expression: PsiExpression): String? {
        val unwrapped = PsiUtil.skipParenthesizedExprDown(expression) ?: expression
        if (unwrapped is PsiMethodCallExpression || unwrapped is PsiNewExpression) {
            return null
        }
        return when (unwrapped) {
            is PsiLiteralExpression -> unwrapped.value as? String
            is PsiReferenceExpression -> ArmeriaRouteSupport.extractJavaStringConstant(unwrapped)
            else ->
                JavaPsiFacade
                    .getInstance(unwrapped.project)
                    .constantEvaluationHelper
                    .computeConstantExpression(unwrapped) as? String
        }
    }

    internal fun extractStringValues(expression: PsiExpression): List<String> {
        ArmeriaRouteSupport.extractJavaStringConstant(expression)?.let { return listOf(it) }
        val call = PsiUtil.skipParenthesizedExprDown(expression) as? PsiMethodCallExpression ?: return emptyList()
        return call.argumentList.expressions.mapNotNull { ArmeriaRouteSupport.extractJavaStringConstant(it) }
    }

    internal fun extractHeaders(expression: PsiExpression): List<String> {
        val unwrapped = PsiUtil.skipParenthesizedExprDown(expression) ?: expression
        val call = unwrapped as? PsiMethodCallExpression ?: return emptyList()
        if (call.methodExpression.referenceName == "of") {
            val pairs = headerPairsFromOf(call)
            if (pairs.isNotEmpty()) {
                return pairs
            }
        }
        return call.argumentList.expressions.flatMap { nested -> extractHeaders(nested) }
    }

    private fun headerPairsFromOf(call: PsiMethodCallExpression): List<String> {
        val values = call.argumentList.expressions.mapNotNull(::extractHeaderComponent)
        if (values.size < 2 || values.size % 2 != 0) {
            return emptyList()
        }
        return values.chunked(2).map { (name, value) -> "$name: $value" }
    }

    private fun extractHeaderComponent(expression: PsiExpression): String? {
        ArmeriaRouteSupport.extractJavaStringConstant(expression)?.let { return it }
        val reference = PsiUtil.skipParenthesizedExprDown(expression) as? PsiReferenceExpression ?: return null
        val field = reference.resolve() as? PsiField ?: return null
        val constant = field.computeConstantValue()
        if (constant is CharSequence && constant.isNotEmpty()) {
            return constant.toString()
        }
        val name = field.name ?: return null
        return name.lowercase().replace('_', '-')
    }
}
