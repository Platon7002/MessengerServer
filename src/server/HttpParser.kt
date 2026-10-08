package server

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLDecoder

class HttpParser {

    private companion object {
        const val MAX_LINE_BYTES = 8 * 1024
        const val MAX_HEADERS = 100
        const val MAX_BODY_BYTES = 1024 * 1024
    }
    fun parse(input: InputStream): HttpRequest? {
        val requestLine = readLine(input, firstLine = true) ?: return null

        val parts = requestLine.split(' ')
        if (parts.size != 3) {
            throw HttpParseException("Неверная стартовая строка запроса")
        }
        val method = parts[0]
        val target = parts[1]
        val version = parts[2]
        if (!Regex("[A-Z]{3,10}").matches(method)) {
            throw HttpParseException("Неверный HTTP-метод")
        }
        if (!version.startsWith("HTTP/1.")) {
            throw HttpParseException("Поддерживается только HTTP/1.x")
        }

        val headers = readHeaders(input)
        if (headers.containsKey("transfer-encoding")) {
            throw HttpParseException("Заголовок Transfer-Encoding не поддерживается, используйте Content-Length")
        }
        val body = readBody(input, headers)

        val pathPart = target.substringBefore('#').substringBefore('?')
        if (!pathPart.startsWith("/")) {
            throw HttpParseException("Путь должен начинаться с /")
        }
        val path = pathPart.trimEnd('/').ifEmpty { "/" }
        val query = parseQuery(target.substringBefore('#').substringAfter('?', ""))

        return HttpRequest(method, path, query, headers, body)
    }

    private fun readHeaders(input: InputStream): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        while (true) {
            val line = readLine(input, firstLine = false) ?: throw HttpParseException("Запрос оборван")
            if (line.isEmpty()) {
                return headers
            }
            if (headers.size >= MAX_HEADERS) {
                throw HttpParseException("Слишком много заголовков")
            }
            val colon = line.indexOf(':')
            if (colon <= 0) {
                throw HttpParseException("Неверный заголовок: «$line»")
            }
            val name = line.substring(0, colon).trim().lowercase()
            headers[name] = line.substring(colon + 1).trim()
        }
    }

    private fun readBody(input: InputStream, headers: Map<String, String>): String {
        val lengthText = headers["content-length"] ?: return ""
        val length = lengthText.toIntOrNull()
        if (length == null || length < 0) {
            throw HttpParseException("Неверный Content-Length")
        }
        if (length > MAX_BODY_BYTES) {
            throw HttpParseException("Тело запроса слишком большое (максимум $MAX_BODY_BYTES байт)")
        }
        val bytes = input.readNBytes(length)
        if (bytes.size < length) {
            throw HttpParseException("Тело запроса оборвано: ожидалось $length байт, получено ${bytes.size}")
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun readLine(input: InputStream, firstLine: Boolean): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) {
                if (buffer.size() == 0 && firstLine) {
                    return null
                }
                throw HttpParseException("Запрос оборван")
            }
            if (b == '\n'.code) {
                break
            }
            buffer.write(b)
            if (buffer.size() > MAX_LINE_BYTES) {
                throw HttpParseException("Слишком длинная строка в запросе")
            }
        }
        return buffer.toString(Charsets.ISO_8859_1).removeSuffix("\r")
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isEmpty()) {
            return emptyMap()
        }
        val result = LinkedHashMap<String, String>()
        for (pair in raw.split('&')) {
            if (pair.isEmpty()) {
                continue
            }
            val key = decode(pair.substringBefore('='))
            val value = decode(pair.substringAfter('=', ""))
            result[key] = value
        }
        return result
    }

    private fun decode(text: String): String = try {
        URLDecoder.decode(text, Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        throw HttpParseException("Неверное кодирование параметров в адресе")
    }
}
