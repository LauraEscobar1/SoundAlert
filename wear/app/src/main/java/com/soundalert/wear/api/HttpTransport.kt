package com.soundalert.wear.api

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** Petición HTTP mínima. El cuerpo, si lo hay, es JSON. */
data class HttpRequest(val method: String, val url: String, val body: String? = null)

data class HttpResponse(val code: Int, val body: String)

/**
 * Transporte HTTP, aislado para sustituirlo por un fake en los tests.
 * Lanza IOException si no hay red, el servidor no responde o se agota el tiempo.
 */
fun interface HttpTransport {
    @Throws(IOException::class)
    fun execute(request: HttpRequest): HttpResponse
}

/**
 * HttpURLConnection (incluido en Android): sin dependencias extra.
 * Bloqueante: llamar siempre fuera del hilo principal.
 */
class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 10_000,
) : HttpTransport {

    override fun execute(request: HttpRequest): HttpResponse {
        val connection = URI(request.url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            if (request.body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }
}
