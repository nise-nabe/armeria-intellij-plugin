package com.linecorp.intellij.plugins.armeria.run

import com.intellij.ide.browsers.OpenUrlHyperlinkInfo
import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project

class ArmeriaDocServiceConsoleFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> {
        return arrayOf(ArmeriaDocServiceConsoleFilter())
    }
}

private class ArmeriaDocServiceConsoleFilter : Filter {
    private val urlPattern = Regex("""https?://\S+?(?:/docs(?:/index\.html)?\S*|/docservice\S*)""")

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val match = urlPattern.find(line) ?: return null
        val start = entireLength - line.length + match.range.first
        val end = entireLength - line.length + match.range.last + 1
        return Filter.Result(start, end, OpenUrlHyperlinkInfo(match.value))
    }
}
