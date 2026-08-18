package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.InheritanceUtil
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKnownHttpServiceClassifier
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.KnownHttpServiceKind
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaServerDecoratorKotlinSupport {
    fun findings(call: KtCallExpression): List<ArmeriaServerDecoratorFinding> {
        val findings = mutableListOf<ArmeriaServerDecoratorFinding>()
        collectDecoratorOrderFindings(call, findings)
        collectServiceRegistrationFindings(call, findings)
        return findings
    }

    private fun collectDecoratorOrderFindings(
        call: KtCallExpression,
        findings: MutableList<ArmeriaServerDecoratorFinding>,
    ) {
        if (isGlobalBuilderDecoratorCall(call)) {
            addAuthAfterLogging(
                visited = call,
                kinds =
                    collectBuilderDecoratorCalls(call)
                        .filter(::isGlobalBuilderDecoratorCall)
                        .sortedBy { it.textOffset }
                        .map { it to decoratorKind(it) },
                findings = findings,
            )
            return
        }
        if (isHttpServiceDecorateCall(call)) {
            addAuthAfterLogging(
                visited = call,
                kinds = httpServiceDecorateCalls(call).map { it to decoratorKind(it) },
                findings = findings,
            )
            return
        }
        val extraDecorators = parseServiceRegistration(call)?.extraDecorators ?: return
        addAuthAfterLoggingOnExpressions(extraDecorators, findings)
    }

    private fun collectServiceRegistrationFindings(
        call: KtCallExpression,
        findings: MutableList<ArmeriaServerDecoratorFinding>,
    ) {
        val registration = parseServiceRegistration(call) ?: return
        val service = registration.serviceExpression
        if (!registration.hasExplicitPath && isDecoratedServiceWithRoutes(service)) {
            findings +=
                ArmeriaServerDecoratorFinding(
                    highlight = highlightDecorateOrArgument(service),
                    messageKey = ArmeriaServerDecoratorMessages.SERVICE_WITH_ROUTES,
                )
        }
        if (isGrpcService(service) && !hasCorsDecorator(call, registration)) {
            findings +=
                ArmeriaServerDecoratorFinding(
                    highlight = highlightServiceArgument(service),
                    messageKey = ArmeriaServerDecoratorMessages.GRPC_CORS,
                )
        }
    }

    private fun addAuthAfterLogging(
        visited: KtCallExpression,
        kinds: List<Pair<KtCallExpression, ArmeriaServerDecoratorKind?>>,
        findings: MutableList<ArmeriaServerDecoratorFinding>,
    ) {
        val authIndex = kinds.indexOfLast { it.second == ArmeriaServerDecoratorKind.AUTH }
        val loggingIndex = kinds.indexOfLast { it.second == ArmeriaServerDecoratorKind.LOGGING }
        if (authIndex < 0 || loggingIndex < 0 || authIndex <= loggingIndex) {
            return
        }
        if (kinds[authIndex].first != visited) {
            return
        }
        findings +=
            ArmeriaServerDecoratorFinding(
                highlight = highlightDecoratorArgument(visited),
                messageKey = ArmeriaServerDecoratorMessages.AUTH_AFTER_LOGGING,
            )
    }

    private fun addAuthAfterLoggingOnExpressions(
        expressions: List<KtExpression>,
        findings: MutableList<ArmeriaServerDecoratorFinding>,
    ) {
        val kinds = expressions.map { it to decoratorKindFromExpression(it) }
        val authIndex = kinds.indexOfLast { it.second == ArmeriaServerDecoratorKind.AUTH }
        val loggingIndex = kinds.indexOfLast { it.second == ArmeriaServerDecoratorKind.LOGGING }
        if (authIndex < 0 || loggingIndex < 0 || authIndex <= loggingIndex) {
            return
        }
        findings +=
            ArmeriaServerDecoratorFinding(
                highlight = kinds[authIndex].first,
                messageKey = ArmeriaServerDecoratorMessages.AUTH_AFTER_LOGGING,
            )
    }

    private fun parseServiceRegistration(call: KtCallExpression): ServiceRegistrationArgs? {
        val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: return null
        if (methodName != "service" && methodName != "serviceUnder") {
            return null
        }
        if (!ArmeriaBuilderCallHeuristics.looksLikeKotlinBuilderCall(call)) {
            return null
        }
        val valueArguments = call.valueArguments
        if (valueArguments.isEmpty()) {
            return null
        }
        val namedPath = namedArgument(valueArguments, "pathPattern") ?: namedArgument(valueArguments, "pathPrefix")
        val namedService = namedArgument(valueArguments, "service")
        val positional = valueArguments.filter { it.getArgumentName() == null }.mapNotNull { it.getArgumentExpression() }
        val pathExpression =
            namedPath
                ?: when {
                    methodName == "serviceUnder" -> positional.getOrNull(0)
                    positional.firstOrNull()?.let(::isPathPatternExpression) == true -> positional.first()
                    else -> null
                }
        val serviceExpression =
            namedService
                ?: positional.firstOrNull { it != pathExpression }
                ?: return null
        val extraDecorators =
            positional.filter { it != pathExpression && it != serviceExpression } +
                valueArguments.mapNotNull { argument ->
                    val name = argument.getArgumentName()?.asName?.identifier
                    if (name == null || name == "pathPattern" || name == "pathPrefix" || name == "service") {
                        null
                    } else {
                        argument.getArgumentExpression()
                    }
                }
        val hasExplicitPath = pathExpression != null || methodName == "serviceUnder"
        return ServiceRegistrationArgs(
            serviceExpression = serviceExpression,
            hasExplicitPath = hasExplicitPath,
            extraDecorators = extraDecorators.distinct(),
            routePath = pathExpression?.let(::stringValue) ?: "/",
        )
    }

    private fun namedArgument(
        arguments: List<KtValueArgument>,
        name: String,
    ): KtExpression? =
        arguments
            .firstOrNull { it.getArgumentName()?.asName?.identifier == name }
            ?.getArgumentExpression()

    private fun hasCorsDecorator(
        serviceCall: KtCallExpression,
        registration: ServiceRegistrationArgs,
    ): Boolean {
        if (registration.extraDecorators.any { decoratorKindFromExpression(it) == ArmeriaServerDecoratorKind.CORS }) {
            return true
        }
        if (decorateChainHasCors(registration.serviceExpression)) {
            return true
        }
        return builderHasCors(serviceCall, registration.routePath)
    }

    private fun decorateChainHasCors(expression: KtExpression): Boolean {
        var current: KtExpression? = unwrapAndFollow(expression)
        val visited = mutableSetOf<PsiElement>()
        while (current != null && visited.add(current)) {
            val decorateCall = asCall(current)
            if (decorateCall == null || !isHttpServiceDecorateCall(decorateCall)) {
                break
            }
            if (decoratorKind(decorateCall) == ArmeriaServerDecoratorKind.CORS) {
                return true
            }
            current = chainReceiver(decorateCall)?.let(::unwrapAndFollow)
        }
        return false
    }

    private fun builderHasCors(
        serviceCall: KtCallExpression,
        routePath: String,
    ): Boolean = collectBuilderDecoratorCalls(serviceCall).any { corsDecoratorApplies(it, routePath) }

    private fun collectBuilderDecoratorCalls(anchor: KtCallExpression): List<KtCallExpression> {
        val found = LinkedHashSet<KtCallExpression>()
        collectFluentBuilderDecoratorCalls(anchor, found)
        collectEnclosingScopeFluentDecorators(anchor, found)
        val scope = ArmeriaKotlinExpressionSupport.containingKotlinExpressionScope(anchor)
        scope.accept(
            object : KtTreeVisitorVoid() {
                override fun visitCallExpression(expression: KtCallExpression) {
                    if (isBuilderDecoratorCall(expression) && isSameBuilder(anchor, expression)) {
                        found += expression
                    }
                    super.visitCallExpression(expression)
                }
            },
        )
        return found.toList()
    }

    private fun collectFluentBuilderDecoratorCalls(
        anchor: KtCallExpression,
        found: MutableSet<KtCallExpression>,
    ) {
        var current: KtExpression? = chainReceiver(anchor)
        val visited = mutableSetOf<PsiElement>()
        while (current != null && visited.add(current)) {
            val decoratorCall = asCall(current)
            if (decoratorCall != null && isBuilderDecoratorCall(decoratorCall)) {
                found += decoratorCall
            }
            current = nextPrecedingExpression(current)
        }
        var cursor = anchor
        while (true) {
            val next = nextChainedCall(cursor) ?: break
            if (isBuilderDecoratorCall(next)) {
                found += next
            }
            cursor = next
        }
        if (isBuilderDecoratorCall(anchor)) {
            found += anchor
        }
    }

    private fun collectEnclosingScopeFluentDecorators(
        anchor: KtCallExpression,
        found: MutableSet<KtCallExpression>,
    ) {
        var current: PsiElement = anchor
        val visited = mutableSetOf<PsiElement>()
        while (visited.add(current)) {
            val lambda = current.getParentOfType<KtLambdaExpression>(strict = true) ?: break
            val scopeCall = (lambda.parent as? KtValueArgument)?.parent as? KtCallExpression ?: break
            collectFluentBuilderDecoratorCalls(scopeCall, found)
            current = scopeCall
        }
    }

    private fun isSameBuilder(
        anchor: KtCallExpression,
        other: KtCallExpression,
    ): Boolean {
        val anchorVariable = resolveBuilderVariable(anchor)
        val otherVariable = resolveBuilderVariable(other)
        if (anchorVariable != null && anchorVariable == otherVariable) {
            return true
        }
        if (chainReceiver(anchor) == null && chainReceiver(other) == null) {
            return ArmeriaKotlinExpressionSupport.containingKotlinExpressionScope(anchor) ===
                ArmeriaKotlinExpressionSupport.containingKotlinExpressionScope(other)
        }
        return false
    }

    private fun resolveBuilderVariable(call: KtCallExpression): PsiElement? {
        var current: KtExpression? = chainReceiver(call)
        val visited = mutableSetOf<PsiElement>()
        while (current != null && visited.add(current)) {
            when (current) {
                is KtNameReferenceExpression -> {
                    return current.references.firstNotNullOfOrNull { it.resolve() }
                }
                else -> current = chainReceiver(current)
            }
        }
        return null
    }

    private fun corsDecoratorApplies(
        call: KtCallExpression,
        routePath: String,
    ): Boolean {
        val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: return false
        if (methodName != "decorator" && methodName != "decoratorUnder") {
            return false
        }
        if (!ArmeriaBuilderCallHeuristics.looksLikeKotlinBuilderCall(call)) {
            return false
        }
        val arguments = call.valueArguments.mapNotNull { it.getArgumentExpression() }
        val pathExpression: KtExpression?
        val decoratorExpression: KtExpression
        when {
            methodName == "decoratorUnder" -> {
                pathExpression = arguments.getOrNull(0)
                decoratorExpression = arguments.getOrNull(1) ?: return false
            }
            arguments.size >= 2 -> {
                pathExpression = arguments[0]
                decoratorExpression = arguments[1]
            }
            arguments.isNotEmpty() -> {
                pathExpression = null
                decoratorExpression = arguments[0]
            }
            else -> return false
        }
        if (decoratorKindFromExpression(decoratorExpression) != ArmeriaServerDecoratorKind.CORS) {
            return false
        }
        if (pathExpression == null) {
            return true
        }
        val path = stringValue(pathExpression)
        return ArmeriaServerDecoratorTypes.corsDecoratorAppliesToRoute(path, routePath)
    }

    private fun isDecoratedServiceWithRoutes(expression: KtExpression): Boolean {
        val unwrapped = unwrapAndFollow(expression)
        val decorateCall = asCall(unwrapped) ?: return false
        if (!isHttpServiceDecorateCall(decorateCall)) {
            return false
        }
        val seed = decorateSeed(decorateCall) ?: return false
        return isServiceWithRoutes(seed)
    }

    private fun isGrpcService(expression: KtExpression): Boolean = serviceKind(expression) == KnownHttpServiceKind.GRPC

    private fun isServiceWithRoutes(expression: KtExpression): Boolean {
        if (resolvedClass(expression)?.let(::isHttpServiceWithRoutesClass) == true) {
            return true
        }
        val kind = serviceKind(expression) ?: return false
        return ArmeriaServerDecoratorTypes.isServiceWithRoutesKind(kind)
    }

    private fun serviceKind(expression: KtExpression): KnownHttpServiceKind? {
        val typeName = resolveServiceTypeName(expression, mutableSetOf()) ?: return null
        val kind = ArmeriaKnownHttpServiceClassifier.classify(typeName)
        return kind.takeIf { it != KnownHttpServiceKind.HTTP }
    }

    private fun resolveServiceTypeName(
        expression: KtExpression,
        visited: MutableSet<PsiElement>,
    ): String? {
        val unwrapped = unwrap(expression)
        knownServiceTypeName(resolvedClass(unwrapped))?.let { return it }
        val call = asCall(unwrapped)
        if (call != null) {
            if (isHttpServiceDecorateCall(call)) {
                chainReceiver(call)?.let { resolveServiceTypeName(it, visited) }?.let { return it }
            }
            knownServiceTypeName(resolvedMethodClass(call))?.let { return it }
            return chainReceiver(call)?.let { resolveServiceTypeName(it, visited) }
        }
        if (unwrapped is KtNameReferenceExpression) {
            if (!visited.add(unwrapped)) {
                return null
            }
            return when (val resolved = unwrapped.references.firstNotNullOfOrNull { it.resolve() }) {
                is KtProperty -> resolved.initializer?.let { resolveServiceTypeName(it, visited) }
                is PsiVariable -> {
                    knownServiceTypeName(resolved.type.canonicalText)?.let { return it }
                    (resolved.initializer as? KtExpression)?.let { resolveServiceTypeName(it, visited) }
                }
                is PsiClass -> knownServiceTypeName(resolved)
                else -> null
            }
        }
        return null
    }

    private fun knownServiceTypeName(psiClass: PsiClass?): String? {
        val qualified = psiClass?.qualifiedName ?: psiClass?.name ?: return null
        return knownServiceTypeName(qualified)
    }

    private fun knownServiceTypeName(typeName: String?): String? {
        val canonical = typeName?.let(ArmeriaKnownHttpServiceClassifier::canonicalServiceTypeName) ?: return null
        return canonical.takeIf { ArmeriaKnownHttpServiceClassifier.classify(it) != KnownHttpServiceKind.HTTP }
    }

    private fun isHttpServiceWithRoutesClass(psiClass: PsiClass): Boolean =
        psiClass.qualifiedName == ArmeriaServerDecoratorTypes.HTTP_SERVICE_WITH_ROUTES ||
            psiClass.name == "HttpServiceWithRoutes" ||
            InheritanceUtil.isInheritor(psiClass, ArmeriaServerDecoratorTypes.HTTP_SERVICE_WITH_ROUTES)

    private fun decorateSeed(decorateCall: KtCallExpression): KtExpression? {
        var current: KtExpression? = decorateCall
        val visited = mutableSetOf<PsiElement>()
        while (current != null && visited.add(current)) {
            val call = asCall(current)
            if (call == null || !isHttpServiceDecorateCall(call)) {
                return unwrapAndFollow(current)
            }
            current = chainReceiver(call)
        }
        return current
    }

    private fun isGlobalBuilderDecoratorCall(call: KtCallExpression): Boolean =
        isBuilderDecoratorCall(call) &&
            ArmeriaKotlinExpressionSupport.resolveCallName(call) == "decorator" &&
            call.valueArguments.size == 1

    private fun isBuilderDecoratorCall(call: KtCallExpression): Boolean {
        val name = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: return false
        if (name != "decorator" && name != "decoratorUnder") {
            return false
        }
        return ArmeriaBuilderCallHeuristics.looksLikeKotlinBuilderCall(call)
    }

    private fun isHttpServiceDecorateCall(call: KtCallExpression): Boolean {
        if (ArmeriaKotlinExpressionSupport.resolveCallName(call) != "decorate") {
            return false
        }
        val resolvedClass = resolvedMethodClass(call)?.qualifiedName
        if (resolvedClass != null) {
            return isArmeriaHttpServiceTypeName(resolvedClass)
        }
        val receiver = chainReceiver(call) ?: return false
        if (ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(receiver.text)) {
            return false
        }
        return resolvedClass(receiver)?.let(::isHttpServiceClass) == true || isServiceWithRoutes(receiver)
    }

    private fun isArmeriaHttpServiceTypeName(qualifiedName: String): Boolean {
        if (qualifiedName == ArmeriaRouteSupport.SERVER_BUILDER_CLASS) {
            return false
        }
        return qualifiedName == ArmeriaServerDecoratorTypes.HTTP_SERVICE ||
            qualifiedName == ArmeriaServerDecoratorTypes.HTTP_SERVICE_WITH_ROUTES ||
            qualifiedName.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX)
    }

    private fun isHttpServiceClass(psiClass: PsiClass): Boolean =
        psiClass.qualifiedName == ArmeriaServerDecoratorTypes.HTTP_SERVICE ||
            InheritanceUtil.isInheritor(psiClass, ArmeriaServerDecoratorTypes.HTTP_SERVICE)

    private fun httpServiceDecorateCalls(call: KtCallExpression): List<KtCallExpression> =
        decoratorCallsInChain(call, ::isHttpServiceDecorateCall)

    private fun decoratorCallsInChain(
        call: KtCallExpression,
        predicate: (KtCallExpression) -> Boolean,
    ): List<KtCallExpression> {
        val preceding = mutableListOf<KtCallExpression>()
        val visited = mutableSetOf<PsiElement>()
        var current: KtExpression? = chainReceiver(call)
        while (current != null && visited.add(current)) {
            val decoratorCall = asCall(current)
            if (decoratorCall != null && predicate(decoratorCall)) {
                preceding += decoratorCall
            }
            current = nextPrecedingExpression(current)
        }
        val following = mutableListOf<KtCallExpression>()
        var cursor: KtCallExpression = call
        while (true) {
            val next = nextChainedCall(cursor) ?: break
            if (predicate(next)) {
                following += next
            }
            cursor = next
        }
        return preceding.asReversed() + call + following
    }

    private fun nextPrecedingExpression(current: KtExpression): KtExpression? {
        val receiver = chainReceiver(current)
        if (receiver != null && receiver !== current) {
            return unwrap(receiver)
        }
        return resolvedInitializer(current)
    }

    private fun resolvedInitializer(expression: KtExpression): KtExpression? {
        val name =
            when (expression) {
                is KtNameReferenceExpression -> expression
                is KtDotQualifiedExpression -> expression.selectorExpression as? KtNameReferenceExpression
                else -> null
            } ?: return null
        return when (
            val resolved =
                name.references.firstNotNullOfOrNull { it.resolve() }
        ) {
            is KtProperty -> resolved.initializer
            is PsiVariable -> resolved.initializer as? KtExpression
            else -> null
        }
    }

    private fun nextChainedCall(call: KtCallExpression): KtCallExpression? {
        val parent = call.parent
        if (parent is KtDotQualifiedExpression && parent.receiverExpression == call) {
            return parent.selectorExpression as? KtCallExpression
        }
        if (parent is KtDotQualifiedExpression) {
            val grandParent = parent.parent as? KtDotQualifiedExpression
            if (grandParent != null && grandParent.receiverExpression == parent) {
                return grandParent.selectorExpression as? KtCallExpression
            }
        }
        return null
    }

    private fun decoratorKind(call: KtCallExpression): ArmeriaServerDecoratorKind? {
        val argument = call.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null
        return decoratorKindFromExpression(argument)
    }

    private fun decoratorKindFromExpression(expression: KtExpression): ArmeriaServerDecoratorKind? =
        ArmeriaServerDecoratorKind.fromSimpleName(decoratorClassSimpleName(expression))

    private fun decoratorClassSimpleName(expression: KtExpression): String {
        var current: KtExpression = unwrap(expression)
        while (true) {
            when (current) {
                is KtCallExpression -> {
                    val receiver = chainReceiver(current)
                    if (receiver != null) {
                        current = unwrap(receiver)
                    } else {
                        return ArmeriaKotlinExpressionSupport.resolveCallName(current).orEmpty()
                    }
                }
                is KtDotQualifiedExpression -> {
                    when (val selector = current.selectorExpression) {
                        is KtCallExpression -> current = selector
                        is KtNameReferenceExpression -> {
                            val name = selector.getReferencedName()
                            if (name == "java" || name == "class") {
                                current = unwrap(current.receiverExpression)
                            } else {
                                return name
                            }
                        }
                        is KtClassLiteralExpression -> {
                            current = unwrap(selector.receiverExpression ?: current.receiverExpression)
                        }
                        else -> current = current.receiverExpression
                    }
                }
                is KtClassLiteralExpression -> {
                    current = unwrap(current.receiverExpression ?: return current.text.substringAfterLast('.'))
                }
                is KtNameReferenceExpression -> return current.getReferencedName()
                else -> return current.text.substringAfterLast('.').substringBefore('(')
            }
        }
    }

    private fun isPathPatternExpression(expression: KtExpression): Boolean {
        val unwrapped = unwrap(expression)
        if (unwrapped is KtStringTemplateExpression) {
            return true
        }
        if (unwrapped !is KtNameReferenceExpression) {
            return false
        }
        return when (val resolved = unwrapped.references.firstNotNullOfOrNull { it.resolve() }) {
            is KtProperty ->
                unwrapOrNull(resolved.initializer) is KtStringTemplateExpression
            is PsiVariable -> {
                val typeName = resolved.type.canonicalText
                typeName == "java.lang.String" || typeName == "String"
            }
            else -> false
        }
    }

    private fun stringValue(expression: KtExpression): String? {
        val unwrapped = unwrap(expression)
        if (unwrapped is KtStringTemplateExpression && unwrapped.entries.size <= 1) {
            return unwrapped.entries.firstOrNull()?.text ?: unwrapped.text.trim('"')
        }
        return null
    }

    private fun highlightDecorateOrArgument(expression: KtExpression): PsiElement {
        val unwrapped = unwrapAndFollow(expression)
        val decorateCall = asCall(unwrapped)
        if (decorateCall != null && isHttpServiceDecorateCall(decorateCall)) {
            return decorateCall.calleeExpression ?: decorateCall
        }
        return highlightServiceArgument(expression)
    }

    private fun highlightServiceArgument(expression: KtExpression): PsiElement {
        val unwrapped = unwrap(expression)
        return (unwrapped as? KtNameReferenceExpression) ?: unwrapped
    }

    private fun highlightDecoratorArgument(call: KtCallExpression): PsiElement =
        call.valueArguments
            .firstOrNull()
            ?.getArgumentExpression()
            ?: call.calleeExpression
            ?: call

    private fun resolvedMethodClass(call: KtCallExpression): PsiClass? {
        val references =
            call.calleeExpression
                ?.references
                ?.toList()
                .orEmpty()
        for (reference in references) {
            val resolved = reference.resolve() as? PsiMethod ?: continue
            resolved.containingClass?.let { return it }
        }
        val parent = call.parent as? KtDotQualifiedExpression
        if (parent != null) {
            for (reference in parent.references) {
                val resolved = reference.resolve() as? PsiMethod ?: continue
                resolved.containingClass?.let { return it }
            }
        }
        return null
    }

    private fun resolvedClass(expression: KtExpression): PsiClass? {
        val unwrapped = unwrap(expression)
        asCall(unwrapped)?.let { return resolvedMethodClass(it) }
        if (unwrapped is KtNameReferenceExpression) {
            return resolvedType(unwrapped)
        }
        return null
    }

    private fun resolvedType(expression: KtNameReferenceExpression): PsiClass? =
        when (val resolved = expression.references.firstNotNullOfOrNull { it.resolve() }) {
            is PsiVariable -> (resolved.type as? PsiClassType)?.resolve()
            is KtProperty -> resolved.initializer?.let(::resolvedClass)
            is PsiClass -> resolved
            else -> null
        }

    private fun asCall(expression: KtExpression): KtCallExpression? =
        when (expression) {
            is KtCallExpression -> expression
            is KtDotQualifiedExpression -> expression.selectorExpression as? KtCallExpression
            else -> null
        }

    private fun chainReceiver(expression: KtExpression): KtExpression? {
        val receiver =
            when (expression) {
                is KtDotQualifiedExpression -> expression.receiverExpression
                is KtCallExpression -> {
                    val parent = expression.parent
                    if (parent is KtDotQualifiedExpression && parent.selectorExpression == expression) {
                        parent.receiverExpression
                    } else {
                        when (val callee = expression.calleeExpression) {
                            is KtDotQualifiedExpression -> callee.receiverExpression
                            else -> null
                        }
                    }
                }
                else -> null
            } ?: return null
        return unwrap(receiver)
    }

    private fun unwrapAndFollow(expression: KtExpression): KtExpression {
        val visited = mutableSetOf<PsiElement>()
        var current = unwrap(expression)
        while (current is KtNameReferenceExpression && visited.add(current)) {
            current =
                when (val resolved = current.references.firstNotNullOfOrNull { it.resolve() }) {
                    is KtProperty -> unwrap(resolved.initializer ?: return current)
                    is PsiVariable -> unwrap((resolved.initializer as? KtExpression) ?: return current)
                    else -> return current
                }
        }
        return current
    }

    private fun unwrapOrNull(expression: KtExpression?): KtExpression? = expression?.let(::unwrap)

    private fun unwrap(expression: KtExpression): KtExpression =
        ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: expression

    private data class ServiceRegistrationArgs(
        val serviceExpression: KtExpression,
        val hasExplicitPath: Boolean,
        val extraDecorators: List<KtExpression>,
        val routePath: String,
    )
}
