package com.superduper.sonoswidget.sonos

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

interface SonosHttpClient {
    fun get(url: String): String
    fun getBytes(url: String): ByteArray
    fun soap(url: String, soapAction: String, envelope: String): String
}

class JavaNetSonosHttpClient(
    private val connectTimeoutMs: Int = 3_000,
    private val readTimeoutMs: Int = 5_000
) : SonosHttpClient {
    override fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.requestMethod = "GET"
        return connection.useResponseText()
    }

    override fun getBytes(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.requestMethod = "GET"
        return try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    override fun soap(url: String, soapAction: String, envelope: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        connection.setRequestProperty("SOAPACTION", soapAction)
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(envelope)
        }
        return connection.useResponseText()
    }

    private fun HttpURLConnection.useResponseText(): String {
        return try {
            val stream = if (responseCode in 200..299) inputStream else errorStream
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            disconnect()
        }
    }
}
