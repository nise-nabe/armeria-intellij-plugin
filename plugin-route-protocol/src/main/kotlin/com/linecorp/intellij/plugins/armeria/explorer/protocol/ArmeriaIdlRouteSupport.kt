package com.linecorp.intellij.plugins.armeria.explorer.protocol
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope

internal object ArmeriaIdlRouteSupport {
    const val GRAPHQL_SERVICE_CLASS = "com.linecorp.armeria.server.graphql.GraphqlService"
    const val THRIFT_HTTP_SERVICE_CLASS = "com.linecorp.armeria.server.thrift.THttpService"
    const val DEFAULT_GRAPHQL_MOUNT_PATH = "/graphql"

    // Service classes live in library jars — resolve with allScope.
    fun isGraphqlOnClasspath(project: Project): Boolean =
        JavaPsiFacade.getInstance(project).findClass(GRAPHQL_SERVICE_CLASS, GlobalSearchScope.allScope(project)) != null

    fun isThriftOnClasspath(project: Project): Boolean =
        JavaPsiFacade.getInstance(project).findClass(THRIFT_HTTP_SERVICE_CLASS, GlobalSearchScope.allScope(project)) != null

    fun stripBlockComments(text: String): String = text.replace(Regex("""/\*[\s\S]*?\*/"""), "")

    fun stripLineComments(text: String): String =
        text
            .lineSequence()
            .map { line -> line.substringBefore("//").substringBefore("#") }
            .joinToString("\n")

    fun stripComments(text: String): String = stripLineComments(stripBlockComments(text))

    fun extractBracedBody(
        text: String,
        openBraceIndex: Int,
    ): String? {
        if (openBraceIndex !in text.indices || text[openBraceIndex] != '{') {
            return null
        }
        var depth = 0
        val start = openBraceIndex + 1
        for (index in openBraceIndex until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return text.substring(start, index)
                    }
                }
            }
        }
        return null
    }
}
