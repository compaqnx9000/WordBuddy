package com.hotgis.wordbuddy.data

import org.json.JSONArray
import org.json.JSONObject

data class VocabBackupNotebook(
    val name: String,
    val entries: List<VocabEntry>,
)

object VocabNotebookExporter {
    fun toJson(books: List<VocabBackupNotebook>): String {
        val notebooks = JSONArray()
        books.forEach { book ->
            notebooks.put(
                JSONObject()
                    .put("name", book.name)
                    .put("entries", JSONArray().apply { book.entries.forEach { put(entryToJson(it)) } }),
            )
        }
        return JSONObject()
            .put("version", 2)
            .put("exportedAt", System.currentTimeMillis())
            .put("notebooks", notebooks)
            .toString(2)
    }

    private fun entryToJson(entry: VocabEntry): JSONObject {
        val json = JSONObject()
            .put("word", entry.text)
            .put("isPhrase", entry.isPhrase)
            .put("definitions", definitionsToJson(entry.definitions))
            .put("examples", examplesToJson(entry.examples))
            .put("nearWords", stringListToJson(entry.nearWords))
            .put("synonyms", stringListToJson(entry.synonyms))
            .put("antonyms", stringListToJson(entry.antonyms))
            .put("sortOrder", entry.sortOrder)
            .put("addedAt", entry.addedAtMillis)
        if (!entry.ipaUk.isNullOrBlank()) json.put("ipaUk", entry.ipaUk)
        if (!entry.ipaUs.isNullOrBlank()) json.put("ipaUs", entry.ipaUs)
        return json
    }

    private fun definitionsToJson(definitions: List<Definition>): JSONArray {
        val array = JSONArray()
        definitions.forEach { def ->
            array.put(
                JSONObject()
                    .put("pos", def.pos)
                    .put("meaning", def.meaning)
                    .put("user", def.isUserAdded),
            )
        }
        return array
    }

    private fun examplesToJson(examples: List<ExampleSentence>): JSONArray {
        val array = JSONArray()
        examples.forEach { example ->
            array.put(
                JSONObject()
                    .put("en", example.english)
                    .put("zh", example.chinese),
            )
        }
        return array
    }

    private fun stringListToJson(words: List<String>): JSONArray {
        val array = JSONArray()
        words.forEach { array.put(it) }
        return array
    }
}

object VocabNotebookImporter {
    fun fromBackup(raw: String): List<VocabBackupNotebook> {
        val root = JSONObject(raw)
        when (val version = root.optInt("version", 0)) {
            2 -> {
                val array = root.optJSONArray("notebooks") ?: throw IllegalArgumentException("备份文件格式无效")
                if (array.length() == 0) throw IllegalArgumentException("备份文件格式无效")
                return buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val name = obj.optString("name").trim()
                        if (name.isBlank()) continue
                        add(VocabBackupNotebook(name, parseEntries(obj.optJSONArray("entries"))))
                    }
                }
            }
            1 -> {
                val notebook = root.optJSONObject("notebook")
                val name = notebook?.optString("name")?.trim().orEmpty().ifBlank { "导入的生词本" }
                return listOf(VocabBackupNotebook(name, parseEntries(root.optJSONArray("entries"))))
            }
            else -> throw IllegalArgumentException("不支持的备份版本")
        }
    }

    private fun parseEntries(array: JSONArray?): List<VocabEntry> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val word = obj.optString("word").trim()
                if (word.isBlank()) continue
                add(
                    VocabEntry(
                        text = word,
                        isPhrase = obj.optBoolean("isPhrase", false),
                        ipaUk = obj.optString("ipaUk").trim().ifBlank { null },
                        ipaUs = obj.optString("ipaUs").trim().ifBlank { null },
                        definitions = parseDefinitions(obj.optJSONArray("definitions")),
                        examples = parseExamples(obj.optJSONArray("examples")),
                        nearWords = parseStringList(obj.optJSONArray("nearWords")),
                        synonyms = parseStringList(obj.optJSONArray("synonyms")),
                        antonyms = parseStringList(obj.optJSONArray("antonyms")),
                        sortOrder = obj.optInt("sortOrder", 0),
                        addedAtMillis = obj.optLong("addedAt", System.currentTimeMillis()),
                    ),
                )
            }
        }
    }

    private fun parseDefinitions(array: JSONArray?): List<Definition> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(
                    Definition(
                        pos = obj.optString("pos"),
                        meaning = obj.optString("meaning"),
                        isUserAdded = obj.optBoolean("user", false),
                    ),
                )
            }
        }
    }

    private fun parseExamples(array: JSONArray?): List<ExampleSentence> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val en = obj.optString("en").trim()
                val zh = obj.optString("zh").trim()
                if (en.isNotBlank() && zh.isNotBlank()) add(ExampleSentence(en, zh))
            }
        }
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val word = array.optString(i).trim()
                if (word.isNotBlank()) add(word)
            }
        }
    }
}
