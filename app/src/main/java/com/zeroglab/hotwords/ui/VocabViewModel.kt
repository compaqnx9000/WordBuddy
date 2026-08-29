package com.zeroglab.hotwords.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroglab.hotwords.audio.TtsPlayer
import com.zeroglab.hotwords.data.AiImageClient
import com.zeroglab.hotwords.data.DictionaryClient
import com.zeroglab.hotwords.data.ImageCodec
import com.zeroglab.hotwords.data.Definition
import com.zeroglab.hotwords.data.LookupResult
import com.zeroglab.hotwords.data.MnemonicCatalog
import com.zeroglab.hotwords.data.NearWordsFinder
import com.zeroglab.hotwords.data.Notebook
import com.zeroglab.hotwords.data.SortMode
import com.zeroglab.hotwords.data.StudySettings
import com.zeroglab.hotwords.data.SettingsStore
import com.zeroglab.hotwords.data.VocabEntry
import com.zeroglab.hotwords.data.VocabRepository
import com.zeroglab.hotwords.data.WordFilter
import com.zeroglab.hotwords.data.NotebookImportResult
import com.zeroglab.hotwords.data.VocabNotebookExporter
import com.zeroglab.hotwords.data.VocabNotebookImporter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DateSection(
    val dateLabel: String,
    val entries: List<VocabEntry>,
)

data class VocabUiState(
    val filter: WordFilter = WordFilter.ALL,
    val sortMode: SortMode = SortMode.MANUAL,
    val query: String = "",
    val searching: Boolean = false,
    val hideDefinitions: Boolean = true,
    val revealedIds: Set<Long> = emptySet(),
    val cardIndex: Int = 0,
    val shuffledIds: List<Long>? = null,
    val playing: Boolean = false,
    val settings: StudySettings = StudySettings(),
    val lookupQuery: String = "",
    val lookupLoading: Boolean = false,
    val lookupError: String? = null,
    val lookupResult: LookupResult? = null,
    val imageBusy: Boolean = false,
    val imageError: String? = null,
    val activeNotebookId: Long = Notebook.DEFAULT_ID,
)

class VocabViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = VocabRepository(application)
    private val settingsStore = SettingsStore(application)
    private val dictionary = DictionaryClient()
    private val tts = TtsPlayer(application)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

    private val _ui = MutableStateFlow(VocabUiState())
    val ui: StateFlow<VocabUiState> = _ui.asStateFlow()
    val notebooks: StateFlow<List<Notebook>> = repo.notebooks

    val filteredWords: StateFlow<List<VocabEntry>> = combine(repo.items, _ui) { words, state ->
        filterAndSort(words.filter { it.notebookId == state.activeNotebookId }, state)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var autoPlayJob: Job? = null
    private var speakJob: Job? = null
    private var lookupJob: Job? = null

    init {
        val loaded = settingsStore.loadSettings()
        var activeId = settingsStore.loadActiveNotebookId()
        val notebookIds = repo.notebooks.value.map { it.id }.toSet()
        if (activeId !in notebookIds) {
            activeId = Notebook.DEFAULT_ID
            settingsStore.saveActiveNotebookId(activeId)
        }
        var settings = loaded
        if (settings.defaultNotebookId !in notebookIds) {
            settings = settings.copy(defaultNotebookId = Notebook.DEFAULT_ID)
            settingsStore.saveSettings(settings)
        }
        _ui.value = VocabUiState(settings = settings, activeNotebookId = activeId)
    }

    fun activeNotebook(): Notebook? =
        notebooks.value.firstOrNull { it.id == _ui.value.activeNotebookId }

    fun defaultNotebook(): Notebook? =
        notebooks.value.firstOrNull { it.id == _ui.value.settings.defaultNotebookId }
        ?: notebooks.value.firstOrNull()

    fun sections(): List<DateSection> {
        val list = filteredWords.value
        if (list.isEmpty()) return emptyList()
        val sortMode = _ui.value.sortMode
        if (sortMode == SortMode.MANUAL || sortMode == SortMode.ALPHA) {
            return listOf(DateSection("", list))
        }
        val zone = ZoneId.systemDefault()
        return list
            .groupBy { word ->
                Instant.ofEpochMilli(word.addedAtMillis).atZone(zone).toLocalDate()
            }
            .toSortedMap(compareByDescending<LocalDate> { it })
            .map { (date, items) ->
                DateSection(dateFormatter.format(date), items)
            }
    }

    fun studyDeck(): List<VocabEntry> {
        val filtered = filteredWords.value
        val order = _ui.value.shuffledIds
        return if (order == null) {
            filtered
        } else {
            val map = filtered.associateBy { it.id }
            order.mapNotNull { map[it] } + filtered.filter { it.id !in order.toSet() }
        }
    }

    fun currentCard(): VocabEntry? {
        val deck = studyDeck()
        if (deck.isEmpty()) return null
        return deck[_ui.value.cardIndex.coerceIn(0, deck.lastIndex)]
    }

    fun setLookupQuery(query: String) {
        _ui.update { it.copy(lookupQuery = query, lookupError = null) }
        lookupJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _ui.update { it.copy(lookupResult = null, lookupLoading = false) }
            return
        }
        lookupJob = viewModelScope.launch {
            delay(420)
            lookupNow(trimmed)
        }
    }

    fun submitLookup() {
        lookupJob?.cancel()
        val trimmed = _ui.value.lookupQuery.trim()
        if (trimmed.isEmpty()) return
        lookupJob = viewModelScope.launch { lookupNow(trimmed) }
    }

    fun toggleStar() {
        val result = _ui.value.lookupResult ?: return
        viewModelScope.launch {
            if (result.saved) {
                repo.delete(result.entry.id)
                _ui.update {
                    it.copy(
                        lookupResult = LookupResult(
                            entry = result.entry.copy(id = 0L),
                            saved = false,
                        ),
                    )
                }
            } else {
                ensureLookupSaved()
            }
        }
    }

    /** Save current lookup word if needed, then run [block] with the notebook id. */
    fun withLookupSaved(block: (Long) -> Unit) {
        viewModelScope.launch {
            val id = ensureLookupSaved() ?: return@launch
            block(id)
        }
    }

    private suspend fun ensureLookupSaved(): Long? {
        val result = _ui.value.lookupResult ?: return null
        if (result.saved && result.entry.id > 0L) return result.entry.id
        val app = getApplication<Application>()
        val bundled = withContext(Dispatchers.IO) {
            MnemonicCatalog.bytesFor(app, result.entry.text)
        }
        val toSave = result.entry.copy(
            notebookId = _ui.value.settings.defaultNotebookId,
            imageBlob = result.entry.imageBlob ?: bundled,
            addedAtMillis = System.currentTimeMillis(),
        )
        val id = repo.insert(toSave)
        val saved = repo.getById(id) ?: toSave.copy(id = id)
        _ui.update { it.copy(lookupResult = LookupResult(saved, true)) }
        return id
    }

    fun deleteWord(id: Long) {
        deleteWords(listOf(id))
    }

    fun deleteWords(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repo.delete(it) }
            _ui.update { state ->
                val result = state.lookupResult
                if (result != null && result.entry.id in ids) {
                    state.copy(lookupResult = LookupResult(result.entry.copy(id = 0L), false))
                } else {
                    state
                }
            }
        }
    }

    fun reorderWords(from: Int, to: Int) {
        val stateForOrder = _ui.value.copy(
            sortMode = SortMode.MANUAL,
            query = "",
            searching = false,
        )
        val list = filterAndSort(repo.items.value, stateForOrder).toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        _ui.value = stateForOrder
        viewModelScope.launch {
            repo.rewriteOrders(_ui.value.activeNotebookId, list.map { it.id })
        }
    }

    fun setEntryImage(id: Long, uri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(imageBusy = true, imageError = null) }
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    ImageCodec.fromUri(getApplication(), uri)
                }
                repo.updateImageBlob(id, bytes)
                syncLookupImage(id)
            }.onFailure {
                _ui.update { it.copy(imageError = "选图失败，请换一张再试") }
            }
            _ui.update { it.copy(imageBusy = false) }
        }
    }

    fun setLookupImage(uri: Uri) {
        withLookupSaved { id -> setEntryImage(id, uri) }
    }

    fun generateAiImage(id: Long, meaningHint: String) {
        viewModelScope.launch {
            _ui.update { it.copy(imageBusy = true, imageError = null) }
            runCatching {
                val entry = repo.getById(id) ?: error("missing word")
                val bytes = AiImageClient.generate(
                    word = entry.text,
                    meaningHint = meaningHint.ifBlank { entry.definitions.firstOrNull()?.label },
                )
                repo.updateImageBlob(id, bytes)
                syncLookupImage(id)
            }.onFailure {
                _ui.update {
                    it.copy(imageError = "AI 生图失败，请检查网络后再试（免费接口有时会忙）")
                }
            }
            _ui.update { it.copy(imageBusy = false) }
        }
    }

    fun generateAiForLookup(meaningHint: String) {
        withLookupSaved { id -> generateAiImage(id, meaningHint) }
    }

    fun clearImageError() = _ui.update { it.copy(imageError = null) }

    private suspend fun syncLookupImage(id: Long) {
        val latest = repo.getById(id) ?: return
        _ui.update { state ->
            val result = state.lookupResult
            if (result != null && result.entry.id == id) {
                state.copy(lookupResult = result.copy(entry = latest), imageError = null)
            } else {
                state.copy(imageError = null)
            }
        }
    }

    fun setFilter(filter: WordFilter) = _ui.update { it.copy(filter = filter) }

    fun cycleSort() = _ui.update { state ->
        val next = when (state.sortMode) {
            SortMode.MANUAL -> SortMode.TIME_DESC
            SortMode.TIME_DESC -> SortMode.TIME_ASC
            SortMode.TIME_ASC -> SortMode.ALPHA
            SortMode.ALPHA -> SortMode.MANUAL
        }
        state.copy(sortMode = next)
    }

    fun setQuery(query: String) = _ui.update { it.copy(query = query) }

    fun setSearching(searching: Boolean) = _ui.update {
        it.copy(searching = searching, query = if (searching) it.query else "")
    }

    fun toggleHideDefinitions() = _ui.update {
        it.copy(hideDefinitions = !it.hideDefinitions, revealedIds = emptySet())
    }

    fun toggleReveal(id: Long) = _ui.update { state ->
        val next = state.revealedIds.toMutableSet()
        if (!next.add(id)) next.remove(id)
        state.copy(revealedIds = next)
    }

    fun openCard(id: Long? = null, shuffled: Boolean = false) {
        stopAutoPlay()
        val filtered = filteredWords.value
        val order = if (shuffled) filtered.shuffled().map { it.id } else null
        val deck = if (order == null) {
            filtered
        } else {
            val map = filtered.associateBy { it.id }
            order.mapNotNull { map[it] }
        }
        val index = if (id == null) 0 else deck.indexOfFirst { it.id == id }.coerceAtLeast(0)
        _ui.update { it.copy(cardIndex = index, shuffledIds = order, playing = false) }
        val word = deck.getOrNull(index) ?: return
        if (_ui.value.settings.speakOnPageChange) speak(word)
    }

    fun step(delta: Int) {
        val deck = studyDeck()
        if (deck.isEmpty()) return
        val settings = _ui.value.settings
        var next = _ui.value.cardIndex + delta
        if (settings.loop) {
            next = (next % deck.size + deck.size) % deck.size
        } else {
            next = next.coerceIn(0, deck.lastIndex)
        }
        selectCard(next)
    }

    fun selectCard(index: Int) {
        val deck = studyDeck()
        if (deck.isEmpty()) return
        val next = index.coerceIn(0, deck.lastIndex)
        if (next == _ui.value.cardIndex) return
        _ui.update { it.copy(cardIndex = next) }
        val word = deck[next]
        if (_ui.value.settings.speakOnPageChange && !_ui.value.playing) speak(word)
    }

    fun toggleShuffle() {
        val filtered = filteredWords.value
        val enabling = _ui.value.shuffledIds == null
        val current = currentCard()
        val order = if (enabling) filtered.shuffled().map { it.id } else null
        val deck = if (order == null) filtered else {
            val map = filtered.associateBy { it.id }
            order.mapNotNull { map[it] }
        }
        val index = deck.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
        _ui.update { it.copy(shuffledIds = order, cardIndex = index) }
    }

    fun toggleCardSpeak() {
        val enabling = !_ui.value.settings.speakOnPageChange
        if (!enabling) {
            speakJob?.cancel()
            speakJob = null
            tts.stop()
        }
        updateSettings { it.copy(speakOnPageChange = enabling) }
        if (enabling) {
            currentCard()?.let { speak(it) }
        }
    }

    fun speakCurrent() {
        currentCard()?.let { speak(it) }
    }

    fun speak(entry: VocabEntry) {
        speakJob?.cancel()
        speakJob = viewModelScope.launch {
            tts.speak(entry.text, _ui.value.settings.accent)
        }
    }

    fun speakText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        speak(VocabEntry(text = trimmed, definitions = emptyList()))
    }

    fun speakTextSlow(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        speakJob?.cancel()
        speakJob = viewModelScope.launch {
            tts.speak(trimmed, _ui.value.settings.accent, slow = true)
        }
    }

    fun speakSyllables(parts: List<String>) {
        if (parts.isEmpty()) return
        speakJob?.cancel()
        speakJob = viewModelScope.launch {
            tts.speakSequence(parts, _ui.value.settings.accent)
        }
    }

    fun isWordSaved(word: String): Boolean {
        val notebookId = _ui.value.settings.defaultNotebookId
        return repo.items.value.any {
            it.notebookId == notebookId && it.text.equals(word.trim(), ignoreCase = true)
        }
    }

    fun toggleSaveRelatedWord(entry: VocabEntry) {
        viewModelScope.launch {
            val notebookId = _ui.value.settings.defaultNotebookId
            val existing = repo.getByWord(notebookId, entry.text)
            if (existing != null) {
                repo.delete(existing.id)
                _ui.update { state ->
                    val result = state.lookupResult
                    if (result != null && result.entry.text.equals(entry.text, ignoreCase = true)) {
                        state.copy(
                            lookupResult = LookupResult(
                                entry = result.entry.copy(id = 0L),
                                saved = false,
                            ),
                        )
                    } else {
                        state
                    }
                }
            } else {
                val app = getApplication<Application>()
                val bundled = withContext(Dispatchers.IO) {
                    MnemonicCatalog.bytesFor(app, entry.text)
                }
                val toSave = entry.copy(
                    id = 0L,
                    notebookId = notebookId,
                    imageBlob = entry.imageBlob ?: bundled,
                    addedAtMillis = System.currentTimeMillis(),
                )
                val id = repo.insert(toSave)
                val saved = repo.getById(id) ?: toSave.copy(id = id)
                _ui.update { state ->
                    val result = state.lookupResult
                    if (result != null && result.entry.text.equals(entry.text, ignoreCase = true)) {
                        state.copy(lookupResult = LookupResult(saved, true))
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun selectNotebook(id: Long) {
        if (notebooks.value.none { it.id == id }) return
        settingsStore.saveActiveNotebookId(id)
        _ui.update {
            it.copy(
                activeNotebookId = id,
                cardIndex = 0,
                shuffledIds = null,
                revealedIds = emptySet(),
            )
        }
    }

    fun createNotebook(name: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repo.createNotebook(name) }
                .onSuccess { id -> selectNotebook(id) }
                .onFailure { onError(it.message ?: "创建失败") }
        }
    }

    fun deleteNotebook(id: Long, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repo.deleteNotebook(id) }
                .onSuccess {
                    _ui.update { state ->
                        var next = state
                        if (state.activeNotebookId == id) {
                            settingsStore.saveActiveNotebookId(Notebook.DEFAULT_ID)
                            next = next.copy(
                                activeNotebookId = Notebook.DEFAULT_ID,
                                cardIndex = 0,
                                shuffledIds = null,
                                revealedIds = emptySet(),
                            )
                        }
                        if (state.settings.defaultNotebookId == id) {
                            val settings = state.settings.copy(defaultNotebookId = Notebook.DEFAULT_ID)
                            settingsStore.saveSettings(settings)
                            next = next.copy(settings = settings)
                        }
                        next
                    }
                }
                .onFailure { onError(it.message ?: "删除失败") }
        }
    }

    fun wordCountInNotebook(notebookId: Long): Int = repo.wordCountInNotebook(notebookId)

    fun moveEntriesToNotebook(
        entryIds: List<Long>,
        targetNotebookId: Long,
        onError: (String) -> Unit = {},
    ) {
        if (entryIds.isEmpty()) return
        viewModelScope.launch {
            runCatching { repo.moveEntriesToNotebook(entryIds, targetNotebookId) }
                .onFailure { onError(it.message ?: "移动失败") }
        }
    }

    fun setDefaultNotebook(id: Long) {
        if (notebooks.value.none { it.id == id }) return
        updateSettings { it.copy(defaultNotebookId = id) }
    }

    fun toggleAutoPlay() {
        if (_ui.value.playing) {
            stopAutoPlay()
        } else {
            startAutoPlay()
        }
    }

    fun updateDefinitions(entryId: Long, definitions: List<Definition>) {
        viewModelScope.launch {
            if (entryId > 0L) {
                repo.updateDefinitions(entryId, definitions)
                syncEntryEverywhere(entryId)
            } else {
                _ui.update { state ->
                    val result = state.lookupResult ?: return@update state
                    val updated = result.entry.copy(definitions = definitions)
                    state.copy(lookupResult = result.copy(entry = updated))
                }
            }
        }
    }

    fun exportNotebookJson(): String {
        val notebookId = _ui.value.activeNotebookId
        val entries = repo.items.value.filter { it.notebookId == notebookId }
        val notebook = notebooks.value.firstOrNull { it.id == notebookId }
        return VocabNotebookExporter.toJson(entries, notebook)
    }

    fun suggestedExportFileName(): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
        return "hotwords-backup-$stamp.json"
    }

    suspend fun importNotebookJson(json: String): NotebookImportResult {
        val entries = VocabNotebookImporter.fromJson(json)
        if (entries.isEmpty()) {
            throw IllegalArgumentException("备份中没有词条")
        }
        return repo.importEntries(_ui.value.activeNotebookId, entries)
    }

    private suspend fun syncEntryEverywhere(id: Long) {
        val latest = repo.getById(id) ?: return
        _ui.update { state ->
            var next = state
            val result = state.lookupResult
            if (result != null && result.entry.id == id) {
                next = next.copy(lookupResult = result.copy(entry = latest))
            }
            next
        }
    }

    fun updateSettings(transform: (StudySettings) -> StudySettings) {
        _ui.update { state ->
            val next = transform(state.settings)
            settingsStore.saveSettings(next)
            state.copy(settings = next)
        }
    }

    private suspend fun lookupNow(query: String) {
        _ui.update { it.copy(lookupLoading = true, lookupError = null) }
        try {
            val lookedUp = dictionary.lookup(query)
            val notebookId = _ui.value.settings.defaultNotebookId
            val saved = repo.getByWord(notebookId, lookedUp.text)
            // Always refresh related fields so algorithm improvements apply immediately.
            val near = NearWordsFinder.find(lookedUp.text)
            val synonyms = lookedUp.synonyms
            val antonyms = lookedUp.antonyms
            val examples = lookedUp.examples
            val withRelated = lookedUp.copy(
                nearWords = near,
                synonyms = synonyms,
                antonyms = antonyms,
                examples = examples,
            )
            val entry = when {
                saved == null -> withRelated
                else -> {
                    val merged = saved.copy(
                        nearWords = near,
                        synonyms = synonyms,
                        antonyms = antonyms,
                        examples = examples,
                        definitions = saved.definitions.ifEmpty { lookedUp.definitions },
                        ipaUk = lookedUp.ipaUk ?: saved.ipaUk,
                        ipaUs = lookedUp.ipaUs ?: saved.ipaUs,
                    )
                    repo.updateRelatedWords(
                        saved.id,
                        nearWords = near,
                        synonyms = synonyms,
                        antonyms = antonyms,
                        examples = examples,
                    )
                    merged
                }
            }
            _ui.update {
                it.copy(
                    lookupLoading = false,
                    lookupResult = LookupResult(entry, saved != null),
                    lookupError = null,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _ui.update {
                it.copy(
                    lookupLoading = false,
                    lookupResult = null,
                    lookupError = "没查到，检查网络后再试",
                )
            }
        }
    }

    fun lookupNearWord(word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        _ui.update { it.copy(lookupQuery = trimmed, lookupError = null) }
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch { lookupNow(trimmed) }
    }

    private fun filterAndSort(words: List<VocabEntry>, state: VocabUiState): List<VocabEntry> {
        var list = words
        list = when (state.filter) {
            WordFilter.ALL -> list
            WordFilter.WORDS -> list.filter { !it.isPhrase }
            WordFilter.PHRASES -> list.filter { it.isPhrase }
        }
        if (state.query.isNotBlank()) {
            val q = state.query.trim().lowercase()
            list = list.filter { entry ->
                entry.text.lowercase().contains(q) || entry.definitionLine.contains(q)
            }
        }
        return when (state.sortMode) {
            SortMode.MANUAL -> list.sortedWith(compareBy({ it.sortOrder }, { it.id }))
            SortMode.TIME_DESC -> list.sortedByDescending { it.addedAtMillis }
            SortMode.TIME_ASC -> list.sortedBy { it.addedAtMillis }
            SortMode.ALPHA -> list.sortedBy { it.text.lowercase() }
        }
    }

    private fun startAutoPlay() {
        autoPlayJob?.cancel()
        speakJob?.cancel()
        tts.stop()
        _ui.update { it.copy(playing = true) }
        autoPlayJob = viewModelScope.launch {
            while (_ui.value.playing) {
                val word = currentCard() ?: break
                if (_ui.value.settings.speakOnPageChange) {
                    tts.speak(word.text, _ui.value.settings.accent)
                }
                if (!_ui.value.playing) break
                delay(_ui.value.settings.autoPlayIntervalMs)
                if (!_ui.value.playing) break
                val before = _ui.value.cardIndex
                step(1)
                if (!_ui.value.settings.loop && _ui.value.cardIndex == before) {
                    stopAutoPlay()
                    break
                }
            }
        }
    }

    fun stopAutoPlay() {
        autoPlayJob?.cancel()
        autoPlayJob = null
        speakJob?.cancel()
        speakJob = null
        tts.stop()
        _ui.update { it.copy(playing = false) }
    }

    override fun onCleared() {
        stopAutoPlay()
        tts.shutdown()
        super.onCleared()
    }
}
