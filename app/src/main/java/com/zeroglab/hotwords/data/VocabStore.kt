package com.zeroglab.hotwords.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class ImportAction { Added, Updated }

data class NotebookImportResult(
    val added: Int,
    val updated: Int,
)

private const val TABLE = "vocab"
private const val NOTEBOOK_TABLE = "notebooks"
private const val DB_VERSION = 8

private const val VOCAB_DDL = """
    CREATE TABLE $TABLE (
        id INTEGER PRIMARY KEY NOT NULL,
        notebook_id INTEGER NOT NULL,
        word TEXT NOT NULL COLLATE NOCASE,
        is_phrase INTEGER NOT NULL DEFAULT 0,
        ipa_uk TEXT,
        ipa_us TEXT,
        definitions TEXT NOT NULL,
        examples TEXT DEFAULT '[]',
        near_words TEXT DEFAULT '[]',
        synonyms TEXT DEFAULT '[]',
        antonyms TEXT DEFAULT '[]',
        image_blob BLOB,
        sort_order INTEGER NOT NULL DEFAULT 0,
        added_at INTEGER NOT NULL,
        UNIQUE(notebook_id, word)
    )
"""

private const val NOTEBOOK_DDL = """
    CREATE TABLE $NOTEBOOK_TABLE (
        id INTEGER PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        sort_order INTEGER NOT NULL DEFAULT 0,
        created_at INTEGER NOT NULL,
        kind TEXT NOT NULL DEFAULT 'user',
        slug TEXT,
        word_count INTEGER NOT NULL DEFAULT 0
    )
"""

