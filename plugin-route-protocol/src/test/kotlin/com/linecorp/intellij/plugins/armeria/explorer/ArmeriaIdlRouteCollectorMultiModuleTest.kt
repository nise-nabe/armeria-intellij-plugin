package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PsiTestUtil
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaProtocolRouteContributor
import java.io.File

class ArmeriaIdlRouteCollectorMultiModuleTest : HeavyPlatformTestCase() {
    override fun setUp() {
        super.setUp()
        allowTestSandboxRoots()
        createTestProjectStructure()
        registerArmeriaIdlStubs()
    }

    private fun allowTestSandboxRoots() {
        val sandboxRoot = File(PathManager.getConfigPath()).parentFile
        val pluginsTestDir = sandboxRoot?.resolve("plugins-test")
        if (pluginsTestDir != null && pluginsTestDir.isDirectory) {
            VfsRootAccess.allowRootAccess(testRootDisposable, pluginsTestDir.absolutePath)
            return
        }

        VfsRootAccess.allowRootAccess(testRootDisposable, PathManager.getPluginsPath())
        sandboxRoot?.absolutePath?.let { root ->
            VfsRootAccess.allowRootAccess(testRootDisposable, root)
        }
    }

    fun testCollectGraphqlRoutesKeepDuplicateOperationsAcrossModules() {
        val schema =
            """
            type Query {
                user: User
            }
            """.trimIndent()
        createTextFileInModule(module, "schema.graphql", schema)
        val additionalModule = createAdditionalModule("additionalModule")
        createTextFileInModule(additionalModule, "other.graphql", schema)

        val routes =
            ArmeriaRouteCollector
                .collect(
                    project,
                    contributors = listOf(ArmeriaProtocolRouteContributor),
                ).filter { it.protocol == RouteProtocol.GRAPHQL.presentableName() }

        assertEquals(2, routes.size)
        assertEquals(setOf("Query.user"), routes.map { it.target }.toSet())
        assertEquals(setOf(module.name, additionalModule.name), routes.map { it.moduleName }.toSet())
    }

    fun testCollectThriftRoutesKeepDuplicateOperationsAcrossModules() {
        val thrift =
            """
            service HelloService {
                string sayHello(1: string name),
            }
            """.trimIndent()
        createTextFileInModule(module, "hello.thrift", thrift)
        val additionalModule = createAdditionalModule("additionalModule")
        createTextFileInModule(additionalModule, "other.thrift", thrift)

        val routes =
            ArmeriaRouteCollector
                .collect(
                    project,
                    contributors = listOf(ArmeriaProtocolRouteContributor),
                ).filter { it.protocol == RouteProtocol.THRIFT.presentableName() }

        assertEquals(2, routes.size)
        assertEquals(setOf("HelloService.sayHello"), routes.map { it.target }.toSet())
        assertEquals(setOf(module.name, additionalModule.name), routes.map { it.moduleName }.toSet())
    }

    private fun registerArmeriaIdlStubs() {
        createTextFileInModule(
            module,
            "com/linecorp/armeria/server/graphql/GraphqlService.java",
            """
            package com.linecorp.armeria.server.graphql;

            public final class GraphqlService {
            }
            """.trimIndent(),
        )
        createTextFileInModule(
            module,
            "com/linecorp/armeria/server/thrift/THttpService.java",
            """
            package com.linecorp.armeria.server.thrift;

            public final class THttpService {
            }
            """.trimIndent(),
        )
    }

    private fun createTextFileInModule(
        targetModule: Module,
        relativePath: String,
        content: String,
    ): VirtualFile {
        val sourceRoot = moduleSourceRoot(targetModule)
        val parts = relativePath.split("/")
        var directory = sourceRoot
        for (part in parts.dropLast(1)) {
            directory = directory.findChild(part) ?: createChildDirectory(directory, part)
        }
        val file = createChildData(directory, parts.last())
        setFileText(file, content)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        return file
    }

    private fun createAdditionalModule(name: String): Module {
        val tempDir = createTempDir(name)
        val additionalModule = createModuleAt(name, project, moduleType, tempDir.toPath())
        PsiTestUtil.addSourceContentToRoots(additionalModule, getVirtualFile(tempDir))
        return additionalModule
    }

    private fun moduleSourceRoot(targetModule: Module): VirtualFile {
        val moduleRootManager = ModuleRootManager.getInstance(targetModule)
        return moduleRootManager.sourceRoots.firstOrNull()
            ?: moduleRootManager.contentRoots.first()
    }
}
