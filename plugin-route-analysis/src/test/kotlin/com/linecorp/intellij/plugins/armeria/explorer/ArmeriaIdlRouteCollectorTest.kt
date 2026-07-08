package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaIdlRouteCollectorTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerMinimalArmeriaServerStubs()
    }

    fun testParseGraphqlQueryFields() {
        val operations = ArmeriaGraphqlRouteCollector.parseOperations(
            """
            type Query {
                user: User
                posts: [Post!]!
            }
            """.trimIndent(),
        )

        assertEquals(2, operations.size)
        assertEquals(GraphqlOperation("Query", "user"), operations[0])
        assertEquals(GraphqlOperation("Query", "posts"), operations[1])
    }

    fun testParseGraphqlExtendTypeAndSubscription() {
        val operations = ArmeriaGraphqlRouteCollector.parseOperations(
            """
            extend type Query {
                search(term: String!): [Result!]!
            }

            type Subscription {
                messageAdded: Message
            }
            """.trimIndent(),
        )

        assertEquals(2, operations.size)
        assertEquals(GraphqlOperation("Query", "search"), operations[0])
        assertEquals(GraphqlOperation("Subscription", "messageAdded"), operations[1])
    }

    fun testParseGraphqlMultilineFieldArguments() {
        val operations = ArmeriaGraphqlRouteCollector.parseOperations(
            """
            type Query {
                user(
                    id: ID!
                ): User
            }
            """.trimIndent(),
        )

        assertEquals(1, operations.size)
        assertEquals(GraphqlOperation("Query", "user"), operations.single())
    }

    fun testParseThriftServiceMethods() {
        val operations = ArmeriaThriftRouteCollector.parseOperations(
            """
            service HelloService {
                void ping(),
                string echo(1: string message),
                oneway void fireAndForget(),
            }
            """.trimIndent(),
        )

        assertEquals(2, operations.size)
        assertEquals(ThriftOperation("HelloService", "ping"), operations[0])
        assertEquals(ThriftOperation("HelloService", "echo"), operations[1])
    }

    fun testParseThriftServiceIgnoresBlockCommentWithBraces() {
        val operations = ArmeriaThriftRouteCollector.parseOperations(
            """
            service HelloService {
                /** legacy { block } */
                void ping(),
            }
            """.trimIndent(),
        )

        assertEquals(1, operations.size)
        assertEquals(ThriftOperation("HelloService", "ping"), operations.single())
    }

    fun testParseThriftServiceWithExtends() {
        val operations = ArmeriaThriftRouteCollector.parseOperations(
            """
            service DerivedService extends BaseService {
                void ping(),
            }
            """.trimIndent(),
        )

        assertEquals(1, operations.size)
        assertEquals(ThriftOperation("DerivedService", "ping"), operations.single())
    }

    fun testParseGraphqlIgnoresHashComments() {
        val operations = ArmeriaGraphqlRouteCollector.parseOperations(
            """
            # Query type
            type Query {
                # user field
                user: User
            }
            """.trimIndent(),
        )

        assertEquals(1, operations.size)
        assertEquals(GraphqlOperation("Query", "user"), operations.single())
    }

    fun testParseThriftIgnoresHashComments() {
        val operations = ArmeriaThriftRouteCollector.parseOperations(
            """
            # Hello service
            service HelloService {
                # ping method
                void ping(),
            }
            """.trimIndent(),
        )

        assertEquals(1, operations.size)
        assertEquals(ThriftOperation("HelloService", "ping"), operations.single())
    }

    fun testCollectGraphqlRoutesWhenGraphqlOnClasspath() {
        registerArmeriaIdlStubs()
        myFixture.configureByText(
            "schema.graphql",
            """
            type Query {
                user: User
            }

            type Mutation {
                createUser(name: String!): User
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
            .filter { it.protocol == RouteProtocol.GRAPHQL.presentableName() }

        assertEquals(2, routes.size)
        assertTrue(routes.all { it.path == ArmeriaIdlRouteSupport.DEFAULT_GRAPHQL_MOUNT_PATH })
        assertTrue(routes.all { it.httpMethod.isBlank() })
        assertTrue(routes.all { it.routeMatch == RouteMatch.NON_HTTP })
        assertEquals("Query.user", routes.first { it.target.startsWith("Query.") }.target)
        assertEquals("Mutation.createUser", routes.first { it.target.startsWith("Mutation.") }.target)
    }

    fun testCollectGraphqlRoutesFromGraphqlsExtension() {
        registerArmeriaIdlStubs()
        myFixture.configureByText(
            "schema.graphqls",
            """
            type Query {
                health: String
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
            .filter { it.protocol == RouteProtocol.GRAPHQL.presentableName() }

        assertEquals(1, routes.size)
        assertEquals("Query.health", routes.single().target)
    }

    fun testDuplicateGraphqlRoutesAreDeduplicated() {
        registerArmeriaIdlStubs()
        myFixture.configureByText(
            "schema.graphql",
            """
            type Query {
                user: User
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "schema.graphqls",
            """
            type Query {
                user: User
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
            .filter { it.protocol == RouteProtocol.GRAPHQL.presentableName() }

        assertEquals(1, routes.size)
        assertEquals("Query.user", routes.single().target)
        assertEquals(ArmeriaIdlRouteSupport.DEFAULT_GRAPHQL_MOUNT_PATH, routes.single().path)
    }

    fun testCollectThriftRoutesWhenThriftOnClasspath() {
        registerArmeriaIdlStubs()
        myFixture.configureByText(
            "hello.thrift",
            """
            service HelloService {
                string sayHello(1: string name),
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
            .filter { it.protocol == RouteProtocol.THRIFT.presentableName() }

        assertEquals(1, routes.size)
        val route = routes.single()
        assertEquals("/HelloService", route.path)
        assertEquals("HelloService.sayHello", route.target)
        assertEquals(RouteMatch.NON_HTTP, route.routeMatch)
        assertTrue(route.httpMethod.isBlank())
    }

    fun testDuplicateThriftRoutesAreDeduplicated() {
        registerArmeriaIdlStubs()
        myFixture.configureByText(
            "hello.thrift",
            """
            service HelloService {
                string sayHello(1: string name),
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "hello-copy.thrift",
            """
            service HelloService {
                string sayHello(1: string name),
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
            .filter { it.protocol == RouteProtocol.THRIFT.presentableName() }

        assertEquals(1, routes.size)
        assertEquals("/HelloService", routes.single().path)
        assertEquals("HelloService.sayHello", routes.single().target)
    }

    fun testSkipIdlRoutesWhenProtocolNotOnClasspath() {
        myFixture.configureByText(
            "schema.graphql",
            """
            type Query {
                user: User
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "hello.thrift",
            """
            service HelloService {
                void ping(),
            }
            """.trimIndent(),
        )

        val routesWithoutIdlStubs = ArmeriaRouteCollector.collect(project)
            .filter { it.routeMatch == RouteMatch.NON_HTTP }

        assertTrue(routesWithoutIdlStubs.isEmpty())
    }
}
