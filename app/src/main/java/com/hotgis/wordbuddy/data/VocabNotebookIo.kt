package com.hotgis.wordbuddy.data

import org.json.JSONArray
import org.json.JSONObject

private const val EXPORT_VERSION = 1

object VocabNotebookExporter {
    fun toJson(entries: List<VocabEntry>, notebook: Notebook? = null): String {
        val root = JSONObject()
            .put("version", EXPORT_VERSION)
            .put("exportedAt", System.currentTimeMillis())
        if (notebook != null) {
            root.put(
                "notebook",
                JSONObject()
                    .put("id", notebook.id)
                    .put("name", notebook.name),
            )
        }
        root.put("entries", JSONArray().apply { entries.forEach { put(entryToJson(it)) } })
        return root.toString(2)
    }

    private fun entryToJson(entry: VocabEntry): JSONObject {
        return JSONObject()
            .put("word", entry.text)
            .put("isPhrase", entry.isPhrase)
            .put("definitions", definitionsToJson(entry.definitions))
            .put("examples", examplesToJson(entry.examples))
            .put("nearWords", stringListToJson(entry.nearWords))
            .put("synonyms", stringListToJson(entry.synonyms))
            .put("antonyms", stringListToJson(entry.antonyms))
            .put("sortOrder", entry.sortOrder)
            .put("addedAt", entry.addedAtMillis)
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
    fun fromJson(raw: String): List<VocabEntry> {
        val root = JSONObject(raw)
        val version = root.optInt("version", 0)
        if (version != EXPORT_VERSION) {
            throw IllegalArgumentException("不支持的备份版本")
        }
        val array = root.optJSONArray("entries") ?: throw IllegalArgumentException("备份文件格式无效")
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val word = obj.optString("word").trim()
                if (word.isBlank()) continue
                add(
                    VocabEntry(
                        text = word,
                        isPhrase = obj.optBoolean("isPhrase", false),
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
