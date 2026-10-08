package logging

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ServerLogger(
    private val requestLog: File = File("server.log"),
    private val errorLog: File = File("errors.log")
) {

    private val lock = Any()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    private fun now(): String = LocalDateTime.now().format(formatter)

    fun logRequest(method: String, path: String, user: String?) {
        val line = "${now()} $method $path ${user ?: "guest"}"
        println(line)
        append(requestLog, line)
    }

    fun error(message: String, error: Throwable? = null) {
        val details = if (error != null) " | ${error::class.simpleName}: ${error.message}" else ""
        val line = "${now()} ERROR $message$details"
        System.err.println(line)
        append(errorLog, line)
    }

    fun info(message: String) {
        println("${now()} INFO $message")
    }

    private fun append(file: File, line: String) {
        synchronized(lock) {
            try {
                file.appendText(line + "\n", Charsets.UTF_8)
            } catch (ignored: Exception) {
            }
        }
    }
}
