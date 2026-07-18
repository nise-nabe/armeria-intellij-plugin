package com.linecorp.intellij.plugins.armeria.explorer

object ArmeriaDocServiceMountResolver {
    private val DEFAULT_MOUNT_PATHS = listOf("/docs", "/internal/docs")

    fun candidateMountPaths(
        staticRoutes: List<ArmeriaRoute>,
        userMountPath: String?,
    ): List<String> {
        val candidates = linkedSetOf<String>()
        userMountPath?.takeIf { it.isNotBlank() }?.let {
            candidates += ArmeriaDocServiceEndpointValidator.normalizeMountPath(it)
        }
        staticRoutes
            .filter { it.isDocService }
            .map { ArmeriaDocServiceEndpointValidator.normalizeMountPath(it.path) }
            .forEach { candidates += it }
        DEFAULT_MOUNT_PATHS.forEach { candidates += it }
        return candidates.toList()
    }
}
