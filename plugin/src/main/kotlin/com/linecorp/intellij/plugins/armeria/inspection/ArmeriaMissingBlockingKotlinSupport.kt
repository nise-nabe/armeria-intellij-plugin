package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry

internal object ArmeriaMissingBlockingKotlinSupport {
    private const val BUILDER_METHOD = "builder"
    private const val USE_BLOCKING_TASK_EXECUTOR = "useBlockingTaskExecutor"
    private val DATA_FETCHER_METHODS = setOf("dataFetcher", "dataFetchers", "DataFetcher")
    private val GRAPHQL_BUILDER_METHODS = setOf("runtimeWiring", "useBlockingTaskExecutor", "graphql")
    private val HTTP_SERVICE_HANDLER_METHODS =
        setOf(
            "serve",
            "doGet",
            "doPost",
            "doPut",
            "doDelete",
            "doHead",
            "doPatch",
            "doOptions",
            "doTrace",
            "doConnect",
            "doQuery",
        )

    fun shouldInspect(function: KtNamedFunction): Boolean = shouldInspect(function, ignoreClassBlocking = false)

    fun shouldInspect(
        function: KtNamedFunction,
        ignoreClassBlocking: Boolean,
    ): Boolean {
        if (hasBlockingOrNonBlocking(function.annotationEntries) ||
            (!ignoreClassBlocking && containingClass(function)?.annotationEntries?.let(::hasBlockingOrNonBlocking) == true)
        ) {
            return false
        }
        if (ArmeriaKotlinMethodRoute.from(function) != null) {
            return true
        }
        if (isGrpcServiceOverride(function) || isHttpServiceOverride(function)) {
            return true
        }
        return isEventLoopDataFetcher(function)
    }

    fun findings(function: KtNamedFunction): List<ArmeriaBlockingCallFinding> {
        val body = function.bodyExpression ?: return emptyList()
        return findingsIn(body, function)
    }

    fun findingsIn(
        scope: PsiElement,
        boundary: PsiElement,
    ): List<ArmeriaBlockingCallFinding> {
        val findings = mutableListOf<ArmeriaBlockingCallFinding>()
        scope.forEachDescendant { element ->
            val call = element as? KtCallExpression ?: return@forEachDescendant
            if (!isOnInspectedFunctionPath(boundary, call)) {
                return@forEachDescendant
            }
            val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: return@forEachDescendant
            val resolved = resolvePsiMethod(call)
            val qualifierText =
                (call.parent as? KtDotQualifiedExpression)?.receiverExpression?.text
                    ?: (call.calleeExpression as? KtDotQualifiedExpression)?.receiverExpression?.text
            if (!ArmeriaBlockingCallPatterns.isBlockingCall(
                    methodName = methodName,
                    ownerFqn = resolved?.containingClass?.qualifiedName,
                    unresolved = resolved == null,
                    qualifierText = qualifierText,
                    argumentCount = call.valueArguments.size,
                )
            ) {
                return@forEachDescendant
            }
            findings +=
                ArmeriaBlockingCallFinding(
                    highlight = highlight(call),
                    methodName = methodName,
                )
        }
        return findings
    }

    fun quickFixes(function: KtNamedFunction): Array<LocalQuickFix> {
        if (!honorsBlockingAnnotation(function)) {
            return emptyArray()
        }
        val fixes =
            mutableListOf<LocalQuickFix>(
                ArmeriaAddBlockingAnnotationKotlinQuickFix.forFunction(function),
            )
        if (shouldOfferClassFix(function)) {
            containingClass(function)?.let { fixes += ArmeriaAddBlockingAnnotationKotlinQuickFix.forClass(it) }
        }
        return fixes.toTypedArray()
    }

    fun honorsBlockingAnnotation(function: KtNamedFunction): Boolean =
        ArmeriaKotlinMethodRoute.from(function) != null || isGrpcServiceOverride(function)

    fun isGraphqlServiceBuilderCall(call: KtCallExpression): Boolean {
        if (ArmeriaKotlinExpressionSupport.resolveCallName(call) != BUILDER_METHOD) {
            return false
        }
        val resolved = resolvePsiMethod(call)?.containingClass?.qualifiedName
        if (resolved == ArmeriaGraphqlBlockingSupport.GRAPHQL_SERVICE_CLASS ||
            resolved == ArmeriaGraphqlBlockingSupport.GRAPHQL_SERVICE_BUILDER_CLASS
        ) {
            return true
        }
        val receiverText = chainReceiver(call)?.text ?: return false
        return receiverText == "GraphqlService" || receiverText.endsWith(".GraphqlService")
    }

