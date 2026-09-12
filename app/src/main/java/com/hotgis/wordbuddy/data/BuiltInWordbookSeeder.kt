package com.hotgis.wordbuddy.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Packaged 中考 / 高考 / CET-4 / CET-6 wordbooks.
 * Guests (and servers missing a catalog) read these from APK assets.
 * Local ids are negative so they never collide with Postgres ids.
 */
object BuiltInWordbookSeeder {
    data class Spec(
        val slug: String,
        val name: String,
        val sortOrder: Int,
        val localId: Long,
        val assetFile: String,
    )

    data class Packed(
        val notebook: Notebook,
        val heads: WordHeads,
        val entries: List<VocabEntry>,
    )

    val specs: List<Spec> = listOf(
        Spec(Notebook.ZHONGKAO_SLUG, "中考词汇", 0, -1L, "wordbooks/zhongkao.enriched.jsonl"),
        Spec(Notebook.GAOKAO_SLUG, "高考词汇", 1, -2L, "wordbooks/gaokao.enriched.jsonl"),
        Spec(Notebook.CET4_SLUG, "大学四级", 2, -3L, "wordbooks/cet4.enriched.jsonl"),
        Spec(Notebook.CET6_SLUG, "大学六级", 3, -4L, "wordbooks/cet6.enriched.jsonl"),
    )

    private val packsById = mutableMapOf<Long, Packed>()
    private val packsBySlug = mutableMapOf<String, Packed>()

    fun isBundledId(notebookId: Long): Boolean = notebookId < 0L

    fun specFor(notebookId: Long): Spec? = specs.firstOrNull { it.localId == notebookId }

    fun specForSlug(slug: String?): Spec? =
        slug?.let { key -> specs.firstOrNull { it.slug == key } }

    fun missingNotebooks(serverCatalogs: List<Notebook>): List<Notebook> {
        val slugs = serverCatalogs.mapNotNull { it.slug }.toSet()
        return specs.filter { it.slug !in slugs }.map { it.toNotebook() }
    }

    fun cached(notebookId: Long): Packed? = packsById[notebookId]

    @Synchronized
    fun load(context: Context, notebookId: Long): Packed? {
        packsById[notebookId]?.let { return it }
        val spec = specFor(notebookId) ?: return null
        return loadSpec(context, spec)
    }

    @Synchronized
    fun loadBySlug(context: Context, slug: String): Packed? {
        packsBySlug[slug]?.let { return it }
        val spec = specForSlug(slug) ?: return null
        return loadSpec(context, spec)
    }

    private fun Spec.toNotebook(wordCount: Int = 0): Notebook = Notebook(
        id = localId,
        name = name,
        sortOrder = sortOrder,
        kind = Notebook.KIND_CATALOG,
        slug = slug,
        wordCount = wordCount,
    )

    private fun loadSpec(context: Context, spec: Spec): Packed {
        val entries = ArrayList<VocabEntry>()
        val letterIndex = linkedMapOf<Char, Int>()
        context.assets.open(spec.assetFile).bufferedReader().use { reader ->
            var index = 0
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEachLine
                val obj = JSONObject(trimmed)
                val text = obj.optString("text").trim()
                if (text.isEmpty()) return@forEachLine
                val id = wordId(spec.localId, index)
                val letter = wordInitial(text)
                if (letter !in letterIndex) letterIndex[letter] = index
                entries += VocabEntry(
                    id = id,
                    notebookId = spec.localId,
                    text = text,
                    isPhrase = obj.optBoolean("isPhrase") || text.contains(' '),
                    ipaUk = obj.optString("ipaUk").takeIf { it.isNotBlank() },
                    ipaUs = obj.optString("ipaUs").takeIf { it.isNotBlank() },
                    definitions = parseDefinitions(obj.optJSONArray("definitions")),
                    examples = parseExamples(obj.optJSONArray("examples")),
                    nearWords = parseStringList(obj.optJSONArray("nearWords")),
                    synonyms = parseStringList(obj.optJSONArray("synonyms")),
                    antonyms = parseStringList(obj.optJSONArray("antonyms")),
                    sortOrder = index,
                )
                index += 1
            }
        }
        val heads = WordHeads(
            items = entries.map { entry ->
                WordHead(
                    id = entry.id,
                    text = entry.text,
                    isPhrase = entry.isPhrase,
                    ipaUk = entry.ipaUk,
                    ipaUs = entry.ipaUs,
                    sortOrder = entry.sortOrder,
                )
            },
            total = entries.size,
            letterIndex = letterIndex,
        )
        val packed = Packed(spec.toNotebook(entries.size), heads, entries)
        packsById[spec.localId] = packed
        packsBySlug[spec.slug] = packed
        return packed
    }

    private fun wordId(localNotebookId: Long, index: Int): Long =
        (-localNotebookId) * 100_000_000L + index + 1

    private fun wordInitial(text: String): Char {
        val first = text.trim().firstOrNull()?.uppercaseChar() ?: return '#'
        return if (first in 'A'..'Z') first else '#'
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
                add(
                    ExampleSentence(
                        english = obj.optString("en").ifBlank { obj.optString("english") },
                        chinese = obj.optString("zh").ifBlank { obj.optString("chinese") },
                    ),
                )
            }
        }
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }
}
