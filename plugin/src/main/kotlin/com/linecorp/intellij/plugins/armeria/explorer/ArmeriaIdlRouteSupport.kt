package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope

internal object ArmeriaIdlRouteSupport {
    const val GRAPHQL_SERVICE_CLASS = "com.linecorp.armeria.server.graphql.GraphqlService"
    const val THRIFT_HTTP_SERVICE_CLASS = "com.linecorp.armeria.server.thrift.THttpService"
    const val DEFAULT_GRAPHQL_MOUNT_PATH = "/graphql"

    fun isGraphqlOnClasspath(project: Project, scope: GlobalSearchScope): Boolean =
        JavaPsiFacade.getInstance(project).findClass(GRAPHQL_SERVICE_CLASS, scope) != null

    fun isThriftOnClasspath(project: Project, scope: GlobalSearchScope): Boolean =
        JavaPsiFacade.getInstance(project).findClass(THRIFT_HTTP_SERVICE_CLASS, scope) != null

    fun stripBlockComments(text: String): String {
        return text.replace(Regex("""/\*[\s\S]*?\*/"""), "")
    }

    fun stripLineComments(text: String): String {
        return text.lineSequence()
            .map { line -> line.substringBefore("//").substringBefore("#") }
            .joinToString("\n")
    }

    fun stripComments(text: String): String = stripLineComments(stripBlockComments(text))

    fun extractBracedBody(text: String, openBraceIndex: Int): String? {
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
