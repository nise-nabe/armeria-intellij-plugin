package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaParamPathVariableInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerContentAnnotationStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaParamPathVariableInspection())
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "@Get(\"/users/{id}\")|id",
            "@Get(\"/hello/:name\")|name",
            "@Get(\"/users/{id:[0-9]+}\")|id",
            "@Get(\"/years/{year:[0-9]{4}}\")|year",
            "@Get(\"regex:^(?<userId>\\\\d+)$\")|userId",
        ],
    )
    fun highlightsMissingPathVariable(
        annotation: String,
        variableName: String,
    ) {
        myFixture.configureByText(
            "MissingService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class MissingService {
                $annotation
                public String handler() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val expected = message("inspection.param.path.variable.missing", variableName)
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(1, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }

    @Test
    fun allowsMatchingParam() {
        configureUsersGet("""public String handler(@Param("id") String id) { return id; }""")
        assertNoParamMismatchHighlights()
    }

    @Test
    fun allowsParamWithoutValueUsingParameterName() {
        configureUsersGet("public String handler(@Param String id) { return id; }")
        assertNoParamMismatchHighlights()
    }

    @Test
    fun allowsQueryParamWhenPathVariablesAreBound() {
        configureUsersGet("""public String handler(@Param("id") String id, @Param("page") int page) { return id; }""")
        assertNoParamMismatchHighlights()
    }

    @Test
    fun ignoresRoutesWithoutPathVariables() {
        myFixture.configureByText(
            "QueryService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Param;

            public class QueryService {
                @Get("/users")
                public String handler(@Param("page") int page) {
                    return "ok";
                }
            }
            """.trimIndent(),
        )
        assertNoParamMismatchHighlights()
    }

    @Test
    fun allowsQueryParamWhenPathVariableIsMissing() {
        configureUsersGet("""public String handler(@Param("userId") String userId, @Param("page") int page) { return userId; }""")
        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }.toSet()
        assertTrue(message("inspection.param.path.variable.missing", "id") in descriptions)
        assertTrue(descriptions.none { it.startsWith("@Param") })
    }

    @Test
    fun treatsNestedBraceQuantifierAsConstraintNotName() {
        myFixture.configureByText(
            "YearService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Param;

            public class YearService {
                @Get("/years/{year:[0-9]{4}}")
                public String handler(@Param("year") String year) {
                    return year;
                }
            }
            """.trimIndent(),
        )
        assertNoParamMismatchHighlights()
    }

    @Test
    fun includesPathPrefixVariables() {
        myFixture.configureByText(
            "PrefixedService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.PathPrefix;

            @PathPrefix("/orgs/{org}")
            public class PrefixedService {
                @Get("/users/{id}")
                public String handler() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )
        val expected = message("inspection.param.path.variable.missing", "org, id")
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(1, highlights.size)
    }

    private fun configureUsersGet(methodBody: String) {
        myFixture.configureByText(
            "UserService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Param;

            public class UserService {
                @Get("/users/{id}")
                $methodBody
            }
            """.trimIndent(),
        )
    }

    private fun assertNoParamMismatchHighlights() {
        val highlights =
            myFixture.doHighlighting().filter {
                it.description == message("inspection.param.path.variable.missing", "id") ||
                    it.description?.startsWith("@Param") == true ||
                    it.description?.startsWith("Path variable") == true
            }
        assertTrue(highlights.isEmpty(), highlights.joinToString { it.description.orEmpty() })
    }
}
