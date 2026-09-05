package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKnownHttpServiceClassifier
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.KnownHttpServiceKind

internal object ArmeriaServerDecoratorSupport {
    fun findings(call: PsiMethodCallExpression): List<ArmeriaServerDecoratorFinding> {
        val findings = mutableListOf<ArmeriaServerDecoratorFinding>()
        collectDecoratorOrderFindings(call, findings)
        collectServiceRegistrationFindings(call, findings)
        return findings
    }

    private fun collectDecoratorOrderFindings(
        call: PsiMethodCallExpression,
        findings: MutableList<ArmeriaServerDecoratorFinding>,
    ) {
        if (isBuilderDecoratorCall(call)) {
            val entries =
                collectBuilderDecoratorCalls(call)
                    .mapNotNull(::parseBuilderDecoratorEntry)
                    .sortedBy { decoratorApplicationOffset(it.call) }
            val visitedEntry = entries.firstOrNull { it.call == call } ?: return
            val overlapping =
                entries.filter { entry ->
                    (
                        entry.kind == ArmeriaServerDecoratorKind.AUTH ||
                            entry.kind == ArmeriaServerDecoratorKind.LOGGING
                    ) &&
                        decoratorPathsOverlap(entry.pathPattern, visitedEntry.pathPattern)
                }
            addAuthAfterLogging(
                visited = call,
                kinds = overlapping.map { it.call to it.kind },
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
        call: PsiMethodCallExpression,
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
        visited: PsiMethodCallExpression,
        kinds: List<Pair<PsiMethodCallExpression, ArmeriaServerDecoratorKind?>>,
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
        expressions: List<PsiExpression>,
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

    private fun parseServiceRegistration(call: PsiMethodCallExpression): ServiceRegistrationArgs? {
        val methodName = call.methodExpression.referenceName ?: return null
        if (methodName != "service" && methodName != "serviceUnder") {
            return null
        }
        if (!looksLikeServerBuilderCall(call)) {
            return null
        }
        val arguments = call.argumentList.expressions
        if (arguments.isEmpty()) {
            return null
        }
        if (methodName == "serviceUnder") {
            val service = arguments.getOrNull(1) ?: return null
            return ServiceRegistrationArgs(
                serviceExpression = service,
                hasExplicitPath = true,
                extraDecorators = arguments.drop(2),
                routePath = stringValue(arguments[0]) ?: "/",
            )
        }
        return if (isPathPatternExpression(arguments[0])) {
            val service = arguments.getOrNull(1) ?: return null
            ServiceRegistrationArgs(
                serviceExpression = service,
                hasExplicitPath = true,
                extraDecorators = arguments.drop(2),
                routePath = stringValue(arguments[0]) ?: "/",
            )
        } else {
            ServiceRegistrationArgs(
                serviceExpression = arguments[0],
                hasExplicitPath = false,
                extraDecorators = arguments.drop(1),
                routePath = "/",
            )
        }
    }

    private fun hasCorsDecorator(
        serviceCall: PsiMethodCallExpression,
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

    private fun decorateChainHasCors(expression: PsiExpression): Boolean {
        var current: PsiExpression? = unwrapAndFollow(expression)
        val visited = mutableSetOf<PsiElement>()
        while (current is PsiMethodCallExpression && visited.add(current) && isHttpServiceDecorateCall(current)) {
            if (decoratorKind(current) == ArmeriaServerDecoratorKind.CORS) {
                return true
            }
            current = unwrapAndFollowOrNull(current.methodExpression.qualifierExpression)
        }
        return false
    }

    private fun builderHasCors(
        serviceCall: PsiMethodCallExpression,
        routePath: String,
    ): Boolean = collectBuilderDecoratorCalls(serviceCall).any { corsDecoratorApplies(it, routePath) }

    private fun collectBuilderDecoratorCalls(anchor: PsiMethodCallExpression): List<PsiMethodCallExpression> {
        val found = LinkedHashSet<PsiMethodCallExpression>()
        collectFluentBuilderDecoratorCalls(anchor, found)
        val builderVariable = resolveBuilderVariable(anchor) ?: return found.toList()
        val scope = builderSearchScope(anchor) ?: return found.toList()
        for (candidate in PsiTreeUtil.findChildrenOfType(scope, PsiMethodCallExpression::class.java)) {
            if (isBuilderDecoratorCall(candidate) && resolveBuilderVariable(candidate) == builderVariable) {
                found += candidate
            }
        }
        return found.toList()
    }

    private fun builderSearchScope(anchor: PsiMethodCallExpression): PsiElement? {
        PsiTreeUtil.getParentOfType(anchor, PsiMethod::class.java)?.body?.let { return it }
        PsiTreeUtil.getParentOfType(anchor, PsiLambdaExpression::class.java)?.body?.let { return it }
        return PsiTreeUtil.getParentOfType(anchor, PsiCodeBlock::class.java)
    }

    private fun collectFluentBuilderDecoratorCalls(
        anchor: PsiMethodCallExpression,
        found: MutableSet<PsiMethodCallExpression>,
    ) {
        var current: PsiExpression? = unwrapOrNull(anchor.methodExpression.qualifierExpression)
        val visited = mutableSetOf<PsiElement>()
        while (current != null && visited.add(current)) {
            when (current) {
                is PsiMethodCallExpression -> {
                    if (isBuilderDecoratorCall(current)) {
                        found += current
                    }
                    current = unwrapOrNull(current.methodExpression.qualifierExpression)
                }
                is PsiReferenceExpression -> {
                    val resolved = current.resolve()
                    current =
                        if (resolved is PsiVariable) {
                            unwrapOrNull(resolved.initializer)
                        } else {
                            null
                        }
                }
                else -> break
            }
        }
        var cursor: PsiExpression = anchor
        while (true) {
            val parent = enclosingQualifierCall(cursor) ?: break
            if (isBuilderDecoratorCall(parent)) {
                found += parent
            }
            cursor = parent
        }
        if (isBuilderDecoratorCall(anchor)) {
            found += anchor
        }
    }

    private fun resolveBuilderVariable(call: PsiMethodCallExpression): PsiVariable? {
        var current: PsiExpression? = unwrapOrNull(call.methodExpression.qualifierExpression)
        val visited = mutableSetOf<PsiElement>()
        while (current != null && visited.add(current)) {
            when (current) {
                is PsiReferenceExpression -> return current.resolve() as? PsiVariable
                is PsiMethodCallExpression -> current = unwrapOrNull(current.methodExpression.qualifierExpression)
                else -> return null
            }
        }
        return null
    }

    private fun corsDecoratorApplies(
        call: PsiMethodCallExpression,
        routePath: String,
    ): Boolean {
        val methodName = call.methodExpression.referenceName ?: return false
        if (methodName != "decorator" && methodName != "decoratorUnder") {
            return false
        }
        if (!looksLikeServerBuilderCall(call)) {
            return false
        }
        val arguments = call.argumentList.expressions
        val pathExpression: PsiExpression?
        val decoratorExpression: PsiExpression
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
        val path = stringValue(pathExpression) ?: return false
        return ArmeriaServerDecoratorTypes.corsDecoratorAppliesToRoute(path, routePath)
    }

    private fun isDecoratedServiceWithRoutes(expression: PsiExpression): Boolean {
        val unwrapped = unwrapAndFollow(expression)
        if (unwrapped !is PsiMethodCallExpression || !isHttpServiceDecorateCall(unwrapped)) {
            return false
        }
        val seed = decorateSeed(unwrapped) ?: return false
        return isServiceWithRoutes(seed)
    }

    private fun isGrpcService(expression: PsiExpression): Boolean = serviceKind(expression) == KnownHttpServiceKind.GRPC

    private fun isServiceWithRoutes(expression: PsiExpression): Boolean {
        if (isHttpServiceWithRoutesType(expression.type)) {
            return true
        }
        val kind = serviceKind(expression) ?: return false
        return ArmeriaServerDecoratorTypes.isServiceWithRoutesKind(kind)
    }

    private fun serviceKind(expression: PsiExpression): KnownHttpServiceKind? {
        val typeName = resolveServiceTypeName(expression, mutableSetOf()) ?: return null
        val kind = ArmeriaKnownHttpServiceClassifier.classify(typeName)
        return kind.takeIf { it != KnownHttpServiceKind.HTTP }
    }

    private fun resolveServiceTypeName(
        expression: PsiExpression,
        visited: MutableSet<PsiElement>,
    ): String? {
        val unwrapped = unwrap(expression)
        knownServiceTypeName(unwrapped.type)?.let { return it }
        return when (unwrapped) {
            is PsiMethodCallExpression -> {
                if (isHttpServiceDecorateCall(unwrapped)) {
                    unwrapped.methodExpression.qualifierExpression
                        ?.let { resolveServiceTypeName(it, visited) }
                        ?.let { return it }
                }
                val resolvedClass = unwrapped.resolveMethod()?.containingClass
                knownServiceTypeName(resolvedClass)?.let { return it }
                unwrapped.methodExpression.qualifierExpression?.let { resolveServiceTypeName(it, visited) }
            }
            is PsiReferenceExpression -> {
                if (!visited.add(unwrapped)) {
                    return null
                }
                when (val resolved = unwrapped.resolve()) {
                    is PsiVariable -> {
                        knownServiceTypeName(resolved.type)?.let { return it }
                        resolved.initializer?.let { resolveServiceTypeName(it, visited) }
                    }
                    is PsiClass -> knownServiceTypeName(resolved)
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun knownServiceTypeName(type: PsiType?): String? {
        val canonical = type?.canonicalText ?: return null
        val name = ArmeriaKnownHttpServiceClassifier.canonicalServiceTypeName(canonical)
        return name.takeIf { ArmeriaKnownHttpServiceClassifier.classify(it) != KnownHttpServiceKind.HTTP }
    }

    private fun knownServiceTypeName(psiClass: PsiClass?): String? {
        val qualified = psiClass?.qualifiedName ?: psiClass?.name ?: return null
        val name = ArmeriaKnownHttpServiceClassifier.canonicalServiceTypeName(qualified)
        return name.takeIf { ArmeriaKnownHttpServiceClassifier.classify(it) != KnownHttpServiceKind.HTTP }
    }

    private fun isHttpServiceWithRoutesType(type: PsiType?): Boolean {
        if (type == null) {
            return false
        }
        if (InheritanceUtil.isInheritor(type, ArmeriaServerDecoratorTypes.HTTP_SERVICE_WITH_ROUTES)) {
            return true
        }
        val psiClass = (type as? PsiClassType)?.resolve() ?: return false
        return psiClass.qualifiedName == ArmeriaServerDecoratorTypes.HTTP_SERVICE_WITH_ROUTES ||
            psiClass.name == "HttpServiceWithRoutes"
    }

    private fun decorateSeed(decorateCall: PsiMethodCallExpression): PsiExpression? {
        var current: PsiExpression? = decorateCall
        val visited = mutableSetOf<PsiElement>()
        while (current is PsiMethodCallExpression && visited.add(current) && isHttpServiceDecorateCall(current)) {
            current = unwrapAndFollowOrNull(current.methodExpression.qualifierExpression)
        }
        return current
    }

    private fun isGlobalBuilderDecoratorCall(call: PsiMethodCallExpression): Boolean =
        isBuilderDecoratorCall(call) &&
            call.methodExpression.referenceName == "decorator" &&
            call.argumentList.expressionCount == 1

    private fun decoratorApplicationOffset(call: PsiMethodCallExpression): Int =
        call.methodExpression.referenceNameElement?.textOffset ?: call.textOffset

    private fun isBuilderDecoratorCall(call: PsiMethodCallExpression): Boolean {
        val name = call.methodExpression.referenceName ?: return false
        if (name != "decorator" && name != "decoratorUnder") {
            return false
        }
        return looksLikeServerBuilderCall(call)
    }

    private fun looksLikeServerBuilderCall(expression: PsiMethodCallExpression): Boolean {
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
        if (resolvedClass?.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX) == true) {
            return true
        }
        val qualifierText = expression.methodExpression.qualifierExpression?.text ?: return false
        return ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(qualifierText)
    }

    private fun isHttpServiceDecorateCall(call: PsiMethodCallExpression): Boolean {
        if (call.methodExpression.referenceName != "decorate") {
            return false
        }
        val resolvedClass = call.resolveMethod()?.containingClass?.qualifiedName
        if (resolvedClass != null) {
            return isArmeriaHttpServiceTypeName(resolvedClass)
        }
        val qualifier = call.methodExpression.qualifierExpression ?: return false
        if (ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(qualifier.text)) {
            return false
        }
        return isHttpServiceType(qualifier.type) || isServiceWithRoutes(qualifier)
    }

    private fun isArmeriaHttpServiceTypeName(qualifiedName: String): Boolean {
        if (qualifiedName == ArmeriaRouteSupport.SERVER_BUILDER_CLASS) {
            return false
        }
        return qualifiedName == ArmeriaServerDecoratorTypes.HTTP_SERVICE ||
            qualifiedName == ArmeriaServerDecoratorTypes.HTTP_SERVICE_WITH_ROUTES ||
            qualifiedName.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX)
    }

    private fun isHttpServiceType(type: PsiType?): Boolean {
        if (type == null) {
            return false
        }
        return InheritanceUtil.isInheritor(type, ArmeriaServerDecoratorTypes.HTTP_SERVICE) ||
            type.canonicalText == ArmeriaServerDecoratorTypes.HTTP_SERVICE
    }

    private fun httpServiceDecorateCalls(call: PsiMethodCallExpression): List<PsiMethodCallExpression> {
        val preceding = mutableListOf<PsiMethodCallExpression>()
        val visited = mutableSetOf<PsiElement>()
        var current: PsiExpression? = unwrapOrNull(call.methodExpression.qualifierExpression)
        while (current != null && visited.add(current)) {
            when (current) {
                is PsiMethodCallExpression -> {
                    if (isHttpServiceDecorateCall(current)) {
                        preceding += current
                    }
                    current = unwrapOrNull(current.methodExpression.qualifierExpression)
                }
                is PsiReferenceExpression -> {
                    val resolved = current.resolve()
                    current =
                        if (resolved is PsiVariable) {
                            unwrapOrNull(resolved.initializer)
                        } else {
                            null
                        }
                }
                else -> break
            }
        }
        val following = mutableListOf<PsiMethodCallExpression>()
        var cursor: PsiExpression = call
        while (true) {
            val parent = enclosingQualifierCall(cursor) ?: break
            if (isHttpServiceDecorateCall(parent)) {
                following += parent
            }
            cursor = parent
        }
        return preceding.asReversed() + call + following
    }

    private fun enclosingQualifierCall(expression: PsiExpression): PsiMethodCallExpression? {
        var element: PsiElement? = expression.parent
        while (element != null) {
            if (element is PsiParenthesizedExpression || element is PsiTypeCastExpression) {
                element = element.parent
                continue
            }
            if (element is PsiMethodCallExpression) {
                val qualifier = unwrapOrNull(element.methodExpression.qualifierExpression)
                if (qualifier == unwrap(expression)) {
                    return element
                }
            }
            element = element.parent
        }
        return null
    }

    private fun decoratorKind(call: PsiMethodCallExpression): ArmeriaServerDecoratorKind? =
        parseBuilderDecoratorEntry(call)?.kind
            ?: call.argumentList.expressions
                .firstOrNull()
                ?.let(::decoratorKindFromExpression)

    private data class BuilderDecoratorEntry(
        val call: PsiMethodCallExpression,
        val kind: ArmeriaServerDecoratorKind?,
        val pathPattern: String?,
        val decoratorExpression: PsiExpression,
    )

    private fun parseBuilderDecoratorEntry(call: PsiMethodCallExpression): BuilderDecoratorEntry? {
        if (!isBuilderDecoratorCall(call)) {
            return null
        }
        val methodName = call.methodExpression.referenceName ?: return null
        val arguments = call.argumentList.expressions
        val pathExpression: PsiExpression?
        val decoratorExpression: PsiExpression
        when {
            methodName == "decoratorUnder" -> {
                pathExpression = arguments.getOrNull(0)
                decoratorExpression = arguments.getOrNull(1) ?: return null
            }
            arguments.size >= 2 -> {
                pathExpression = arguments[0]
                decoratorExpression = arguments[1]
            }
            arguments.isNotEmpty() -> {
                pathExpression = null
                decoratorExpression = arguments[0]
            }
            else -> return null
        }
        val pathPattern =
            if (pathExpression == null) {
                null
            } else {
                stringValue(pathExpression) ?: return null
            }
        return BuilderDecoratorEntry(
            call = call,
            kind = decoratorKindFromExpression(decoratorExpression),
            pathPattern = pathPattern,
            decoratorExpression = decoratorExpression,
        )
    }

    private fun decoratorPathsOverlap(
        left: String?,
        right: String?,
    ): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) {
            return true
        }
        return ArmeriaServerDecoratorTypes.corsDecoratorAppliesToRoute(left, right) ||
            ArmeriaServerDecoratorTypes.corsDecoratorAppliesToRoute(right, left)
    }

    private fun decoratorKindFromExpression(expression: PsiExpression): ArmeriaServerDecoratorKind? =
        ArmeriaServerDecoratorKind.fromSimpleName(decoratorClassSimpleName(expression))

    private fun decoratorClassSimpleName(expression: PsiExpression): String {
        var current: PsiExpression = unwrap(expression)
        while (current is PsiMethodCallExpression) {
            val qualifier = current.methodExpression.qualifierExpression
            current =
                if (qualifier != null) {
                    unwrap(qualifier)
                } else {
                    return current.methodExpression.referenceName.orEmpty()
                }
        }
        return when (current) {
            is PsiClassObjectAccessExpression ->
                current.operand.type.presentableText
                    .substringAfterLast('.')
            is PsiReferenceExpression -> current.referenceName.orEmpty()
            is PsiMethodCallExpression -> current.methodExpression.referenceName.orEmpty()
            else -> current.text.substringAfterLast('.').substringBefore('(')
        }
    }

    private fun isPathPatternExpression(expression: PsiExpression): Boolean {
        val unwrapped = unwrap(expression)
        if (unwrapped is PsiLiteralExpression && unwrapped.value is String) {
            return true
        }
        val typeName = unwrapped.type?.canonicalText ?: return false
        return typeName == "java.lang.String" || typeName == "String"
    }

    private fun stringValue(expression: PsiExpression): String? = ArmeriaRouteSupport.extractJavaStringConstant(expression)

    private fun highlightDecorateOrArgument(expression: PsiExpression): PsiElement {
        val unwrapped = unwrap(expression)
        if (unwrapped is PsiMethodCallExpression && isHttpServiceDecorateCall(unwrapped)) {
            return unwrapped.methodExpression.referenceNameElement ?: unwrapped
        }
        return highlightServiceArgument(expression)
    }

    private fun highlightServiceArgument(expression: PsiExpression): PsiElement =
        (unwrap(expression) as? PsiReferenceExpression)?.referenceNameElement ?: unwrap(expression)

    private fun highlightDecoratorArgument(call: PsiMethodCallExpression): PsiElement {
        val parsed = parseBuilderDecoratorEntry(call)
        if (parsed != null) {
            return parsed.decoratorExpression
        }
        return call.argumentList.expressions.firstOrNull()
            ?: call.methodExpression.referenceNameElement
            ?: call
    }

    private fun unwrapAndFollow(expression: PsiExpression): PsiExpression {
        val visited = mutableSetOf<PsiElement>()
        var current = unwrap(expression)
        while (current is PsiReferenceExpression && visited.add(current)) {
            val resolved = current.resolve() as? PsiVariable ?: return current
            current = unwrap(resolved.initializer ?: return current)
        }
        return current
    }

    private fun unwrapAndFollowOrNull(expression: PsiExpression?): PsiExpression? = expression?.let(::unwrapAndFollow)

    private fun unwrapOrNull(expression: PsiExpression?): PsiExpression? = expression?.let(::unwrap)

    private fun unwrap(expression: PsiExpression): PsiExpression {
        var current = expression
        while (true) {
            current =
                when (current) {
                    is PsiParenthesizedExpression -> current.expression ?: return current
                    is PsiTypeCastExpression -> current.operand ?: return current
                    else -> return current
                }
        }
    }

    private data class ServiceRegistrationArgs(
        val serviceExpression: PsiExpression,
        val hasExplicitPath: Boolean,
        val extraDecorators: List<PsiExpression>,
        val routePath: String,
    )
}
