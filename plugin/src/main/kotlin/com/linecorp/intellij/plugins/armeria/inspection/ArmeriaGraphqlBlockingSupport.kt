package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant

internal enum class GraphqlBlockingCoverage {
    ALL_BLOCKING_EXECUTOR,
    HAS_EVENT_LOOP_REGISTRATION,
    NOT_REGISTERED,
}

internal object ArmeriaGraphqlBlockingSupport {
    const val GRAPHQL_SERVICE_CLASS = "com.linecorp.armeria.server.graphql.GraphqlService"
    const val GRAPHQL_SERVICE_BUILDER_CLASS = "com.linecorp.armeria.server.graphql.GraphqlServiceBuilder"
    const val DATA_FETCHER_CLASS = "graphql.schema.DataFetcher"
    private const val BUILDER_METHOD = "builder"
    private const val USE_BLOCKING_TASK_EXECUTOR = "useBlockingTaskExecutor"
    private val DATA_FETCHER_METHODS = setOf("dataFetcher", "dataFetchers")
    private val GRAPHQL_BUILDER_METHODS = setOf("runtimeWiring", "useBlockingTaskExecutor", "graphql")

    fun isGraphqlServiceBuilderCall(call: PsiMethodCallExpression): Boolean {
        if (call.methodExpression.referenceName != BUILDER_METHOD) {
            return false
        }
        val resolved = call.resolveMethod()?.containingClass?.qualifiedName
        if (resolved == GRAPHQL_SERVICE_CLASS || resolved == GRAPHQL_SERVICE_BUILDER_CLASS) {
            return true
        }
        val qualifier = unwrap(call.methodExpression.qualifierExpression) ?: return false
        val qualifierText = qualifier.text
        return qualifierText == "GraphqlService" || qualifierText.endsWith(".GraphqlService")
    }

    fun isGraphqlDataFetcherLambda(lambda: PsiLambdaExpression): Boolean {
        if (!isDirectArgumentToDataFetcher(lambda)) {
            return false
        }
        if (graphqlChainCalls(lambda) != null) {
            return true
        }
        return flowsIntoGraphqlChain(lambda)
    }

    fun hasBlockingTaskExecutor(element: PsiElement): Boolean {
        val chain = graphqlChainCalls(element) ?: return false
        return chain.any(::isUseBlockingTaskExecutorTrue)
    }

    /** First `useBlockingTaskExecutor(...)` in the GraphqlService builder chain, if any. */
    fun findUseBlockingTaskExecutorCall(element: PsiElement): PsiMethodCallExpression? {
        val chain = graphqlChainCalls(element) ?: return null
        return chain.firstOrNull { it.methodExpression.referenceName == USE_BLOCKING_TASK_EXECUTOR }
    }

    fun hasBlockingDataFetcher(builderCall: PsiMethodCallExpression): Boolean {
        val outermost = outermostChainCall(builderCall)
        if (containsBlockingFetcher(outermost)) {
            return true
        }
        return externalWiringRoots(outermost).any(::containsBlockingFetcher)
    }

    private fun containsBlockingFetcher(root: PsiElement): Boolean {
        var found = false
        root.forEachDescendant { element ->
            when {
                found -> return@forEachDescendant
                element is PsiLambdaExpression && isDirectArgumentToDataFetcher(element) -> {
                    val body = element.body ?: return@forEachDescendant
                    if (ArmeriaMissingBlockingSupport.findingsIn(body, element).isNotEmpty()) {
                        found = true
                    }
                }
                element is PsiAnonymousClass && ArmeriaMissingBlockingSupport.isDataFetcherClass(element) -> {
                    if (dataFetcherClassHasBlockingCall(element)) {
                        found = true
                    }
                }
                element is PsiNewExpression && element.anonymousClass == null -> {
                    val cls = element.classOrAnonymousClassReference?.resolve() as? PsiClass
                    if (cls != null &&
                        ArmeriaMissingBlockingSupport.isDataFetcherClass(cls) &&
                        dataFetcherClassHasBlockingCall(cls)
                    ) {
                        found = true
                    }
                }
                element is PsiMethodCallExpression &&
                    element.methodExpression.referenceName in DATA_FETCHER_METHODS -> {
                    if (dataFetcherCallHasBlockingTarget(element)) {
                        found = true
                    }
                }
            }
        }
        return found
    }

