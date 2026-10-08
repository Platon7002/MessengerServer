package controller

import json.JsonFormatException
import json.JsonParser
import logging.ServerLogger
import server.HttpRequest
import server.HttpResponse
import server.RequestHandler
import service.AuthService
import service.MessageService
import service.ServiceException

class ApiController(
    private val auth: AuthService,
    private val messages: MessageService,
    private val logger: ServerLogger
) : RequestHandler {

    override fun handle(request: HttpRequest): HttpResponse =
        try {
            route(request)
        } catch (e: ServiceException) {
            HttpResponse.error(e.status, e.message ?: "Ошибка")
        } catch (e: JsonFormatException) {
            HttpResponse.error(400, "Некорректный JSON: ${e.message}")
        } catch (e: Exception) {
            logger.error("Ошибка в обработчике ${request.method} ${request.path}", e)
            HttpResponse.error(500, "Внутренняя ошибка сервера")
        }

    private fun route(request: HttpRequest): HttpResponse {
        val segments = request.path.split('/').filter { it.isNotEmpty() }
        return when {
            request.path == "/register" -> onlyMethods(request, "POST") { register(request) }
            request.path == "/login" -> onlyMethods(request, "POST") { login(request) }
            request.path == "/messages" -> when (request.method) {
                "GET" -> listMessages(request)
                "POST" -> createMessage(request)
                else -> methodNotAllowed("GET, POST")
            }
            segments.size == 2 && segments[0] == "messages" ->
                onlyMethods(request, "DELETE") { deleteMessage(request, segments[1]) }
            else -> HttpResponse.error(404, "Путь ${request.path} не найден")
        }
    }


    private fun register(request: HttpRequest): HttpResponse {
        val body = readJsonObject(request)
        val user = auth.register(body["username"] as? String, body["password"] as? String)
        return HttpResponse.json(
            201,
            mapOf("message" to "Пользователь зарегистрирован", "id" to user.id, "username" to user.username)
        )
    }

    private fun login(request: HttpRequest): HttpResponse {
        val body = readJsonObject(request)
        val token = auth.login(body["username"] as? String, body["password"] as? String)
        return HttpResponse.json(200, mapOf("token" to token))
    }

    private fun listMessages(request: HttpRequest): HttpResponse {
        requireUser(request)
        val limitText = request.query["limit"]
        val limit = if (limitText == null) null
        else limitText.toIntOrNull() ?: throw ServiceException(400, "Параметр limit должен быть числом")
        val result = messages.list(request.query["author"], limit)
        return HttpResponse.json(200, result.map { it.toMap() })
    }

    private fun createMessage(request: HttpRequest): HttpResponse {
        val username = requireUser(request)
        val body = readJsonObject(request)
        val message = messages.create(username, body["text"] as? String)
        return HttpResponse.json(201, message.toMap())
    }

    private fun deleteMessage(request: HttpRequest, idText: String): HttpResponse {
        val username = requireUser(request)
        val id = idText.toIntOrNull() ?: throw ServiceException(400, "ID сообщения должен быть числом")
        messages.delete(username, id)
        return HttpResponse.json(200, mapOf("message" to "Сообщение $id удалено"))
    }


    private fun requireUser(request: HttpRequest): String {
        val header = request.headers["authorization"]
        val token = header
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring(7)
            ?.trim()
        if (token.isNullOrEmpty()) {
            throw ServiceException(401, "Нужна авторизация: заголовок Authorization: Bearer <token>")
        }
        val username = auth.authenticate(token)
            ?: throw ServiceException(401, "Неверный токен")
        request.authenticatedUser = username
        return username
    }

    private fun readJsonObject(request: HttpRequest): Map<String, Any?> {
        if (request.body.isBlank()) {
            throw ServiceException(400, "Тело запроса пустое, ожидается JSON-объект")
        }
        val parsed = JsonParser(request.body).parse()
        if (parsed !is Map<*, *>) {
            throw ServiceException(400, "Ожидается JSON-объект { ... }")
        }
        return parsed.entries.associate { (key, value) -> key.toString() to value }
    }

    private fun onlyMethods(request: HttpRequest, allowed: String, action: () -> HttpResponse): HttpResponse =
        if (request.method == allowed) action() else methodNotAllowed(allowed)

    private fun methodNotAllowed(allowed: String): HttpResponse =
        HttpResponse.error(405, "Метод не поддерживается. Допустимо: $allowed", mapOf("Allow" to allowed))
}
