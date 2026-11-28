package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import org.junit.Test
import kotlin.test.assertEquals

class ArmeriaRuntimeStaticDiffTest {
    @Test
    fun summarize_matchesParameterizedShapesAcrossVariableNames() {
        val summary =
            ArmeriaRuntimeStaticDiff.summarize(
                staticRoutes = listOf(route(path = "/users/{id}", httpMethod = "GET", routeMatch = RouteMatch.ANNOTATED_HTTP)),
                runtimeRoutes = listOf(route(path = "/users/{name}", httpMethod = "GET", routeMatch = RouteMatch.RUNTIME)),
            )

        assertEquals(ArmeriaRuntimeStaticDiff.Summary(sharedCount = 1, runtimeOnlyCount = 0, staticOnlyCount = 0), summary)
    }

    @Test
    fun summarize_countsRuntimeOnlyAndStaticOnlyRoutes() {
        val summary =
            ArmeriaRuntimeStaticDiff.summarize(
                staticRoutes =
                    listOf(
                        route(path = "/static", httpMethod = "GET"),
                        route(path = "/users/{id}", httpMethod = "GET", routeMatch = RouteMatch.ANNOTATED_HTTP),
                    ),
                runtimeRoutes =
                    listOf(
                        route(path = "/users/{name}", httpMethod = "GET", routeMatch = RouteMatch.RUNTIME),
                        route(path = "/runtime", httpMethod = "GET", routeMatch = RouteMatch.RUNTIME),
                    ),
            )

        assertEquals(ArmeriaRuntimeStaticDiff.Summary(sharedCount = 1, runtimeOnlyCount = 1, staticOnlyCount = 1), summary)
    }

    @Test
    fun summarize_doesNotMatchDifferentMethods() {
        val summary =
            ArmeriaRuntimeStaticDiff.summarize(
                staticRoutes = listOf(route(path = "/users", httpMethod = "POST", routeMatch = RouteMatch.ANNOTATED_HTTP)),
                runtimeRoutes = listOf(route(path = "/users", httpMethod = "GET", routeMatch = RouteMatch.RUNTIME)),
            )

        assertEquals(ArmeriaRuntimeStaticDiff.Summary(sharedCount = 0, runtimeOnlyCount = 1, staticOnlyCount = 1), summary)
    }

    @Test
    fun summarize_methodAgnosticStaticRouteCoversRuntimeMethod() {
        val summary =
            ArmeriaRuntimeStaticDiff.summarize(
                staticRoutes = listOf(route(path = "/service", httpMethod = "")),
                runtimeRoutes = listOf(route(path = "/service", httpMethod = "POST", routeMatch = RouteMatch.RUNTIME)),
            )

        assertEquals(ArmeriaRuntimeStaticDiff.Summary(sharedCount = 1, runtimeOnlyCount = 0, staticOnlyCount = 0), summary)
    }

    @Test
    fun summarize_prefixStaticRouteCoversNestedRuntimeRoute() {
        val summary =
            ArmeriaRuntimeStaticDiff.summarize(
                staticRoutes = listOf(route(path = "/api", httpMethod = "", routeMatch = RouteMatch.SERVICE_UNDER)),
                runtimeRoutes = listOf(route(path = "/api/users", httpMethod = "GET", routeMatch = RouteMatch.RUNTIME)),
            )

        assertEquals(ArmeriaRuntimeStaticDiff.Summary(sharedCount = 1, runtimeOnlyCount = 0, staticOnlyCount = 0), summary)
    }

    @Test
    fun summarize_fileServiceStaticRouteCoversNestedRuntimeRoute() {
        val summary =
            ArmeriaRuntimeStaticDiff.summarize(
                staticRoutes = listOf(route(path = "/assets", httpMethod = "GET", routeMatch = RouteMatch.FILE_SERVICE)),
                runtimeRoutes = listOf(route(path = "/assets/style.css", httpMethod = "GET", routeMatch = RouteMatch.RUNTIME)),
            )

        assertEquals(ArmeriaRuntimeStaticDiff.Summary(sharedCount = 1, runtimeOnlyCount = 0, staticOnlyCount = 0), summary)
    }

    @Test
    fun summarize_globCatchAllStaticRouteCoversNestedRuntimeRoute() {
        val summary =
            ArmeriaRuntimeStaticDiff.summarize(
                staticRoutes =
                    listOf(
                        route(
                            path = "/assets/**",
                            httpMethod = "GET",
                            routeMatch = RouteMatch.SERVICE,
                            pathType = PathType.GLOB,
                        ),
                    ),
                runtimeRoutes = listOf(route(path = "/assets/style.css", httpMethod = "GET", routeMatch = RouteMatch.RUNTIME)),
            )

        assertEquals(ArmeriaRuntimeStaticDiff.Summary(sharedCount = 1, runtimeOnlyCount = 0, staticOnlyCount = 0), summary)
    }

    @Test
    fun summarize_matchesEquivalentTrailingSlashes() {
        val summary =
            ArmeriaRuntimeStaticDiff.summarize(
                staticRoutes = listOf(route(path = "/users/", httpMethod = "GET", routeMatch = RouteMatch.ANNOTATED_HTTP)),
                runtimeRoutes = listOf(route(path = "/users", httpMethod = "GET", routeMatch = RouteMatch.RUNTIME)),
            )

        assertEquals(ArmeriaRuntimeStaticDiff.Summary(sharedCount = 1, runtimeOnlyCount = 0, staticOnlyCount = 0), summary)
    }

    @Test
    fun summarize_preservesExplicitExactVariablesAsLiterals() {
        val summary =
            ArmeriaRuntimeStaticDiff.summarize(
                staticRoutes =
                    listOf(
                        route(
                            path = "/status/:ready",
                            httpMethod = "GET",
                            routeMatch = RouteMatch.ANNOTATED_HTTP,
                            explicitExactPath = true,
                        ),
                    ),
                runtimeRoutes = listOf(route(path = "/status/ok", httpMethod = "GET", routeMatch = RouteMatch.RUNTIME)),
            )

        assertEquals(ArmeriaRuntimeStaticDiff.Summary(sharedCount = 0, runtimeOnlyCount = 1, staticOnlyCount = 1), summary)
    }

    private fun route(
        path: String,
        httpMethod: String,
        routeMatch: RouteMatch = RouteMatch.SERVICE,
        explicitExactPath: Boolean = false,
        pathType: PathType = PathType.EXACT,
    ): ArmeriaRoute =
        ArmeriaRoute(
            protocol = "HTTP",
            httpMethod = httpMethod,
            path = path,
            target = "Handler",
            routeMatch = routeMatch,
            moduleName = "app",
            targetUnresolved = false,
            isDocService = false,
            decorators = emptyList(),
            exceptionHandlers = emptyList(),
            explicitExactPath = explicitExactPath,
            pathType = pathType,
            pointer = TestPsiPointer,
        )

    private object TestPsiPointer : SmartPsiElementPointer<PsiElement> {
        override fun getElement(): PsiElement? = null

        override fun getContainingFile(): PsiFile? = null

        override fun getRange(): TextRange? = null

        override fun getProject(): Project = throw UnsupportedOperationException()

        override fun getVirtualFile(): VirtualFile = throw UnsupportedOperationException()

        override fun getPsiRange(): TextRange? = null
    }
}
