package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable

internal object ArmeriaJavaClientInvocationCollector {
    fun collect(
        expression: PsiMethodCallExpression,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
    ): Boolean {
        val methodName = expression.methodExpression.referenceName ?: return false
        if (methodName !in ArmeriaClientSupport.HTTP_INVOCATION_METHOD_NAMES) {
            return false
        }
        if (!isHttpClientInvocation(expression, methodName)) {
            return false
        }
        val invocation = invocationFromCall(expression, methodName) ?: return false
        val factory = resolveOwningFactory(expression.methodExpression.qualifierExpression)
        val protocol =
            factory?.clientType?.let { ClientProtocol.fromPresentableName(it) }
                ?: protocolFromResolvedCall(expression)
                ?: ClientProtocol.HTTP
        val uri = factory?.uri?.takeIf { it.isNotBlank() } ?: invocation.path
        ArmeriaClientCollector.addEndpoint(
            element = expression,
            protocol = protocol,
            target = invocationTarget(expression, protocol),
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

    private data class InvocationInfo(
        val method: String,
        val path: String,
        val contentType: String? = null,
        val body: String? = null,
        val headers: List<String> = emptyList(),
    )

    private fun isHttpClientInvocation(
        expression: PsiMethodCallExpression,
        methodName: String,
    ): Boolean {
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
        if (ArmeriaClientInvocationSupport.isPreparationClass(resolvedClass)) {
            return false
        }
        if (ArmeriaClientSupport.isHttpClientClass(resolvedClass)) {
            return true
        }
        if (methodName !in ArmeriaClientSupport.HTTP_METHOD_INVOCATION_NAMES) {
            return looksLikeExecuteOnHttpClient(expression)
        }
        return looksLikeHttpClientQualifier(expression.methodExpression.qualifierExpression)
    }

    private fun looksLikeExecuteOnHttpClient(expression: PsiMethodCallExpression): Boolean {
        if (expression.methodExpression.referenceName != "execute") {
            return false
        }
        val first = expression.argumentList.expressions.firstOrNull() ?: return false
        return executeInvocation(first) != null &&
            looksLikeHttpClientQualifier(expression.methodExpression.qualifierExpression)
    }

    private fun looksLikeHttpClientQualifier(qualifier: PsiExpression?): Boolean {
        if (findOwningFactoryCall(qualifier) != null) {
            return true
        }
        val text = qualifier?.text.orEmpty()
        return text.contains("RestClient") ||
            text.contains("WebClient") ||
            text.contains("BlockingWebClient")
    }

    private fun protocolFromResolvedCall(expression: PsiMethodCallExpression): ClientProtocol? {
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
        return ArmeriaClientSupport.protocolForClass(resolvedClass)
    }

    private fun invocationTarget(
        expression: PsiMethodCallExpression,
        protocol: ClientProtocol,
    ): String =
        expression.methodExpression.qualifierExpression?.text
            ?: protocol.presentableName()

    private fun invocationFromCall(
        expression: PsiMethodCallExpression,
        methodName: String,
    ): InvocationInfo? {
        val arguments = expression.argumentList.expressions
        val chained = collectPreparationChain(expression)
        if (methodName == "execute") {
            val fromArg = arguments.firstOrNull()?.let(::executeInvocation) ?: return null
            return fromArg.merge(chained)
        }
        val httpMethod = ArmeriaClientSupport.httpMethodForInvocation(methodName) ?: return null
        val path =
            arguments
                .firstOrNull()
                ?.let(ArmeriaClientCollector::extractResolvedString)
                ?.takeIf(ArmeriaClientInvocationSupport::isResolvedPath)
                ?: return null
        val fromArgs = argumentPayload(arguments.drop(1))
        return InvocationInfo(
            method = httpMethod,
            path = path,
            contentType = fromArgs.contentType,
            body = fromArgs.body,
        ).merge(chained)
    }

    private data class Payload(
        val contentType: String? = null,
        val body: String? = null,
    )

    private fun argumentPayload(arguments: List<PsiExpression>): Payload {
        var contentType: String? = null
        var body: String? = null
        for (argument in arguments) {
            extractMediaType(argument)?.let { contentType = it }
            ArmeriaClientCollector.extractResolvedString(argument)?.let { body = it }
        }
        return Payload(contentType = contentType, body = body)
    }

    private fun executeInvocation(expression: PsiExpression): InvocationInfo? {
        val call = unwrapToMethodCall(expression) ?: return null
        val callee = call.methodExpression.referenceName
        if (callee != "of") {
            val nested = call.argumentList.expressions.firstOrNull() ?: return null
            return executeInvocation(nested)
        }
        val arguments = call.argumentList.expressions
        if (arguments.size < 2) {
            return arguments.firstOrNull()?.let(::executeInvocation)
        }
        val method = extractHttpMethod(arguments[0]) ?: return null
        val path =
            ArmeriaClientCollector
                .extractResolvedString(arguments[1])
                ?.takeIf(ArmeriaClientInvocationSupport::isResolvedPath)
                ?: return null
        val headers = mutableListOf<String>()
        var contentType: String? = null
        var index = 2
        while (index + 1 < arguments.size) {
            val name = extractHeaderName(arguments[index])
            val value = ArmeriaClientCollector.extractResolvedString(arguments[index + 1])
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

    private fun unwrapToMethodCall(expression: PsiExpression): PsiMethodCallExpression? {
        var current: PsiExpression? = expression
        while (current != null) {
            when (current) {
                is PsiMethodCallExpression -> return current
                is PsiReferenceExpression -> {
                    val resolved = current.resolve()
                    current = (resolved as? PsiVariable)?.initializer
                }
                else -> return null
            }
        }
        return null
    }

    private fun collectPreparationChain(start: PsiMethodCallExpression): InvocationInfo {
        var contentType: String? = null
        var body: String? = null
        val headers = mutableListOf<String>()
        var current = start
        while (true) {
            val next = ArmeriaClientCollector.findEnclosingQualifierCall(current) ?: break
            val name = next.methodExpression.referenceName
            val args = next.argumentList.expressions
            when (name) {
                "header" -> {
                    val headerName = args.getOrNull(0)?.let(::extractHeaderName)
                    val headerValue = args.getOrNull(1)?.let(ArmeriaClientCollector::extractResolvedString)
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
                        ArmeriaClientCollector.extractResolvedString(argument)?.let { body = it }
                    }
                }
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

    private fun extractHttpMethod(expression: PsiExpression): String? {
        val reference = expression as? PsiReferenceExpression
        val resolved = reference?.resolve()
        val name =
            when (resolved) {
                is PsiEnumConstant -> resolved.name
                is PsiField -> resolved.name
                else -> reference?.referenceName
            } ?: return null
        return ArmeriaClientSupport.httpMethodForInvocation(name)
    }

    private fun extractHeaderName(expression: PsiExpression): String? {
        ArmeriaClientCollector.extractResolvedString(expression)?.let { return it }
        val reference = expression as? PsiReferenceExpression ?: return null
        return ArmeriaClientInvocationSupport.headerNameFromConstantName(reference.referenceName)
    }

    private fun extractMediaType(expression: PsiExpression): String? {
        val literal = ArmeriaClientCollector.extractResolvedString(expression)
        if (literal != null && '/' in literal) {
            return literal
        }
        val reference = expression as? PsiReferenceExpression ?: return null
        return ArmeriaClientInvocationSupport.mediaTypeFromConstantName(reference.referenceName)
    }

    private fun resolveOwningFactory(qualifier: PsiExpression?): ArmeriaClientEndpoint? {
        val factoryCall = findOwningFactoryCall(qualifier) ?: return null
        val collected = mutableListOf<ArmeriaClientEndpoint>()
        ArmeriaClientCollector.collectClientFromMethodCall(factoryCall, collected, mutableSetOf())
        return collected.firstOrNull()
    }

    private fun findOwningFactoryCall(start: PsiExpression?): PsiMethodCallExpression? {
        var current: PsiExpression? = start
        val visited = mutableSetOf<PsiExpression>()
        while (current != null && visited.add(current)) {
            when (val expression = current) {
                is PsiMethodCallExpression -> {
                    val methodName = expression.methodExpression.referenceName
                    val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
                    if (methodName != null &&
                        (
                            methodName in ArmeriaClientSupport.FACTORY_METHOD_NAMES ||
                                methodName in ArmeriaClientSupport.CONVERSION_METHOD_NAMES
                        ) &&
                        ArmeriaClientSupport.protocolForInvocation(methodName, resolvedClass) != null
                    ) {
                        return expression
                    }
                    current = expression.methodExpression.qualifierExpression
                }
                is PsiReferenceExpression -> {
                    current = (expression.resolve() as? PsiVariable)?.initializer
                }
                else -> return null
            }
        }
        return null
    }
}
