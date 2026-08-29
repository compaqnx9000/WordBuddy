package com.zeroglab.hotwords.data

/**
 * Natural phonics helpers: syllable breaks (scan vowel nuclei back-to-front)
 * and letter roles for coloring (vowel / consonant).
 */
object NaturalPhonics {
    private val vowelChars = setOf('a', 'e', 'i', 'o', 'u')

    data class LetterSpan(
        val char: Char,
        val isVowel: Boolean,
        val isSilentE: Boolean = false,
    )

    data class PhonicsBreakdown(
        val syllables: List<String>,
        val letters: List<LetterSpan>,
    ) {
        val dotted: String
            get() = syllables.joinToString("·")
    }

    fun analyze(raw: String): PhonicsBreakdown {
        val lettersOnly = raw.mapNotNull { ch -> if (ch.isLetter()) ch else null }
        if (lettersOnly.isEmpty()) {
            return PhonicsBreakdown(syllables = listOf(raw), letters = emptyList())
        }
        val lower = lettersOnly.joinToString("") { it.lowercaseChar().toString() }
        val silent = BooleanArray(lower.length) { isSilentE(lower, it) }
        val nuclei = lower.indices.filter { isNucleus(lower, it, silent) }
        val lowerSyllables = splitByNuclei(lower, nuclei)
        // Rebuild syllables with original casing.
        var cursor = 0
        val syllables = lowerSyllables.map { syl ->
            val piece = lettersOnly.subList(cursor, cursor + syl.length).joinToString("")
            cursor += syl.length
            piece
        }
        val letters = lettersOnly.mapIndexed { i, ch ->
            LetterSpan(
                char = ch,
                isVowel = isVowelLetter(lower, i),
                isSilentE = silent[i],
            )
        }
        return PhonicsBreakdown(syllables = syllables, letters = letters)
    }

    private fun isVowelLetter(word: String, index: Int): Boolean {
        val ch = word[index]
        if (ch in vowelChars) return true
        // y acts as a vowel except at the very start (yes, yellow)
        if (ch == 'y' && index > 0) return true
        return false
    }

    /** Magic-e / silent e: ...VCe... (e not followed by another vowel). */
    private fun isSilentE(word: String, index: Int): Boolean {
        if (word[index] != 'e') return false
        if (index < 2) return false
        if (word[index - 1] in vowelChars || word[index - 1] == 'y') return false
        if (word[index - 2] !in vowelChars && word[index - 2] != 'y') return false
        if (index + 1 < word.length && word[index + 1] in vowelChars) return false
        return true
    }

    private fun isNucleus(word: String, index: Int, silent: BooleanArray): Boolean {
        if (silent[index]) return false
        if (!isVowelLetter(word, index)) return false
        // Collapse diphthong / double vowels into one nucleus (keep the first).
        if (index > 0 && isVowelLetter(word, index - 1) && !silent[index - 1]) return false
        return true
    }

    /**
     * Between two nuclei, the last consonant goes to the next syllable as onset;
     * earlier consonants stay with the previous syllable (coda). Matches
     * de|fi|nite|ly for "definitely".
     */
    private fun splitByNuclei(word: String, nuclei: List<Int>): List<String> {
        if (nuclei.isEmpty()) return listOf(word)
        val starts = IntArray(nuclei.size)
        starts[0] = 0
        for (s in 1 until nuclei.size) {
            val prevN = nuclei[s - 1]
            val curN = nuclei[s]
            val lastBetween = curN - 1
            starts[s] = if (lastBetween > prevN) lastBetween else curN
        }
        return nuclei.indices.map { s ->
            val start = starts[s]
            val end = if (s + 1 < nuclei.size) starts[s + 1] else word.length
            word.substring(start, end)
        }.filter { it.isNotEmpty() }
    }
}
