package com.hotgis.wordbuddy.data

/**
 * Builds one everyday bilingual example per lexical sense,
 * skipping proper-name senses.
 */
object SenseExampleBuilder {
    private val nameSense = Regex(
        """人名|【名】|（名）|\(名\)|姓氏|地名|河名|山名""",
    )
    private val senseSplit = Regex("""[；;]""")
    private val tagStrip = Regex("""<[^>]+>""")
    private val parenNoise = Regex("""[（(][^）)]*[）)]""")

    data class Sense(
        val pos: String,
        val meaning: String,
    )

    fun examplesFor(
        word: String,
        definitions: List<Definition>,
        corpus: List<ExampleSentence>,
    ): List<ExampleSentence> {
        val senses = extractSenses(definitions)
        if (senses.isEmpty()) {
            return corpus.sortedBy { it.english.length }.take(2)
        }
        val remaining = corpus.toMutableList()
        return senses.map { sense ->
            val bestIndex = remaining.indices.maxByOrNull { scoreMatch(remaining[it], sense) }
            val best = bestIndex?.let { remaining[it] }
            val score = best?.let { scoreMatch(it, sense) } ?: 0
            if (best != null && score > 0) {
                remaining.removeAt(bestIndex!!)
                best
            } else {
                synthesize(word, sense)
            }
        }
    }

    /** Prefer fewer major senses for everyday study: merge move-adjacent fragments. */
    fun extractSenses(definitions: List<Definition>): List<Sense> {
        val raw = definitions.flatMap { def ->
            if (isNameSense(def.meaning) || isNameSense(def.pos)) return@flatMap emptyList()
            splitMeaning(def.meaning).mapNotNull { part ->
                val cleaned = cleanMeaning(part)
                if (cleaned.isBlank() || isNameSense(cleaned)) null
                else Sense(def.pos, cleaned)
            }
        }.distinctBy { it.meaning }

        // Collapse near-duplicate "move" senses (e.g. 稍微移动 / 挪开一块地方)
        val collapsed = mutableListOf<Sense>()
        for (sense in raw) {
            val moveLike = isMoveSense(sense.meaning)
            val existingMove = collapsed.indexOfFirst { isMoveSense(it.meaning) }
            if (moveLike && existingMove >= 0) {
                // keep the shorter / clearer one
                if (sense.meaning.length < collapsed[existingMove].meaning.length) {
                    collapsed[existingMove] = sense
                }
                continue
            }
            collapsed += sense
        }
        return collapsed.take(5)
    }

    private fun isMoveSense(meaning: String): Boolean {
        if (listOf("让步", "主意", "态度", "意见").any { meaning.contains(it) }) return false
        return listOf("移动", "挪", "动").any { meaning.contains(it) } ||
            meaning.contains("地方")
    }

    private fun splitMeaning(raw: String): List<String> {
        val parts = senseSplit.split(raw).map { it.trim() }.filter { it.isNotBlank() }
        return parts.ifEmpty { listOf(raw.trim()) }
    }

    private fun cleanMeaning(raw: String): String {
        return raw.replace(tagStrip, "").replace(Regex("""^[,，、\s]+"""), "").trim()
    }

    private fun isNameSense(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (nameSense.containsMatchIn(t)) return true
        // e.g. （Budge）（英）巴奇（人名）
        if (t.contains("（") && Regex("""（[A-Z][a-zA-Z'-]+）""").containsMatchIn(t)) {
            return t.contains("英") || t.contains("美") || t.contains("人名")
        }
        return false
    }

    private fun scoreMatch(example: ExampleSentence, sense: Sense): Int {
        val zh = example.chinese
        val meaning = sense.meaning
        var score = 0

        val keywords = Regex("""[\u4e00-\u9fff]{2,}""")
            .findAll(meaning.replace(tagStrip, "").replace(parenNoise, ""))
            .map { it.value }
            .toList()
        for (kw in keywords) {
            if (zh.contains(kw)) score += kw.length + 3
            else if (kw.length >= 2 && zh.contains(kw.take(2))) score += 1
        }

        val moveHints = listOf("移动", "挪开", "挪", "动弹", "动不了", "稍微")
        val mindHints = listOf("让步", "改变主意", "主意", "态度", "意见", "妥协")
        val nounHints = listOf("羔羊皮", "羊皮", "皮")

        val senseMove = moveHints.any { meaning.contains(it) }
        val senseMind = mindHints.any { meaning.contains(it) }
        val senseNoun = nounHints.any { meaning.contains(it) }
        val zhMove = listOf("动弹", "动不了", "挪", "移动").any { zh.contains(it) }
        val zhMind = listOf("让步", "主意", "妥协", "改变").any { zh.contains(it) }
        val zhNoun = listOf("皮", "毛", "革").any { zh.contains(it) }

        when {
            senseMind && zhMind -> score += 10
            senseMove && zhMove && !zhMind -> score += 8
            senseNoun && zhNoun -> score += 10
            senseMind && zhMove -> score -= 2
            senseMove && zhMind -> score -= 2
        }

        if (Regex("""\b[A-Z][a-z]+ [A-Z]""").containsMatchIn(example.english)) score -= 4
        return score
    }

    private fun synthesize(word: String, sense: Sense): ExampleSentence {
        val meaning = sense.meaning.replace(tagStrip, "").trim()
        val pos = sense.pos.lowercase()
        val gloss = shortGloss(meaning)
        return when {
            meaning.contains("皮") || (pos.startsWith("n") && !pos.startsWith("num")) -> ExampleSentence(
                english = "This coat is lined with $word.",
                chinese = "这件外套用${gloss}做衬里。",
            )
            mindSense(meaning) -> ExampleSentence(
                english = "They refused to $word on the issue.",
                chinese = "在这个问题上他们不肯${if (meaning.contains("让步")) "让步" else "改变主意"}。",
            )
            meaning.contains("挪") || meaning.contains("地方") -> ExampleSentence(
                english = "Could you $word up a little?",
                chinese = "你能稍微挪开一点吗？",
            )
            pos.startsWith("v") || meaning.contains("移动") || meaning.contains("动") -> ExampleSentence(
                english = "The heavy box wouldn't $word.",
                chinese = "那个沉重的箱子怎么也挪不动。",
            )
            else -> ExampleSentence(
                english = "People often use \"$word\" in daily life.",
                chinese = "人们常在日常生活中用到「$word」（$gloss）。",
            )
        }
    }

    private fun mindSense(meaning: String): Boolean {
        return listOf("让步", "主意", "态度", "意见").any { meaning.contains(it) }
    }

    private fun shortGloss(meaning: String): String {
        val cleaned = meaning
            .replace(tagStrip, "")
            .replace(parenNoise, "")
            .replace(Regex("""^（使）|^使"""), "")
            .trim()
        return cleaned.take(12).ifBlank { meaning.take(12) }
    }
}
