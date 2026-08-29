package com.zeroglab.hotwords.data

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DictionaryClient {
    suspend fun lookup(query: String): VocabEntry = withContext(Dispatchers.IO) {
        val q = query.trim()
        require(q.isNotEmpty()) { "empty query" }

        var youdaoRoot: JSONObject? = null
        val fromYoudao = runCatching {
            val raw = httpGet(youdaoUrl(q))
            youdaoRoot = JSONObject(raw)
            parseYoudao(youdaoRoot!!, q)
        }.getOrNull()

        val base = when {
            fromYoudao != null && fromYoudao.definitions.isNotEmpty() -> fromYoudao
            else -> {
                val fromBaidu = runCatching {
                    parseBaiduSug(httpGet(baiduSugUrl(q)), q)
                }.getOrNull()
                fromBaidu?.copy(ipaUk = fromYoudao?.ipaUk, ipaUs = fromYoudao?.ipaUs)
                    ?: error("no definition")
            }
        }

        val text = base.text
        coroutineScope {
            val synDeferred = async { loadCommonSynonyms(youdaoRoot, text) }
            val antDeferred = async { loadCommonAntonyms(text) }
            val corpus = collectCorpusExamples(youdaoRoot)
            val examples = SenseExampleBuilder.examplesFor(text, base.definitions, corpus)
            base.copy(
                examples = examples,
                synonyms = synDeferred.await(),
                antonyms = antDeferred.await(),
            )
        }
    }

    private fun parseYoudao(root: JSONObject, fallback: String): VocabEntry? {
        val wordObj = jsonObjOrFirst(jsonObjOrFirst(root, "ec"), "word")
            ?: jsonObjOrFirst(jsonObjOrFirst(root, "simple"), "word")
            ?: return null
        val text = parseReturnPhrase(wordObj) ?: fallback
        val uk = wordObj.optString("ukphone").orEmpty().ifBlank { null }
        val us = wordObj.optString("usphone").orEmpty().ifBlank { null }
        val defs = parseTrs(wordObj.optJSONArray("trs"))
        if (defs.isEmpty() && uk == null && us == null) return null
        return VocabEntry(
            text = text,
            isPhrase = text.contains(' '),
            ipaUk = uk,
            ipaUs = us,
            definitions = defs.ifEmpty { listOf(Definition("", text)) },
            examples = emptyList(),
            synonyms = emptyList(),
        )
    }

    private fun collectCorpusExamples(root: JSONObject?): List<ExampleSentence> {
        if (root == null) return emptyList()
        val candidates = mutableListOf<ExampleSentence>()
        collectSentencePairs(root.optJSONObject("blng_sents_part")?.optJSONArray("sentence-pair"), candidates)
        collectAuthSentences(root.optJSONObject("auth_sents_part")?.optJSONArray("sent"), candidates)
        return candidates
            .distinctBy { it.english.lowercase() }
            .sortedWith(compareBy<ExampleSentence> { everydayScore(it) }.thenBy { it.english.length })
    }

    private fun collectSentencePairs(pairs: JSONArray?, out: MutableList<ExampleSentence>) {
        if (pairs == null) return
        for (i in 0 until pairs.length()) {
            val obj = pairs.optJSONObject(i) ?: continue
            val eng = cleanExampleText(
                obj.optString("sentence").ifBlank { obj.optString("sentence-eng") },
            )
            val zh = cleanExampleText(obj.optString("sentence-translation"))
            if (eng.isNotBlank() && zh.isNotBlank() && !looksLikeProperNameExample(eng, zh)) {
                out += ExampleSentence(eng, zh)
            }
        }
    }

    private fun collectAuthSentences(sents: JSONArray?, out: MutableList<ExampleSentence>) {
        if (sents == null) return
        for (i in 0 until sents.length()) {
            val obj = sents.optJSONObject(i) ?: continue
            val eng = cleanExampleText(
                obj.optString("foreign").ifBlank { obj.optString("speech") },
            )
            val zh = cleanExampleText(obj.optString("translation"))
            if (eng.isNotBlank() && zh.isNotBlank() && !looksLikeProperNameExample(eng, zh)) {
                out += ExampleSentence(eng, zh)
            }
        }
    }

    private fun looksLikeProperNameExample(eng: String, zh: String): Boolean {
        if (zh.contains("人名")) return true
        // "Richard Budge, Parks Manager..."
        return Regex("""^[A-Z][a-z]+ [A-Z][a-z]+,""").containsMatchIn(eng)
    }

    private fun cleanExampleText(raw: String): String {
        return raw
            .replace(Regex("</?b>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Lower is better for everyday usefulness. */
    private fun everydayScore(example: ExampleSentence): Int {
        val eng = example.english
        var score = 0
        if (eng.length > 90) score += 3
        if (eng.length > 60) score += 1
        if (eng.contains(',') && eng.length > 50) score += 1
        val formalHints = listOf("whereas", "hereby", "thereof", "aforesaid", "pursuant")
        if (formalHints.any { eng.contains(it, ignoreCase = true) }) score += 4
        return score
    }

    private suspend fun loadCommonSynonyms(root: JSONObject?, word: String): List<String> {
        val fromDatamuse = datamuseRelatedScored("rel_syn", word)
            .ifEmpty { datamuseTagged(word, tag = "syn") }
        val fromYoudao = parseYoudaoSynonyms(root)
        return mergeCommonWords(
            primary = fromDatamuse.sortedByDescending { it.second }.map { it.first },
            scored = fromYoudao.map { it to 1.0 },
            exclude = word,
        )
    }

    private suspend fun loadCommonAntonyms(word: String): List<String> {
        val fromAnt = datamuseRelatedScored("rel_ant", word)
        val fromMlAnt = datamuseTagged(word, tag = "ant")
        return mergeCommonWords(
            primary = fromAnt.sortedByDescending { it.second }.map { it.first },
            scored = fromMlAnt,
            exclude = word,
        )
    }

    private fun mergeCommonWords(
        primary: List<String>,
        scored: List<Pair<String, Double>>,
        exclude: String,
    ): List<String> {
        val out = linkedSetOf<String>()
        primary.forEach { w ->
            val lw = w.lowercase()
            if (isSingleCommonLemma(lw) && !lw.equals(exclude, true)) out += lw
        }
        scored.sortedByDescending { it.second }.forEach { (w, freq) ->
            if (out.size >= 5) return@forEach
            if (freq < 0.3 && out.size >= 2) return@forEach
            val lw = w.lowercase()
            if (isSingleCommonLemma(lw) && !lw.equals(exclude, true)) out += lw
        }
        return out.take(5)
    }

    private fun isSingleCommonLemma(word: String): Boolean {
        val w = word.trim().lowercase()
        if (w.length < 2 || w.length > 14) return false
        if (w.contains(' ')) return false
        if (!w.all { it.isLetter() || it == '-' }) return false
        return true
    }

    private fun parseYoudaoSynonyms(root: JSONObject?): List<String> {
        if (root == null) return emptyList()
        val synos = root.optJSONObject("syno")?.optJSONArray("synos") ?: return emptyList()
        val words = linkedSetOf<String>()
        for (i in 0 until synos.length()) {
            val ws = synos.optJSONObject(i)
                ?.optJSONObject("syno")
                ?.optJSONArray("ws")
                ?: continue
            for (j in 0 until ws.length()) {
                val w = ws.optJSONObject(j)?.optString("w").orEmpty().trim()
                if (isSingleCommonLemma(w)) words += w.lowercase()
            }
        }
        return words.toList()
    }

    private fun datamuseRelatedScored(rel: String, word: String): List<Pair<String, Double>> {
        val q = word.trim().lowercase()
        if (q.length < 2 || q.contains(' ')) return emptyList()
        val url = "https://api.datamuse.com/words?$rel=${enc(q)}&md=f&max=20"
        val raw = runCatching { httpGet(url) }.getOrNull() ?: return emptyList()
        return parseDatamuseScored(raw)
    }

    private fun datamuseTagged(word: String, tag: String): List<Pair<String, Double>> {
        val q = word.trim().lowercase()
        if (q.length < 2 || q.contains(' ')) return emptyList()
        val url = "https://api.datamuse.com/words?ml=${enc(q)}&md=f&max=25"
        val raw = runCatching { httpGet(url) }.getOrNull() ?: return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val tags = obj.optJSONArray("tags") ?: continue
                var matched = false
                var freq = 0.0
                for (j in 0 until tags.length()) {
                    val t = tags.optString(j)
                    if (t == tag) matched = true
                    if (t.startsWith("f:")) freq = t.removePrefix("f:").toDoubleOrNull() ?: 0.0
                }
                if (!matched) continue
                val w = obj.optString("word").orEmpty().trim().lowercase()
                if (isSingleCommonLemma(w)) add(w to freq)
            }
        }
    }

    private fun parseDatamuseScored(raw: String): List<Pair<String, Double>> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val w = obj.optString("word").orEmpty().trim().lowercase()
                if (!isSingleCommonLemma(w)) continue
                var freq = 0.0
                val tags = obj.optJSONArray("tags")
                if (tags != null) {
                    for (j in 0 until tags.length()) {
                        val t = tags.optString(j)
                        if (t.startsWith("f:")) {
                            freq = t.removePrefix("f:").toDoubleOrNull() ?: 0.0
                        }
                    }
                }
                add(w to freq)
            }
        }
    }

    private fun parseReturnPhrase(word: JSONObject): String? {
        if (!word.has("return-phrase") || word.isNull("return-phrase")) return null
        return when (val value = word.get("return-phrase")) {
            is String -> value.trim().ifBlank { null }
            is JSONObject -> extractPlainText(value.opt("l")?.let { if (it is JSONObject) it.opt("i") else it })
            else -> null
        }
    }

    private fun parseTrs(trs: JSONArray?): List<Definition> {
        if (trs == null) return emptyList()
        return buildList {
            for (i in 0 until trs.length()) {
                val item = trs.optJSONObject(i) ?: continue
                val tran = item.optString("tran").trim()
                if (tran.isNotBlank()) {
                    add(Definition(item.optString("pos").trim(), tran))
                    continue
                }
                val trArray = item.optJSONArray("tr") ?: continue
                for (j in 0 until trArray.length()) {
                    val tr = trArray.optJSONObject(j) ?: continue
                    val payload = tr.optJSONObject("l")?.opt("i")
                    extractPlainTextList(payload).forEach { line ->
                        add(splitPosMeaning(line))
                    }
                }
            }
        }
    }

    private fun parseBaiduSug(raw: String, query: String): VocabEntry? {
        val root = JSONObject(raw)
        val data = root.optJSONArray("data") ?: return null
        var meaning: String? = null
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val key = item.optString("k")
            if (key.equals(query, ignoreCase = true)) {
                meaning = item.optString("v").trim()
                break
            }
        }
        if (meaning.isNullOrBlank() && data.length() > 0) {
            meaning = data.optJSONObject(0)?.optString("v")?.trim()
        }
        if (meaning.isNullOrBlank()) return null
        return VocabEntry(
            text = query,
            isPhrase = query.contains(' '),
            definitions = listOf(Definition("", meaning)),
        )
    }

    private fun splitPosMeaning(raw: String): Definition {
        val match = POS_PREFIX.find(raw.trim())
        return if (match != null) {
            Definition(match.groupValues[1], match.groupValues[2].trim())
        } else {
            Definition("", raw.trim())
        }
    }

    private fun extractPlainText(value: Any?): String? {
        return when (value) {
            is String -> value.trim().ifBlank { null }
            is JSONArray -> {
                buildString {
                    for (i in 0 until value.length()) {
                        val part = value.optString(i).trim()
                        if (part.isNotBlank()) {
                            if (isNotEmpty()) append(' ')
                            append(part)
                        }
                    }
                }.ifBlank { null }
            }
            else -> null
        }
    }

    private fun extractPlainTextList(value: Any?): List<String> {
        return when (value) {
            is String -> listOf(value.trim()).filter { it.isNotBlank() }
            is JSONArray -> buildList {
                for (i in 0 until value.length()) {
                    val part = value.optString(i).trim()
                    if (part.isNotBlank()) add(part)
                }
            }
            else -> emptyList()
        }
    }

    private fun youdaoUrl(query: String): String {
        return "https://dict.youdao.com/jsonapi?q=${enc(query)}&doctype=json"
    }

    private fun baiduSugUrl(query: String): String {
        return "https://fanyi.baidu.com/sug?kw=${enc(query)}"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun httpGet(url: String): String {
        val conn = java.net.URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) HotWords/0.1")
            conn.setRequestProperty("Accept", "application/json,text/plain,*/*")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            if (code !in 200..299) error("http $code")
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun jsonObjOrFirst(parent: JSONObject?, key: String): JSONObject? {
        if (parent == null || !parent.has(key) || parent.isNull(key)) return null
        return when (val value = parent.get(key)) {
            is JSONObject -> value
            is JSONArray -> value.optJSONObject(0)
            else -> null
        }
    }

    companion object {
        private val POS_PREFIX = Regex("""^([a-z]+\.)\s*(.+)$""", RegexOption.IGNORE_CASE)
    }
}
