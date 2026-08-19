package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKnownHttpServiceClassifier
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.KnownHttpServiceKind
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal object ArmeriaMissingDocServiceKotlinSupport {
    private val RPC_KINDS =
        setOf(
            KnownHttpServiceKind.GRPC,
            KnownHttpServiceKind.THRIFT,
            KnownHttpServiceKind.GRAPHQL,
        )

    fun highlight(root: PsiElement): PsiElement? {
        val calls = PsiTreeUtil.findChildrenOfType(root, KtCallExpression::class.java)
        var hasDocService = false
        val annotatedOrRpc = mutableListOf<KtCallExpression>()
        var serverBuilder: KtCallExpression? = null
        for (call in calls) {
            val name = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: continue
            when (name) {
                "builder" -> {
                    if (isServerBuilder(call)) {
                        serverBuilder = serverBuilder ?: call
                    }
                    if (classifyExpression(call) == KnownHttpServiceKind.DOC_SERVICE) {
                        hasDocService = true
                    }
                }
                "build", "of" -> {
                    if (classifyExpression(call) == KnownHttpServiceKind.DOC_SERVICE) {
                        hasDocService = true
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

    private fun highlightElement(call: KtCallExpression?): PsiElement? {
        if (call == null) {
            return null
        }
        val callee = call.calleeExpression
        val selector = (callee as? KtDotQualifiedExpression)?.selectorExpression
        return selector ?: callee ?: call
    }

    private fun isServerBuilder(call: KtCallExpression): Boolean {
        val receiverExpression =
            (call.calleeExpression as? KtDotQualifiedExpression)?.receiverExpression
                ?: (call.parent as? KtDotQualifiedExpression)?.receiverExpression
                ?: return false
        val receiver = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(receiverExpression)
        if (receiver is KtNameReferenceExpression) {
            if (receiver.getReferencedName() == "Server") {
                return true
            }
            val resolved = receiver.references.firstOrNull()?.resolve()
            if (resolved is PsiClass && resolved.qualifiedName == "com.linecorp.armeria.server.Server") {
                return true
            }
        }
        return false
    }

    private fun serviceImplementation(call: KtCallExpression): KtExpression? {
        val args = call.valueArguments
        val named =
            args.firstOrNull { argument ->
                argument.getArgumentName()?.asName?.identifier == "service"
            }
        named?.getArgumentExpression()?.let { return it }
        return when {
            args.size >= 2 -> args[1].getArgumentExpression()
            args.size == 1 -> args[0].getArgumentExpression()
            else -> null
        }
    }

    private fun classifyExpression(expression: KtExpression): KnownHttpServiceKind {
        var current: KtExpression? = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression)
        while (current != null) {
            when (val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(current) ?: current) {
                is KtCallExpression -> {
                    val callee = unwrapped.calleeExpression
                    current =
                        when (callee) {
                            is KtDotQualifiedExpression -> callee.receiverExpression
                            is KtNameReferenceExpression -> {
                                val kind = ArmeriaKnownHttpServiceClassifier.classify(callee.getReferencedName())
                                if (kind != KnownHttpServiceKind.HTTP) {
                                    return kind
                                }
                                (unwrapped.parent as? KtDotQualifiedExpression)?.receiverExpression
                            }
                            else -> null
                        }
                }
                is KtDotQualifiedExpression -> current = unwrapped.receiverExpression
                is KtNameReferenceExpression -> {
                    val kind = ArmeriaKnownHttpServiceClassifier.classify(unwrapped.getReferencedName())
                    if (kind != KnownHttpServiceKind.HTTP) {
                        return kind
                    }
                    val resolved = unwrapped.references.firstOrNull()?.resolve()
                    if (resolved is PsiClass) {
                        return ArmeriaKnownHttpServiceClassifier.classify(resolved.qualifiedName.orEmpty())
                    }
                    current = null
                }
                else -> current = null
            }
        }
        return KnownHttpServiceKind.HTTP
    }
}
