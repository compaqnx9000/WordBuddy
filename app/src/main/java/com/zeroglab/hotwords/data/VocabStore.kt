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
private const val DB_VERSION = 6

private const val VOCAB_DDL = """
    CREATE TABLE $TABLE (
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
"""

private const val NOTEBOOK_DDL = """
    CREATE TABLE $NOTEBOOK_TABLE (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL COLLATE NOCASE UNIQUE,
        sort_order INTEGER NOT NULL DEFAULT 0,
        created_at INTEGER NOT NULL
    )
"""

class VocabDbHelper(context: Context) : SQLiteOpenHelper(context, "hotwords.db", null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(NOTEBOOK_DDL)
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO $NOTEBOOK_TABLE (id, name, sort_order, created_at) VALUES (?, ?, 0, ?)",
            arrayOf(Notebook.DEFAULT_ID, Notebook.DEFAULT_NAME, now),
        )
        db.execSQL(VOCAB_DDL.trimIndent())
        db.execSQL("CREATE INDEX idx_vocab_notebook_sort ON $TABLE(notebook_id, sort_order)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            runCatching { db.execSQL("ALTER TABLE $TABLE ADD COLUMN image_blob BLOB") }
        }
        if (oldVersion < 3) {
            runCatching { db.execSQL("ALTER TABLE $TABLE ADD COLUMN near_words TEXT DEFAULT '[]'") }
        }
        if (oldVersion < 4) {
            runCatching { db.execSQL("ALTER TABLE $TABLE ADD COLUMN synonyms TEXT DEFAULT '[]'") }
            runCatching { db.execSQL("ALTER TABLE $TABLE ADD COLUMN antonyms TEXT DEFAULT '[]'") }
        }
        if (oldVersion < 5) {
            runCatching { db.execSQL("ALTER TABLE $TABLE ADD COLUMN examples TEXT DEFAULT '[]'") }
        }
        if (oldVersion < 6) {
            migrateToMultiNotebook(db)
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
        if (id == Notebook.DEFAULT_ID) error("默认生词本不能删除")
        if (listNotebooks().size <= 1) error("至少保留一个生词本")
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(TABLE, "notebook_id = ?", arrayOf(id.toString()))
            writableDatabase.delete(NOTEBOOK_TABLE, "id = ?", arrayOf(id.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
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
        values.put("notebook_id", notebookId)
        values.put("sort_order", nextFrontOrder(notebookId))
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
            put("ipa_uk", ipaUk)
            put("ipa_us", ipaUs)
            put("definitions", encodeDefinitions(definitions))
            put("examples", encodeExamples(examples))
            put("near_words", encodeStringList(nearWords))
            put("synonyms", encodeStringList(synonyms))
            put("antonyms", encodeStringList(antonyms))
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
    private val _items = MutableStateFlow(db.listAll())
    private val _notebooks = MutableStateFlow(db.listNotebooks())
    val items: StateFlow<List<VocabEntry>> = _items.asStateFlow()
    val notebooks: StateFlow<List<Notebook>> = _notebooks.asStateFlow()

    private fun refresh() {
        _items.value = db.listAll()
        _notebooks.value = db.listNotebooks()
    }

    suspend fun getByWord(notebookId: Long, word: String): VocabEntry? = withContext(Dispatchers.IO) {
        db.findByWord(notebookId, word)
    }

    suspend fun getById(id: Long): VocabEntry? = withContext(Dispatchers.IO) {
        db.findById(id)
    }

    suspend fun insert(entry: VocabEntry): Long = withContext(Dispatchers.IO) {
        val id = db.insert(entry)
        refresh()
        id
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.delete(id)
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
    val array = JSONArray(raw)
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
    val array = JSONArray(raw)
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
    val array = JSONArray(raw)
    return buildList {
        for (i in 0 until array.length()) {
            val word = array.optString(i).trim()
            if (word.isNotBlank()) add(word)
        }
    }
}
