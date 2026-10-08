package repository

import data.Message
import logging.ServerLogger
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

class MessageRepository(private val store: JsonFileStore, logger: ServerLogger) {

    private val lock = Any()
    private val messages = mutableListOf<Message>()
    private var nextId = 1                       // генерация ID

    init {
        for (item in store.load()) {
            val message = fromMap(item)
            if (message == null) {
                logger.error("messages.json: пропущена некорректная запись $item")
            } else {
                messages.add(message)
            }
        }
        messages.sortBy { it.id }
        nextId = (messages.maxOfOrNull { it.id } ?: 0) + 1
    }

    fun add(author: String, text: String): Message = synchronized(lock) {
        val message = Message(nextId++, author, text, LocalDateTime.now().withNano(0))
        messages.add(message)
        try {
            save()
        } catch (e: Exception) {
            messages.remove(message)
            nextId--
            throw e
        }
        message
    }

    fun findAll(): List<Message> = synchronized(lock) { messages.toList() }

    fun findById(id: Int): Message? = synchronized(lock) { messages.find { it.id == id } }

    fun delete(id: Int): Boolean = synchronized(lock) {
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) {
            return false
        }
        val removed = messages.removeAt(index)
        try {
            save()
        } catch (e: Exception) {
            messages.add(index, removed)          // откат
            throw e
        }
        true
    }

    private fun save() = store.save(messages.map { it.toMap() })

    private fun fromMap(map: Map<String, Any?>): Message? {
        val id = (map["id"] as? Long)?.toInt() ?: return null
        val author = map["author"] as? String ?: return null
        val text = map["text"] as? String ?: return null
        val createdAt = try {
            LocalDateTime.parse(map["createdAt"] as? String ?: return null)
        } catch (e: DateTimeParseException) {
            return null
        }
        if (id < 1 || author.isBlank()) {
            return null
        }
        return Message(id, author, text, createdAt)
    }
}
