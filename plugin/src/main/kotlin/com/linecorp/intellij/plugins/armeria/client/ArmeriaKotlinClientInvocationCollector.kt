package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal object ArmeriaKotlinClientInvocationCollector {
    fun collect(
        call: KtCallExpression,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
    ): Boolean {
        val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: return false
        if (methodName !in ArmeriaClientSupport.HTTP_INVOCATION_METHOD_NAMES) {
            return false
        }
        if (!isHttpClientInvocation(call, methodName)) {
            return false
        }
        val invocation = invocationFromCall(call, methodName) ?: return false
        val factory = resolveOwningFactory(ArmeriaKotlinClientCollector.qualifierReceiver(call))
        val protocol =
            factory?.clientType?.let { ClientProtocol.fromPresentableName(it) }
                ?: ArmeriaClientSupport.protocolForClass(ArmeriaKotlinClientCollector.resolveContainingClass(call))
                ?: ClientProtocol.HTTP
        val uri = factory?.uri?.takeIf { it.isNotBlank() } ?: invocation.path
        ArmeriaClientCollector.addEndpoint(
            element = call,
            protocol = protocol,
            target = invocationTarget(call, protocol),
            uri = uri,
            endpoints = endpoints,
            seenEndpoints = seenEndpoints,
            decorators = factory?.decorators.orEmpty(),
            endpointGroup = factory?.endpointGroup,
            transport = factory?.transport,
            httpMethod = invocation.method,
            requestPath = invocation.path,
            contentType = invocation.contentType,
            requestBody = invocation.body,
            requestHeaders = invocation.headers,
        )
        return true
    }

    fun isHttpClientInvocation(
        call: KtCallExpression,
        methodName: String,
    ): Boolean {
        val resolvedClass = resolvedOwnerClassName(call)
        if (ArmeriaClientInvocationSupport.isPreparationClass(resolvedClass)) {
            return false
        }
        if (ArmeriaClientSupport.isHttpClientClass(resolvedClass)) {
            return true
        }
        if (resolvedClass != null) {
            return false
        }
        if (methodName !in ArmeriaClientSupport.HTTP_METHOD_INVOCATION_NAMES) {
            return looksLikeExecuteOnHttpClient(call)
        }
        return looksLikeHttpClientReceiver(call)
    }

    private data class InvocationInfo(
        val method: String,
        val path: String,
        val contentType: String? = null,
        val body: String? = null,
        val headers: List<String> = emptyList(),
    )

    private fun looksLikeExecuteOnHttpClient(call: KtCallExpression): Boolean {
        if (ArmeriaKotlinExpressionSupport.resolveCallName(call) != "execute") {
            return false
        }
        val arguments = call.valueArguments.mapNotNull { it.getArgumentExpression() }
        return executeFromArguments(arguments) != null && looksLikeHttpClientReceiver(call)
    }

    private fun looksLikeHttpClientReceiver(call: KtCallExpression): Boolean {
        if (findOwningFactoryCall(ArmeriaKotlinClientCollector.qualifierReceiver(call)) != null) {
            return true
        }
        val receiver = ArmeriaKotlinClientCollector.qualifierReceiver(call)?.text.orEmpty()
        return ArmeriaClientInvocationSupport.containsHttpClientSimpleName(receiver)
    }

    private fun resolvedOwnerClassName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        val references =
            when (callee) {
                is KtNameReferenceExpression -> callee.references.toList()
                is KtQualifiedExpression -> callee.references.toList()
                else -> emptyList()
            }
        for (reference in references) {
            when (val resolved = reference.resolve()) {
                is PsiMethod -> resolved.containingClass?.qualifiedName?.let { return it }
                is PsiClass -> resolved.qualifiedName?.let { return it }
                is KtNamedFunction ->
                    PsiTreeUtil
                        .getParentOfType(resolved, KtClass::class.java)
                        ?.fqName
                        ?.asString()
                        ?.let { return it }
                is KtClass -> resolved.fqName?.asString()?.let { return it }
            }
        }
        return null
    }

    private fun invocationTarget(
        call: KtCallExpression,
        protocol: ClientProtocol,
    ): String = ArmeriaKotlinClientCollector.qualifierReceiver(call)?.text ?: protocol.presentableName()

    private fun invocationFromCall(
        call: KtCallExpression,
        methodName: String,
    ): InvocationInfo? {
        val arguments = call.valueArguments.mapNotNull { it.getArgumentExpression() }
        val chained = collectPreparationChain(call)
        if (methodName == "execute") {
            val fromArgs = executeFromArguments(arguments) ?: return null
            return fromArgs.merge(chained)
        }
        val httpMethod = ArmeriaClientSupport.httpMethodForInvocation(methodName) ?: return null
        val path =
            arguments
                .firstOrNull()
                ?.let(::extractResolvedString)
                ?.takeIf(ArmeriaClientInvocationSupport::isResolvedPath)
                ?: return null
        val fromArgs = argumentPayload(httpMethod, arguments.drop(1))
        return InvocationInfo(
            method = httpMethod,
            path = path,
            contentType = fromArgs.contentType,
            body = fromArgs.body,
        ).merge(chained)
    }

    private fun executeFromArguments(arguments: List<KtExpression>): InvocationInfo? {
        arguments.firstOrNull()?.let(::executeInvocation)?.let { return it }
        if (arguments.size < 2) {
            return null
        }
        val method = extractHttpMethod(arguments[0]) ?: return null
        val path =
            extractResolvedString(arguments[1])
                ?.takeIf(ArmeriaClientInvocationSupport::isResolvedPath)
                ?: return null
        return InvocationInfo(method = method, path = path)
    }

    private data class Payload(
        val contentType: String? = null,
        val body: String? = null,
    )

    private fun argumentPayload(
        httpMethod: String,
        arguments: List<KtExpression>,
    ): Payload {
        if (!ArmeriaClientInvocationSupport.capturesRequestBody(httpMethod)) {
            return Payload()
        }
        var contentType: String? = null
        var body: String? = null
        for (argument in arguments) {
            extractMediaType(argument)?.let { contentType = it }
            extractResolvedString(argument)?.let { body = it }
        }
        return Payload(contentType = contentType, body = body)
    }

    private fun executeInvocation(expression: KtExpression): InvocationInfo? {
        val call =
            ArmeriaKotlinClientCollector.callExpressionInChain(expression)
                ?: return null
        val callee = ArmeriaKotlinExpressionSupport.resolveCallName(call)
        if (callee != "of") {
            val nested = call.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null
            return executeInvocation(nested)
        }
        val arguments = call.valueArguments.mapNotNull { it.getArgumentExpression() }
        if (arguments.size < 2) {
            return arguments.firstOrNull()?.let(::executeInvocation)
        }
        val method = extractHttpMethod(arguments[0]) ?: return null
        val path =
            extractResolvedString(arguments[1])
                ?.takeIf(ArmeriaClientInvocationSupport::isResolvedPath)
                ?: return null
        val headers = mutableListOf<String>()
        var contentType: String? = null
        var index = 2
        while (index + 1 < arguments.size) {
            val name = extractHeaderName(arguments[index])
            val value = extractResolvedString(arguments[index + 1])
            if (name != null && value != null) {
                if (name.equals("Content-Type", ignoreCase = true)) {
                    contentType = value
                }
                headers += "$name: $value"
            }
            index += 2
        }
        return InvocationInfo(method = method, path = path, contentType = contentType, headers = headers)
    }

    private fun collectPreparationChain(start: KtCallExpression): InvocationInfo {
        var contentType: String? = null
        var body: String? = null
        val headers = mutableListOf<String>()
        var current = start
        while (true) {
            val next = ArmeriaKotlinClientCollector.findNextChainedCall(current) ?: break
            val name = ArmeriaKotlinExpressionSupport.resolveCallName(next)
            val args = next.valueArguments.mapNotNull { it.getArgumentExpression() }
            when (name) {
                "header" -> {
                    val headerName = args.getOrNull(0)?.let(::extractHeaderName)
                    val headerValue = args.getOrNull(1)?.let(::extractResolvedString)
                    if (headerName != null && headerValue != null) {
                        if (headerName.equals("Content-Type", ignoreCase = true)) {
                            contentType = headerValue
                        }
                        headers += "$headerName: $headerValue"
                    }
                }
                "content" -> {
                    for (argument in args) {
                        extractMediaType(argument)?.let { contentType = it }
                        extractResolvedString(argument)?.let { body = it }
                    }
                }
            }
            if (next === current) {
                break
            }
            current = next
        }
        return InvocationInfo(method = "", path = "", contentType = contentType, body = body, headers = headers)
    }

    private fun InvocationInfo.merge(chain: InvocationInfo): InvocationInfo =
        copy(
            contentType = chain.contentType ?: contentType,
            body = chain.body ?: body,
            headers = (headers + chain.headers).distinct(),
        )

    private fun extractHttpMethod(expression: KtExpression): String? {
        val name = constantReferenceName(expression) ?: return null
        return ArmeriaClientSupport.httpMethodForInvocation(name)
    }

    private fun extractHeaderName(expression: KtExpression): String? {
        extractResolvedString(expression)?.let { return it }
        return ArmeriaClientInvocationSupport.headerNameFromConstantName(constantReferenceName(expression))
    }

    private fun extractMediaType(expression: KtExpression): String? {
        val literal = extractResolvedString(expression)
        if (literal != null && '/' in literal) {
            return literal
        }
        return ArmeriaClientInvocationSupport.mediaTypeFromConstantName(constantReferenceName(expression))
    }

    private fun constantReferenceName(expression: KtExpression): String? {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: expression
        return when (unwrapped) {
            is KtNameReferenceExpression -> unwrapped.getReferencedName()
            is KtDotQualifiedExpression ->
                (unwrapped.selectorExpression as? KtNameReferenceExpression)?.getReferencedName()
            else -> null
        } ?: run {
            val resolved = unwrapped.references.firstOrNull()?.resolve()
            when (resolved) {
                is PsiEnumConstant -> resolved.name
                is PsiField -> resolved.name
                else -> null
            }
        }
    }

    private fun extractResolvedString(expression: KtExpression?): String? {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return null
        if (unwrapped is KtStringTemplateExpression) {
            if (unwrapped.hasInterpolation()) {
                return null
            }
            return buildString {
                for (entry in unwrapped.entries) {
                    when (entry) {
                        is KtLiteralStringTemplateEntry -> append(entry.text)
                        is KtEscapeStringTemplateEntry -> append(entry.unescapedValue)
                        else -> return null
                    }
                }
            }
        }
        if (unwrapped is KtNameReferenceExpression || unwrapped is KtDotQualifiedExpression) {
            val resolved = unwrapped.references.firstOrNull()?.resolve()
            when (resolved) {
                is KtProperty -> return extractResolvedString(resolved.initializer)
                is PsiVariable -> {
                    val initializer = resolved.initializer
                    if (initializer is KtExpression) {
                        return extractResolvedString(initializer)
                    }
                    return ArmeriaRouteSupport.evaluateJavaStringConstant(resolved)
                }
            }
        }
        return null
    }

    private fun resolveOwningFactory(receiver: KtExpression?): ArmeriaClientEndpoint? {
        val factoryCall = findOwningFactoryCall(receiver) ?: return null
        return ArmeriaKotlinClientCollector.endpointForCall(factoryCall)
    }

    private fun findOwningFactoryCall(start: KtExpression?): KtCallExpression? {
        var current: KtExpression? = start
        val visited = mutableSetOf<KtExpression>()
        while (current != null && visited.add(current)) {
            val call = ArmeriaKotlinClientCollector.callExpressionInChain(current)
            if (call != null) {
                val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call)
                if (methodName != null &&
                    (
                        methodName in ArmeriaClientSupport.FACTORY_METHOD_NAMES ||
                            methodName in ArmeriaClientSupport.CONVERSION_METHOD_NAMES
                    ) &&
                    ArmeriaKotlinClientCollector.protocolForCall(call) != null
                ) {
                    return call
                }
                current = ArmeriaKotlinClientCollector.qualifierReceiver(call)
                continue
            }
            val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(current) ?: current
            if (unwrapped is KtNameReferenceExpression) {
                val resolved = unwrapped.references.firstOrNull()?.resolve()
                current =
                    when (resolved) {
                        is KtProperty -> resolved.initializer
                        is PsiVariable -> resolved.initializer as? KtExpression
                        else -> null
                    }
                continue
            }
            return null
        }
        return null
    }
}
