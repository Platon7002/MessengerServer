package server

import logging.ServerLogger
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class HttpServer(
    private val port: Int,
    private val handler: RequestHandler,
    private val logger: ServerLogger,
    threads: Int = 32
) {

    private companion object {
        const val READ_TIMEOUT_MS = 10_000
    }

    private val pool: ExecutorService = Executors.newFixedThreadPool(threads)
    private val parser = HttpParser()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    fun start() {
        val socket = ServerSocket(port)
        serverSocket = socket
        running = true
        logger.info("Сервер запущен: http://localhost:$port  (остановка: красная кнопка Stop)")

        while (running) {
            try {
                val client = socket.accept()
                pool.execute { serve(client) }
            } catch (e: SocketException) {
                if (running) {
                    logger.error("Ошибка при приёме подключения", e)
                }
            } catch (e: RejectedExecutionException) {
                logger.error("Пул потоков остановлен", e)
            } catch (e: IOException) {
                logger.error("Ошибка ввода-вывода при приёме подключения", e)
            }
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (ignored: IOException) {

        }
        pool.shutdown()
        pool.awaitTermination(3, TimeUnit.SECONDS)
        logger.info("Сервер остановлен")
    }

    private fun serve(socket: Socket) {
        socket.use { client ->
            var request: HttpRequest? = null
            val response: HttpResponse = try {
                client.soTimeout = READ_TIMEOUT_MS
                val parsed = parser.parse(BufferedInputStream(client.getInputStream()))
                if (parsed == null) {
                    return
                }
                request = parsed
                handler.handle(parsed)
            } catch (e: HttpParseException) {
                HttpResponse.error(400, e.message ?: "Некорректный HTTP-запрос")
            } catch (e: SocketTimeoutException) {
                logger.error("Клиент ${client.remoteSocketAddress} слишком долго не присылал запрос")
                return
            } catch (e: IOException) {
                logger.error("Соединение с клиентом ${client.remoteSocketAddress} разорвано", e)
                return
            } catch (e: Exception) {
                logger.error("Необработанная ошибка при обработке запроса", e)
                HttpResponse.error(500, "Внутренняя ошибка сервера")
            }

            try {
                val out = BufferedOutputStream(client.getOutputStream())
                out.write(response.toBytes())
                out.flush()
            } catch (e: IOException) {
                logger.error("Не удалось отправить ответ: клиент отключился", e)
            }
            logger.logRequest(request?.method ?: "-", request?.path ?: "-", request?.authenticatedUser)
        }
    }
}
