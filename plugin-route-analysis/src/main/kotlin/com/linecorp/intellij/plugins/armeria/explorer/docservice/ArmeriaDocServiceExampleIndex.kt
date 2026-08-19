package com.linecorp.intellij.plugins.armeria.explorer.docservice

/**
 * Examples collected from `DocServiceBuilder.exampleRequests` / `exampleHeaders`.
 *
 * [methodName] is null for service-level examples that apply to every method.
 */
data class ArmeriaDocServiceExampleKey(
    val serviceName: String,
    val methodName: String?,
)

data class ArmeriaDocServiceExampleIndex(
    val requestsByKey: Map<ArmeriaDocServiceExampleKey, List<String>>,
    val headersByKey: Map<ArmeriaDocServiceExampleKey, List<String>>,
) {
    fun isEmpty(): Boolean = requestsByKey.isEmpty() && headersByKey.isEmpty()

    fun requestsFor(ref: ArmeriaDocServiceMethodRef): List<String> = lookup(requestsByKey, ref)

    fun headersFor(ref: ArmeriaDocServiceMethodRef): List<String> = lookup(headersByKey, ref)

    private fun lookup(
        map: Map<ArmeriaDocServiceExampleKey, List<String>>,
        ref: ArmeriaDocServiceMethodRef,
    ): List<String> {
        val methodSpecific = valuesFor(map, ref.serviceName, ref.methodName)
        val serviceLevel = valuesFor(map, ref.serviceName, methodName = null)
        return (methodSpecific + serviceLevel).distinct()
    }

    private fun valuesFor(
        map: Map<ArmeriaDocServiceExampleKey, List<String>>,
        serviceName: String,
        methodName: String?,
    ): List<String> {
        map[ArmeriaDocServiceExampleKey(serviceName, methodName)]?.let { return it }
        val simple = simpleName(serviceName)
        if (simple != serviceName) {
            map[ArmeriaDocServiceExampleKey(simple, methodName)]?.let { return it }
        }
        return map.entries
            .filter { (key, _) -> key.methodName == methodName && serviceMatches(key.serviceName, serviceName) }
            .flatMap { it.value }
    }

    companion object {
        fun serviceMatches(
            exampleService: String,
            routeService: String,
        ): Boolean {
            if (exampleService == routeService) {
                return true
            }
            val exampleSimple = protobufServiceSimpleName(exampleService)
            val routeSimple = protobufServiceSimpleName(routeService)
            return exampleSimple.isNotEmpty() && exampleSimple == routeSimple
        }

        fun simpleName(serviceName: String): String = serviceName.substringAfterLast('.')

        private fun protobufServiceSimpleName(serviceName: String): String =
            simpleName(serviceName).removeSuffix("Grpc").removeSuffix("ImplBase")
    }

    class Builder {
        private val requests = mutableMapOf<ArmeriaDocServiceExampleKey, MutableList<String>>()
        private val headers = mutableMapOf<ArmeriaDocServiceExampleKey, MutableList<String>>()

        fun addRequests(
            serviceName: String,
            methodName: String?,
            values: List<String>,
        ) {
            add(requests, serviceName, methodName, values)
        }

        fun addHeaders(
            serviceName: String,
            methodName: String?,
            values: List<String>,
        ) {
            add(headers, serviceName, methodName, values)
        }

        fun build(): ArmeriaDocServiceExampleIndex =
            ArmeriaDocServiceExampleIndex(
                requestsByKey = requests.mapValues { it.value.distinct() },
                headersByKey = headers.mapValues { it.value.distinct() },
            )

        private fun add(
            map: MutableMap<ArmeriaDocServiceExampleKey, MutableList<String>>,
            serviceName: String,
            methodName: String?,
            values: List<String>,
        ) {
            val trimmedService = serviceName.trim()
            if (trimmedService.isEmpty()) {
                return
            }
            val trimmedMethod = methodName?.trim()?.takeIf { it.isNotEmpty() }
            val filtered = values.map { it.trim() }.filter { it.isNotEmpty() }
            if (filtered.isEmpty()) {
                return
            }
            map.getOrPut(ArmeriaDocServiceExampleKey(trimmedService, trimmedMethod)) { mutableListOf() } += filtered
        }
    }
}
