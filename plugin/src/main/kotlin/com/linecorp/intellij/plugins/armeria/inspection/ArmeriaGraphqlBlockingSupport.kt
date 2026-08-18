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
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.searches.ReferencesSearch
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
        if (graphqlChainCalls(lambda) == null) {
            return false
        }
        return isDirectArgumentToDataFetcher(lambda)
    }

    fun hasBlockingTaskExecutor(element: PsiElement): Boolean {
        val chain = graphqlChainCalls(element) ?: return false
        return chain.any(::isUseBlockingTaskExecutorTrue)
    }

    fun hasBlockingDataFetcher(builderCall: PsiMethodCallExpression): Boolean {
        val outermost = outermostChainCall(builderCall)
        var found = false
        outermost.forEachDescendant { element ->
            when {
                found -> return@forEachDescendant
                element is PsiLambdaExpression && isGraphqlDataFetcherLambda(element) -> {
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
        return graphqlBuilderCoverages(file) { outermost -> chainReferencesClass(outermost, psiClass) }
    }

    private fun coveragesFromReferences(psiClass: PsiClass): List<Boolean> =
        try {
            val coverages = mutableListOf<Boolean>()
            ReferencesSearch.search(psiClass, psiClass.useScope).forEach { reference ->
                val chain = graphqlChainCalls(reference.element) ?: return@forEach
                coverages += chain.any(::isUseBlockingTaskExecutorTrue)
            }
            coverages
        } catch (_: IndexNotReadyException) {
            emptyList()
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
        outermost: PsiMethodCallExpression,
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
                        }
                    }
                }
            }
        }
        return found
    }

    private fun dataFetcherClassHasBlockingCall(psiClass: PsiClass): Boolean {
        if (ArmeriaMissingBlockingSupport.hasBlockingOrNonBlocking(psiClass)) {
            return false
        }
        return psiClass.methods.any { method ->
            ArmeriaMissingBlockingSupport.isDataFetcherGet(method) &&
                !ArmeriaMissingBlockingSupport.hasBlockingOrNonBlocking(method) &&
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
        var graphqlCall: PsiMethodCallExpression? = null
        while (current != null && current !is PsiMethod && current !is PsiClass && current !is PsiFile) {
            if (current is PsiMethodCallExpression && isGraphqlBuilderMethod(current)) {
                graphqlCall = current
                break
            }
            current = current.parent
        }
        val seed = graphqlCall ?: return null
        val builder = findGraphqlServiceBuilderCall(seed)
        val outermost = outermostChainCall(builder)
        return chainCalls(builder, outermost)
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

    private fun findGraphqlServiceBuilderCall(seed: PsiMethodCallExpression): PsiMethodCallExpression {
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
        return seed
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