class VocabDbHelper(context: Context) : SQLiteOpenHelper(context, "hotwords.db", null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(NOTEBOOK_DDL.trimIndent())
        db.execSQL(VOCAB_DDL.trimIndent())
        db.execSQL("CREATE INDEX idx_vocab_notebook_sort ON $TABLE(notebook_id, sort_order)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 8) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE")
            db.execSQL("DROP TABLE IF EXISTS $NOTEBOOK_TABLE")
            onCreate(db)
        }
    }

    private fun migrateToMultiNotebook(db: SQLiteDatabase) {
        db.execSQL(NOTEBOOK_DDL)
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO $NOTEBOOK_TABLE (id, name, sort_order, created_at) VALUES (?, ?, 0, ?)",
            arrayOf(Notebook.DEFAULT_ID, Notebook.DEFAULT_NAME, now),
        )
        db.execSQL(
            """
            CREATE TABLE vocab_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                notebook_id INTEGER NOT NULL DEFAULT 1,
                word TEXT NOT NULL COLLATE NOCASE,
                is_phrase INTEGER NOT NULL DEFAULT 0,
                ipa_uk TEXT,
                ipa_us TEXT,
                definitions TEXT NOT NULL,
                examples TEXT DEFAULT '[]',
                near_words TEXT DEFAULT '[]',
                synonyms TEXT DEFAULT '[]',
                antonyms TEXT DEFAULT '[]',
                image_blob BLOB,
                sort_order INTEGER NOT NULL DEFAULT 0,
                added_at INTEGER NOT NULL,
                UNIQUE(notebook_id, word)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO vocab_new (
                id, notebook_id, word, is_phrase, ipa_uk, ipa_us, definitions, examples,
                near_words, synonyms, antonyms, image_blob, sort_order, added_at
            )
            SELECT id, 1, word, is_phrase, ipa_uk, ipa_us, definitions, examples,
                near_words, synonyms, antonyms, image_blob, sort_order, added_at
            FROM $TABLE
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE $TABLE")
        db.execSQL("ALTER TABLE vocab_new RENAME TO $TABLE")
        db.execSQL("CREATE INDEX idx_vocab_notebook_sort ON $TABLE(notebook_id, sort_order)")
    }

    fun listNotebooks(): List<Notebook> {
        val items = mutableListOf<Notebook>()
        readableDatabase.query(
            NOTEBOOK_TABLE,
            null,
            null,
            null,
            null,
            null,
            "sort_order ASC, id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items += cursor.toNotebook()
            }
        }
        return items
    }

    fun findNotebook(id: Long): Notebook? =
        queryNotebook("id = ?", arrayOf(id.toString()))

    fun findNotebookByName(name: String): Notebook? =
        queryNotebook("name = ? COLLATE NOCASE", arrayOf(name.trim()))

    fun createNotebook(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "名称不能为空" }
        if (findNotebookByName(trimmed) != null) error("已有同名生词本")
        val values = ContentValues().apply {
            put("name", trimmed)
            put("sort_order", nextNotebookOrder())
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertOrThrow(NOTEBOOK_TABLE, null, values)
    }

    fun deleteNotebook(id: Long) {
        val notebook = findNotebook(id) ?: return
        if (notebook.isSystem || notebook.kind == Notebook.KIND_CATALOG) error("系统词书不能删除")
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(TABLE, "notebook_id = ?", arrayOf(id.toString()))
            writableDatabase.delete(NOTEBOOK_TABLE, "id = ?", arrayOf(id.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun replaceNotebooks(notebooks: List<Notebook>) {
        writableDatabase.beginTransaction()
        try {
            val keepIds = notebooks.map { it.id }
            if (keepIds.isEmpty()) {
                writableDatabase.delete(TABLE, null, null)
            } else {
                val placeholders = keepIds.joinToString(",") { "?" }
                writableDatabase.delete(
                    TABLE,
                    "notebook_id NOT IN ($placeholders)",
                    keepIds.map { it.toString() }.toTypedArray(),
                )
            }
            writableDatabase.delete(NOTEBOOK_TABLE, null, null)
            notebooks.forEach { book ->
                writableDatabase.insertWithOnConflict(
                    NOTEBOOK_TABLE,
                    null,
                    ContentValues().apply {
                        put("id", book.id)
                        put("name", book.name)
                        put("sort_order", book.sortOrder)
                        put("created_at", book.createdAtMillis)
                        put("kind", book.kind)
                        put("slug", book.slug)
                        put("word_count", book.wordCount)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun setWordCount(notebookId: Long, count: Int) {
        writableDatabase.update(
            NOTEBOOK_TABLE,
            ContentValues().apply { put("word_count", count.coerceAtLeast(0)) },
            "id = ?",
            arrayOf(notebookId.toString()),
        )
    }

    fun upsertEntries(entries: List<VocabEntry>) {
        if (entries.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            entries.forEach { entry ->
                writableDatabase.insertWithOnConflict(
                    TABLE,
                    null,
                    entry.toValues(includeWord = true).apply {
                        put("id", entry.id)
                        put("notebook_id", entry.notebookId)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun wipeAll() {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(TABLE, null, null)
            writableDatabase.delete(NOTEBOOK_TABLE, null, null)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    /** Bulk-insert built-in wordbook rows (full VocabEntry fields). Caller should ensure notebook empty. */
    fun seedNotebookEntries(notebookId: Long, entries: List<VocabEntry>) {
        if (entries.isEmpty()) return
        val now = System.currentTimeMillis()
        writableDatabase.beginTransaction()
        try {
            entries.forEachIndexed { index, entry ->
                val values = entry.toValues(includeWord = true).apply {
                    put("notebook_id", notebookId)
                    put("sort_order", index)
                    put("added_at", now)
                    putNull("image_blob")
                }
                writableDatabase.insertWithOnConflict(
                    TABLE,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun clearNotebookWords(notebookId: Long) {
        writableDatabase.delete(TABLE, "notebook_id = ?", arrayOf(notebookId.toString()))
    }

    fun moveEntriesToNotebook(entryIds: List<Long>, targetNotebookId: Long) {
        if (entryIds.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            entryIds.forEach { id ->
                val entry = findById(id) ?: return@forEach
                if (entry.notebookId == targetNotebookId) return@forEach
                val duplicate = findByWord(targetNotebookId, entry.text)
                if (duplicate != null) {
                    patchRelatedIfEmpty(duplicate, entry)
                    delete(id)
                } else {
                    val values = ContentValues().apply {
                        put("notebook_id", targetNotebookId)
                        put("sort_order", nextFrontOrder(targetNotebookId))
                    }
                    writableDatabase.update(TABLE, values, "id = ?", arrayOf(id.toString()))
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun countWordsInNotebook(notebookId: Long): Int = countWordsInNotebookInternal(notebookId)

    fun listAll(): List<VocabEntry> = listByNotebook(null)

    fun listByNotebook(notebookId: Long?): List<VocabEntry> {
        val items = mutableListOf<VocabEntry>()
        val selection = notebookId?.let { "notebook_id = ?" }
        val args = notebookId?.let { arrayOf(it.toString()) }
        readableDatabase.query(
            TABLE,
            null,
            selection,
            args,
            null,
            null,
            "sort_order ASC, id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items += cursor.toEntry()
            }
        }
        return items
    }

    fun findById(id: Long): VocabEntry? = queryOne("id = ?", arrayOf(id.toString()))

    fun findByWord(notebookId: Long, word: String): VocabEntry? =
        queryOne("notebook_id = ? AND word = ? COLLATE NOCASE", arrayOf(notebookId.toString(), word.trim()))

    fun insert(entry: VocabEntry): Long {
        val notebookId = entry.notebookId
        val existing = findByWord(notebookId, entry.text)
        if (existing != null) {
            patchRelatedIfEmpty(existing, entry)
            return existing.id
        }
        val values = entry.toValues(includeWord = true)
        if (entry.id > 0L) values.put("id", entry.id)
        values.put("notebook_id", notebookId)
        values.put("sort_order", if (entry.sortOrder != 0) entry.sortOrder else nextFrontOrder(notebookId))
        values.put("added_at", entry.addedAtMillis)
        return writableDatabase.insertOrThrow(TABLE, null, values)
    }

    fun delete(id: Long) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id.toString()))
    }

    fun updateImageBlob(id: Long, imageBlob: ByteArray?) {
        val values = ContentValues()
        if (imageBlob == null) {
            values.putNull("image_blob")
        } else {
            values.put("image_blob", imageBlob)
        }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(id.toString()))
    }

    fun updateRelatedWords(
        id: Long,
        nearWords: List<String>? = null,
        synonyms: List<String>? = null,
        antonyms: List<String>? = null,
        examples: List<ExampleSentence>? = null,
    ) {
        val values = ContentValues()
        if (nearWords != null) values.put("near_words", encodeStringList(nearWords))
        if (synonyms != null) values.put("synonyms", encodeStringList(synonyms))
        if (antonyms != null) values.put("antonyms", encodeStringList(antonyms))
        if (examples != null) values.put("examples", encodeExamples(examples))
        if (values.size() == 0) return
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(id.toString()))
    }

    fun updateDefinitions(id: Long, definitions: List<Definition>) {
        val values = ContentValues()
        values.put("definitions", encodeDefinitions(definitions))
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(id.toString()))
    }

    fun applyImport(notebookId: Long, entry: VocabEntry): ImportAction {
        val existing = findByWord(notebookId, entry.text)
        if (existing == null) {
            val values = entry.toValues(includeWord = true)
            values.put("notebook_id", notebookId)
            values.put("sort_order", nextFrontOrder(notebookId))
            values.put("added_at", entry.addedAtMillis)
            values.putNull("ipa_uk")
            values.putNull("ipa_us")
            values.putNull("image_blob")
            writableDatabase.insertOrThrow(TABLE, null, values)
            return ImportAction.Added
        }
        val values = ContentValues().apply {
            put("is_phrase", if (entry.isPhrase) 1 else 0)
            put("definitions", encodeDefinitions(entry.definitions))
            put("examples", encodeExamples(entry.examples))
            put("near_words", encodeStringList(entry.nearWords))
            put("synonyms", encodeStringList(entry.synonyms))
            put("antonyms", encodeStringList(entry.antonyms))
        }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(existing.id.toString()))
        return ImportAction.Updated
    }

    fun importEntries(notebookId: Long, entries: List<VocabEntry>): NotebookImportResult {
        var added = 0
        var updated = 0
        writableDatabase.beginTransaction()
        try {
            entries.forEach { entry ->
                when (applyImport(notebookId, entry.copy(notebookId = notebookId))) {
                    ImportAction.Added -> added++
                    ImportAction.Updated -> updated++
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return NotebookImportResult(added = added, updated = updated)
    }

    fun rewriteOrders(notebookId: Long, ids: List<Long>) {
        writableDatabase.beginTransaction()
        try {
            ids.forEachIndexed { index, id ->
                writableDatabase.update(
                    TABLE,
                    ContentValues().apply { put("sort_order", index) },
                    "id = ? AND notebook_id = ?",
                    arrayOf(id.toString(), notebookId.toString()),
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun countWordsInNotebookInternal(notebookId: Long): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE WHERE notebook_id = ?",
            arrayOf(notebookId.toString()),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun patchRelatedIfEmpty(existing: VocabEntry, incoming: VocabEntry) {
        val near = if (existing.nearWords.isEmpty() && incoming.nearWords.isNotEmpty()) incoming.nearWords else null
        val syn = if (existing.synonyms.isEmpty() && incoming.synonyms.isNotEmpty()) incoming.synonyms else null
        val ant = if (existing.antonyms.isEmpty() && incoming.antonyms.isNotEmpty()) incoming.antonyms else null
        val examples = if (existing.examples.isEmpty() && incoming.examples.isNotEmpty()) incoming.examples else null
        if (near != null || syn != null || ant != null || examples != null) {
            updateRelatedWords(existing.id, near, syn, ant, examples)
        }
    }

    private fun nextFrontOrder(notebookId: Long): Int {
        readableDatabase.rawQuery(
            "SELECT COALESCE(MIN(sort_order), 0) FROM $TABLE WHERE notebook_id = ?",
            arrayOf(notebookId.toString()),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) - 1 else 0
        }
    }

    private fun nextNotebookOrder(): Int {
        readableDatabase.rawQuery(
            "SELECT COALESCE(MAX(sort_order), -1) FROM $NOTEBOOK_TABLE",
            null,
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) + 1 else 0
        }
    }

    private fun queryNotebook(where: String, args: Array<String>): Notebook? {
        readableDatabase.query(NOTEBOOK_TABLE, null, where, args, null, null, null, "1").use { cursor ->
            return if (cursor.moveToFirst()) cursor.toNotebook() else null
        }
    }

    private fun queryOne(where: String, args: Array<String>): VocabEntry? {
        readableDatabase.query(TABLE, null, where, args, null, null, null, "1").use { cursor ->
            return if (cursor.moveToFirst()) cursor.toEntry() else null
        }
    }

    private fun VocabEntry.toValues(includeWord: Boolean): ContentValues {
        return ContentValues().apply {
            if (includeWord) put("word", text.trim())
            put("is_phrase", if (isPhrase) 1 else 0)
            if (ipaUk == null) putNull("ipa_uk") else put("ipa_uk", ipaUk)
            if (ipaUs == null) putNull("ipa_us") else put("ipa_us", ipaUs)
            put("definitions", encodeDefinitions(definitions))
            put("examples", encodeExamples(examples))
            put("near_words", encodeStringList(nearWords))
            put("synonyms", encodeStringList(synonyms))
            put("antonyms", encodeStringList(antonyms))
            put("sort_order", sortOrder)
            put("added_at", if (addedAtMillis > 0L) addedAtMillis else System.currentTimeMillis())
            if (imageBlob == null) {
                putNull("image_blob")
            } else {
                put("image_blob", imageBlob)
            }
        }
    }

    private fun Cursor.toNotebook(): Notebook {
        return Notebook(
            id = getLong(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            sortOrder = getInt(getColumnIndexOrThrow("sort_order")),
            createdAtMillis = getLong(getColumnIndexOrThrow("created_at")),
            kind = optionalString("kind") ?: Notebook.KIND_USER,
            slug = optionalString("slug"),
            wordCount = runCatching { getInt(getColumnIndexOrThrow("word_count")) }.getOrDefault(0),
        )
    }

    private fun Cursor.toEntry(): VocabEntry {
        val blobIndex = getColumnIndex("image_blob")
        val blob = if (blobIndex >= 0 && !isNull(blobIndex)) getBlob(blobIndex) else null
        return VocabEntry(
            id = getLong(getColumnIndexOrThrow("id")),
            notebookId = getLong(getColumnIndexOrThrow("notebook_id")),
            text = getString(getColumnIndexOrThrow("word")),
            isPhrase = getInt(getColumnIndexOrThrow("is_phrase")) == 1,
            ipaUk = getStringOrNull("ipa_uk"),
            ipaUs = getStringOrNull("ipa_us"),
            definitions = decodeDefinitions(getString(getColumnIndexOrThrow("definitions"))),
            examples = decodeExamples(optionalString("examples")),
            nearWords = decodeStringList(optionalString("near_words")),
            synonyms = decodeStringList(optionalString("synonyms")),
            antonyms = decodeStringList(optionalString("antonyms")),
            imageBlob = blob,
            sortOrder = getInt(getColumnIndexOrThrow("sort_order")),
            addedAtMillis = getLong(getColumnIndexOrThrow("added_at")),
        )
    }

    private fun Cursor.optionalString(column: String): String? {
        val index = getColumnIndex(column)
        if (index < 0 || isNull(index)) return null
        return getString(index)
    }

    private fun Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }
}

class VocabRepository(context: Context) {
    private val db = VocabDbHelper(context.applicationContext)
    private var activeNotebookId: Long = 0L
    private var nextCursor: String? = null
    var hasMore: Boolean = false
        private set
    /** When > 0, [_items} is a window starting at this absolute notebook index (alphabet jump). */
    var listWindowStart: Int = 0
        private set

    private val _items = MutableStateFlow(emptyList<VocabEntry>())
    private val _notebooks = MutableStateFlow(db.listNotebooks())
    val items: StateFlow<List<VocabEntry>> = _items.asStateFlow()
    val notebooks: StateFlow<List<Notebook>> = _notebooks.asStateFlow()

    fun publishNotebooks(books: List<Notebook>) {
        db.replaceNotebooks(books)
        _notebooks.value = db.listNotebooks()
    }

    fun openCachedNotebook(notebookId: Long) {
        activeNotebookId = notebookId
        val cached = db.listByNotebook(notebookId)
        _items.value = cached
        listWindowStart = 0
        restorePaging(cached, notebookId)
    }

    fun applyFirstPage(notebookId: Long, page: WordPage) {
        activeNotebookId = notebookId
        db.upsertEntries(page.items)
        val cached = db.listByNotebook(notebookId)
        val count = maxOf(page.total, cached.size)
        db.setWordCount(notebookId, count)
        nextCursor = page.nextCursor
        hasMore = page.nextCursor != null || cached.size < count
        listWindowStart = 0
        _items.value = cached
        _notebooks.value = db.listNotebooks()
    }

    fun applyNextPage(notebookId: Long, page: WordPage) {
        db.upsertEntries(page.items)
        nextCursor = page.nextCursor
        hasMore = page.nextCursor != null
        if (activeNotebookId != notebookId) return
        val seen = _items.value.mapTo(HashSet()) { it.id }
        val appended = page.items.filter { seen.add(it.id) }
        if (appended.isNotEmpty()) {
            _items.value = _items.value + appended
        }
    }

    /** Replace the in-memory list with a window starting at [fromIndex] (fast alphabet seek). */
    fun applySeekWindow(notebookId: Long, page: WordPage, fromIndex: Int) {
        activeNotebookId = notebookId
        db.upsertEntries(page.items)
        if (page.total > 0) db.setWordCount(notebookId, page.total)
        nextCursor = page.nextCursor
        hasMore = page.nextCursor != null
        listWindowStart = fromIndex.coerceAtLeast(0)
        _items.value = page.items
        _notebooks.value = db.listNotebooks()
    }

    /** Full ordered stubs (or existing full rows) so alphabet / slider can seek in RAM. */
    fun applyHeads(notebookId: Long, heads: List<VocabEntry>, total: Int) {
        activeNotebookId = notebookId
        val existing = if (_items.value.firstOrNull()?.notebookId == notebookId) {
            _items.value.associateBy { it.id }
        } else {
            emptyMap()
        }
        _items.value = heads.map { head ->
            val old = existing[head.id]
            if (old != null && old.definitions.isNotEmpty()) {
                old.copy(
                    text = head.text,
                    isPhrase = head.isPhrase,
                    ipaUk = head.ipaUk ?: old.ipaUk,
                    ipaUs = head.ipaUs ?: old.ipaUs,
                    sortOrder = head.sortOrder,
                )
            } else {
                head
            }
        }
        val count = maxOf(total, heads.size)
        db.setWordCount(notebookId, count)
        nextCursor = heads.lastOrNull()?.let { "${it.sortOrder}:${it.id}" }
        hasMore = heads.size < count || _items.value.any { it.definitions.isEmpty() }
        listWindowStart = 0
        _notebooks.value = db.listNotebooks()
    }

    /** Append more ordered stubs after [applyHeads] preview (progressive catalog load). */
    fun appendHeadStubs(notebookId: Long, more: List<VocabEntry>, total: Int) {
        if (activeNotebookId != notebookId || more.isEmpty()) return
        val seen = _items.value.mapTo(HashSet()) { it.id }
        val appended = more.filter { seen.add(it.id) }
        if (appended.isEmpty()) return
        _items.value = _items.value + appended
        val count = maxOf(total, _items.value.size)
        db.setWordCount(notebookId, count)
        nextCursor = _items.value.lastOrNull()?.let { "${it.sortOrder}:${it.id}" }
        hasMore = _items.value.size < count || _items.value.any { it.definitions.isEmpty() }
    }

    /** Overlay full details onto an already-complete heads list. Does not replace order. */
    fun mergeDetails(notebookId: Long, page: WordPage) {
        if (activeNotebookId != notebookId || page.items.isEmpty()) return
        val byId = page.items.associateBy { it.id }
        val byText = page.items.associateBy { it.text.trim().lowercase() }
        val current = _items.value
        if (current.isEmpty()) {
            applyNextPage(notebookId, page)
            return
        }
        var changed = false
        val next = current.mapIndexed { index, row ->
            val fresh = byId[row.id]
                ?: byText[row.text.trim().lowercase()]
                ?: page.items.getOrNull(index - listWindowStart)?.takeIf {
                    it.text.equals(row.text, ignoreCase = true)
                }
                ?: return@mapIndexed row
            if (fresh.definitions.isEmpty() && row.definitions.isNotEmpty()) {
                row
            } else {
                changed = true
                // Keep the list-row id/order even when details came from packaged assets.
                fresh.copy(
                    id = row.id,
                    notebookId = notebookId,
                    sortOrder = row.sortOrder,
                    text = row.text,
                )
            }
        }
        if (changed) _items.value = next
        hasMore = _items.value.any { it.definitions.isEmpty() }
        nextCursor = _items.value.lastOrNull()?.let { "${it.sortOrder}:${it.id}" }
    }

    fun persistEntries(entries: List<VocabEntry>) {
        db.upsertEntries(entries)
    }

    private fun restorePaging(items: List<VocabEntry>, notebookId: Long) {
        val total = _notebooks.value.firstOrNull { it.id == notebookId }?.wordCount ?: items.size
        nextCursor = items.lastOrNull()?.let { "${it.sortOrder}:${it.id}" }
        hasMore = items.size < total
        listWindowStart = 0
    }

    fun currentCursor(): String? = nextCursor

    fun wipeCache() {
        db.wipeAll()
        activeNotebookId = 0L
        nextCursor = null
        hasMore = false
        listWindowStart = 0
        _items.value = emptyList()
        _notebooks.value = emptyList()
    }

    private fun refresh() {
        if (activeNotebookId > 0L) {
            _items.value = db.listByNotebook(activeNotebookId)
        }
        _notebooks.value = db.listNotebooks()
    }

    fun peekWord(notebookId: Long, word: String): VocabEntry? = db.findByWord(notebookId, word)

    suspend fun getByWord(notebookId: Long, word: String): VocabEntry? = withContext(Dispatchers.IO) {
        db.findByWord(notebookId, word)
    }

    suspend fun getById(id: Long): VocabEntry? = withContext(Dispatchers.IO) {
        db.findById(id)
    }

    suspend fun cacheEntry(entry: VocabEntry) = withContext(Dispatchers.IO) {
        val existed = db.findById(entry.id) != null
        db.upsertEntries(listOf(entry))
        if (!existed) {
            val count = maxOf(
                (_notebooks.value.firstOrNull { it.id == entry.notebookId }?.wordCount ?: 0) + 1,
                db.countWordsInNotebook(entry.notebookId),
            )
            db.setWordCount(entry.notebookId, count)
        }
        refresh()
    }

    suspend fun insert(entry: VocabEntry): Long = withContext(Dispatchers.IO) {
        val id = db.insert(entry)
        refresh()
        id
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val existing = db.findById(id)
        db.delete(id)
        if (existing != null) {
            val count = db.countWordsInNotebook(existing.notebookId)
            val reported = _notebooks.value.firstOrNull { it.id == existing.notebookId }?.wordCount ?: count
            db.setWordCount(existing.notebookId, minOf(count, (reported - 1).coerceAtLeast(0)))
        }
        refresh()
    }

    suspend fun createNotebook(name: String): Long = withContext(Dispatchers.IO) {
        val id = db.createNotebook(name)
        refresh()
        id
    }

    suspend fun deleteNotebook(id: Long) = withContext(Dispatchers.IO) {
        db.deleteNotebook(id)
        refresh()
    }

    suspend fun moveEntriesToNotebook(entryIds: List<Long>, targetNotebookId: Long) =
        withContext(Dispatchers.IO) {
            db.moveEntriesToNotebook(entryIds, targetNotebookId)
            refresh()
        }

    fun wordCountInNotebook(notebookId: Long): Int = db.countWordsInNotebook(notebookId)

    suspend fun updateImageBlob(id: Long, imageBlob: ByteArray?) = withContext(Dispatchers.IO) {
        db.updateImageBlob(id, imageBlob)
        refresh()
    }

    suspend fun updateRelatedWords(
        id: Long,
        nearWords: List<String>? = null,
        synonyms: List<String>? = null,
        antonyms: List<String>? = null,
        examples: List<ExampleSentence>? = null,
    ) = withContext(Dispatchers.IO) {
        db.updateRelatedWords(id, nearWords, synonyms, antonyms, examples)
        refresh()
    }

    suspend fun updateDefinitions(id: Long, definitions: List<Definition>) = withContext(Dispatchers.IO) {
        db.updateDefinitions(id, definitions)
        refresh()
    }

    suspend fun rewriteOrders(notebookId: Long, ids: List<Long>) {
        val idSet = ids.toSet()
        val map = _items.value.filter { it.notebookId == notebookId }.associateBy { it.id }
        val reordered = ids.mapIndexedNotNull { index, id ->
            map[id]?.copy(sortOrder = index)
        }
        val rest = _items.value.filter { it.id !in idSet || it.notebookId != notebookId }
        _items.value = reordered + rest
        withContext(Dispatchers.IO) {
            db.rewriteOrders(notebookId, ids)
        }
    }

    suspend fun importEntries(notebookId: Long, entries: List<VocabEntry>): NotebookImportResult =
        withContext(Dispatchers.IO) {
            val result = db.importEntries(notebookId, entries)
            refresh()
            result
        }
}

internal fun encodeDefinitions(definitions: List<Definition>): String {
    val array = JSONArray()
    definitions.forEach { item ->
        array.put(
            JSONObject()
                .put("pos", item.pos)
                .put("meaning", item.meaning)
                .put("user", item.isUserAdded),
        )
    }
    return array.toString()
}

internal fun decodeDefinitions(raw: String): List<Definition> {
    if (raw.isBlank()) return emptyList()
    val array = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
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

internal fun encodeExamples(examples: List<ExampleSentence>): String {
    val array = JSONArray()
    examples.forEach { item ->
        array.put(
            JSONObject()
                .put("en", item.english)
                .put("zh", item.chinese),
        )
    }
    return array.toString()
}

internal fun decodeExamples(raw: String?): List<ExampleSentence> {
    if (raw.isNullOrBlank()) return emptyList()
    val array = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
    return buildList {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val en = obj.optString("en").trim()
            val zh = obj.optString("zh").trim()
            if (en.isNotBlank() && zh.isNotBlank()) add(ExampleSentence(en, zh))
        }
    }
}

internal fun encodeStringList(words: List<String>): String {
    val array = JSONArray()
    words.forEach { array.put(it) }
    return array.toString()
}

internal fun decodeStringList(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    val array = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
    return buildList {
        for (i in 0 until array.length()) {
            val word = array.optString(i).trim()
            if (word.isNotBlank()) add(word)
        }
    }
}
