package json

class JsonFormatException(message: String) : Exception(message)

class JsonParser(private val text: String) {

    private var pos = 0

    fun parse(): Any? {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        if (pos != text.length) {
            fail("лишние символы после конца JSON")
        }
        return value
    }

    private fun fail(message: String): Nothing =
        throw JsonFormatException("$message (позиция $pos)")

    private fun peek(): Char? = text.getOrNull(pos)

    private fun skipWhitespace() {
        while (pos < text.length && text[pos].isWhitespace()) {
            pos++
        }
    }

    private fun expect(ch: Char) {
        if (peek() != ch) {
            fail("ожидался символ '$ch'")
        }
        pos++
    }

    private fun parseValue(): Any? {
        val ch = peek() ?: fail("неожиданный конец данных")
        return when (ch) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f', 'n' -> parseLiteral()
            else -> parseNumber()
        }
    }

    private fun parseObject(): Map<String, Any?> {
        expect('{')
        val result = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (peek() == '}') {
            pos++
            return result
        }
        var finished = false
        while (!finished) {
            skipWhitespace()
            if (peek() != '"') {
                fail("ожидался ключ в кавычках")
            }
            val key = parseString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            result[key] = parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                '}' -> {
                    pos++
                    finished = true
                }
                else -> fail("ожидалась ',' или '}'")
            }
        }
        return result
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        val result = mutableListOf<Any?>()
        skipWhitespace()
        if (peek() == ']') {
            pos++
            return result
        }
        var finished = false
        while (!finished) {
            skipWhitespace()
            result.add(parseValue())
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                ']' -> {
                    pos++
                    finished = true
                }
                else -> fail("ожидалась ',' или ']'")
            }
        }
        return result
    }

    private fun parseString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            val ch = peek() ?: fail("строка не закрыта кавычкой")
            pos++
            when {
                ch == '"' -> return sb.toString()
                ch == '\\' -> sb.append(parseEscape())
                ch < ' ' -> fail("недопустимый управляющий символ в строке")
                else -> sb.append(ch)
            }
        }
    }

    private fun parseEscape(): Char {
        val ch = peek() ?: fail("обрыв после символа '\\'")
        pos++
        return when (ch) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (pos + 4 > text.length) {
                    fail("неполная последовательность \\u")
                }
                val code = text.substring(pos, pos + 4).toIntOrNull(16)
                    ?: fail("неверная последовательность \\u")
                pos += 4
                code.toChar()
            }
            else -> fail("неизвестная escape-последовательность '\\$ch'")
        }
    }

    private fun parseLiteral(): Any? {
        for ((word, value) in listOf("true" to true, "false" to false, "null" to null)) {
            if (text.startsWith(word, pos)) {
                pos += word.length
                return value
            }
        }
        fail("неизвестное значение")
    }

    private fun parseNumber(): Any {
        val start = pos
        while (pos < text.length && text[pos] in "+-0123456789.eE") {
            pos++
        }
        val token = text.substring(start, pos)
        if (token.isEmpty()) {
            fail("неожиданный символ '${text[pos]}'")
        }
        val isFraction = token.any { it == '.' || it == 'e' || it == 'E' }
        val number: Any? = if (isFraction) token.toDoubleOrNull() else token.toLongOrNull()
        return number ?: fail("неверное число '$token'")
    }
}
