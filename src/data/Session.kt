package data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Session(
    val token: String,
    val username: String,
    val createdAt: LocalDateTime
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "token" to token,
        "username" to username,
        "createdAt" to createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    )
}