    fun isGraphqlDataFetcherLambda(lambda: KtLambdaExpression): Boolean {
        if (graphqlBuilderCall(lambda) == null) {
            return false
        }
        val call = enclosingCall(lambda) ?: return false
        val name = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: return false
        if (name !in DATA_FETCHER_METHODS) {
            return false
        }
        return isDirectLambdaArgument(lambda, call)
    }

    fun dataFetcherLambdas(call: KtCallExpression): List<KtLambdaExpression> {
        if (ArmeriaKotlinExpressionSupport.resolveCallName(call) !in DATA_FETCHER_METHODS) {
            return emptyList()
        }
        if (graphqlBuilderCall(call) == null) {
            return emptyList()
        }
        val lambdas = linkedSetOf<KtLambdaExpression>()
        call.lambdaArguments.mapNotNullTo(lambdas) { it.getLambdaExpression() }
        call.valueArguments.mapNotNullTo(lambdas) { it.getArgumentExpression() as? KtLambdaExpression }
        return lambdas.toList()
    }

    fun hasBlockingTaskExecutor(element: PsiElement): Boolean {
        val builder = graphqlBuilderCall(element) ?: return false
        return chainCalls(builder, outermostChainCall(builder)).any(::isUseBlockingTaskExecutorTrue)
    }

    fun hasBlockingDataFetcher(builderCall: KtCallExpression): Boolean {
        val root = chainRoot(outermostChainCall(builderCall))
        var found = false
        root.forEachDescendant { element ->
            if (found) {
                return@forEachDescendant
            }
            when {
                element is KtLambdaExpression && isGraphqlDataFetcherLambda(element) -> {
                    val body = element.bodyExpression ?: return@forEachDescendant
                    if (findingsIn(body, element).isNotEmpty()) {
                        found = true
                    }
                }
                element is KtObjectDeclaration && element.isObjectLiteral() -> {
                    if (isDataFetcherHierarchy(element) && dataFetcherClassHasBlockingCall(element)) {
                        found = true
                    }
                }
                element is KtCallExpression -> {
                    val className = ArmeriaKotlinExpressionSupport.resolveCallName(element) ?: return@forEachDescendant
                    val klass = resolveClassByName(element, className) ?: return@forEachDescendant
                    if (isDataFetcherType(klass) && dataFetcherClassHasBlockingCall(klass)) {
                        found = true
                    }
                }
            }
        }
        return found
    }

    fun highlight(call: KtCallExpression): PsiElement {
        val callee = call.calleeExpression
        if (callee is KtDotQualifiedExpression) {
            return callee.selectorExpression ?: callee
        }
        return callee ?: call
    }

    private fun shouldOfferClassFix(function: KtNamedFunction): Boolean {
        val klass = containingClass(function) ?: return false
        if (klass is KtObjectDeclaration && klass.isObjectLiteral()) {
            return false
        }
        if (hasBlockingOrNonBlocking(klass.annotationEntries)) {
            return false
        }
        val inspectable =
            klass.declarations.filterIsInstance<KtNamedFunction>().filter {
                shouldInspect(it, ignoreClassBlocking = true)
            }
        if (inspectable.size <= 1) {
            return false
        }
        return inspectable.all { findings(it).isNotEmpty() }
    }

    private fun isEventLoopDataFetcher(function: KtNamedFunction): Boolean {
        if (function.name != "get") {
            return false
        }
        val klass = containingClass(function) ?: return false
        if (!isDataFetcherHierarchy(klass)) {
            return false
        }
        return when (blockingExecutorCovers(klass)) {
            GraphqlBlockingCoverage.HAS_EVENT_LOOP_REGISTRATION -> true
            GraphqlBlockingCoverage.ALL_BLOCKING_EXECUTOR,
            GraphqlBlockingCoverage.NOT_REGISTERED,
            -> false
        }
    }

    private fun blockingExecutorCovers(klass: KtClassOrObject): GraphqlBlockingCoverage {
        if (klass is KtObjectDeclaration && klass.isObjectLiteral()) {
            val builder = graphqlBuilderCall(klass) ?: return GraphqlBlockingCoverage.NOT_REGISTERED
            val usesExecutor = chainCalls(builder, outermostChainCall(builder)).any(::isUseBlockingTaskExecutorTrue)
            return if (usesExecutor) {
                GraphqlBlockingCoverage.ALL_BLOCKING_EXECUTOR
            } else {
                GraphqlBlockingCoverage.HAS_EVENT_LOOP_REGISTRATION
            }
        }
        val sameFile = coveragesInFile(klass)
        if (sameFile.isNotEmpty()) {
            return reduce(sameFile)
        }
        val searched = coveragesFromReferences(klass)
        if (searched.isEmpty()) {
            return GraphqlBlockingCoverage.NOT_REGISTERED
        }
        return reduce(searched)
    }

