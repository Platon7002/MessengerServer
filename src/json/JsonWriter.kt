package json

object JsonWriter {

    fun write(value: Any?, pretty: Boolean = true): String {
        val sb = StringBuilder()
        append(sb, value, 0, pretty)
        return sb.toString()
    }

    private fun append(sb: StringBuilder, value: Any?, level: Int, pretty: Boolean) {
        when (value) {
            null -> sb.append("null")
            is String -> sb.append(quote(value))
            is Boolean, is Int, is Long -> sb.append(value.toString())
            is Double, is Float -> sb.append(value.toString())
            is Map<*, *> -> appendObject(sb, value, level, pretty)
            is List<*> -> appendArray(sb, value, level, pretty)
            else -> sb.append(quote(value.toString()))
        }
    }

    private fun appendObject(sb: StringBuilder, map: Map<*, *>, level: Int, pretty: Boolean) {
        if (map.isEmpty()) {
            sb.append("{}")
            return
        }
        sb.append('{')
        var first = true
        for ((key, value) in map) {
            if (!first) {
                sb.append(',')
            }
            first = false
            newLine(sb, level + 1, pretty)
            sb.append(quote(key.toString())).append(if (pretty) ": " else ":")
            append(sb, value, level + 1, pretty)
        }
        newLine(sb, level, pretty)
        sb.append('}')
    }

    private fun appendArray(sb: StringBuilder, list: List<*>, level: Int, pretty: Boolean) {
        if (list.isEmpty()) {
            sb.append("[]")
            return
        }
        sb.append('[')
        var first = true
        for (item in list) {
            if (!first) {
                sb.append(',')
            }
            first = false
            newLine(sb, level + 1, pretty)
            append(sb, item, level + 1, pretty)
        }
        newLine(sb, level, pretty)
        sb.append(']')
    }

    private fun newLine(sb: StringBuilder, level: Int, pretty: Boolean) {
        if (pretty) {
            sb.append('\n').append("  ".repeat(level))
        }
    }

    private fun quote(value: String): String {
        val sb = StringBuilder("\"")
        for (ch in value) {
            when {
                ch == '"' -> sb.append("\\\"")
                ch == '\\' -> sb.append("\\\\")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                ch < ' ' -> sb.append("\\u%04x".format(ch.code))
                else -> sb.append(ch)
            }
        }
        return sb.append('"').toString()
    }
}
