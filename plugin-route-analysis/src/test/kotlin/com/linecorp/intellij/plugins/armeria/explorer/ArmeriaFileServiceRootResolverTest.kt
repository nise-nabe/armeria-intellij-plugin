package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.linecorp.intellij.plugins.armeria.explorer.model.FileServiceRoot
import com.linecorp.intellij.plugins.armeria.explorer.model.FileServiceRootKind
import com.linecorp.intellij.plugins.armeria.explorer.navigation.ArmeriaFileServiceRootResolver
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArmeriaFileServiceRootResolverTest : ArmeriaFixtureTestBase() {
    fun testResolveFileSystemRelativePath() {
        val dir = myFixture.tempDirFixture.findOrCreateDir("static")

        val resolved =
            ArmeriaFileServiceRootResolver.resolve(
                project,
                FileServiceRoot(FileServiceRootKind.FILE_SYSTEM, "static"),
            )

        assertEquals(dir, resolved)
    }

    fun testResolveFileSystemAbsolutePath() {
        val ioDir = FileUtil.createTempDirectory("fileService", "root", true)
        val expected = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioDir)
        assertNotNull(expected)

        val resolved =
            ArmeriaFileServiceRootResolver.resolve(
                project,
                FileServiceRoot(FileServiceRootKind.FILE_SYSTEM, ioDir.absolutePath),
            )

        assertEquals(expected, resolved)
    }

    fun testResolveFileSystemMissingDirectoryReturnsNull() {
        val resolved =
            ArmeriaFileServiceRootResolver.resolve(
                project,
                FileServiceRoot(FileServiceRootKind.FILE_SYSTEM, "missing/static"),
            )

        assertNull(resolved)
    }

    fun testResolveClassPathAbsolutePath() {
        val dir = myFixture.tempDirFixture.findOrCreateDir("public")

        val resolved =
            ArmeriaFileServiceRootResolver.resolve(
                project,
                FileServiceRoot(FileServiceRootKind.CLASS_PATH, "/public"),
            )

        assertEquals(dir, resolved)
    }

    fun testResolveClassPathAnchoredPath() {
        val dir = myFixture.tempDirFixture.findOrCreateDir("example/assets")

        val resolved =
            ArmeriaFileServiceRootResolver.resolve(
                project,
                FileServiceRoot(FileServiceRootKind.CLASS_PATH, "assets", anchorClassName = "example.Main"),
            )

        assertEquals(dir, resolved)
    }

    fun testResolveClassPathMissingReturnsNull() {
        val resolved =
            ArmeriaFileServiceRootResolver.resolve(
                project,
                FileServiceRoot(FileServiceRootKind.CLASS_PATH, "missing", anchorClassName = "example.Main"),
            )

        assertNull(resolved)
    }

    fun testIndexFileForReturnsIndexHtml() {
        val dir = myFixture.tempDirFixture.findOrCreateDir("static")
        val index = myFixture.addFileToProject("static/index.html", "<html></html>").virtualFile

        val found = ArmeriaFileServiceRootResolver.indexFileFor(dir)

        assertNotNull(found)
        assertEquals(index, found)
    }

    fun testIndexFileForMissingReturnsNull() {
        val dir = myFixture.tempDirFixture.findOrCreateDir("static")

        assertNull(ArmeriaFileServiceRootResolver.indexFileFor(dir))
    }
}