    private fun reduce(coverages: List<Boolean>): GraphqlBlockingCoverage =
        if (coverages.all { it }) {
            GraphqlBlockingCoverage.ALL_BLOCKING_EXECUTOR
        } else {
            GraphqlBlockingCoverage.HAS_EVENT_LOOP_REGISTRATION
        }

    private fun coveragesInFile(klass: KtClassOrObject): List<Boolean> {
        val className = klass.name ?: return emptyList()
        val coverages = mutableListOf<Boolean>()
        klass.containingKtFile.forEachDescendant { element ->
            val call = element as? KtCallExpression ?: return@forEachDescendant
            if (!isGraphqlServiceBuilderCall(call)) {
                return@forEachDescendant
            }
            val outermost = outermostChainCall(call)
            if (!chainReferencesName(chainRoot(outermost), className)) {
                return@forEachDescendant
            }
            coverages += chainCalls(call, outermost).any(::isUseBlockingTaskExecutorTrue)
        }
        return coverages
    }

    private fun coveragesFromReferences(klass: KtClassOrObject): List<Boolean> =
        try {
            val coverages = mutableListOf<Boolean>()
            ReferencesSearch.search(klass, klass.useScope).forEach { reference ->
                val builder = graphqlBuilderCall(reference.element) ?: return@forEach
                coverages += chainCalls(builder, outermostChainCall(builder)).any(::isUseBlockingTaskExecutorTrue)
            }
            coverages
        } catch (_: IndexNotReadyException) {
            emptyList()
        }

    private fun chainReferencesName(
        root: PsiElement,
        className: String,
    ): Boolean {
        var found = false
        root.forEachDescendant { element ->
            if (found) {
                return@forEachDescendant
            }
            when (element) {
                is KtNameReferenceExpression -> {
                    if (element.getReferencedName() == className || refersToClass(element, className)) {
                        found = true
                    }
                }
                is KtCallExpression -> {
                    if (ArmeriaKotlinExpressionSupport.resolveCallName(element) == className) {
                        found = true
                    }
                }
            }
        }
        return found
    }

    private fun refersToClass(
        reference: KtNameReferenceExpression,
        className: String,
    ): Boolean =
        when (val resolved = reference.references.firstNotNullOfOrNull { it.resolve() }) {
            is PsiClass -> resolved.name == className
            is KtClassOrObject -> resolved.name == className
            is PsiVariable -> (resolved.type as? PsiClassType)?.resolve()?.name == className
            is KtProperty ->
                resolved.typeReference
                    ?.text
                    ?.substringBefore('<')
                    ?.trim() == className
            is KtParameter ->
                resolved.typeReference
                    ?.text
                    ?.substringBefore('<')
                    ?.trim() == className
            else -> false
        }

    private fun dataFetcherClassHasBlockingCall(klass: PsiElement): Boolean =
        when (klass) {
            is KtClassOrObject -> {
                if (hasBlockingOrNonBlocking(klass.annotationEntries)) {
                    false
                } else {
                    klass.declarations.filterIsInstance<KtNamedFunction>().any { function ->
                        function.name == "get" &&
                            !hasBlockingOrNonBlocking(function.annotationEntries) &&
                            findings(function).isNotEmpty()
                    }
                }
            }
            is PsiClass ->
                !ArmeriaMissingBlockingSupport.hasBlockingOrNonBlocking(klass) &&
                    klass.methods.any { method ->
                        ArmeriaMissingBlockingSupport.isDataFetcherGet(method) &&
                            !ArmeriaMissingBlockingSupport.hasBlockingOrNonBlocking(method) &&
                            ArmeriaMissingBlockingSupport.findings(method).isNotEmpty()
                    }
            else -> false
        }

    private fun isDirectLambdaArgument(
        lambda: KtLambdaExpression,
        call: KtCallExpression,
    ): Boolean {
        if (call.lambdaArguments.any { it.getLambdaExpression() == lambda }) {
            return true
        }
        return call.valueArguments.any { it.getArgumentExpression() == lambda }
    }

