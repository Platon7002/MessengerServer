package data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Message(
    val id: Int,
    val author: String,
    val text: String,
    val createdAt: LocalDateTime
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "author" to author,
        "text" to text,
        "createdAt" to createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    )
}
