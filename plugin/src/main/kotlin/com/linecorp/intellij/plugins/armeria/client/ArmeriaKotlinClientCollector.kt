package com.linecorp.intellij.plugins.armeria.client

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaKotlinRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression

internal object ArmeriaKotlinClientCollector {
    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            val file = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            if (!ArmeriaKotlinRouteCollector.referencesArmeriaKotlinContent(file)) {
                continue
            }
            file.forEachDescendant { element ->
                val call = element as? KtCallExpression ?: return@forEachDescendant
                collectClientFromCall(call, endpoints, seenEndpoints)
            }
        }
    }

    internal fun protocolForCall(call: KtCallExpression): ClientProtocol? {
        val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: return null
        if (methodName in ArmeriaClientSupport.HTTP_INVOCATION_METHOD_NAMES &&
            ArmeriaKotlinClientInvocationCollector.isHttpClientInvocation(call, methodName)
        ) {
            return ArmeriaClientSupport.protocolForClass(resolveContainingClass(call))
                ?: ClientProtocol.HTTP
        }
        if (methodName !in ArmeriaClientSupport.FACTORY_METHOD_NAMES &&
            methodName !in ArmeriaClientSupport.CONVERSION_METHOD_NAMES
        ) {
            return null
        }
        return ArmeriaClientSupport.protocolForInvocation(methodName, resolveContainingClass(call))
    }

    internal fun endpointForCall(element: PsiElement): ArmeriaClientEndpoint? {
        val call = element as? KtCallExpression ?: return null
        val endpoints = mutableListOf<ArmeriaClientEndpoint>()
        collectClientFromCall(call, endpoints, mutableSetOf())
        return endpoints.firstOrNull()
    }

    private fun collectClientFromCall(
        call: KtCallExpression,
        endpoints: MutableList<ArmeriaClientEndpoint>,
        seenEndpoints: MutableSet<String>,
    ) {
        if (isNestedInsideClientFactoryArgument(call) || isQualifierOfClientConversion(call)) {
            return
        }
        if (ArmeriaKotlinClientInvocationCollector.collect(call, endpoints, seenEndpoints)) {
            return
        }
        val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: return
        val resolvedClass = resolveContainingClass(call)
        val protocol = ArmeriaClientSupport.protocolForInvocation(methodName, resolvedClass) ?: return
        val metadata = extractClientMetadata(call, methodName, protocol) ?: return
        val target = resolveTargetName(call) ?: resolvedClass?.substringAfterLast('.').orEmpty()
        ArmeriaClientCollector.addEndpoint(
            element = call,
            protocol = protocol,
            target = target,
            uri = metadata.uri,
            endpoints = endpoints,
            seenEndpoints = seenEndpoints,
            decorators = metadata.decorators,
            endpointGroup = metadata.endpointGroup,
            transport = metadata.transport,
        )
    }

    private data class ClientMetadata(
        val uri: String,
        val decorators: List<String> = emptyList(),
        val endpointGroup: String? = null,
        val transport: String? = null,
    )

    private fun extractClientMetadata(
        call: KtCallExpression,
        methodName: String,
        protocol: ClientProtocol,
    ): ClientMetadata? {
        val arguments = call.valueArguments.mapNotNull { it.getArgumentExpression() }
        val decorators = ArmeriaKotlinClientDecoratorSupport.collectKotlinClientDecorators(call)
        if (methodName in ArmeriaClientSupport.CONVERSION_METHOD_NAMES) {
            val receiver = qualifierReceiver(call) ?: return null
            val webClientInfo = extractWebClientTransport(receiver) ?: return null
            return webClientInfo.toMetadata(decorators)
        }
        return when (methodName) {
            "newClient", "of" -> extractFactoryMetadata(arguments, protocol, decorators)
            "builder" -> {
                if (arguments.isEmpty()) {
                    null
                } else {
                    extractFactoryMetadata(arguments, protocol, decorators)
                }
            }
            else -> null
        }
    }

    private fun extractFactoryMetadata(
        arguments: List<KtExpression>,
        protocol: ClientProtocol,
        decorators: List<String>,
    ): ClientMetadata? {
        if (ArmeriaClientSupport.wrapsWebClientTransport(protocol)) {
            val firstArg = arguments.firstOrNull() ?: return null
            val webClientInfo = extractWebClientTransport(firstArg)
            if (webClientInfo != null) {
                return webClientInfo.toMetadata(
                    extraDecorators = decorators,
                    transport = message("client.explorer.transport.webClient"),
                )
            }
        }
        if (arguments.size >= 2 && isEndpointGroupArgument(arguments[1])) {
            val endpointGroup =
                ArmeriaKotlinClientEndpointGroupSupport.labelKotlinEndpointGroup(arguments[1]) ?: return null
            return ClientMetadata(
                uri =
                    ArmeriaKotlinClientEndpointGroupSupport.extractKotlinEndpointGroupUri(arguments[1])
                        ?: endpointGroup,
                decorators = decorators,
                endpointGroup = endpointGroup,
            )
        }
        val uri = ArmeriaKotlinExpressionSupport.extractKotlinString(arguments.firstOrNull()) ?: return null
        return ClientMetadata(uri = uri, decorators = decorators)
    }

    private data class WebClientTransportInfo(
        val uri: String,
        val decorators: List<String>,
        val endpointGroup: String? = null,
    )

    private fun WebClientTransportInfo.toMetadata(
        extraDecorators: List<String> = emptyList(),
        transport: String? = null,
    ): ClientMetadata =
        ClientMetadata(
            uri = uri,
            decorators = (extraDecorators + decorators).distinct(),
            endpointGroup = endpointGroup,
            transport = transport,
        )

    private fun extractWebClientTransport(expression: KtExpression): WebClientTransportInfo? {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return null
        val call = callExpressionInChain(unwrapped)
        if (call != null) {
            val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call)
            val resolvedClass = resolveContainingClass(call)
            if (methodName in ArmeriaClientSupport.CONVERSION_METHOD_NAMES &&
                (
                    ArmeriaClientSupport.isWebClientClass(resolvedClass) ||
                        looksLikeWebClientFactoryReceiver(call)
                )
            ) {
                val receiver = qualifierReceiver(call) ?: return null
                return extractWebClientTransport(receiver)
            }
            if (methodName in ArmeriaClientSupport.FACTORY_METHOD_NAMES &&
                (ArmeriaClientSupport.isWebClientClass(resolvedClass) || looksLikeWebClientFactoryReceiver(call))
            ) {
                val arguments = call.valueArguments.mapNotNull { it.getArgumentExpression() }
                val decorators = ArmeriaKotlinClientDecoratorSupport.collectKotlinClientDecorators(call)
                if (arguments.size >= 2 && isEndpointGroupArgument(arguments[1])) {
                    val endpointGroup =
                        ArmeriaKotlinClientEndpointGroupSupport.labelKotlinEndpointGroup(arguments[1])
                            ?: return null
                    val uri =
                        ArmeriaKotlinClientEndpointGroupSupport.extractKotlinEndpointGroupUri(arguments[1])
                            ?: endpointGroup
                    return WebClientTransportInfo(uri = uri, decorators = decorators, endpointGroup = endpointGroup)
                }
                val uri = ArmeriaKotlinExpressionSupport.extractKotlinString(arguments.firstOrNull()) ?: return null
                return WebClientTransportInfo(uri = uri, decorators = decorators)
            }
            // Unwrap fluent WebClientBuilder chains passed to Retrofit, e.g.
            // WebClient.builder(uri).decorator(...).build() or without a trailing build().
            if (isWebClientBuilderChainCall(call, resolvedClass)) {
                val factoryCall = findWebClientFactoryInQualifierChain(call) ?: return null
                return extractWebClientTransport(factoryCall)
            }
        }
        if (unwrapped is KtNameReferenceExpression) {
            val resolved = unwrapped.references.firstOrNull()?.resolve()
            if (resolved is KtProperty) {
                return extractWebClientTransport(resolved.initializer ?: return null)
            }
            if (resolved is PsiVariable) {
                return extractWebClientTransport(resolved.initializer as? KtExpression ?: return null)
            }
        }
        return null
    }

    private fun isWebClientBuilderChainCall(
        call: KtCallExpression,
        resolvedClass: String?,
    ): Boolean {
        if (resolvedClass?.startsWith(ArmeriaClientSupport.ARMERIA_CLIENT_PACKAGE_PREFIX) == true) {
            return true
        }
        val receiverText =
            when (val callee = call.calleeExpression) {
                is KtQualifiedExpression -> callee.receiverExpression.text
                else -> (call.parent as? KtQualifiedExpression)?.receiverExpression?.text
            }.orEmpty()
        return ArmeriaClientSupport.looksLikeClientBuilderReceiverText(receiverText) ||
            receiverText.contains("WebClient")
    }

    private fun isEndpointGroupArgument(expression: KtExpression): Boolean =
        ArmeriaKotlinClientEndpointGroupSupport.labelKotlinEndpointGroup(expression) != null

    private fun findWebClientFactoryInQualifierChain(call: KtCallExpression): KtCallExpression? {
        var current: KtExpression? = qualifierReceiver(call)
        while (current != null) {
            val factoryCall = callExpressionInChain(current)
            if (factoryCall != null) {
                val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(factoryCall)
                val resolvedClass = resolveContainingClass(factoryCall)
                if (methodName in ArmeriaClientSupport.FACTORY_METHOD_NAMES &&
                    (
                        ArmeriaClientSupport.isWebClientClass(resolvedClass) ||
                            looksLikeWebClientFactoryReceiver(factoryCall)
                    )
                ) {
                    return factoryCall
                }
            }
            current = qualifierReceiver(current)
        }
        return null
    }

    private fun looksLikeWebClientFactoryReceiver(call: KtCallExpression): Boolean {
        val receiverText =
            when (val callee = call.calleeExpression) {
                is KtQualifiedExpression -> callee.receiverExpression.text
                else -> (call.parent as? KtQualifiedExpression)?.receiverExpression?.text
            }.orEmpty()
        val simpleName = receiverText.substringAfterLast('.')
        return simpleName == "WebClient" || receiverText.endsWith(".WebClient")
    }

    internal fun callExpressionInChain(expression: KtExpression): KtCallExpression? =
        when (val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: expression) {
            is KtCallExpression -> unwrapped
            is KtQualifiedExpression -> unwrapped.selectorExpression as? KtCallExpression
            else -> null
        }

    internal fun qualifierReceiver(expression: KtExpression): KtExpression? =
        when (expression) {
            is KtCallExpression -> {
                when (val callee = expression.calleeExpression) {
                    is KtQualifiedExpression -> callee.receiverExpression
                    else -> (expression.parent as? KtQualifiedExpression)?.receiverExpression
                }
            }
            is KtQualifiedExpression -> expression.receiverExpression
            else -> null
        }

    private fun isNestedInsideClientFactoryArgument(call: KtCallExpression): Boolean {
        var element: PsiElement? = call.parent
        while (element != null) {
            val outerCall =
                element as? KtCallExpression ?: run {
                    element = element.parent
                    continue
                }
            val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(outerCall) ?: ""
            val outerClass = resolveContainingClass(outerCall)
            if (ArmeriaClientSupport.protocolForInvocation(methodName, outerClass) != null &&
                isDescendantOfValueArgument(call, outerCall)
            ) {
                return true
            }
            element = element.parent
        }
        return false
    }

    private fun isQualifierOfClientConversion(call: KtCallExpression): Boolean {
        var current: KtCallExpression = call
        while (true) {
            val next = findNextChainedCall(current) ?: return false
            val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(next)
            if (methodName in ArmeriaClientSupport.CONVERSION_METHOD_NAMES) {
                val resolvedClass = resolveContainingClass(next)
                if (ArmeriaClientSupport.isWebClientClass(resolvedClass) ||
                    looksLikeWebClientFactoryReceiver(next) ||
                    findWebClientFactoryInQualifierChain(next) != null ||
                    resolvedClass?.startsWith(ArmeriaClientSupport.ARMERIA_CLIENT_PACKAGE_PREFIX) == true
                ) {
                    return true
                }
            }
            if (next === current) {
                return false
            }
            current = next
        }
    }

    internal fun findNextChainedCall(call: KtCallExpression): KtCallExpression? {
        var current: PsiElement = call
        while (true) {
            val parent = current.parent ?: return null
            when {
                parent is KtParenthesizedExpression -> current = parent
                parent is KtUnaryExpression && parent.operationToken == KtTokens.EXCLEXCL -> current = parent
                parent is KtQualifiedExpression -> {
                    val receiver = parent.receiverExpression
                    if (receiver == current || PsiTreeUtil.isAncestor(receiver, current, false)) {
                        val selector = parent.selectorExpression ?: return null
                        return selector as? KtCallExpression ?: callExpressionInChain(selector)
                    }
                    current = parent
                }
                else -> return null
            }
        }
    }

    private fun isDescendantOfValueArgument(
        call: KtCallExpression,
        outerCall: KtCallExpression,
    ): Boolean =
        outerCall.valueArguments.mapNotNull { it.getArgumentExpression() }.any { argument ->
            PsiTreeUtil.isAncestor(argument, call, false)
        }

    private fun resolveTargetName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        val receiver =
            when (callee) {
                is KtQualifiedExpression -> callee.receiverExpression
                else -> (call.parent as? KtQualifiedExpression)?.receiverExpression
            }
        return receiver?.text
    }

    internal fun resolveContainingClass(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        val references =
            when (callee) {
                is KtNameReferenceExpression -> callee.references.toList()
                is KtQualifiedExpression -> callee.references.toList()
                else -> emptyList()
            }
        for (reference in references) {
            val resolved = reference.resolve()
            val qualifiedName =
                when (resolved) {
                    is PsiMethod -> resolved.containingClass?.qualifiedName
                    is PsiClass -> resolved.qualifiedName
                    else -> null
                }
            if (ArmeriaClientSupport.protocolForClass(qualifiedName) != null) {
                return qualifiedName
            }
        }
        val qualifierText =
            when (callee) {
                is KtQualifiedExpression -> callee.receiverExpression.text
                else -> (call.parent as? KtQualifiedExpression)?.receiverExpression?.text
            }.orEmpty()
        val fromSimpleName = protocolForClassBySimpleName(qualifierText, call.containingFile as? KtFile)
        if (fromSimpleName != null) {
            return fromSimpleName
        }
        val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call)
        if (methodName in ArmeriaClientSupport.CONVERSION_METHOD_NAMES &&
            findWebClientFactoryInQualifierChain(call) != null
        ) {
            return "com.linecorp.armeria.client.WebClient"
        }
        return null
    }

    private fun protocolForClassBySimpleName(
        qualifierText: String,
        file: KtFile?,
    ): String? {
        if (qualifierText.isBlank()) {
            return null
        }
        if (qualifierText.startsWith("com.linecorp.armeria")) {
            return qualifierText.takeIf { ArmeriaClientSupport.protocolForClass(it) != null }
        }
        val simpleName = qualifierText.substringAfterLast('.')
        val importFqcn =
            file
                ?.importList
                ?.imports
                ?.firstOrNull { import ->
                    import.importedFqName?.shortName()?.asString() == simpleName
                }?.importedFqName
                ?.asString()
        return importFqcn?.takeIf { ArmeriaClientSupport.protocolForClass(it) != null }
    }
}
