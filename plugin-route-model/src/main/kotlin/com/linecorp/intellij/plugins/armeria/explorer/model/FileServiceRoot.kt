package com.linecorp.intellij.plugins.armeria.explorer.model

/** How a `FileService` static root is addressed. */
enum class FileServiceRootKind {
    /** `FileService.of(File)` / `of(Path)` / `fileService(path, File)` — an O/S file-system directory. */
    FILE_SYSTEM,

    /** `FileService.of(ClassLoader, path)` / `of(Class, path)` — a class-path resource directory. */
    CLASS_PATH,
}

/**
 * Static root declaration extracted from a `FileService` registration.
 *
 * [path] is the declared path as written: a file-system path (relative to the project or absolute)
 * or a class-path resource path. [anchorClassName] holds the qualified anchor class for
 * `FileService.of(Class, path)` registrations; a relative [path] resolves against that class's
 * package while a leading `/` means an absolute class-path lookup.
 */
data class FileServiceRoot(
    val kind: FileServiceRootKind,
    val path: String,
    val anchorClassName: String = "",
)
