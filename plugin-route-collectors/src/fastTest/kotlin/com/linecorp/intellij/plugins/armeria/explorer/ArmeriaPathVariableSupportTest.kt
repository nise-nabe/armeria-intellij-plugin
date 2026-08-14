package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaPathVariableSupport
import org.junit.Test
import kotlin.test.assertEquals

class ArmeriaPathVariableSupportTest {
    @Test
    fun extractBraceAndColonVariables() {
        assertEquals(listOf("id", "name"), ArmeriaPathVariableSupport.extractPathVariables("/users/{id}/hello/:name"))
    }

    @Test
    fun extractNestedBraceQuantifierUsesNameOnly() {
        assertEquals(listOf("year"), ArmeriaPathVariableSupport.extractPathVariables("/years/{year:[0-9]{4}}"))
        assertEquals(listOf("id"), ArmeriaPathVariableSupport.extractPathVariables("/users/{id:[0-9]{1,10}}"))
        assertEquals(listOf("id"), ArmeriaPathVariableSupport.extractPathVariables("/users/{id:[0-9]+}"))
    }

    @Test
    fun colonInsideBraceConstraintIsNotAVariable() {
        assertEquals(listOf("id"), ArmeriaPathVariableSupport.extractPathVariables("/users/{id:uuid}"))
    }

    @Test
    fun replaceNestedBraceQuantifierPreservesConstraint() {
        assertEquals(
            "/years/{fullYear:[0-9]{4}}",
            ArmeriaPathVariableSupport.replacePathVariableName("/years/{year:[0-9]{4}}", "year", "fullYear"),
        )
    }

    @Test
    fun occurrencesPointAtNestedBraceName() {
        val path = "/years/{year:[0-9]{4}}"
        val occurrences = ArmeriaPathVariableSupport.pathVariableOccurrences(path)
        assertEquals(listOf("year"), occurrences.map { it.name })
        assertEquals("year", path.substring(occurrences[0].startOffset, occurrences[0].endOffset))
    }

    @Test
    fun extractCatchAllBraceVariable() {
        assertEquals(listOf("path"), ArmeriaPathVariableSupport.extractPathVariables("/files/{*path}"))
    }

    @Test
    fun extractRegexNamedGroups() {
        assertEquals(
            listOf("org", "id"),
            ArmeriaPathVariableSupport.extractPathVariables("regex:^/orgs/(?<org>[^/]+)/users/(?<id>\\d+)$"),
        )
    }

    @Test
    fun globPathsHaveNoVariables() {
        assertEquals(emptyList(), ArmeriaPathVariableSupport.extractPathVariables("glob:/users/**"))
    }

    @Test
    fun regexWithoutNamedGroupsIsEmpty() {
        assertEquals(emptyList(), ArmeriaPathVariableSupport.extractPathVariables("regex:\\d{2,3}"))
    }

    @Test
    fun replacePreservesConstraintsAndNamedGroups() {
        assertEquals(
            "/users/{userId:[0-9]+}/:userId",
            ArmeriaPathVariableSupport.replacePathVariableName("/users/{id:[0-9]+}/:id", "id", "userId"),
        )
        assertEquals(
            "regex:^(?<userId>\\d+)$",
            ArmeriaPathVariableSupport.replacePathVariableName("regex:^(?<id>\\d+)$", "id", "userId"),
        )
    }

    @Test
    fun replaceDoesNotTouchLongerNames() {
        assertEquals(
            "/users/{identity}",
            ArmeriaPathVariableSupport.replacePathVariableName("/users/{identity}", "id", "userId"),
        )
    }

    @Test
    fun occurrencesAndReplaceKeepPathsWithoutLeadingSlash() {
        val path = "users/{id}"
        val occurrences = ArmeriaPathVariableSupport.pathVariableOccurrences(path)
        assertEquals(listOf("id"), occurrences.map { it.name })
        assertEquals("id", path.substring(occurrences[0].startOffset, occurrences[0].endOffset))
        assertEquals(
            "users/{userId}",
            ArmeriaPathVariableSupport.replacePathVariableName(path, "id", "userId"),
        )
    }

    @Test
    fun occurrencesPointAtVariableNames() {
        val occurrences = ArmeriaPathVariableSupport.pathVariableOccurrences("/users/{id}/:name")
        assertEquals(listOf("id", "name"), occurrences.map { it.name })
        assertEquals("id", "/users/{id}/:name".substring(occurrences[0].startOffset, occurrences[0].endOffset))
        assertEquals("name", "/users/{id}/:name".substring(occurrences[1].startOffset, occurrences[1].endOffset))
    }

    @Test
    fun prefixPathsStillExtractVariables() {
        assertEquals(
            listOf("id"),
            ArmeriaPathVariableSupport.extractPathVariables("/users/{id}", PathType.PREFIX),
        )
    }
}
