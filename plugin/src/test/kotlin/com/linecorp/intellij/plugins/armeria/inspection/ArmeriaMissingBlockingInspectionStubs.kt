package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

fun JavaCodeInsightTestFixture.registerMissingBlockingGraphqlStubs() {
    addClass(
        """
        package graphql.schema;

        public interface DataFetcher<T> {
            T get(Object environment) throws Exception;
        }
        """.trimIndent(),
    )
    addClass(
        """
        package graphql.schema.idl;

        public class TypeRuntimeWiring {
            public TypeRuntimeWiring dataFetcher(String fieldName, graphql.schema.DataFetcher<?> dataFetcher) {
                return this;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.server.graphql;

        public final class GraphqlService {
            public static GraphqlServiceBuilder builder() {
                return new GraphqlServiceBuilder();
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.server.graphql;

        public final class GraphqlServiceBuilder {
            public GraphqlServiceBuilder runtimeWiring(Object configurer) {
                return this;
            }

            public GraphqlServiceBuilder useBlockingTaskExecutor(boolean useBlockingTaskExecutor) {
                return this;
            }

            public com.linecorp.armeria.server.graphql.GraphqlService build() {
                return new GraphqlService();
            }
        }
        """.trimIndent(),
    )
}

fun JavaCodeInsightTestFixture.registerMissingBlockingHttpServiceStubs() {
    addClass(
        """
        package com.linecorp.armeria.server;

        public interface HttpService {
            Object serve(Object ctx, Object req) throws Exception;
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.server;

        public abstract class AbstractHttpService implements HttpService {
            @Override
            public Object serve(Object ctx, Object req) throws Exception {
                return doGet(ctx, req);
            }

            protected Object doGet(Object ctx, Object req) throws Exception {
                return null;
            }

            protected Object doPost(Object ctx, Object req) throws Exception {
                return null;
            }

            protected Object doPut(Object ctx, Object req) throws Exception {
                return null;
            }

            protected Object doDelete(Object ctx, Object req) throws Exception {
                return null;
            }

            protected Object doHead(Object ctx, Object req) throws Exception {
                return null;
            }

            protected Object doPatch(Object ctx, Object req) throws Exception {
                return null;
            }

            protected Object doOptions(Object ctx, Object req) throws Exception {
                return null;
            }

            protected Object doTrace(Object ctx, Object req) throws Exception {
                return null;
            }

            protected Object doConnect(Object ctx, Object req) throws Exception {
                return null;
            }

            protected Object doQuery(Object ctx, Object req) throws Exception {
                return null;
            }
        }
        """.trimIndent(),
    )
}
