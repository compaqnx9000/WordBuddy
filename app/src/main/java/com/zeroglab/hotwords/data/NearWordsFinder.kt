package com.zeroglab.hotwords.data

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Finds common English words with edit distance 1
 * (one letter inserted, deleted, or substituted) via Datamuse.
 */
object NearWordsFinder {
    private val lettersOnly = Regex("^[a-z]+$")

    suspend fun find(word: String, min: Int = 2, max: Int = 5): List<String> = withContext(Dispatchers.IO) {
        val q = word.trim().lowercase()
        if (q.length < 2 || !lettersOnly.matches(q)) return@withContext emptyList()

        coroutineScope {
            val jobs = buildList {
                // delete one letter
                for (i in q.indices) {
                    val shorter = q.removeRange(i, i + 1)
                    if (shorter.length >= 2) add(async { scoredExact(shorter) })
                }
                // insert one letter
                for (i in 0..q.length) {
                    val pattern = q.substring(0, i) + "?" + q.substring(i)
                    add(async { scoredPattern(pattern) })
                }
                // substitute one letter
                for (i in q.indices) {
                    val pattern = q.substring(0, i) + "?" + q.substring(i + 1)
                    add(async { scoredPattern(pattern) })
                }
            }
            jobs.awaitAll()
                .flatten()
                .filter { (w, freq) ->
                    w != q &&
                        lettersOnly.matches(w) &&
                        editDistanceOne(q, w) &&
                        !isInflectionOf(q, w) &&
                        (freq >= 0.05 || w.length <= q.length + 1)
                }
                .groupBy { it.first }
                .map { (w, rows) -> w to rows.maxOf { it.second } }
                .sortedWith(
                    compareByDescending<Pair<String, Double>> { it.second }
                        .thenBy { abs(it.first.length - q.length) }
                        .thenBy { it.first.length }
                        .thenBy { it.first },
                )
                .map { it.first }
                .take(max)
                .let { picked ->
                    if (picked.size >= min) picked else picked
                }
        }
    }

    fun editDistanceOne(a: String, b: String): Boolean {
        if (abs(a.length - b.length) > 1) return false
        if (a.length == b.length) {
            var diff = 0
            for (i in a.indices) {
                if (a[i] != b[i] && ++diff > 1) return false
            }
            return diff == 1
        }
        val shorter = if (a.length < b.length) a else b
        val longer = if (a.length < b.length) b else a
        var si = 0
        var li = 0
        var skipped = 0
        while (si < shorter.length && li < longer.length) {
            if (shorter[si] == longer[li]) {
                si++
                li++
            } else {
                if (++skipped > 1) return false
                li++
            }
        }
        skipped += longer.length - li
        return skipped == 1 && si == shorter.length
    }

    private fun isInflectionOf(base: String, other: String): Boolean {
        val forms = buildSet {
            add(base + "s")
            add(base + "es")
            add(base + "ed")
            add(base + "ing")
            if (base.endsWith("e")) {
                add(base + "d")
                add(base.dropLast(1) + "ing")
            }
            if (base.endsWith("y") && base.length > 1 && base[base.lastIndex - 1] !in "aeiou") {
                add(base.dropLast(1) + "ies")
                add(base.dropLast(1) + "ied")
            }
        }
        return other in forms
    }

    private fun scoredExact(sp: String): List<Pair<String, Double>> {
        val raw = runCatching { httpGet(datamuseUrl(sp, max = 6)) }.getOrNull() ?: return emptyList()
        return parseScored(raw).filter { it.first.equals(sp, ignoreCase = true) }
    }

    private fun scoredPattern(pattern: String): List<Pair<String, Double>> {
        val raw = runCatching { httpGet(datamuseUrl(pattern, max = 12)) }.getOrNull() ?: return emptyList()
        return parseScored(raw)
    }

    private fun parseScored(raw: String): List<Pair<String, Double>> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val word = obj.optString("word").orEmpty().trim().lowercase()
                if (word.isBlank()) continue
                add(word to freqFromTags(obj.optJSONArray("tags")))
            }
        }
    }

    private fun freqFromTags(tags: JSONArray?): Double {
        if (tags == null) return 0.0
        for (i in 0 until tags.length()) {
            val tag = tags.optString(i)
            if (tag.startsWith("f:")) {
                return tag.removePrefix("f:").toDoubleOrNull() ?: 0.0
            }
        }
        return 0.0
    }

    private fun datamuseUrl(sp: String, max: Int): String {
        val encoded = URLEncoder.encode(sp, StandardCharsets.UTF_8.name())
        return "https://api.datamuse.com/words?sp=$encoded&md=f&max=$max"
    }

    private fun httpGet(url: String): String {
        val conn = java.net.URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "HotWords/0.1 (Android)")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            if (code !in 200..299) error("http $code")
            return body
        } finally {
            conn.disconnect()
        }
    }
}
