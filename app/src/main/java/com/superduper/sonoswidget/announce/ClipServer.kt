package com.superduper.sonoswidget.announce

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Minimal HTTP server that serves a single audio file at /clip.wav on the LAN so
 * Sonos can pull it. Handles concurrent fetches (one per speaker) and basic Range
 * requests, then is thrown away once the announcement is done.
 */
class ClipServer(
    private val file: File,
    private val contentType: String = "audio/wav"
) {
    private var server: ServerSocket? = null
    @Volatile private var running = false

    /** Binds an ephemeral port and starts accepting. Returns the chosen port. */
    fun start(): Int {
        val socket = ServerSocket(0)
        server = socket
        running = true
        thread(name = "clip-server-accept") {
            while (running) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break // socket closed during stop()
                }
                thread(name = "clip-server-conn") { handle(client) }
            }
        }
        Log.i(TAG, "Clip server listening on port ${socket.localPort} for ${file.name} (${file.length()} bytes)")
        return socket.localPort
    }

    fun url(host: String, port: Int): String = "http://$host:$port/clip.wav"

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            var rangeStart = 0L
            var rangeEnd = file.length() - 1
            var isRange = false
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
                if (header.startsWith("Range:", ignoreCase = true)) {
                    parseRange(header, file.length())?.let { (start, end) ->
                        rangeStart = start
                        rangeEnd = end
                        isRange = true
                    }
                }
            }

            val isHead = requestLine.startsWith("HEAD", ignoreCase = true)
            val output = socket.getOutputStream()
            val length = rangeEnd - rangeStart + 1
            Log.i(TAG, "Serving ${requestLine.substringBefore(' ')} range=$isRange bytes=$rangeStart-$rangeEnd")

            val statusLine = if (isRange) "HTTP/1.1 206 Partial Content" else "HTTP/1.1 200 OK"
            val headers = buildString {
                append("$statusLine\r\n")
                append("Content-Type: $contentType\r\n")
                append("Content-Length: $length\r\n")
                append("Accept-Ranges: bytes\r\n")
                if (isRange) append("Content-Range: bytes $rangeStart-$rangeEnd/${file.length()}\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            output.write(headers.toByteArray(Charsets.US_ASCII))
            if (!isHead) writeBody(output, rangeStart, length)
            output.flush()
        }
    }

    private fun writeBody(output: OutputStream, start: Long, length: Long) {
        file.inputStream().use { input ->
            var skipped = 0L
            while (skipped < start) skipped += input.skip(start - skipped)
            val buffer = ByteArray(16 * 1024)
            var remaining = length
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read == -1) break
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
    }

    private fun parseRange(header: String, fileLength: Long): Pair<Long, Long>? {
        val spec = header.substringAfter("bytes=", "").trim()
        if (spec.isEmpty()) return null
        val parts = spec.split('-', limit = 2)
        val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val end = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull() ?: (fileLength - 1)
        if (start > end || start >= fileLength) return null
        return start to minOf(end, fileLength - 1)
    }

    companion object {
        private const val TAG = "SonosWidget"
    }
}
