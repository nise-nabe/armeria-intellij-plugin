package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceEndpointValidator
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceSpecificationParser
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.message
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

data class ArmeriaDocServiceFetchRequest(
    val host: String,
    val port: Int,
    val useHttps: Boolean,
    val mountPaths: List<String>,
    val project: Project,
)

sealed interface ArmeriaDocServiceFetchResult {
    data class Success(
        val routes: List<ArmeriaRoute>,
        val resolvedMountPath: String,
        val specificationUrl: String,
    ) : ArmeriaDocServiceFetchResult

    data class Failure(
        val message: String,
    ) : ArmeriaDocServiceFetchResult
}

object ArmeriaRuntimeRouteFetcher {
    private const val CONNECT_TIMEOUT_MS = 2_000
    private const val READ_TIMEOUT_MS = 5_000
    private const val MAX_RESPONSE_BYTES = 5 * 1024 * 1024

    fun fetch(request: ArmeriaDocServiceFetchRequest): ArmeriaDocServiceFetchResult {
        ProgressManager.checkCanceled()
        val errors = mutableListOf<String>()
        for (mountPath in request.mountPaths.distinct()) {
            ProgressManager.checkCanceled()
            val url =
                ArmeriaDocServiceEndpointValidator.buildSpecificationUrl(
                    request.host,
                    request.port,
                    request.useHttps,
                    mountPath,
                )
            when (val readResult = readUrl(url)) {
                is ReadResult.Success -> {
                    val parsed = ArmeriaDocServiceSpecificationParser.parse(readResult.body)
                    val routes = toArmeriaRoutes(parsed.routes, project = request.project)
                    if (routes.isNotEmpty()) {
                        val resolvedMountPath =
                            parsed.docServiceMountPath
                                ?.let(ArmeriaDocServiceEndpointValidator::normalizeMountPath)
                                ?: mountPath
                        return ArmeriaDocServiceFetchResult.Success(
                            routes = routes,
                            resolvedMountPath = resolvedMountPath,
                            specificationUrl = url,
                        )
                    }
                    errors += message("route.explorer.sync.error.noRoutesAtUrl", url)
                }
                is ReadResult.Failure -> errors += message("route.explorer.sync.error.urlFailed", url, readResult.message)
            }
        }
        return ArmeriaDocServiceFetchResult.Failure(
            if (errors.isEmpty()) {
                message("route.explorer.sync.error.noMountPaths")
            } else {
                errors.joinToString("\n")
            },
        )
    }

    fun fetchFromSpecification(
        specificationJson: String,
        moduleName: String,
        protocol: String,
        project: Project? = null,
    ): List<ArmeriaRoute> =
        toArmeriaRoutes(
            ArmeriaDocServiceSpecificationParser.parse(specificationJson).routes,
            moduleName = moduleName,
            protocol = protocol,
            project = project,
        )

    private fun toArmeriaRoutes(
        routes: List<ArmeriaDocServiceSpecificationParser.ParsedRoute>,
        moduleName: String = message("route.explorer.module.runtime"),
        protocol: String = message("route.explorer.protocol.runtime"),
        project: Project? = null,
    ): List<ArmeriaRoute> =
        routes.map { parsed ->
            ArmeriaRoute.createRuntime(
                httpMethod = parsed.httpMethod,
                path = parsed.path,
                target = parsed.serviceName + if (parsed.methodName.isBlank()) "" else "/${parsed.methodName}",
                moduleName = moduleName,
                protocol = protocol,
                project = project,
            )
        }

    private sealed interface ReadResult {
        data class Success(
            val body: String,
        ) : ReadResult

        data class Failure(
            val message: String,
        ) : ReadResult
    }

    private fun readUrl(url: String): ReadResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return ReadResult.Failure(message("route.explorer.sync.error.httpStatus", responseCode))
            }
            val body = connection.inputStream.use(::readLimitedBody)
            ReadResult.Success(body)
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (exception: IOException) {
            ReadResult.Failure(exception.message ?: exception.javaClass.simpleName)
        } catch (exception: Exception) {
            ReadResult.Failure(exception.message ?: exception.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    private fun readLimitedBody(input: java.io.InputStream): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8_192)
        var total = 0
        while (true) {
            ProgressManager.checkCanceled()
            val read = input.read(chunk)
            if (read < 0) {
                break
            }
            total += read
            if (total > MAX_RESPONSE_BYTES) {
                throw IOException(message("route.explorer.sync.error.responseTooLarge", MAX_RESPONSE_BYTES))
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toString(Charsets.UTF_8)
    }
}
