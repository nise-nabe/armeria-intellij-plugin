package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKnownHttpServiceClassifier
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.KnownHttpServiceKind

internal object ArmeriaMissingDocServiceSupport {
    private val RPC_KINDS =
        setOf(
            KnownHttpServiceKind.GRPC,
            KnownHttpServiceKind.THRIFT,
            KnownHttpServiceKind.GRAPHQL,
        )

    fun highlight(root: PsiElement): PsiElement? {
        val calls = PsiTreeUtil.findChildrenOfType(root, PsiMethodCallExpression::class.java)
        val annotatedOrRpc = mutableListOf<PsiMethodCallExpression>()
        var serverBuilder: PsiMethodCallExpression? = null
        var hasDocService = false
        for (call in calls) {
            val name = call.methodExpression.referenceName ?: continue
            when (name) {
                "builder" -> {
                    if (isServerBuilder(call)) {
                        serverBuilder = serverBuilder ?: call
                    }
                }
                "annotatedService" -> annotatedOrRpc += call
                "service", "serviceUnder" -> {
                    val implementation = serviceImplementation(call) ?: continue
                    val kind = classifyExpression(implementation)
                    if (kind == KnownHttpServiceKind.DOC_SERVICE) {
                        hasDocService = true
                    } else if (kind in RPC_KINDS) {
                        annotatedOrRpc += call
                    }
                }
            }
        }
        if (hasDocService || annotatedOrRpc.isEmpty()) {
            return null
        }
        return highlightElement(serverBuilder) ?: highlightElement(annotatedOrRpc.first())
    }

    private fun highlightElement(call: PsiMethodCallExpression?): PsiElement? = call?.methodExpression?.referenceNameElement ?: call

    private fun isServerBuilder(call: PsiMethodCallExpression): Boolean {
        val qualifier = call.methodExpression.qualifierExpression ?: return false
        val typeName = qualifier.type?.canonicalText
        if (typeName == ArmeriaRouteSupport.ARMERIA_SERVER_CLASS) {
            return true
        }
        val reference = qualifier as? PsiReferenceExpression ?: return false
        return reference.qualifiedName == ArmeriaRouteSupport.ARMERIA_SERVER_CLASS ||
            reference.referenceName == "Server"
    }

    private fun serviceImplementation(call: PsiMethodCallExpression): PsiExpression? {
        val args = call.argumentList.expressions
        return when {
            args.size >= 2 -> args[1]
            args.size == 1 -> args[0]
            else -> null
        }
    }

    private fun classifyNew(expression: PsiNewExpression): KnownHttpServiceKind {
        val name =
            expression.classReference?.qualifiedName
                ?: expression.classReference?.referenceName
                ?: return KnownHttpServiceKind.HTTP
        return ArmeriaKnownHttpServiceClassifier.classify(name)
    }

    private fun classifyExpression(expression: PsiExpression): KnownHttpServiceKind = classifyExpression(expression, mutableSetOf())

    private fun classifyExpression(
        expression: PsiExpression,
        visited: MutableSet<PsiElement>,
    ): KnownHttpServiceKind {
        val unwrapped = PsiUtil.skipParenthesizedExprDown(expression) ?: expression
        if (!visited.add(unwrapped)) {
            return KnownHttpServiceKind.HTTP
        }
        val typeName = unwrapped.type?.canonicalText
        if (!typeName.isNullOrBlank()) {
            val kind = ArmeriaKnownHttpServiceClassifier.classify(typeName)
            if (kind != KnownHttpServiceKind.HTTP) {
                return kind
            }
        }
        return when (unwrapped) {
            is PsiNewExpression -> classifyNew(unwrapped)
            is PsiMethodCallExpression -> classifyCallChain(unwrapped)
            is PsiReferenceExpression -> {
                val resolved = unwrapped.resolve() as? PsiVariable ?: return KnownHttpServiceKind.HTTP
                if (!visited.add(resolved)) {
                    return KnownHttpServiceKind.HTTP
                }
                val initializer = resolved.initializer ?: return KnownHttpServiceKind.HTTP
                classifyExpression(initializer, visited)
            }
            else -> KnownHttpServiceKind.HTTP
        }
    }

    private fun classifyCallChain(call: PsiMethodCallExpression): KnownHttpServiceKind {
        var current: PsiExpression? = call
        while (current != null) {
            when (val unwrapped = PsiUtil.skipParenthesizedExprDown(current) ?: current) {
                is PsiMethodCallExpression -> {
                    val resolvedClass = unwrapped.resolveMethod()?.containingClass?.qualifiedName
                    if (!resolvedClass.isNullOrBlank()) {
                        val kind = ArmeriaKnownHttpServiceClassifier.classify(resolvedClass)
                        if (kind != KnownHttpServiceKind.HTTP) {
                            return kind
                        }
                    }
                    current = unwrapped.methodExpression.qualifierExpression
                }
                is PsiNewExpression -> return classifyNew(unwrapped)
                is PsiReferenceExpression -> {
                    val name = unwrapped.qualifiedName ?: unwrapped.referenceName
                    current = null
                    if (!name.isNullOrBlank()) {
                        return ArmeriaKnownHttpServiceClassifier.classify(name)
                    }
                }
                else -> current = null
            }
        }
        return KnownHttpServiceKind.HTTP
    }
}
