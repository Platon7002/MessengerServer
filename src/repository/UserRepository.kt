package repository

import data.User
import logging.ServerLogger

class UserRepository(private val store: JsonFileStore, logger: ServerLogger) {

    private val lock = Any()
    private val users = mutableListOf<User>()
    private var nextId = 1

    init {
        val loaded = store.load()
        for (item in loaded) {
            val user = fromMap(item)
            if (user == null) {
                logger.error("users.json: пропущена некорректная запись $item")
            } else {
                users.add(user)
            }
        }
        nextId = (users.maxOfOrNull { it.id } ?: 0) + 1
    }

    fun findByUsername(username: String): User? = synchronized(lock) {
        users.find { it.username.equals(username, ignoreCase = true) }
    }

    fun addIfAbsent(username: String, salt: String, passwordHash: String): User? = synchronized(lock) {
        if (users.any { it.username.equals(username, ignoreCase = true) }) {
            return null
        }
        val user = User(nextId++, username, salt, passwordHash)
        users.add(user)
        try {
            store.save(users.map { it.toMap() })
        } catch (e: Exception) {
            users.remove(user)
            nextId--
            throw e
        }
        user
    }

    private fun fromMap(map: Map<String, Any?>): User? {
        val id = (map["id"] as? Long)?.toInt() ?: return null
        val username = map["username"] as? String ?: return null
        val salt = map["salt"] as? String ?: return null
        val hash = map["passwordHash"] as? String ?: return null
        if (id < 1 || username.isBlank()) {
            return null
        }
        return User(id, username, salt, hash)
    }
}
