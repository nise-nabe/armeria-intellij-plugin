package com.linecorp.intellij.plugins.armeria.inspection

internal object ArmeriaBlockingCallPatterns {
    private val JOIN_OWNERS =
        setOf(
            "java.lang.Thread",
            "java.util.concurrent.Future",
            "java.util.concurrent.CompletableFuture",
            "java.util.concurrent.CompletionStage",
        )
    private val AWAIT_OWNERS =
        setOf(
            "java.util.concurrent.CountDownLatch",
            "java.util.concurrent.CyclicBarrier",
            "java.util.concurrent.locks.Condition",
            "java.util.concurrent.Future",
            "java.util.concurrent.CompletableFuture",
        )
    private val GET_OWNERS =
        setOf(
            "java.util.concurrent.Future",
            "java.util.concurrent.CompletableFuture",
        )
    private val JDBC_OWNERS =
        setOf(
            "java.sql.DriverManager",
            "java.sql.Connection",
            "java.sql.Statement",
            "java.sql.PreparedStatement",
            "java.sql.CallableStatement",
            "java.sql.ResultSet",
            "javax.sql.DataSource",
            "jakarta.sql.DataSource",
        )
    private val FILES_METHODS = setOf("readAllBytes", "readString", "write", "writeString", "copy")

    fun isBlockingCall(
        methodName: String,
        ownerFqn: String?,
        unresolved: Boolean,
        qualifierText: String?,
        argumentCount: Int,
    ): Boolean {
        when (methodName) {
            "sleep" -> {
                if (ownerFqn == "java.lang.Thread") {
                    return true
                }
                if (unresolved && qualifierText?.substringAfterLast('.') == "Thread") {
                    return true
                }
            }
            "join" -> {
                if (ownerFqn != null && ownerFqn in JOIN_OWNERS) {
                    return true
                }
                if (unresolved && argumentCount == 0 && !qualifierText.isNullOrBlank()) {
                    return true
                }
            }
            "await" -> {
                if (ownerFqn != null && ownerFqn in AWAIT_OWNERS) {
                    return true
                }
            }
            "get" -> {
                if (ownerFqn != null && ownerFqn in GET_OWNERS) {
                    return true
                }
            }
            "runBlocking" -> {
                if (ownerFqn?.startsWith("kotlinx.coroutines") == true) {
                    return true
                }
                if (unresolved && qualifierText.isNullOrBlank()) {
                    return true
                }
            }
        }
        if (ownerFqn != null && ownerFqn in JDBC_OWNERS) {
            return true
        }
        return ownerFqn == "java.nio.file.Files" && methodName in FILES_METHODS
    }
}
