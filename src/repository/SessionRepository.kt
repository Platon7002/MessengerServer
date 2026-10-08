package repository

import data.Session
import logging.ServerLogger
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

class SessionRepository(private val store: JsonFileStore, logger: ServerLogger) {

    private val lock = Any()
    private val sessions = LinkedHashMap<String, Session>()   // токен -> сессия

    init {
        for (item in store.load()) {
            val session = fromMap(item)
            if (session == null) {
                logger.error("sessions.json: пропущена некорректная запись $item")
            } else {
                sessions[session.token] = session
            }
        }
    }

    fun add(session: Session) {
        synchronized(lock) {
            sessions[session.token] = session
            try {
                store.save(sessions.values.map { it.toMap() })
            } catch (e: Exception) {
                sessions.remove(session.token)
                throw e
            }
        }
    }

    fun findByToken(token: String): Session? = synchronized(lock) { sessions[token] }

    private fun fromMap(map: Map<String, Any?>): Session? {
        val token = map["token"] as? String ?: return null
        val username = map["username"] as? String ?: return null
        val createdAt = try {
            LocalDateTime.parse(map["createdAt"] as? String ?: return null)
        } catch (e: DateTimeParseException) {
            return null
        }
        if (token.isBlank() || username.isBlank()) {
            return null
        }
        return Session(token, username, createdAt)
    }
}
