package com.linecorp.intellij.plugins.armeria.client

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport

object ArmeriaClientCollector {
    private val KOTLIN_PLUGIN_ID = PluginId.getId("org.jetbrains.kotlin")

    fun collect(project: Project): List<ArmeriaClientEndpoint> {
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            computeProjectEndpoints(project)
        }
    }

    private fun computeProjectEndpoints(project: Project): CachedValueProvider.Result<List<ArmeriaClientEndpoint>> {
        val scope = GlobalSearchScope.projectScope(project)
        val endpoints = mutableListOf<ArmeriaClientEndpoint>()
        val seenEndpoints = mutableSetOf<String>()
        collectJava(project, scope, endpoints, seenEndpoints)
        if (isKotlinPluginAvailable()) {
            ArmeriaKotlinClientCollector.collect(project, scope, endpoints, seenEndpoints)
        }
        val sorted = endpoints.sortedWith(compareBy({ it.clientType }, { it.uri }, { it.target }))
        return CachedValueProvider.Result.create(
            sorted,
            PsiModificationTracker.MODIFICATION_COUNT,
        )
    }

    private fun collectJava(
        project: Project,
        scope: GlobalSearchScope,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)) {
            val file = PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile ?: continue
            if (!ArmeriaRouteCollector.referencesArmeriaJavaContent(file)) {
                continue
            }
            file.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    collectClientFromMethodCall(expression, endpoints, seenEndpoints)
                    super.visitMethodCallExpression(expression)
                }
            })
        }
    }

    internal fun collectClientFromMethodCall(
        expression: PsiMethodCallExpression,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
    ) {
        val methodName = expression.methodExpression.referenceName ?: return
        if (methodName !in ArmeriaClientSupport.FACTORY_METHOD_NAMES) {
            return
        }
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName ?: return
        val protocol = ArmeriaClientSupport.protocolForClass(resolvedClass) ?: return
        val target = expression.methodExpression.qualifierExpression?.text
            ?: expression.methodExpression.referenceName
            ?: resolvedClass
        val uri = extractUri(expression, methodName) ?: target
        addEndpoint(expression, protocol, target, uri, endpoints, seenEndpoints)
    }

    internal fun addEndpoint(
        element: PsiElement,
        protocol: ClientProtocol,
        target: String,
        uri: String,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
    ) {
        val virtualFile = element.containingFile?.virtualFile ?: return
        val dedupeKey = "${virtualFile.path}:${element.textRange.startOffset}"
        if (!seenEndpoints.add(dedupeKey)) {
            return
        }
        endpoints += ArmeriaClientEndpoint.create(element, protocol.presentableName(), target, uri)
    }

    private fun extractUri(expression: PsiMethodCallExpression, methodName: String): String? {
        val arguments = expression.argumentList.expressions
        return when (methodName) {
            "newClient", "of" -> extractString(arguments.firstOrNull())
            "builder" -> extractString(arguments.firstOrNull())
            else -> null
        }
    }

    internal fun extractString(expression: PsiExpression?): String? {
        return when (expression) {
            null -> null
            is PsiLiteralExpression -> expression.value as? String
            else -> {
                val constantValue = JavaPsiFacade.getInstance(expression.project)
                    .constantEvaluationHelper
                    .computeConstantExpression(expression) as? String
                constantValue ?: expression.text.takeIf { StringUtil.isNotEmpty(it) }
            }
        }
    }

    private fun isKotlinPluginAvailable(): Boolean = PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)
}
