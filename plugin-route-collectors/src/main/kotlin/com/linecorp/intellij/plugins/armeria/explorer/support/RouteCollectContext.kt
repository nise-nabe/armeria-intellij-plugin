package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute

data class RouteCollectContext(
    val project: Project,
    val scope: GlobalSearchScope,
    val routes: MutableList<ArmeriaRoute>,
    val seenServiceRegistrations: MutableSet<String>,
    val seenConfigRoutes: MutableSet<String>,
    val fallbackScannedFiles: MutableSet<VirtualFile>,
    val registration: RouteRegistrationCallbacks,
    /** When true, this is a proto-only overlay pass; non-proto contributors must skip. */
    val includeProtoRoutes: Boolean = false,
)
