package client

import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private var host = "localhost"
private var port = 8080
private var passed = 0
private var failed = 0

private class Reply(val code: Int, val text: String)

private fun send(method: String, path: String, body: String? = null, token: String? = null): Reply {
    val connection = URI("http://$host:$port$path").toURL().openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.connectTimeout = 5000
    connection.readTimeout = 10000
    if (token != null) {
        connection.setRequestProperty("Authorization", "Bearer $token")
    }
    if (body != null) {
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    }
    val code = connection.responseCode
    val stream = if (code >= 400) connection.errorStream else connection.inputStream
    val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    return Reply(code, text)
}

private fun step(
    title: String,
    expected: List<Int>,
    method: String,
    path: String,
    body: String? = null,
    token: String? = null
): Reply {
    val reply = send(method, path, body, token)
    val ok = reply.code in expected
    if (ok) passed++ else failed++
    println()
    println("${if (ok) "[OK]    " else "[ОШИБКА]"} $title")
    println("   -> $method $path${if (body != null) "  $body" else ""}")
    println("   <- ${reply.code} ${reply.text.replace("\n", " ").replace(Regex("\\s+"), " ").take(200)}")
    if (!ok) {
        println("   ожидался код: $expected")
    }
    return reply
}

private fun check(title: String, condition: Boolean) {
    if (condition) passed++ else failed++
    println()
    println("${if (condition) "[OK]    " else "[ОШИБКА]"} $title")
}

private fun tokenFrom(reply: Reply): String =
    Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(reply.text)?.groupValues?.get(1).orEmpty()

private fun idFrom(reply: Reply): Int =
    Regex("\"id\"\\s*:\\s*(\\d+)").find(reply.text)?.groupValues?.get(1)?.toIntOrNull() ?: -1

private fun section(title: String) {
    println()
    println("=========== $title ===========")
}