    private fun enclosingCall(lambda: KtLambdaExpression): KtCallExpression? {
        var current: PsiElement? = lambda.parent
        while (current != null && current !is KtNamedFunction && current !is KtClassOrObject) {
            if (current is KtCallExpression) {
                return current
            }
            current = current.parent
        }
        return null
    }

    private fun graphqlBuilderCall(start: PsiElement): KtCallExpression? {
        var current: PsiElement? = start
        while (current != null && current !is KtFile) {
            if (current is KtCallExpression && isGraphqlBuilderMethod(current)) {
                return findGraphqlServiceBuilderCall(current)
            }
            current = current.parent
        }
        return null
    }

    private fun isGraphqlBuilderMethod(call: KtCallExpression): Boolean {
        if (isGraphqlServiceBuilderCall(call)) {
            return true
        }
        val resolved = resolvePsiMethod(call)?.containingClass?.qualifiedName
        if (resolved == ArmeriaGraphqlBlockingSupport.GRAPHQL_SERVICE_CLASS ||
            resolved == ArmeriaGraphqlBlockingSupport.GRAPHQL_SERVICE_BUILDER_CLASS
        ) {
            return true
        }
        val name = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: return false
        return name in GRAPHQL_BUILDER_METHODS
    }

    private fun findGraphqlServiceBuilderCall(seed: KtCallExpression): KtCallExpression {
        var current: KtExpression? = seed
        val visited = mutableSetOf<PsiElement>()
        while (current != null && visited.add(current)) {
            val call = asCall(current)
            if (call != null && isGraphqlServiceBuilderCall(call)) {
                return call
            }
            current = chainReceiver(current)
        }
        return seed
    }

    private fun asCall(expression: KtExpression): KtCallExpression? =
        when (expression) {
            is KtCallExpression -> expression
            is KtDotQualifiedExpression -> expression.selectorExpression as? KtCallExpression
            else -> null
        }

    private fun chainRoot(call: KtCallExpression): PsiElement {
        var current: PsiElement = call
        while (current.parent is KtDotQualifiedExpression) {
            current = current.parent
        }
        return current
    }

    private fun outermostChainCall(call: KtCallExpression): KtCallExpression {
        var current = call
        val visited = mutableSetOf<PsiElement>()
        while (visited.add(current)) {
            current = nextChainedCall(current) ?: break
        }
        return current
    }

    private fun chainCalls(
        builder: KtCallExpression,
        outermost: KtCallExpression,
    ): List<KtCallExpression> {
        val calls = mutableListOf<KtCallExpression>()
        var current: KtCallExpression? = builder
        val visited = mutableSetOf<PsiElement>()
        while (current != null && visited.add(current)) {
            calls += current
            if (current == outermost) {
                break
            }
            current = nextChainedCall(current)
        }
        return calls
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
        return ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(receiver) ?: receiver
    }

    private fun isUseBlockingTaskExecutorTrue(call: KtCallExpression): Boolean {
        if (ArmeriaKotlinExpressionSupport.resolveCallName(call) != USE_BLOCKING_TASK_EXECUTOR) {
            return false
        }
        val argument = call.valueArguments.firstOrNull()?.getArgumentExpression() ?: return false
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(argument) ?: argument
        return unwrapped.text == "true"
    }

    private fun hasBlockingOrNonBlocking(entries: List<KtAnnotationEntry>): Boolean =
        entries.any { entry ->
            val name = ArmeriaKotlinAnnotationSupport.qualifiedName(entry)
            name == ArmeriaRouteSupport.BLOCKING_ANNOTATION ||
                name == ArmeriaRouteSupport.NON_BLOCKING_ANNOTATION
        }

    fun containingClass(function: KtNamedFunction): KtClassOrObject? = PsiTreeUtil.getParentOfType(function, KtClassOrObject::class.java)

    private fun isGrpcServiceOverride(function: KtNamedFunction): Boolean {
        if (!function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            return false
        }
        val klass = containingClass(function) ?: return false
        return isHierarchy(klass, ArmeriaMissingBlockingSupport::isGrpcServiceType) { name ->
            name.endsWith("ImplBase") || name == "BindableService"
        }
    }

    private fun isHttpServiceOverride(function: KtNamedFunction): Boolean {
        if (!function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            return false
        }
        val name = function.name ?: return false
        if (name !in HTTP_SERVICE_HANDLER_METHODS) {
            return false
        }
        val klass = containingClass(function) ?: return false
        return isHierarchy(klass, ArmeriaMissingBlockingSupport::isHttpServiceType) { typeName ->
            typeName == "HttpService" || typeName == "AbstractHttpService"
        }
    }

