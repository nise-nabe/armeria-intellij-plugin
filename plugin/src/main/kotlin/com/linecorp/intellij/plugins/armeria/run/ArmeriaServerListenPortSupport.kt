package com.linecorp.intellij.plugins.armeria.run

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport

internal object ArmeriaServerListenPortSupport {
    private val KOTLIN_PLUGIN_ID = PluginId.getId("org.jetbrains.kotlin")
    private val LISTEN_METHODS = setOf("http", "https", "port")
    private val ARMERIA_SERVER_PACKAGE = "com.linecorp.armeria.server"

    fun extractFromMainClass(
        project: Project,
        module: Module,
        mainClassFqn: String?,
    ): ArmeriaListenEndpoint? {
        if (mainClassFqn.isNullOrBlank()) {
            return null
        }
        val psiClass =
            JavaPsiFacade.getInstance(project).findClass(
                mainClassFqn,
                GlobalSearchScope.moduleScope(module),
            ) ?: return null
        return extractFromFile(psiClass.containingFile)
    }

    fun extractFromFile(file: PsiFile?): ArmeriaListenEndpoint? {
        file ?: return null
        extractFromJava(file)?.let { return it }
        if (isKotlinPluginAvailable()) {
            ArmeriaKotlinServerListenPortSupport.extractFromFile(file)?.let { return it }
        }
        return null
    }

    private fun extractFromJava(file: PsiFile): ArmeriaListenEndpoint? {
        val calls = PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression::class.java)
        val candidates = mutableListOf<ListenCandidate>()
        for (call in calls) {
            val methodName = call.methodExpression.referenceName ?: continue
            if (methodName !in LISTEN_METHODS) {
                continue
            }
            if (!looksLikeServerBuilder(call)) {
                continue
            }
            val port = ArmeriaJavaIntConstantSupport.extractPort(call.argumentList.expressions.firstOrNull()) ?: continue
            val https =
                when (methodName) {
                    "https" -> true
                    "http" -> false
                    else ->
                        ArmeriaSessionProtocols.extraArgsSuggestHttps(
                            call.argumentList.expressions
                                .drop(1)
                                .joinToString(" ") { it.text },
                        )
                }
            candidates +=
                ListenCandidate(
                    port = port,
                    https = https,
                    kind = kindFor(methodName),
                )
        }
        return pick(candidates)
    }

    internal fun pick(candidates: List<ListenCandidate>): ArmeriaListenEndpoint? {
        candidates.firstOrNull { it.kind == ListenKind.HTTP }?.let {
            return ArmeriaListenEndpoint(it.port, https = false)
        }
        candidates.firstOrNull { it.kind == ListenKind.HTTPS }?.let {
            return ArmeriaListenEndpoint(it.port, https = true)
        }
        candidates.firstOrNull { it.kind == ListenKind.PORT }?.let {
            return ArmeriaListenEndpoint(it.port, https = it.https)
        }
        return null
    }

    private fun looksLikeServerBuilder(call: PsiMethodCallExpression): Boolean {
        var current: PsiMethodCallExpression? = call
        var hops = 0
        while (current != null && hops < 32) {
            val methodName = current.methodExpression.referenceName
            if (methodName == "builder" && isArmeriaServerQualifier(current.methodExpression.qualifierExpression)) {
                return true
            }
            val containing =
                current
                    .resolveMethod()
                    ?.containingClass
                    ?.qualifiedName
            if (containing == ArmeriaRouteSupport.SERVER_BUILDER_CLASS) {
                return true
            }
            current = current.methodExpression.qualifierExpression as? PsiMethodCallExpression
            hops++
        }
        val qualifierType =
            call.methodExpression.qualifierExpression
                ?.type
                ?.canonicalText
        return qualifierType == ArmeriaRouteSupport.SERVER_BUILDER_CLASS ||
            (
                qualifierType == ArmeriaRouteSupport.SERVER_BUILDER_SIMPLE_NAME &&
                    referencesArmeriaServer(call.containingFile)
            )
    }

    private fun isArmeriaServerQualifier(qualifier: PsiExpression?): Boolean {
        qualifier ?: return false
        val resolved = (qualifier as? PsiReferenceExpression)?.resolve() as? PsiClass
        if (resolved != null) {
            return resolved.qualifiedName == ArmeriaRouteSupport.ARMERIA_SERVER_CLASS
        }
        val qualifierText = qualifier.text
        if (qualifierText == ArmeriaRouteSupport.ARMERIA_SERVER_CLASS) {
            return true
        }
        if (qualifierText != "Server" && !qualifierText.endsWith(".Server")) {
            return false
        }
        return referencesArmeriaServer(qualifier.containingFile)
    }

    internal fun referencesArmeriaServer(file: PsiFile?): Boolean {
        val text = file?.text ?: return false
        return text.contains(ARMERIA_SERVER_PACKAGE)
    }

    private fun kindFor(methodName: String): ListenKind =
        when (methodName) {
            "http" -> ListenKind.HTTP
            "https" -> ListenKind.HTTPS
            else -> ListenKind.PORT
        }

    private fun isKotlinPluginAvailable(): Boolean = PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)

    internal enum class ListenKind {
        HTTP,
        HTTPS,
        PORT,
    }

    internal data class ListenCandidate(
        val port: Int,
        val https: Boolean,
        val kind: ListenKind,
    )
}