    fun blockingExecutorCovers(psiClass: PsiClass): GraphqlBlockingCoverage {
        if (psiClass is PsiAnonymousClass) {
            val chain = graphqlChainCalls(psiClass) ?: return GraphqlBlockingCoverage.NOT_REGISTERED
            return if (chain.any(::isUseBlockingTaskExecutorTrue)) {
                GraphqlBlockingCoverage.ALL_BLOCKING_EXECUTOR
            } else {
                GraphqlBlockingCoverage.HAS_EVENT_LOOP_REGISTRATION
            }
        }
        val sameFile = coveragesInFile(psiClass)
        if (sameFile.isNotEmpty()) {
            return reduce(sameFile)
        }
        val searched = coveragesFromReferences(psiClass)
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

    private fun coveragesInFile(psiClass: PsiClass): List<Boolean> {
        val file = psiClass.containingFile ?: return emptyList()
        val precise =
            graphqlBuilderCoverages(file) { outermost ->
                val roots = listOf(outermost) + externalWiringRoots(outermost)
                roots.any { root ->
                    chainReferencesClass(root, psiClass) || chainRegistersDataFetcherClass(root, psiClass)
                }
            }
        if (precise.isNotEmpty()) {
            return precise
        }
        return emptyList()
    }

    private fun chainRegistersDataFetcherClass(
        outermost: PsiElement,
        psiClass: PsiClass,
    ): Boolean {
        var found = false
        outermost.forEachDescendant { element ->
            if (found) {
                return@forEachDescendant
            }
            val call = element as? PsiMethodCallExpression ?: return@forEachDescendant
            if (call.methodExpression.referenceName !in DATA_FETCHER_METHODS) {
                return@forEachDescendant
            }
            found =
                call.argumentList.expressions.any { argument ->
                    resolveDataFetcherClass(argument) == psiClass
                }
        }
        return found
    }

    private fun coveragesFromReferences(psiClass: PsiClass): List<Boolean> =
        try {
            val coverages = mutableListOf<Boolean>()
            val searched = mutableSetOf<PsiElement>()
            val queue = ArrayDeque<PsiElement>()
            queue.add(psiClass)
            while (queue.isNotEmpty()) {
                val holder = queue.removeFirst()
                if (!searched.add(holder)) {
                    continue
                }
                ReferencesSearch.search(holder, holder.useScope).forEach { reference ->
                    val chain = graphqlChainCalls(reference.element)
                    if (chain != null) {
                        coverages += chain.any(::isUseBlockingTaskExecutorTrue)
                    } else {
                        enclosingWiringHolder(reference.element)?.let(queue::add)
                    }
                }
            }
            coverages
        } catch (_: IndexNotReadyException) {
            emptyList()
        }

    /**
     * Nearest local variable, field, or method that owns [element]. Used to follow a DataFetcher
     * reference outward when the registration happens inside extracted wiring helpers.
     */
    private fun enclosingWiringHolder(element: PsiElement): PsiElement? =
        PsiTreeUtil.getParentOfType(element, PsiVariable::class.java, PsiMethod::class.java)

    /**
     * Whether [element] sits inside a variable/method whose value flows into a
     * `GraphqlService.builder()` chain (e.g. a `RuntimeWiring` helper passed to `runtimeWiring`).
     */
    private fun flowsIntoGraphqlChain(element: PsiElement): Boolean =
        try {
            val searched = mutableSetOf<PsiElement>()
            val queue = ArrayDeque<PsiElement>()
            enclosingWiringHolder(element)?.let(queue::add)
            while (queue.isNotEmpty()) {
                val holder = queue.removeFirst()
                if (!searched.add(holder)) {
                    continue
                }
                for (reference in ReferencesSearch.search(holder, holder.useScope)) {
                    if (graphqlChainCalls(reference.element) != null) {
                        return true
                    }
                    enclosingWiringHolder(reference.element)?.let(queue::add)
                }
            }
            false
        } catch (_: IndexNotReadyException) {
            false
        }

    /**
     * Subtrees outside [seed]'s own PSI tree that may still contain `dataFetcher(...)`
     * registrations: bodies of user helper methods, method references such as `this::configure`,
     * and initializers of variables passed into the builder chain.
     */
    private fun externalWiringRoots(seed: PsiElement): List<PsiElement> {
        val visited = mutableSetOf<PsiElement>()
        val roots = mutableListOf<PsiElement>()
        val queue = ArrayDeque<PsiElement>()
        queue.add(seed)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) {
                continue
            }
            if (current !== seed) {
                roots += current
            }
            enqueueExternalSources(current, queue)
        }
        return roots
    }

    private fun enqueueExternalSources(
        element: PsiElement,
        queue: ArrayDeque<PsiElement>,
    ) {
        externalSource(element, queue)
        element.forEachDescendant { externalSource(it, queue) }
    }

    private fun externalSource(
        element: PsiElement,
        queue: ArrayDeque<PsiElement>,
    ) {
        when (element) {
            is PsiMethodReferenceExpression ->
                (element.resolve() as? PsiMethod)?.body?.let(queue::add)
            is PsiMethodCallExpression ->
                element
                    .resolveMethod()
                    ?.takeIf(::isUserWiringMethod)
                    ?.body
                    ?.let(queue::add)
            is PsiReferenceExpression ->
                (element.resolve() as? PsiVariable)?.initializer?.let(queue::add)
        }
    }

    /**
     * Helper methods that may carry `dataFetcher(...)` registrations. Library methods
     * (graphql-java / Armeria / JDK) are already covered by scanning the call's own subtree.
     */
    private fun isUserWiringMethod(method: PsiMethod): Boolean {
        if (method.isConstructor) {
            return false
        }
        val qualifiedName = method.containingClass?.qualifiedName ?: return false
        return !qualifiedName.startsWith("graphql.") &&
            !qualifiedName.startsWith("com.linecorp.armeria.") &&
            !qualifiedName.startsWith("java.")
    }

    private fun graphqlBuilderCoverages(
        file: PsiFile,
        matches: (PsiMethodCallExpression) -> Boolean,
    ): List<Boolean> {
        val coverages = mutableListOf<Boolean>()
        file.forEachDescendant { element ->
            val call = element as? PsiMethodCallExpression ?: return@forEachDescendant
            if (!isGraphqlServiceBuilderCall(call)) {
                return@forEachDescendant
            }
            val outermost = outermostChainCall(call)
            if (!matches(outermost)) {
                return@forEachDescendant
            }
            val chain = chainCalls(call, outermost)
            coverages += chain.any(::isUseBlockingTaskExecutorTrue)
        }
        return coverages
    }

    private fun chainReferencesClass(
        outermost: PsiElement,
        psiClass: PsiClass,
    ): Boolean {
        val className = psiClass.name ?: return false
        var found = false
        outermost.forEachDescendant { element ->
            if (found) {
                return@forEachDescendant
            }
            when (element) {
                is PsiNewExpression -> {
                    val resolved = element.classOrAnonymousClassReference?.resolve()
                    if (resolved == psiClass ||
                        (resolved == null && element.classReference?.referenceName == className)
                    ) {
                        found = true
                    }
                }
                is PsiReferenceExpression -> {
                    when (val resolved = element.resolve()) {
                        psiClass -> found = true
                        is PsiVariable -> {
                            val typeClass = (resolved.type as? PsiClassType)?.resolve()
                            if (typeClass == psiClass) {
                                found = true
                            }
                            val typeText = resolved.type.canonicalText
                            if (typeText == className || typeText.endsWith(".$className")) {
                                found = true
                            }
                            val initializer = resolved.initializer as? PsiNewExpression
                            if (initializer?.classOrAnonymousClassReference?.resolve() == psiClass ||
                                initializer?.classReference?.referenceName == className
                            ) {
                                found = true
                            }
                        }
                    }
                }
            }
        }
        return found
    }

    private fun dataFetcherCallHasBlockingTarget(call: PsiMethodCallExpression): Boolean =
        call.argumentList.expressions.any { argument ->
            val cls = resolveDataFetcherClass(argument) ?: return@any false
            dataFetcherClassHasBlockingCall(cls)
        }

    private fun resolveDataFetcherClass(expression: PsiExpression): PsiClass? = resolveDataFetcherClass(expression, mutableSetOf())

    private fun resolveDataFetcherClass(
        expression: PsiExpression,
        visited: MutableSet<PsiElement>,
    ): PsiClass? {
        val unwrapped = unwrap(expression) ?: return null
        if (!visited.add(unwrapped)) {
            return null
        }
        return when (unwrapped) {
            is PsiNewExpression -> resolveFromNewExpression(unwrapped)
            is PsiReferenceExpression -> resolveFromReference(unwrapped, visited)
            else -> null
        }
    }

    private fun resolveFromNewExpression(expression: PsiNewExpression): PsiClass? {
        expression.anonymousClass
            ?.takeIf { ArmeriaMissingBlockingSupport.isDataFetcherClass(it) }
            ?.let { return it }
        val resolved = expression.classOrAnonymousClassReference?.resolve() as? PsiClass
        if (resolved != null && ArmeriaMissingBlockingSupport.isDataFetcherClass(resolved)) {
            return resolved
        }
        val name = expression.classReference?.referenceName ?: return null
        return findDataFetcherClassInFile(expression, name)
    }

    private fun resolveFromReference(
        expression: PsiReferenceExpression,
        visited: MutableSet<PsiElement>,
    ): PsiClass? {
        when (val resolved = expression.resolve()) {
            is PsiClass ->
                return resolved.takeIf { ArmeriaMissingBlockingSupport.isDataFetcherClass(it) }
            is PsiVariable -> {
                resolved.initializer?.let { resolveDataFetcherClass(it, visited) }?.let { return it }
                val typeClass = (resolved.type as? PsiClassType)?.resolve()
                if (typeClass != null &&
                    ArmeriaMissingBlockingSupport.isDataFetcherClass(typeClass) &&
                    typeClass.qualifiedName != DATA_FETCHER_CLASS
                ) {
                    return typeClass
                }
                val typeName =
                    resolved.type.canonicalText
                        .substringBefore('<')
                        .substringAfterLast('.')
                return findDataFetcherClassInFile(expression, typeName)
            }
        }
        val name = expression.referenceName ?: return null
        return findVariableDataFetcherClass(expression, name, visited)
    }

    private fun findVariableDataFetcherClass(
        anchor: PsiElement,
        name: String,
        visited: MutableSet<PsiElement>,
    ): PsiClass? {
        val method = PsiTreeUtil.getParentOfType(anchor, PsiMethod::class.java) ?: return null
        val body = method.body ?: return null
        var found: PsiClass? = null
        body.forEachDescendant { element ->
            if (found != null) {
                return@forEachDescendant
            }
            val variable = element as? PsiVariable ?: return@forEachDescendant
            if (variable.name != name) {
                return@forEachDescendant
            }
            val initializer = variable.initializer ?: return@forEachDescendant
            found = resolveDataFetcherClass(initializer, visited)
        }
        return found
    }

    private fun findDataFetcherClassInFile(
        anchor: PsiElement,
        className: String,
    ): PsiClass? {
        if (className.isEmpty() || className == "DataFetcher") {
            return null
        }
        val file = anchor.containingFile ?: return null
        var found: PsiClass? = null
        file.forEachDescendant { element ->
            if (found != null) {
                return@forEachDescendant
            }
            val cls = element as? PsiClass ?: return@forEachDescendant
            if (cls.name == className && ArmeriaMissingBlockingSupport.isDataFetcherClass(cls)) {
                found = cls
            }
        }
        return found
    }

    private fun dataFetcherClassHasBlockingCall(psiClass: PsiClass): Boolean {
        if (ArmeriaMissingBlockingSupport.hasNonBlocking(psiClass)) {
            return false
        }
        return psiClass.methods.any { method ->
            ArmeriaMissingBlockingSupport.isDataFetcherGet(method) &&
                !ArmeriaMissingBlockingSupport.hasNonBlocking(method) &&
                ArmeriaMissingBlockingSupport.findings(method).isNotEmpty()
        }
    }

    private fun isDirectArgumentToDataFetcher(lambda: PsiLambdaExpression): Boolean {
        var current: PsiElement? = lambda.parent
        while (current is PsiParenthesizedExpression || current is PsiTypeCastExpression) {
            current = current.parent
        }
        val argumentList = current as? PsiExpressionList ?: return false
        val call = argumentList.parent as? PsiMethodCallExpression ?: return false
        return call.methodExpression.referenceName in DATA_FETCHER_METHODS
    }

    internal fun graphqlChainCalls(start: PsiElement): List<PsiMethodCallExpression>? {
        var current: PsiElement? =
            if (start is PsiClass || start is PsiMethod) {
                start.parent
            } else {
                start
            }
        while (current != null && current !is PsiMethod && current !is PsiClass && current !is PsiFile) {
            if (current is PsiMethodCallExpression && isGraphqlBuilderMethod(current)) {
                val builder = findGraphqlServiceBuilderCall(current)
                if (builder != null) {
                    val outermost = outermostChainCall(builder)
                    return chainCalls(builder, outermost)
                }
            }
            current = current.parent
        }
        return null
    }

    private fun isGraphqlBuilderMethod(call: PsiMethodCallExpression): Boolean {
        if (isGraphqlServiceBuilderCall(call)) {
            return true
        }
        val resolved = call.resolveMethod()?.containingClass?.qualifiedName
        if (resolved == GRAPHQL_SERVICE_CLASS || resolved == GRAPHQL_SERVICE_BUILDER_CLASS) {
            return true
        }
        return call.methodExpression.referenceName in GRAPHQL_BUILDER_METHODS
    }

    private fun findGraphqlServiceBuilderCall(seed: PsiMethodCallExpression): PsiMethodCallExpression? {
        var current: PsiExpression? = seed
        val visited = mutableSetOf<PsiElement>()
        while (current != null && visited.add(current)) {
            if (current is PsiMethodCallExpression && isGraphqlServiceBuilderCall(current)) {
                return current
            }
            current =
                if (current is PsiMethodCallExpression) {
                    unwrap(current.methodExpression.qualifierExpression)
                } else {
                    null
                }
        }
        return seed.takeIf(::isResolvedGraphqlBuilderCall)
    }

    private fun isResolvedGraphqlBuilderCall(call: PsiMethodCallExpression): Boolean {
        if (isGraphqlServiceBuilderCall(call)) {
            return true
        }
        val resolved = call.resolveMethod()?.containingClass?.qualifiedName
        return resolved == GRAPHQL_SERVICE_CLASS || resolved == GRAPHQL_SERVICE_BUILDER_CLASS
    }

    private fun chainCalls(
        builder: PsiMethodCallExpression,
        outermost: PsiMethodCallExpression,
    ): List<PsiMethodCallExpression> {
        val calls = mutableListOf<PsiMethodCallExpression>()
        var current = outermost
        val visited = mutableSetOf<PsiElement>()
        while (visited.add(current)) {
            calls += current
            if (current == builder) {
                break
            }
            val qualifier = unwrap(current.methodExpression.qualifierExpression) as? PsiMethodCallExpression ?: break
            current = qualifier
        }
        return calls
    }

    private fun outermostChainCall(call: PsiMethodCallExpression): PsiMethodCallExpression {
        var current = call
        val visited = mutableSetOf<PsiElement>()
        while (visited.add(current)) {
            val parentExpr = current.parent as? PsiReferenceExpression ?: break
            val parentCall = parentExpr.parent as? PsiMethodCallExpression ?: break
            if (parentCall.methodExpression != parentExpr) {
                break
            }
            current = parentCall
        }
        return current
    }

    private fun isUseBlockingTaskExecutorTrue(call: PsiMethodCallExpression): Boolean {
        if (call.methodExpression.referenceName != USE_BLOCKING_TASK_EXECUTOR) {
            return false
        }
        return isTrueBoolean(call.argumentList.expressions.firstOrNull())
    }

    private fun isTrueBoolean(expression: PsiExpression?): Boolean {
        val literal = unwrap(expression) as? PsiLiteralExpression ?: return false
        return literal.value == true || literal.text == "true"
    }

    private fun unwrap(expression: PsiExpression?): PsiExpression? {
        var current = expression
        while (true) {
            current =
                when (current) {
                    is PsiParenthesizedExpression -> current.expression
                    is PsiTypeCastExpression -> current.operand
                    else -> return current
                }
        }
    }
}
