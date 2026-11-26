package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.explorer.model.GrpcRouteHint

/**
 * Reads `GrpcService` builder options used as Route Explorer badges:
 * `enableUnframedRequests` and `ProtoReflectionService` registration.
 */
object ArmeriaGrpcServiceOptionsSupport {
    internal const val ENABLE_UNFRAMED_REQUESTS = "enableUnframedRequests"
    internal val ADD_SERVICE_METHODS = setOf("addService", "addServices")
    private val REFLECTION_SIMPLE_NAMES = setOf("ProtoReflectionService", "ProtoReflectionServiceV1")
    private val NAME_TOKENS = Regex("[^A-Za-z0-9_]+")

    fun contentHints(
        serviceExpression: PsiElement?,
        kind: KnownHttpServiceKind,
    ): List<String> {
        if (kind != KnownHttpServiceKind.GRPC) {
            return emptyList()
        }
        val options = collect(serviceExpression)
        return buildList {
            if (options.unframed) {
                add(GrpcRouteHint.UNFRAMED)
            }
            if (options.reflection) {
                add(GrpcRouteHint.REFLECTION)
            }
        }
    }

    fun hasUnframedHint(hints: List<String>): Boolean = GrpcRouteHint.UNFRAMED in hints

    fun hasReflectionHint(hints: List<String>): Boolean = GrpcRouteHint.REFLECTION in hints

    /**
     * Text-scan variant for sources without PSI wiring. Scala registrations are collected by
     * regex over comment-blanked source text, so [registrationText] carries the whole
     * `.service(...)` call span — including the nested `GrpcService.builder()...build()` chain.
     */
    fun scalaContentHints(
        registrationText: String?,
        kind: KnownHttpServiceKind,
    ): List<String> {
        if (kind != KnownHttpServiceKind.GRPC || registrationText == null) {
            return emptyList()
        }
        return buildList {
            if (scalaUnframedEnabled(registrationText)) {
                add(GrpcRouteHint.UNFRAMED)
            }
            if (looksLikeReflectionName(registrationText)) {
                add(GrpcRouteHint.REFLECTION)
            }
        }
    }

    private val SCALA_UNFRAMED_CALL = Regex("""\.enableUnframedRequests\s*(?:\(\s*([^)]*?)\s*\))?""")

    private fun scalaUnframedEnabled(text: String): Boolean {
        // The outermost call wins, mirroring collectJava's mergeInner — in a fluent chain the
        // outermost call appears last in the text.
        val argument =
            SCALA_UNFRAMED_CALL
                .findAll(text)
                .lastOrNull()
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
        return argument != null && argument != "false"
    }

    private fun collect(element: PsiElement?): GrpcServiceOptions {
        if (element == null) {
            return GrpcServiceOptions()
        }
        val partial =
            when {
                element is PsiExpression -> {
                    collectJava(element, mutableSetOf())
                }

                ArmeriaKotlinPluginSupport.isKotlinPluginAvailable() -> {
                    ArmeriaKotlinGrpcServiceOptionsSupport.collect(element, mutableSetOf())
                }

                else -> {
                    PartialOptions()
                }
            }
        return GrpcServiceOptions(
            unframed = partial.unframed == true,
            reflection = partial.reflection,
        )
    }

    internal fun collectJava(
        expression: PsiExpression,
        visited: MutableSet<PsiElement>,
    ): PartialOptions {
        val unwrapped = unwrapJava(expression) ?: return PartialOptions()
        if (!visited.add(unwrapped)) {
            return PartialOptions()
        }
        return when (unwrapped) {
            is PsiMethodCallExpression -> {
                val fromCall = inspectJavaCall(unwrapped)
                val qualifier = unwrapped.methodExpression.qualifierExpression
                val fromQualifier = qualifier?.let { collectJava(it, visited) } ?: PartialOptions()
                fromCall.mergeInner(fromQualifier)
            }

            is PsiReferenceExpression -> {
                val resolved = unwrapped.resolve()
                if (resolved is PsiVariable) {
                    val initializer = resolved.initializer ?: return PartialOptions()
                    collectJava(initializer, visited)
                } else {
                    PartialOptions()
                }
            }

            is PsiNewExpression -> {
                PartialOptions(reflection = looksLikeReflectionName(unwrapped.classReference?.referenceName))
            }

            else -> {
                PartialOptions()
            }
        }
    }

    private fun inspectJavaCall(call: PsiMethodCallExpression): PartialOptions {
        val methodName = call.methodExpression.referenceName
        if (methodName == ENABLE_UNFRAMED_REQUESTS) {
            return PartialOptions(unframed = javaUnframedEnabled(call))
        }
        if (methodName in ADD_SERVICE_METHODS) {
            val reflection = call.argumentList.expressions.any(::javaLooksLikeReflection)
            return PartialOptions(reflection = reflection)
        }
        return PartialOptions()
    }

    private fun javaUnframedEnabled(call: PsiMethodCallExpression): Boolean {
        val argument = call.argumentList.expressions.firstOrNull() ?: return false
        return booleanValue(argument) ?: true
    }

    private fun booleanValue(expression: PsiExpression): Boolean? {
        val literal = unwrapJava(expression) as? PsiLiteralExpression ?: return null
        val value = literal.value
        if (value is Boolean) {
            return value
        }
        return when (literal.text) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private fun javaLooksLikeReflection(expression: PsiExpression): Boolean {
        if (looksLikeReflectionName(expression.text)) {
            return true
        }
        val target = ArmeriaRouteTargetExtractor.extractTarget(expression)
        return looksLikeReflectionName(target.substringAfterLast('.'))
    }

    private fun unwrapJava(expression: PsiExpression?): PsiExpression? {
        var current = expression ?: return null
        while (true) {
            current =
                when (current) {
                    is PsiParenthesizedExpression -> current.expression ?: return null
                    is PsiTypeCastExpression -> current.operand ?: return null
                    else -> return current
                }
        }
    }

    internal fun looksLikeReflectionName(name: String?): Boolean {
        if (name.isNullOrBlank()) {
            return false
        }
        return name.split(NAME_TOKENS).any { token -> token in REFLECTION_SIMPLE_NAMES }
    }

    private data class GrpcServiceOptions(
        val unframed: Boolean = false,
        val reflection: Boolean = false,
    )

    internal data class PartialOptions(
        val unframed: Boolean? = null,
        val reflection: Boolean = false,
    ) {
        fun mergeInner(inner: PartialOptions): PartialOptions =
            PartialOptions(
                unframed = unframed ?: inner.unframed,
                reflection = reflection || inner.reflection,
            )
    }
}
