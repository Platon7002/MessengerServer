package service

import data.Message
import repository.MessageRepository

class MessageService(private val messages: MessageRepository) {

    private val maxTextLength = 1000

    fun create(author: String, text: String?): Message {
        val clean = text?.trim().orEmpty()
        if (clean.isEmpty()) {
            throw ServiceException(400, "Поле text обязательно и не может быть пустым")
        }
        if (clean.length > maxTextLength) {
            throw ServiceException(400, "Текст сообщения не должен быть длиннее $maxTextLength символов")
        }
        return messages.add(author, clean)
    }

    fun list(author: String?, limit: Int?): List<Message> {
        if (limit != null && limit < 1) {
            throw ServiceException(400, "Параметр limit должен быть положительным числом")
        }
        var result = messages.findAll()
        if (!author.isNullOrBlank()) {
            result = result.filter { it.author.equals(author, ignoreCase = true) }
        }
        if (limit != null) {
            result = result.takeLast(limit)
        }
        return result
    }

    fun delete(requester: String, id: Int) {
        val message = messages.findById(id)
            ?: throw ServiceException(404, "Сообщение с ID $id не найдено")
        if (message.author != requester) {
            throw ServiceException(403, "Удалить сообщение может только его автор")
        }
        if (!messages.delete(id)) {
            throw ServiceException(404, "Сообщение с ID $id уже удалено")
        }
    }
}