fun main(args: Array<String>) {
    port = args.firstOrNull()?.toIntOrNull() ?: 8080
    println("Проверяем сервер http://$host:$port")

    try {
        send("GET", "/")
    } catch (e: Exception) {
        println("Сервер не отвечает. Сначала запустите сервер (MainKt), потом этот клиент.")
        return
    }

    section("1. Регистрация")
    step("Регистрация alex", listOf(201, 400), "POST", "/register", """{"username":"alex","password":"12345"}""")
    step("Повторная регистрация alex -> ошибка", listOf(400), "POST", "/register", """{"username":"alex","password":"12345"}""")
    step("Слишком короткий пароль", listOf(400), "POST", "/register", """{"username":"newuser","password":"1"}""")
    step("Недопустимое имя", listOf(400), "POST", "/register", """{"username":"a b","password":"12345"}""")
    step("Битый JSON", listOf(400), "POST", "/register", """{"username": "alex", """)
    step("Пустое тело", listOf(400), "POST", "/register")
    step("Регистрация bob", listOf(201, 400), "POST", "/register", """{"username":"bob","password":"qwerty"}""")

    section("2. Авторизация")
    step("Неверный пароль", listOf(401), "POST", "/login", """{"username":"alex","password":"wrong"}""")
    step("Несуществующий пользователь", listOf(401), "POST", "/login", """{"username":"ghost","password":"12345"}""")
    val alexToken = tokenFrom(step("Вход alex -> токен", listOf(200), "POST", "/login", """{"username":"alex","password":"12345"}"""))
    val bobToken = tokenFrom(step("Вход bob -> токен", listOf(200), "POST", "/login", """{"username":"bob","password":"qwerty"}"""))
    check("Токены получены и различаются", alexToken.isNotEmpty() && bobToken.isNotEmpty() && alexToken != bobToken)

    section("3. Защита токеном")
    step("GET /messages без токена", listOf(401), "GET", "/messages")
    step("GET /messages с неверным токеном", listOf(401), "GET", "/messages", token = "fake-token")
    step("POST /messages без токена", listOf(401), "POST", "/messages", """{"text":"hi"}""")

    section("4. Сообщения (CRUD)")
    val created = step("alex создаёт сообщение", listOf(201), "POST", "/messages", """{"text":"Hello world"}""", alexToken)
    val messageId = idFrom(created)
    step("bob создаёт сообщение", listOf(201), "POST", "/messages", """{"text":"Привет от Боба"}""", bobToken)
    step("Сообщение без текста", listOf(400), "POST", "/messages", """{"text":"   "}""", alexToken)
    step("Список сообщений", listOf(200), "GET", "/messages", token = alexToken)
    step("Фильтр по автору", listOf(200), "GET", "/messages?author=alex", token = alexToken)
    step("Неверный limit", listOf(400), "GET", "/messages?limit=abc", token = alexToken)

    section("5. Права на удаление")
    step("bob удаляет чужое сообщение -> 403", listOf(403), "DELETE", "/messages/$messageId", token = bobToken)
    step("Несуществующее сообщение -> 404", listOf(404), "DELETE", "/messages/99999", token = alexToken)
    step("ID не число -> 400", listOf(400), "DELETE", "/messages/abc", token = alexToken)
    step("Удаление без токена -> 401", listOf(401), "DELETE", "/messages/$messageId")
    step("alex удаляет своё сообщение", listOf(200), "DELETE", "/messages/$messageId", token = alexToken)
    step("Повторное удаление -> 404", listOf(404), "DELETE", "/messages/$messageId", token = alexToken)

    section("6. Маршрутизация")
    step("Неизвестный путь -> 404", listOf(404), "GET", "/unknown")
    step("Неверный метод -> 405", listOf(405), "PUT", "/messages", """{"text":"x"}""", alexToken)
    step("GET /login -> 405", listOf(405), "GET", "/login")

    section("7. Некорректные HTTP-запросы и обрывы")
    val garbage = rawRequest("ЭТО НЕ HTTP\r\n\r\n")
    check("Мусор вместо HTTP -> 400 (ответ: ${garbage.lineSequence().firstOrNull()})", garbage.startsWith("HTTP/1.1 400"))
    val noHost = rawRequest("GET /unknown HTTP/1.1\r\nHost: x\r\n\r\n")
    check("Ручной запрос через сокет -> 404 (ответ: ${noHost.lineSequence().firstOrNull()})", noHost.startsWith("HTTP/1.1 404"))
    brokenConnections()
    step("Сервер жив после оборванных соединений", listOf(200), "GET", "/messages", token = alexToken)

    section("8. Многопоточность: 30 клиентов одновременно")
    concurrentTest(alexToken)

    println()
    println("================================================")
    println("ИТОГО: успешно $passed, с ошибками $failed")
    println(if (failed == 0) "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" else "ЕСТЬ ПРОВАЛЕННЫЕ ПРОВЕРКИ")
}

private fun rawRequest(text: String): String {
    Socket(host, port).use { socket ->
        socket.soTimeout = 5000
        val out: OutputStream = socket.getOutputStream()
        out.write(text.toByteArray(Charsets.UTF_8))
        out.flush()
        return socket.getInputStream().readBytes().toString(Charsets.UTF_8)
    }
}

private fun brokenConnections() {
    repeat(5) {
        Socket(host, port).use { socket ->
            socket.getOutputStream().write("POST /messages HTTP/1.1\r\nContent-Length: 500\r\n\r\n{\"te".toByteArray())
        }
    }
    repeat(3) {
        Socket(host, port).close()
    }
    check("Отправлены 8 оборванных соединений", true)
}

private fun concurrentTest(token: String) {
    val clients = 30
    val pool = Executors.newFixedThreadPool(clients)
    val replies = (1..clients).map { number ->
        pool.submit<Reply> { send("POST", "/messages", """{"text":"Сообщение из потока $number"}""", token) }
    }.map { it.get(30, TimeUnit.SECONDS) }
    pool.shutdown()

    val created = replies.count { it.code == 201 }
    val ids = replies.map { idFrom(it) }
    println()
    println("   ответов 201: $created из $clients, уникальных ID: ${ids.toSet().size}")
    check("Все 30 одновременных запросов обработаны (201)", created == clients)
    check("ID сообщений уникальны", ids.toSet().size == clients && ids.none { it < 0 })

    // Убираем за собой тестовые сообщения
    ids.forEach { send("DELETE", "/messages/$it", token = token) }
    val left = send("GET", "/messages?author=alex", token = token).text
    check("Тестовые сообщения удалены", !left.contains("Сообщение из потока"))
}
