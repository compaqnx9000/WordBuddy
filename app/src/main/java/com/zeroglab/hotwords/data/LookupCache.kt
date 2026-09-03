package com.zeroglab.hotwords.data

/**
 * In-memory LRU cache for dictionary lookups (core + related fields).
 */
object LookupCache {
    private const val MAX_ENTRIES = 128

    private val lock = Any()
    private val store = object : LinkedHashMap<String, VocabEntry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VocabEntry>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun keyFor(word: String): String = word.trim().lowercase()

    fun get(word: String): VocabEntry? = synchronized(lock) {
        store[keyFor(word)]
    }

    fun put(entry: VocabEntry) = synchronized(lock) {
        store[keyFor(entry.text)] = entry
    }
}