    private fun isDataFetcherHierarchy(root: PsiElement): Boolean =
        isHierarchy(root, ArmeriaMissingBlockingSupport::isDataFetcherClass) { name ->
            name == "DataFetcher"
        }

    private fun isHierarchy(
        root: PsiElement,
        javaMatch: (PsiClass) -> Boolean,
        kotlinNameMatch: (String) -> Boolean,
    ): Boolean {
        val visited = mutableSetOf<PsiElement>()
        val queue = ArrayDeque<PsiElement>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) {
                continue
            }
            when (current) {
                is KtClassOrObject -> {
                    if (current.name?.let(kotlinNameMatch) == true) {
                        return true
                    }
                    current.superTypeListEntries.forEach { entry ->
                        val referencedName = entry.typeAsUserType?.referencedName
                        if (referencedName != null && kotlinNameMatch(referencedName)) {
                            return true
                        }
                        resolveSuperType(entry, current)?.let { queue.add(it) }
                    }
                }
                is PsiClass -> {
                    if (javaMatch(current)) {
                        return true
                    }
                    current.supers.forEach { queue.add(it) }
                }
                is PsiMethod -> current.containingClass?.let { queue.add(it) }
                is KtConstructor<*> -> queue.add(current.getContainingClassOrObject())
            }
        }
        return false
    }

    private fun resolveSuperType(
        entry: KtSuperTypeListEntry,
        klass: KtClassOrObject,
    ): PsiElement? {
        entry.typeReference
            ?.references
            ?.firstNotNullOfOrNull { it.resolve() }
            ?.let { resolved -> asHierarchyType(resolved)?.let { return it } }
        val shortName = entry.typeAsUserType?.referencedName ?: return null
        val file = klass.containingKtFile
        val facade = JavaPsiFacade.getInstance(file.project)
        file.importDirectives
            .mapNotNull { it.importPath?.pathStr }
            .firstOrNull { it == shortName || it.endsWith(".$shortName") }
            ?.let { imported ->
                facade.findClass(imported, file.resolveScope)?.let { return it }
            }
        val pkg = file.packageFqName.asString()
        val fqn = if (pkg.isEmpty()) shortName else "$pkg.$shortName"
        return facade.findClass(fqn, file.resolveScope)
    }

    private fun asHierarchyType(element: PsiElement): PsiElement? =
        when (element) {
            is PsiClass, is KtClassOrObject -> element
            is PsiMethod -> element.containingClass
            is KtConstructor<*> -> element.getContainingClassOrObject()
            else -> null
        }

    private fun isOnInspectedFunctionPath(
        boundary: PsiElement,
        element: PsiElement,
    ): Boolean {
        if (element == boundary) {
            return true
        }
        var current: PsiElement? = element.parent
        while (current != null && current != boundary) {
            when (current) {
                is KtLambdaExpression, is KtClassOrObject, is KtNamedFunction -> return false
            }
            current = current.parent
        }
        return current == boundary
    }

    private fun resolvePsiMethod(call: KtCallExpression): PsiMethod? {
        val references =
            call.calleeExpression
                ?.references
                ?.toList()
                .orEmpty()
        for (reference in references) {
            val resolved = reference.resolve()
            if (resolved is PsiMethod) {
                return resolved
            }
        }
        return null
    }

    private fun resolveClassByName(
        call: KtCallExpression,
        className: String,
    ): PsiElement? {
        call.calleeExpression
            ?.references
            ?.firstNotNullOfOrNull { it.resolve() }
            ?.let { resolved -> asHierarchyType(resolved)?.let { return it } }
        val file = call.containingKtFile
        val facade = JavaPsiFacade.getInstance(file.project)
        file.importDirectives
            .mapNotNull { it.importPath?.pathStr }
            .firstOrNull { it == className || it.endsWith(".$className") }
            ?.let { imported ->
                facade.findClass(imported, file.resolveScope)?.let { return it }
            }
        file.declarations
            .filterIsInstance<KtClassOrObject>()
            .firstOrNull { it.name == className }
            ?.let { return it }
        val pkg = file.packageFqName.asString()
        val fqn = if (pkg.isEmpty()) className else "$pkg.$className"
        return facade.findClass(fqn, file.resolveScope)
    }

    private fun isDataFetcherType(element: PsiElement): Boolean =
        when (element) {
            is PsiClass -> ArmeriaMissingBlockingSupport.isDataFetcherClass(element)
            is KtClassOrObject -> isDataFetcherHierarchy(element)
            else -> false
        }
}
