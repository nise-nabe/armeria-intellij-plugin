package com.linecorp.intellij.plugins.armeria.explorer.collector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import java.util.MissingResourceException
import java.util.concurrent.atomic.AtomicInteger

internal class ArmeriaRouteCollectionMetrics {
    val filesScanned = AtomicInteger()
    val armeriaFiles = AtomicInteger()
    val methodCallsVisited = AtomicInteger()
    val resolveCount = AtomicInteger()
    var elapsedMs: Long = 0

    fun snapshot(): Snapshot =
        Snapshot(
            filesScanned = filesScanned.get(),
            armeriaFiles = armeriaFiles.get(),
            methodCallsVisited = methodCallsVisited.get(),
            resolveCount = resolveCount.get(),
            elapsedMs = elapsedMs,
        )

    data class Snapshot(
        val filesScanned: Int,
        val armeriaFiles: Int,
        val methodCallsVisited: Int,
        val resolveCount: Int,
        val elapsedMs: Long,
    ) {
        override fun toString(): String =
            "filesScanned=$filesScanned, armeriaFiles=$armeriaFiles, methodCallsVisited=$methodCallsVisited, " +
                "resolveCount=$resolveCount, elapsedMs=$elapsedMs"
    }

    companion object {
        private val LOG = logger<ArmeriaRouteCollectionMetrics>()
        private val active = ThreadLocal<ArmeriaRouteCollectionMetrics?>()

        @Volatile
        var lastSnapshot: Snapshot? = null
            private set

        internal fun <T> runWith(
            metrics: ArmeriaRouteCollectionMetrics,
            block: () -> T,
        ): T {
            active.set(metrics)
            try {
                return block()
            } finally {
                active.remove()
            }
        }

        internal fun current(): ArmeriaRouteCollectionMetrics? = active.get()

        fun logIfEnabled(snapshot: Snapshot) {
            lastSnapshot = snapshot
            if (ApplicationManager.getApplication().isUnitTestMode) {
                return
            }
            val metricsAtInfo =
                try {
                    Registry.`is`("armeria.route.explorer.metrics")
                } catch (_: MissingResourceException) {
                    false
                }
            if (metricsAtInfo) {
                LOG.info("ArmeriaRouteCollector metrics: $snapshot")
            } else {
                LOG.debug("ArmeriaRouteCollector metrics: $snapshot")
            }
        }
    }
}
