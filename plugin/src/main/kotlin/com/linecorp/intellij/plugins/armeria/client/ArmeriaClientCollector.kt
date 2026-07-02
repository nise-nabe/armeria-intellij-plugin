package com.linecorp.intellij.plugins.armeria.client

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

object ArmeriaClientCollector {
    private val CLIENT_BUILDERS = mapOf(
        "WebClient" to "HTTP",
        "GrpcClient" to "gRPC",
        "ThriftClient" to "Thrift",
    )

    fun collect(project: Project): List<ArmeriaClientEndpoint> {
        val scope = GlobalSearchScope.projectScope(project)
        val endpoints = mutableListOf<ArmeriaClientEndpoint>()
        collectJava(project, scope, endpoints)
        if (isKotlinAvailable()) {
            collectKotlin(project, scope, endpoints)
        }
        return endpoints.sortedBy { "${it.clientType}:${it.uri}" }
    }

    private fun collectJava(project: Project, scope: GlobalSearchScope, endpoints: MutableList<ArmeriaClientEndpoint>) {
        for (virtualFile in FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)) {
            val file = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile ?: continue
            if (!file.text.contains("com.linecorp.armeria.client")) {
                continue
            }
            file.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    val methodName = expression.methodExpression.referenceName
                    if (methodName == "builder" || methodName == "of") {
                        val qualifier = expression.methodExpression.qualifierExpression?.text.orEmpty()
                        val clientType = CLIENT_BUILDERS.entries.firstOrNull { qualifier.contains(it.key) }?.value
                        if (clientType != null) {
                            val uri = extractUri(expression) ?: qualifier
                            endpoints += ArmeriaClientEndpoint.create(expression, clientType, qualifier, uri)
                        }
                    }
                    super.visitMethodCallExpression(expression)
                }
            })
        }
    }

    private fun collectKotlin(project: Project, scope: GlobalSearchScope, endpoints: MutableList<ArmeriaClientEndpoint>) {
        for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            val file = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            if (!file.text.contains("com.linecorp.armeria.client")) {
                continue
            }
            for (call in file.collectDescendantsOfType<KtCallExpression>()) {
                val text = call.calleeExpression?.text.orEmpty()
                val clientType = CLIENT_BUILDERS.entries.firstOrNull { text.contains(it.key) }?.value ?: continue
                if (!text.contains("builder") && !text.contains("of")) {
                    continue
                }
                endpoints += ArmeriaClientEndpoint.create(call, clientType, text, text)
            }
        }
    }

    private fun extractUri(expression: PsiMethodCallExpression): String? {
        return expression.argumentList.expressions.firstOrNull()?.let { arg ->
            when (arg) {
                is PsiLiteralExpression -> arg.value as? String
                else -> arg.text.trim('"')
            }
        }
    }

    private fun isKotlinAvailable(): Boolean = try {
        Class.forName("org.jetbrains.kotlin.idea.KotlinFileType")
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}
