package com.linecorp.intellij.plugins.armeria.explorer.protocol
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRouteMetadata
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol

internal data class GraphqlOperation(
    val operationType: String,
    val fieldName: String,
)

internal object ArmeriaGraphqlRouteCollector {
    private val TYPE_HEADER_PATTERN =
        Regex("""(?:extend\s+)?type\s+(Query|Mutation|Subscription)\s*\{""")

    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
    ) {
        if (!ArmeriaIdlRouteSupport.isGraphqlOnClasspath(project, scope)) {
            return
        }
        val seenGraphqlRoutes = mutableSetOf<String>()
        // FilenameIndex iteration order is unstable; sort so first-wins dedupe is deterministic.
        val virtualFiles =
            GRAPHQL_EXTENSIONS
                .flatMap { extension -> FilenameIndex.getAllFilesByExt(project, extension, scope) }
                .sortedWith(compareBy({ it.path }, { it.name }))
        val psiManager = PsiManager.getInstance(project)
        for (virtualFile in virtualFiles) {
            val psiFile = psiManager.findFile(virtualFile) ?: continue
            val moduleName = ArmeriaRouteMetadata.moduleName(psiFile)
            for (operation in parseOperations(psiFile.text)) {
                val target = "${operation.operationType}.${operation.fieldName}"
                val dedupeKey =
                    "$moduleName:${ArmeriaIdlRouteSupport.DEFAULT_GRAPHQL_MOUNT_PATH}:$target"
                if (!seenGraphqlRoutes.add(dedupeKey)) {
                    continue
                }
                routes +=
                    ArmeriaRoute.create(
                        element = psiFile,
                        protocol = RouteProtocol.GRAPHQL.presentableName(),
                        httpMethod = "",
                        path = ArmeriaIdlRouteSupport.DEFAULT_GRAPHQL_MOUNT_PATH,
                        target = target,
                        routeMatch = RouteMatch.NON_HTTP,
                    )
            }
        }
    }

    internal fun parseOperations(schemaText: String): List<GraphqlOperation> {
        val normalized = ArmeriaIdlRouteSupport.stripComments(schemaText)
        val operations = mutableListOf<GraphqlOperation>()
        for (match in TYPE_HEADER_PATTERN.findAll(normalized)) {
            val operationType = match.groupValues[1]
            val openBraceIndex = normalized.indexOf('{', match.range.first)
            val body = ArmeriaIdlRouteSupport.extractBracedBody(normalized, openBraceIndex) ?: continue
            operations += parseFields(operationType, body)
        }
        return operations
    }

    private fun parseFields(
        operationType: String,
        body: String,
    ): List<GraphqlOperation> {
        val fields = mutableListOf<GraphqlOperation>()
        val lines = body.lineSequence().toList()
        var index = 0
        while (index < lines.size) {
            var line = lines[index].trim()
            if (line.isEmpty()) {
                index++
                continue
            }
            var parenthesisDepth = parenthesisDelta(line)
            index++
            while (parenthesisDepth > 0 && index < lines.size) {
                line += " " + lines[index].trim()
                parenthesisDepth += parenthesisDelta(lines[index])
                index++
            }
            val fieldMatch = FIELD_LINE_PATTERN.find(line.trim())
            if (fieldMatch != null) {
                fields += GraphqlOperation(operationType, fieldMatch.groupValues[1])
            }
        }
        return fields
    }

    private fun parenthesisDelta(line: String): Int {
        var delta = 0
        for (character in line) {
            when (character) {
                '(' -> delta++
                ')' ->
                    if (delta > 0) {
                        delta--
                    }
            }
        }
        return delta
    }

    private val FIELD_LINE_PATTERN = Regex("""^(\w+)\s*(?:\([^)]*\))?\s*:""")
    private val GRAPHQL_EXTENSIONS = listOf("graphql", "graphqls")
}
