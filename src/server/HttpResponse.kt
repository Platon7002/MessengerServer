package server

import json.JsonWriter

class HttpResponse(
    val status: Int,
    val body: String = "",
    val extraHeaders: Map<String, String> = emptyMap()
) {

    fun toBytes(): ByteArray {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val head = StringBuilder()
        head.append("HTTP/1.1 ").append(status).append(' ').append(reasonPhrase(status)).append("\r\n")
        head.append("Content-Type: application/json; charset=utf-8\r\n")
        head.append("Content-Length: ").append(bodyBytes.size).append("\r\n")
        head.append("Connection: close\r\n")
        for ((name, value) in extraHeaders) {
            head.append(name).append(": ").append(value).append("\r\n")
        }
        head.append("\r\n")
        return head.toString().toByteArray(Charsets.ISO_8859_1) + bodyBytes
    }

    companion object {
        fun json(status: Int, value: Any?): HttpResponse = HttpResponse(status, JsonWriter.write(value))

        fun error(status: Int, message: String, extraHeaders: Map<String, String> = emptyMap()): HttpResponse =
            HttpResponse(status, JsonWriter.write(mapOf("error" to message)), extraHeaders)

        fun reasonPhrase(status: Int): String = when (status) {
            200 -> "OK"
            201 -> "Created"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            500 -> "Internal Server Error"
            else -> "Unknown"
        }
    }
}
