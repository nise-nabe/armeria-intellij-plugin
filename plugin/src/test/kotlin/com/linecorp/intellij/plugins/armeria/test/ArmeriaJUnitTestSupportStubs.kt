package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

internal fun JavaCodeInsightTestFixture.registerArmeriaJUnitTestSupportStubs() {
    markDefaultSourceRootAsTestSource(module)
    registerArmeriaAnnotationStubs()
    registerArmeriaBlockingAnnotationStubs()
    addClass(
        """
        package org.junit.jupiter.api.extension;

        public @interface RegisterExtension {}
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.testing.junit5.server;

        import com.linecorp.armeria.client.WebClient;
        import com.linecorp.armeria.client.blocking.BlockingWebClient;

        public abstract class ServerExtension {
            public String httpUri() {
                return "";
            }

            public WebClient webClient() {
                return null;
            }

            public BlockingWebClient blockingWebClient() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client;

        public class WebClient {
            public static WebClient of(String uri) {
                return null;
            }

            public HttpRequestBuilder get(String path) {
                return null;
            }

            public HttpRequestBuilder post(String path) {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client;

        public class HttpRequestBuilder {
            public Object aggregate() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client.blocking;

        public class BlockingWebClient {
            public Object get(String path) {
                return null;
            }
        }
        """.trimIndent(),
    )
}

private fun markDefaultSourceRootAsTestSource(module: Module) {
    val rootManager = ModuleRootManager.getInstance(module)
    val contentRoot = rootManager.contentRoots.firstOrNull() ?: return
    if (rootManager.fileIndex.isInTestSourceContent(contentRoot)) {
        return
    }
    PsiTestUtil.removeSourceRoot(module, contentRoot)
    PsiTestUtil.addSourceRoot(module, contentRoot, true)
}
