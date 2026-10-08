package app

import controller.ApiController
import logging.ServerLogger
import repository.JsonFileStore
import repository.MessageRepository
import repository.SessionRepository
import repository.UserRepository
import server.HttpServer
import service.AuthService
import service.MessageService
import java.io.File
import java.net.BindException

class Application(private val port: Int) {

    fun run() {
        val logger = ServerLogger()

        val users = UserRepository(JsonFileStore(File("users.json"), logger), logger)
        val sessions = SessionRepository(JsonFileStore(File("sessions.json"), logger), logger)
        val messages = MessageRepository(JsonFileStore(File("messages.json"), logger), logger)

        val controller = ApiController(AuthService(users, sessions), MessageService(messages), logger)
        val server = HttpServer(port, controller, logger)

        Runtime.getRuntime().addShutdownHook(Thread { server.stop() })

        try {
            server.start()
        } catch (e: BindException) {
            logger.error("Порт $port занят. Закройте другой запущенный сервер или укажите другой порт.", e)
        }
    }
}
