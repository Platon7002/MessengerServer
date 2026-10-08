package repository

import json.JsonFormatException
import json.JsonParser
import json.JsonWriter
import logging.ServerLogger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class JsonFileStore(private val file: File, private val logger: ServerLogger) {

    fun load(): List<Map<String, Any?>> {
        if (!file.exists()) {
            return emptyList()
        }
        try {
            val text = file.readText(Charsets.UTF_8).removePrefix("﻿")
            if (text.isBlank()) {
                return emptyList()
            }
            val root = JsonParser(text).parse() as? List<*>
                ?: throw JsonFormatException("в файле должен быть список [ ... ]")
            return root.map { item ->
                if (item !is Map<*, *>) {
                    throw JsonFormatException("элемент списка не является объектом")
                }
                item.entries.associate { (key, value) -> key.toString() to value }
            }
        } catch (e: JsonFormatException) {
            quarantine(e)
        } catch (e: IOException) {
            quarantine(e)
        }
        return emptyList()
    }

    private fun quarantine(error: Exception) {
        logger.error("Файл ${file.name} повреждён, начинаем с пустого", error)
        try {
            val broken = File(file.path + ".broken")
            Files.move(file.toPath(), broken.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (ignored: Exception) {
        }
    }

    @Throws(IOException::class)
    fun save(items: List<Map<String, Any?>>) {
        val temp = File(file.path + ".tmp")
        temp.writeText(JsonWriter.write(items), Charsets.UTF_8)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
