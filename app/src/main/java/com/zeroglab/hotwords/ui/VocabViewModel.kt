package com.zeroglab.hotwords.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroglab.hotwords.audio.TtsPlayer
import com.zeroglab.hotwords.data.AiImageClient
import com.zeroglab.hotwords.data.DictionaryClient
import com.zeroglab.hotwords.data.LookupCache
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
import com.zeroglab.hotwords.data.HotWordsApi
import com.zeroglab.hotwords.data.WordHeads
import com.zeroglab.hotwords.data.SessionStore
import com.zeroglab.hotwords.data.UserSession
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext

data class DateSection(
    val dateLabel: String,
    val entries: List<VocabEntry>,
)

private data class ListQuery(
    val notebookId: Long,
    val filter: WordFilter,
    val sortMode: SortMode,
    val query: String,
)

data class VocabUiState(
    val filter: WordFilter = WordFilter.ALL,
    val sortMode: SortMode = SortMode.MANUAL,
    val query: String = "",
    val searching: Boolean = false,
    val hideDefinitions: Boolean = true,
    val revealedIds: Set<Long> = emptySet(),
    val cardIndex: Int = 0,
    /** Permutation of natural indices into [VocabViewModel.filteredWords]; null = sequential. */
    val shuffledOrder: IntArray? = null,
    val playing: Boolean = false,
    val settings: StudySettings = StudySettings(),
    val lookupQuery: String = "",
    val lookupLoading: Boolean = false,
    val lookupRelatedLoading: Boolean = false,
    val lookupError: String? = null,
    val lookupResult: LookupResult? = null,
    val imageBusy: Boolean = false,
    val imageError: String? = null,
    val activeNotebookId: Long = Notebook.DEFAULT_ID,
    val listLoading: Boolean = false,
    val listError: String? = null,
)

private const val DEV_LOGIN_PHONE = "13611283451"
private const val DEV_LOGIN_CODE = "888888"

data class LoginUi(
    val phone: String = DEV_LOGIN_PHONE,
    val code: String = DEV_LOGIN_CODE,
    val password: String = "",
    val passwordConfirm: String = "",
    val needPassword: Boolean = false,
    val sending: Boolean = false,
    val loggingIn: Boolean = false,
    val countdownSec: Int = 0,
    val error: String? = null,
)

class VocabViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = VocabRepository(application)
    private val settingsStore = SettingsStore(application)
    private val sessionStore = SessionStore(application)
    private val api = HotWordsApi()
    private val dictionary = DictionaryClient()
    private val tts = TtsPlayer(application)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

    private val _ui = MutableStateFlow(VocabUiState())
    val ui: StateFlow<VocabUiState> = _ui.asStateFlow()
    val notebooks: StateFlow<List<Notebook>> = repo.notebooks
    private val _session = MutableStateFlow(sessionStore.load())
    val session: StateFlow<UserSession?> = _session.asStateFlow()
    private val _login = MutableStateFlow(LoginUi())
    val login: StateFlow<LoginUi> = _login.asStateFlow()
    private var pageJob: Job? = null
    private var letterIndexJob: Job? = null
    private var catalogLetterIndexJob: Job? = null
    private var hydrateJob: Job? = null
    private var selectNotebookJob: Job? = null
    private var headsAppendJob: Job? = null
    private var countdownJob: Job? = null
    /** notebookId → A–Z/# absolute list index (prefetched for all system catalogs). */
    private val letterIndexByNotebook = mutableMapOf<Long, Map<Char, Int>>()
    private val headsReady = mutableSetOf<Long>()
    private val headsCache = mutableMapOf<Long, WordHeads>()
    private val _alphabetLetterIndex = MutableStateFlow<Map<Char, Int>>(emptyMap())
    /** Server-precomputed A–Z/# → absolute list index for the active notebook. */
    val alphabetLetterIndex: StateFlow<Map<Char, Int>> = _alphabetLetterIndex.asStateFlow()
    private val _pendingListScrollEntryId = MutableStateFlow<Long?>(null)
    /** When leaving card mode, list should land on this entry once. */
    val pendingListScrollEntryId: StateFlow<Long?> = _pendingListScrollEntryId.asStateFlow()

    val filteredWords: StateFlow<List<VocabEntry>> = combine(
        repo.items,
        _ui.map { state ->
            ListQuery(state.activeNotebookId, state.filter, state.sortMode, state.query)
        }.distinctUntilChanged(),
    ) { words, query ->
        filterAndSort(
            words.filter { it.notebookId == query.notebookId },
            query,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var autoPlayJob: Job? = null
    private var speakJob: Job? = null
    private var pageSpeakJob: Job? = null
    private var lookupJob: Job? = null
    private var studyDeckFiltered: List<VocabEntry>? = null
    private var studyDeckOrder: IntArray? = null
    private var studyDeckCache: List<VocabEntry> = emptyList()

    init {
        val loaded = settingsStore.loadSettings()
        var activeId = settingsStore.loadActiveNotebookId()
        val notebookIds = repo.notebooks.value.map { it.id }.toSet()
        if (activeId !in notebookIds) {
            activeId = Notebook.DEFAULT_ID
            settingsStore.saveActiveNotebookId(activeId)
        }
        var settings = loaded
        val defaultNotebook = repo.notebooks.value.firstOrNull { it.id == settings.defaultNotebookId }
        if (settings.defaultNotebookId !in notebookIds || defaultNotebook?.isSystem == true) {
            val fallbackId = repo.notebooks.value.firstOrNull { !it.isSystem }?.id ?: Notebook.DEFAULT_ID
            settings = settings.copy(defaultNotebookId = fallbackId)
            settingsStore.saveSettings(settings)
        }
        val session = _session.value
        val startNotebook = session?.vocabNotebookId?.takeIf { it > 0L }
            ?: activeId.takeIf { it > 0L }
            ?: Notebook.DEFAULT_ID
        _ui.value = VocabUiState(
            settings = settings.copy(
                defaultNotebookId = session?.vocabNotebookId?.takeIf { it > 0L }
                    ?: settings.defaultNotebookId,
            ),
            activeNotebookId = startNotebook,
        )
        if (session != null) {
            viewModelScope.launch { bootstrapSession() }
        } else {
            viewModelScope.launch { bootstrapGuestCatalogs() }
        }
    }

    private fun token(): String = _session.value?.token ?: error("未登录")

    private suspend fun bootstrapSession() {
        val current = _session.value ?: return
        val catalogs = runCatching { api.listCatalogs() }.getOrDefault(emptyList())
        runCatching { api.listNotebooks(current.token) }
            .onSuccess { books ->
                publishMergedNotebooks(books, catalogs)
                val merged = repo.notebooks.value
                val preferred = merged.firstOrNull { it.id == _ui.value.activeNotebookId }
                    ?: merged.firstOrNull { it.id == current.vocabNotebookId }
                    ?: merged.firstOrNull()
                if (preferred != null) {
                    selectNotebook(preferred.id)
                }
            }
            .onFailure { error ->
                if (catalogs.isNotEmpty()) {
                    publishMergedNotebooks(repo.notebooks.value, catalogs)
                    _ui.update { it.copy(listError = null) }
                } else {
                    _ui.update { it.copy(listError = error.message ?: "无法连接服务器") }
                }
                val cached = repo.notebooks.value
                val fallback = cached.firstOrNull { it.id == _ui.value.activeNotebookId }
                    ?: cached.firstOrNull()
                if (fallback != null) {
                    withContext(Dispatchers.IO) { repo.openCachedNotebook(fallback.id) }
                }
            }
    }

    private suspend fun bootstrapGuestCatalogs() {
        runCatching { api.listCatalogs() }
            .onSuccess { books ->
                if (books.isEmpty()) return@onSuccess
                publishMergedNotebooks(repo.notebooks.value, books)
                val preferred = repo.notebooks.value.firstOrNull { it.id == _ui.value.activeNotebookId }
                    ?: repo.notebooks.value.firstOrNull { !it.isSystem }
                    ?: repo.notebooks.value.firstOrNull()
                if (preferred != null) {
                    selectNotebook(preferred.id)
                }
            }
            .onFailure { error ->
                _ui.update { it.copy(listError = error.message ?: "无法加载系统词书") }
            }
    }

    private suspend fun syncPublishedCatalogs() {
        val catalogs = runCatching { api.listCatalogs() }.getOrDefault(emptyList())
        if (catalogs.isEmpty()) return
        publishMergedNotebooks(repo.notebooks.value, catalogs)
    }

    private suspend fun publishMergedNotebooks(
        existing: List<Notebook>,
        catalogs: List<Notebook>,
    ) {
        val catalogIds = catalogs.map { it.id }.toSet()
        val catalogSlugs = catalogs.mapNotNull { it.slug }.toSet()
        val users = existing.filter { book ->
            !book.isSystem && book.id !in catalogIds && book.slug !in catalogSlugs
        }
        val merged = (users + catalogs).sortedWith(
            compareBy<Notebook> { if (it.isSystem) 1 else 0 }
                .thenBy { it.sortOrder }
                .thenBy { it.id },
        )
        withContext(Dispatchers.IO) { repo.publishNotebooks(merged) }
        prefetchCatalogHeads(merged.filter { it.isSystem })
    }

    /**
     * Warm word-head lists + letter maps for 中考 / 高考 / CET4 / CET6
     * so alphabet scrubbing and card seeking stay in RAM.
     */
    private fun prefetchCatalogHeads(catalogs: List<Notebook>) {
        if (catalogs.isEmpty()) return
        val missing = catalogs.filter { it.id !in headsReady }
        if (missing.isEmpty()) {
            applyCachedLetterIndex(_ui.value.activeNotebookId)
            val active = _ui.value.activeNotebookId
            if (active in headsReady && repo.items.value.size < (notebooks.value.firstOrNull { it.id == active }?.wordCount ?: 0)) {
                viewModelScope.launch { loadHeads(active) }
            }
            return
        }
        catalogLetterIndexJob?.cancel()
        catalogLetterIndexJob = viewModelScope.launch {
            coroutineScope {
                missing.map { book ->
                    async { loadHeads(book.id, applyIfActive = book.id == _ui.value.activeNotebookId) }
                }.forEach { it.await() }
            }
            applyCachedLetterIndex(_ui.value.activeNotebookId)
        }
    }

    private fun applyCachedLetterIndex(notebookId: Long) {
        publishLetterIndexForNotebook(notebookId)
    }

    /** Always replace the active map — never leave the previous notebook's offsets. */
    private fun publishLetterIndexForNotebook(notebookId: Long) {
        if (notebookId <= 0L) return
        if (_ui.value.activeNotebookId != notebookId) return
        val map = letterIndexByNotebook[notebookId] ?: headsCache[notebookId]?.letterIndex
        if (map != null) letterIndexByNotebook[notebookId] = map
        _alphabetLetterIndex.value = map.orEmpty()
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

    /**
     * Playback order. Only materialize this for small pager decks (≤80);
     * large catalogs look up the current row in O(1) via [currentCard].
     */
    fun studyDeck(): List<VocabEntry> {
        val filtered = filteredWords.value
        val order = _ui.value.shuffledOrder
        if (order == null) return filtered
        if (filtered === studyDeckFiltered && order === studyDeckOrder) return studyDeckCache
        val deck = ArrayList<VocabEntry>(filtered.size)
        val n = filtered.size
        val seen = BooleanArray(n)
        for (src in order) {
            if (src in 0 until n && !seen[src]) {
                deck.add(filtered[src])
                seen[src] = true
            }
        }
        for (i in 0 until n) if (!seen[i]) deck.add(filtered[i])
        studyDeckFiltered = filtered
        studyDeckOrder = order
        studyDeckCache = deck
        return deck
    }

    fun currentCard(): VocabEntry? {
        val filtered = filteredWords.value
        if (filtered.isEmpty()) return null
        return filtered.getOrNull(naturalIndexOfCurrentCard())
    }

    /** 0-based index in the unshuffled notebook list (not the shuffle playback order). */
    fun naturalIndexOfCurrentCard(): Int {
        val filtered = filteredWords.value
        if (filtered.isEmpty()) return 0
        val last = filtered.lastIndex
        val cardIndex = _ui.value.cardIndex.coerceIn(0, last)
        val order = _ui.value.shuffledOrder
        if (order == null || order.isEmpty()) return cardIndex
        val mapped = if (cardIndex < order.size) order[cardIndex] else cardIndex
        return mapped.coerceIn(0, last)
    }

    fun seekToNaturalIndex(naturalIndex: Int) {
        val filtered = filteredWords.value
        if (filtered.isEmpty()) return
        val natural = naturalIndex.coerceIn(0, filtered.lastIndex)
        val order = _ui.value.shuffledOrder
        val deckIndex = if (order == null || order.isEmpty()) {
            natural
        } else {
            val found = order.indexOf(natural)
            if (found >= 0) found else natural
        }
        selectCard(deckIndex, speak = false)
    }

    fun setLookupQuery(query: String) {
        _ui.update { it.copy(lookupQuery = query, lookupError = null) }
        lookupJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _ui.update { it.copy(lookupResult = null, lookupLoading = false, lookupRelatedLoading = false) }
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
            runCatching {
                if (result.saved) {
                    if (result.entry.id > 0L) {
                        runCatching { api.deleteWord(token(), result.entry.id) }
                        repo.delete(result.entry.id)
                    }
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
            }.onFailure { error ->
                _ui.update { it.copy(lookupError = error.message ?: "收藏失败") }
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
        val notebookId = _session.value?.vocabNotebookId?.takeIf { it > 0L }
            ?: notebooks.value.firstOrNull { !it.isSystem }?.id
            ?: _ui.value.settings.defaultNotebookId.takeIf { it > 0L }
            ?: return null
        val toSave = result.entry.copy(
            notebookId = notebookId,
            imageBlob = null,
            addedAtMillis = System.currentTimeMillis(),
        )
        val saved = api.createWord(token(), notebookId, toSave)
        repo.cacheEntry(saved)
        _ui.update { it.copy(lookupResult = LookupResult(saved, true)) }
        return saved.id
    }

    fun deleteWord(id: Long) {
        deleteWords(listOf(id))
    }

    fun deleteWords(ids: List<Long>) {
        if (ids.isEmpty()) return
        if (activeNotebook()?.isSystem == true) return
        viewModelScope.launch {
            ids.forEach { id ->
                runCatching { api.deleteWord(token(), id) }
                    .onSuccess { repo.delete(id) }
            }
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
        if (activeNotebook()?.isSystem == true) return
        val stateForOrder = _ui.value.copy(
            sortMode = SortMode.MANUAL,
            query = "",
            searching = false,
        )
        val list = filterAndSort(
            repo.items.value.filter { it.notebookId == _ui.value.activeNotebookId },
            stateForOrder,
        ).toMutableList()
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
        val natural = when {
            id == null -> 0
            else -> filtered.indexOfFirst { it.id == id }.coerceAtLeast(0)
        }
        val order = if (shuffled && filtered.isNotEmpty()) {
            IntArray(filtered.size) { it }.also { it.shuffle() }
        } else {
            null
        }
        val index = if (order == null) {
            natural
        } else {
            val found = order.indexOf(natural)
            if (found >= 0) found else 0
        }
        _ui.update { it.copy(cardIndex = index, shuffledOrder = order, playing = false) }
        studyDeckFiltered = null
        val word = filtered.getOrNull(natural) ?: return
        if (_ui.value.settings.speakOnPageChange) speak(word)
    }

    /** Capture the current card word so the list can restore scroll when card closes. */
    fun prepareReturnToList() {
        stopAutoPlay()
        _pendingListScrollEntryId.value = currentCard()?.id
    }

    fun consumePendingListScroll() {
        _pendingListScrollEntryId.value = null
    }

    fun step(delta: Int) {
        val n = filteredWords.value.size
        if (n == 0) return
        val settings = _ui.value.settings
        var next = _ui.value.cardIndex + delta
        if (settings.loop) {
            next = (next % n + n) % n
        } else {
            next = next.coerceIn(0, n - 1)
        }
        selectCard(next)
    }

    fun selectCard(index: Int) {
        selectCard(index, speak = true)
    }

    fun seekCard(index: Int) {
        selectCard(index, speak = false)
    }

    private fun selectCard(index: Int, speak: Boolean) {
        val filtered = filteredWords.value
        if (filtered.isEmpty()) return
        val next = index.coerceIn(0, filtered.lastIndex)
        if (next == _ui.value.cardIndex) return
        _ui.update { it.copy(cardIndex = next) }
        val word = currentCard() ?: return
        if (word.definitions.isEmpty()) hydrateAround(naturalIndexOfCurrentCard())
        if (speak && next >= filtered.lastIndex - 10) loadMoreWords()
        if (speak && _ui.value.settings.speakOnPageChange && !_ui.value.playing) {
            speakAfterPageSettle(word)
        }
    }

    private fun speakAfterPageSettle(entry: VocabEntry) {
        pageSpeakJob?.cancel()
        pageSpeakJob = viewModelScope.launch {
            delay(220)
            if (!_ui.value.settings.speakOnPageChange || _ui.value.playing) return@launch
            speak(entry)
        }
    }

    fun toggleShuffle() {
        val filtered = filteredWords.value
        if (filtered.isEmpty()) return
        val currentNatural = naturalIndexOfCurrentCard().coerceIn(0, filtered.lastIndex)
        val enabling = _ui.value.shuffledOrder == null
        if (!enabling) {
            studyDeckFiltered = null
            _ui.update { it.copy(shuffledOrder = null, cardIndex = currentNatural) }
            return
        }
        val order = IntArray(filtered.size) { it }
        order.shuffle()
        var index = 0
        for (i in order.indices) {
            if (order[i] == currentNatural) {
                index = i
                break
            }
        }
        studyDeckFiltered = null
        _ui.update { it.copy(shuffledOrder = order, cardIndex = index) }
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
        val cleaned = parts.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return
        speakJob?.cancel()
        speakJob = viewModelScope.launch {
            val accent = _ui.value.settings.accent
            if (cleaned.size == 1) {
                tts.speak(cleaned.first(), accent)
                return@launch
            }
            tts.speakSequence(cleaned, accent)
            delay(280)
            tts.speak(cleaned.joinToString(""), accent)
        }
    }

    fun isWordSaved(word: String): Boolean {
        val notebookId = _session.value?.vocabNotebookId ?: return false
        return repo.peekWord(notebookId, word) != null
    }

    fun toggleSaveRelatedWord(entry: VocabEntry) {
        viewModelScope.launch {
            val notebookId = _session.value?.vocabNotebookId ?: return@launch
            val existing = repo.getByWord(notebookId, entry.text)
            if (existing != null) {
                runCatching { api.deleteWord(token(), existing.id) }
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
                val toSave = entry.copy(
                    id = 0L,
                    notebookId = notebookId,
                    imageBlob = null,
                    addedAtMillis = System.currentTimeMillis(),
                )
                val saved = runCatching { api.createWord(token(), notebookId, toSave) }.getOrNull() ?: return@launch
                repo.cacheEntry(saved)
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

    fun onNotebookTabOpened() {
        viewModelScope.launch {
            syncPublishedCatalogs()
            val id = _ui.value.activeNotebookId.takeIf { it > 0L }
                ?: _session.value?.vocabNotebookId?.takeIf { it > 0L }
                ?: repo.notebooks.value.firstOrNull()?.id
                ?: return@launch
            withContext(Dispatchers.IO) { repo.openCachedNotebook(id) }
            refreshAlphabetLetterIndex(id)
            loadHeads(id)
            if (id !in headsReady) {
                if (repo.items.value.isEmpty()) {
                    refreshNotebook(id, reset = true, prefetchAll = true)
                } else if (repo.hasMore) {
                    refreshNotebook(id, reset = false, prefetchAll = true)
                }
            } else {
                hydrateNotebook(id)
            }
        }
    }

    fun selectNotebook(id: Long) {
        if (id <= 0L) return
        if (id == _ui.value.activeNotebookId && repo.items.value.isNotEmpty()) {
            applyCachedLetterIndex(id)
            if (_alphabetLetterIndex.value.isEmpty()) refreshAlphabetLetterIndex(id)
            viewModelScope.launch {
                if (loadHeads(id)) hydrateNotebook(id)
                else if (repo.hasMore) refreshNotebook(id, reset = false, prefetchAll = true)
            }
            return
        }
        // End the chip tap immediately: highlight + clear previous list filter via active id.
        pageJob?.cancel()
        hydrateJob?.cancel()
        selectNotebookJob?.cancel()
        headsAppendJob?.cancel()
        val hasHeadsInMemory = headsCache[id]?.items?.isNotEmpty() == true
        _ui.update {
            it.copy(
                activeNotebookId = id,
                cardIndex = 0,
                shuffledOrder = null,
                revealedIds = emptySet(),
                listError = null,
                listLoading = !hasHeadsInMemory,
            )
        }
        publishLetterIndexForNotebook(id)
        selectNotebookJob = viewModelScope.launch {
            launch(Dispatchers.IO) { settingsStore.saveActiveNotebookId(id) }
            refreshAlphabetLetterIndex(id)
            // Catalogs: apply heads progressively (first screen, then full list).
            // Skip SQLite full reload — that was the multi-second hitch on CET6 etc.
            if (hasHeadsInMemory || notebooks.value.firstOrNull { it.id == id }?.isSystem == true) {
                val loadedHeads = loadHeads(id)
                if (_ui.value.activeNotebookId == id) {
                    _ui.update { it.copy(listLoading = false) }
                }
                if (loadedHeads) {
                    hydrateNotebook(id)
                    return@launch
                }
            }
            // User notebooks / cache miss: small SQLite read is fine.
            withContext(Dispatchers.IO) { repo.openCachedNotebook(id) }
            if (_ui.value.activeNotebookId != id) return@launch
            val loadedHeads = loadHeads(id)
            if (_ui.value.activeNotebookId == id) {
                _ui.update { it.copy(listLoading = false) }
            }
            if (!loadedHeads) {
                val cached = repo.items.value.size
                when {
                    cached == 0 -> refreshNotebook(id, reset = true, prefetchAll = true)
                    repo.hasMore -> refreshNotebook(id, reset = false, prefetchAll = true)
                }
            } else {
                hydrateNotebook(id)
            }
        }
    }

    fun loadMoreWords() {
        val id = _ui.value.activeNotebookId
        if (id <= 0L || pageJob?.isActive == true || !repo.hasMore) return
        if (id in headsReady) {
            hydrateNotebook(id)
            return
        }
        refreshNotebook(id, reset = false, prefetchAll = false)
    }

    /**
     * Ensure the list shows [letter]. With heads in RAM, load stubs up to the
     * server-computed offset then hydrate definitions there.
     */
    fun seekAlphabetLetter(letter: Char) {
        val id = _ui.value.activeNotebookId
        if (id <= 0L) return
        val target = letterIndexByNotebook[id]?.get(letter)
            ?: _alphabetLetterIndex.value[letter]
            ?: _alphabetLetterIndex.value.entries
                .filter { letterSortKey(it.key) > letterSortKey(letter) }
                .minByOrNull { letterSortKey(it.key) }
                ?.value
        if (id in headsReady && target != null) {
            headsAppendJob?.cancel()
            viewModelScope.launch {
                ensureHeadsLoadedUpTo(id, target + 1)
                if (_ui.value.activeNotebookId == id) {
                    hydrateAround(target)
                }
            }
            return
        }
        val token = _session.value?.token
        val notebook = notebooks.value.firstOrNull { it.id == id }
        if (token == null && notebook?.isSystem != true) return
        val items = repo.items.value
        if (items.any { it.notebookId == id && wordInitialOf(it.text) == letter }) {
            hydrateAround(target ?: 0)
            return
        }
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            val windowStart = repo.listWindowStart
            val coveredEnd = windowStart + repo.items.value.size
            val needJump = target != null && (
                target < windowStart ||
                    target >= coveredEnd + SEEK_JUMP_GAP ||
                    (windowStart == 0 && target > repo.items.value.size + SEEK_JUMP_GAP)
                )
            if (needJump && target != null) {
                val ok = if (target <= 0) {
                    fetchPage(token, id, reset = true)
                } else {
                    fetchPage(token, id, reset = false, fromIndex = target)
                }
                if (!ok) return@launch
            } else {
                while (isActive && repo.hasMore && _ui.value.activeNotebookId == id) {
                    if (repo.items.value.any {
                            it.notebookId == id && wordInitialOf(it.text) == letter
                        }
                    ) {
                        break
                    }
                    if (target != null && repo.listWindowStart + repo.items.value.size > target) break
                    if (!fetchPage(token, id, reset = false)) break
                }
            }
        }
    }

    private suspend fun loadHeads(notebookId: Long, applyIfActive: Boolean = true): Boolean {
        val token = _session.value?.token
        val notebook = notebooks.value.firstOrNull { it.id == notebookId }
        if (token == null && notebook?.isSystem != true) return false
        val heads = headsCache[notebookId] ?: run {
            withContext(Dispatchers.IO) {
                runCatching { api.listHeads(token, notebookId) }
                    .recoverCatching { error ->
                        if (notebook?.isSystem == true && token != null) {
                            api.listHeads(null, notebookId)
                        } else {
                            throw error
                        }
                    }
                    .getOrNull()
            }?.also { headsCache[notebookId] = it }
        } ?: return false
        if (heads.items.isEmpty()) return false
        letterIndexByNotebook[notebookId] = heads.letterIndex
        headsReady.add(notebookId)
        if (applyIfActive && _ui.value.activeNotebookId == notebookId) {
            _alphabetLetterIndex.value = heads.letterIndex
            headsAppendJob?.cancel()
            headsAppendJob = viewModelScope.launch {
                applyHeadsProgressive(notebookId, heads)
            }
        }
        return true
    }

    /** Load ordered stubs until at least [minCount] rows exist (alphabet seek). */
    private suspend fun ensureHeadsLoadedUpTo(notebookId: Long, minCount: Int) {
        val heads = headsCache[notebookId] ?: return
        if (_ui.value.activeNotebookId != notebookId) return
        val need = minCount.coerceIn(0, heads.items.size)
        if (repo.items.value.size >= need &&
            repo.items.value.firstOrNull()?.notebookId == notebookId
        ) {
            return
        }
        if (repo.items.value.isEmpty() ||
            repo.items.value.firstOrNull()?.notebookId != notebookId
        ) {
            val previewEnd = minOf(HEADS_PREVIEW_COUNT, need, heads.items.size)
            val preview = withContext(Dispatchers.Default) {
                heads.items.subList(0, previewEnd).map { it.toStub(notebookId) }
            }
            if (_ui.value.activeNotebookId != notebookId) return
            repo.applyHeads(notebookId, preview, heads.total)
            studyDeckFiltered = null
        }
        var from = repo.items.value.size
        while (from < need && _ui.value.activeNotebookId == notebookId) {
            val end = minOf(from + HEADS_CHUNK, need)
            val chunk = withContext(Dispatchers.Default) {
                heads.items.subList(from, end).map { it.toStub(notebookId) }
            }
            if (_ui.value.activeNotebookId != notebookId) return
            repo.appendHeadStubs(notebookId, chunk, heads.total)
            studyDeckFiltered = null
            from = end
            yield()
        }
    }

    /**
     * Paint the first screen of stubs immediately, then append the rest in
     * chunks so chip taps stay responsive on 3k–6k catalogs.
     */
    private suspend fun applyHeadsProgressive(notebookId: Long, heads: WordHeads) {
        if (_ui.value.activeNotebookId != notebookId) return
        val items = heads.items
        val total = heads.total
        val previewCount = minOf(HEADS_PREVIEW_COUNT, items.size)
        val preview = withContext(Dispatchers.Default) {
            items.subList(0, previewCount).map { it.toStub(notebookId) }
        }
        if (_ui.value.activeNotebookId != notebookId) return
        repo.applyHeads(notebookId, preview, total)
        studyDeckFiltered = null
        _ui.update { it.copy(listLoading = false) }
        if (previewCount >= items.size) return
        yield()
        delay(16)
        var from = previewCount
        while (from < items.size) {
            if (_ui.value.activeNotebookId != notebookId) return
            val end = minOf(from + HEADS_CHUNK, items.size)
            val chunk = withContext(Dispatchers.Default) {
                items.subList(from, end).map { it.toStub(notebookId) }
            }
            if (_ui.value.activeNotebookId != notebookId) return
            repo.appendHeadStubs(notebookId, chunk, total)
            studyDeckFiltered = null
            from = end
            yield()
        }
    }

    private fun hydrateNotebook(notebookId: Long, around: Int = 0) {
        if (notebookId != _ui.value.activeNotebookId) return
        if (hydrateJob?.isActive == true) return
        val token = _session.value?.token
        val notebook = notebooks.value.firstOrNull { it.id == notebookId }
        if (token == null && notebook?.isSystem != true) return
        hydrateJob = viewModelScope.launch {
            var from = around.coerceAtLeast(0)
            val total = notebooks.value.firstOrNull { it.id == notebookId }?.wordCount
                ?: repo.items.value.size
            var passes = 0
            while (isActive && _ui.value.activeNotebookId == notebookId && passes < 2) {
                val items = repo.items.value
                while (from < items.size && items[from].definitions.isNotEmpty()) from += 1
                if (from >= total || from >= items.size) {
                    if (passes == 0 && around > 0) {
                        from = 0
                        passes += 1
                        continue
                    }
                    break
                }
                if (!fetchPage(token, notebookId, reset = false, fromIndex = from, mergeOnly = true)) break
                from += PAGE_LIMIT
            }
        }
    }

    private fun hydrateAround(index: Int) {
        val id = _ui.value.activeNotebookId
        if (id !in headsReady) return
        val items = repo.items.value
        val i = index.coerceIn(0, items.lastIndex.coerceAtLeast(0))
        if (items.getOrNull(i)?.definitions?.isEmpty() == true) {
            if (hydrateJob?.isActive == true) hydrateJob?.cancel()
            hydrateNotebook(id, around = i)
        }
    }

    private fun refreshAlphabetLetterIndex(notebookId: Long) {
        letterIndexJob?.cancel()
        publishLetterIndexForNotebook(notebookId)
        val token = _session.value?.token
        val notebook = notebooks.value.firstOrNull { it.id == notebookId }
        if (token == null && notebook?.isSystem != true) return
        if (letterIndexByNotebook.containsKey(notebookId)) return
        // Always refresh in background; catalogs are prefetched at list sync time.
        letterIndexJob = viewModelScope.launch {
            runCatching {
                runCatching { api.letterIndex(token, notebookId) }
                    .recoverCatching { error ->
                        if (notebook?.isSystem == true && token != null) {
                            api.letterIndex(null, notebookId)
                        } else {
                            throw error
                        }
                    }
                    .getOrThrow()
            }.onSuccess { map ->
                letterIndexByNotebook[notebookId] = map
                if (_ui.value.activeNotebookId == notebookId) {
                    _alphabetLetterIndex.value = map
                }
            }
        }
    }

    private fun refreshNotebook(id: Long, reset: Boolean, prefetchAll: Boolean = false) {
        val token = _session.value?.token
        val notebook = notebooks.value.firstOrNull { it.id == id }
        // Guests may only fetch published system catalogs.
        if (token == null && notebook?.isSystem != true) return
        if (!reset && pageJob?.isActive == true && !prefetchAll) return
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            val showLoading = reset && repo.items.value.isEmpty()
            if (showLoading) _ui.update { it.copy(listLoading = true, listError = null) }
            val firstOk = fetchPage(token, id, reset = reset)
            if (showLoading) _ui.update { it.copy(listLoading = false) }
            if (firstOk && prefetchAll && _ui.value.activeNotebookId == id) {
                while (isActive && repo.hasMore && _ui.value.activeNotebookId == id) {
                    if (!fetchPage(token, id, reset = false)) break
                }
            }
        }
    }

    private suspend fun fetchPage(
        token: String?,
        id: Long,
        reset: Boolean,
        fromIndex: Int = 0,
        mergeOnly: Boolean = false,
    ): Boolean {
        return runCatching {
            val cursor = when {
                reset || mergeOnly || fromIndex > 0 -> null
                else -> repo.currentCursor()
            }
            val page = runCatching {
                api.listWords(token, id, cursor, limit = PAGE_LIMIT, fromIndex = fromIndex)
            }
                .recoverCatching { error ->
                    val system = repo.notebooks.value.firstOrNull { it.id == id }?.isSystem == true
                    if (system && token != null) {
                        api.listWords(null, id, cursor, limit = PAGE_LIMIT, fromIndex = fromIndex)
                    } else {
                        throw error
                    }
                }
                .getOrThrow()
            withContext(Dispatchers.IO) {
                when {
                    mergeOnly -> {
                        repo.mergeDetails(id, page)
                        if (page.items.isNotEmpty()) dbPersistAsync(page.items)
                    }
                    fromIndex > 0 && id in headsReady -> {
                        repo.mergeDetails(id, page)
                        if (page.items.isNotEmpty()) dbPersistAsync(page.items)
                    }
                    fromIndex > 0 -> repo.applySeekWindow(id, page, fromIndex)
                    reset -> repo.applyFirstPage(id, page)
                    else -> repo.applyNextPage(id, page)
                }
            }
            studyDeckFiltered = null
        }.onFailure { error ->
            if (error is CancellationException) throw error
            _ui.update { it.copy(listError = error.message ?: "加载失败") }
        }.isSuccess
    }

    private fun dbPersistAsync(items: List<VocabEntry>) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.persistEntries(items) }
        }
    }

    private fun wordInitialOf(text: String): Char {
        val first = text.trim().firstOrNull()?.uppercaseChar() ?: return '#'
        return if (first in 'A'..'Z') first else '#'
    }

    private fun letterSortKey(letter: Char): Int =
        if (letter == '#') 26 else (letter - 'A').coerceIn(0, 25)

    fun createNotebook(name: String, onError: (String) -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            onError("请输入名称")
            return
        }
        viewModelScope.launch {
            runCatching {
                val created = api.createNotebook(token(), trimmed)
                refreshNotebookList()
                created
            }.onSuccess { created ->
                selectNotebook(created.id)
            }.onFailure { error ->
                onError(error.message ?: "创建失败")
            }
        }
    }

    fun deleteNotebook(id: Long, onError: (String) -> Unit = {}) {
        val target = notebooks.value.firstOrNull { it.id == id }
        if (target == null || target.isSystem) {
            onError("系统词书不能删除")
            return
        }
        viewModelScope.launch {
            runCatching {
                api.deleteNotebook(token(), id)
                refreshNotebookList()
            }.onSuccess {
                val remaining = repo.notebooks.value
                val session = _session.value
                if (session != null && session.vocabNotebookId == id) {
                    val nextVocabId = remaining.firstOrNull { !it.isSystem }?.id ?: 0L
                    val updated = session.copy(vocabNotebookId = nextVocabId)
                    sessionStore.save(updated)
                    _session.value = updated
                }
                _ui.update { state ->
                    var next = state
                    if (state.activeNotebookId == id) {
                        val fallback = remaining.firstOrNull { !it.isSystem } ?: remaining.firstOrNull()
                        val nextId = fallback?.id ?: Notebook.DEFAULT_ID
                        settingsStore.saveActiveNotebookId(nextId)
                        next = next.copy(
                            activeNotebookId = nextId,
                            cardIndex = 0,
                            shuffledOrder = null,
                            revealedIds = emptySet(),
                        )
                        if (nextId > 0L) {
                            withContext(Dispatchers.IO) { repo.openCachedNotebook(nextId) }
                        }
                    }
                    if (state.settings.defaultNotebookId == id) {
                        val fallbackId = remaining.firstOrNull { !it.isSystem }?.id ?: Notebook.DEFAULT_ID
                        val settings = state.settings.copy(defaultNotebookId = fallbackId)
                        settingsStore.saveSettings(settings)
                        next = next.copy(settings = settings)
                    }
                    next
                }
            }.onFailure { onError(it.message ?: "删除失败") }
        }
    }

    private suspend fun refreshNotebookList() {
        val catalogs = runCatching { api.listCatalogs() }.getOrDefault(emptyList())
        val books = api.listNotebooks(token())
        publishMergedNotebooks(books, catalogs)
    }

    fun wordCountInNotebook(notebookId: Long): Int =
        notebooks.value.firstOrNull { it.id == notebookId }?.wordCount ?: 0

    fun moveEntriesToNotebook(
        entryIds: List<Long>,
        targetNotebookId: Long,
        onError: (String) -> Unit = {},
    ) {
        if (entryIds.isEmpty()) return
        if (activeNotebook()?.isSystem == true) {
            onError("系统词书不能修改")
            return
        }
        if (notebooks.value.firstOrNull { it.id == targetNotebookId }?.isSystem == true) {
            onError("不能移动到系统词书")
            return
        }
        viewModelScope.launch {
            runCatching { repo.moveEntriesToNotebook(entryIds, targetNotebookId) }
                .onFailure { onError(it.message ?: "移动失败") }
        }
    }

    fun setDefaultNotebook(id: Long) {
        val notebook = notebooks.value.firstOrNull { it.id == id } ?: return
        if (notebook.isSystem) return
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
                runCatching { api.updateWord(token(), entryId, definitions = definitions) }
                    .onSuccess { repo.cacheEntry(it) }
                    .onFailure { repo.updateDefinitions(entryId, definitions) }
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
        val queryKey = LookupCache.keyFor(query)
        _ui.update {
            it.copy(lookupLoading = true, lookupError = null, lookupRelatedLoading = false)
        }
        try {
            LookupCache.get(queryKey)?.let { cached ->
                publishLookupResult(cached, queryKey)
                return
            }

            val core = dictionary.lookupCore(query)
            publishLookupResult(core.entry, queryKey)

            if (_ui.value.lookupQuery.trim().let { LookupCache.keyFor(it) } != queryKey) return

            _ui.update { it.copy(lookupRelatedLoading = true) }

            val (synonyms, antonyms, nearWords) = coroutineScope {
                val synAntDeferred = async {
                    dictionary.enrichSynonymsAntonyms(core.entry.text, core.youdaoRoot)
                }
                val nearDeferred = async { NearWordsFinder.find(core.entry.text) }
                val (syn, ant) = synAntDeferred.await()
                Triple(syn, ant, nearDeferred.await())
            }
            if (_ui.value.lookupQuery.trim().let { LookupCache.keyFor(it) } != queryKey) return

            val enriched = core.entry.copy(
                nearWords = nearWords,
                synonyms = synonyms,
                antonyms = antonyms,
            )
            publishLookupResult(enriched, queryKey, cache = true)
            _ui.update { it.copy(lookupRelatedLoading = false) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _ui.update {
                it.copy(
                    lookupLoading = false,
                    lookupRelatedLoading = false,
                    lookupResult = null,
                    lookupError = "没查到，检查网络后再试",
                )
            }
        }
    }

    private suspend fun publishLookupResult(
        lookedUp: VocabEntry,
        queryKey: String,
        cache: Boolean = false,
    ) {
        val notebookId = _ui.value.settings.defaultNotebookId
        val saved = repo.getByWord(notebookId, lookedUp.text)
        val near = lookedUp.nearWords
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
                if (cache || (near.isNotEmpty() || synonyms.isNotEmpty() || antonyms.isNotEmpty())) {
                    repo.updateRelatedWords(
                        saved.id,
                        nearWords = near,
                        synonyms = synonyms,
                        antonyms = antonyms,
                        examples = examples,
                    )
                }
                merged
            }
        }
        if (cache) {
            LookupCache.put(
                withRelated.copy(
                    id = 0L,
                    notebookId = Notebook.DEFAULT_ID,
                    imageBlob = null,
                ),
            )
        }
        if (_ui.value.lookupQuery.trim().let { LookupCache.keyFor(it) } != queryKey) return
        _ui.update {
            it.copy(
                lookupLoading = false,
                lookupResult = LookupResult(entry, saved != null),
                lookupError = null,
            )
        }
    }

    fun lookupNearWord(word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        _ui.update { it.copy(lookupQuery = trimmed, lookupError = null) }
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch { lookupNow(trimmed) }
    }

    private fun filterAndSort(words: List<VocabEntry>, query: ListQuery): List<VocabEntry> =
        filterAndSort(words, query.filter, query.sortMode, query.query)

    private fun filterAndSort(words: List<VocabEntry>, state: VocabUiState): List<VocabEntry> =
        filterAndSort(words, state.filter, state.sortMode, state.query)

    private fun filterAndSort(
        words: List<VocabEntry>,
        filter: WordFilter,
        sortMode: SortMode,
        query: String,
    ): List<VocabEntry> {
        var list = words
        list = when (filter) {
            WordFilter.ALL -> list
            WordFilter.WORDS -> list.filter { !it.isPhrase }
            WordFilter.PHRASES -> list.filter { it.isPhrase }
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter { entry ->
                entry.text.lowercase().contains(q) || entry.definitionLine.contains(q)
            }
        }
        return when (sortMode) {
            // Repo / heads already keep sort_order order — skip O(n log n) on large catalogs.
            SortMode.MANUAL ->
                if (filter == WordFilter.ALL && query.isBlank()) list
                else list.sortedWith(compareBy({ it.sortOrder }, { it.id }))
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

    fun setLoginPhone(value: String) {
        _login.update { it.copy(phone = value, needPassword = false, error = null) }
    }

    fun setLoginCode(value: String) {
        _login.update { it.copy(code = value, error = null) }
    }

    fun setLoginPassword(value: String) {
        _login.update { it.copy(password = value, error = null) }
    }

    fun setLoginPasswordConfirm(value: String) {
        _login.update { it.copy(passwordConfirm = value, error = null) }
    }

    fun sendLoginCode() {
        val phone = _login.value.phone
        if (phone.length != 11) {
            _login.update { it.copy(error = "请输入11位手机号") }
            return
        }
        viewModelScope.launch {
            _login.update { it.copy(sending = true, error = null) }
            runCatching { api.sendCode(phone) }
                .onSuccess { debug ->
                    startCountdown()
                    if (!debug.isNullOrBlank()) {
                        _login.update { it.copy(code = debug) }
                    }
                }
                .onFailure { error ->
                    _login.update { it.copy(error = error.message ?: "发送失败") }
                }
            _login.update { it.copy(sending = false) }
        }
    }

    fun submitLogin() {
        val state = _login.value
        if (state.needPassword) {
            when {
                state.password.length < 6 -> {
                    _login.update { it.copy(error = "密码至少 6 位") }
                    return
                }
                state.password != state.passwordConfirm -> {
                    _login.update { it.copy(error = "两次密码不一致") }
                    return
                }
            }
        }
        viewModelScope.launch {
            _login.update { it.copy(loggingIn = true, error = null) }
            runCatching {
                if (state.needPassword) {
                    api.register(state.phone, state.code, state.password)
                } else {
                    api.login(state.phone, state.code)
                }
            }.onSuccess { result ->
                if (result.isNewUser) {
                    _login.update {
                        it.copy(
                            needPassword = true,
                            loggingIn = false,
                            error = null,
                        )
                    }
                    return@launch
                }
                val session = result.session ?: error("登录失败")
                enterSession(session)
            }.onFailure { error ->
                _login.update { it.copy(error = error.message ?: "登录失败") }
            }
            _login.update { it.copy(loggingIn = false) }
        }
    }

    private fun enterSession(session: UserSession) {
        sessionStore.save(session)
        _session.value = session
        settingsStore.saveActiveNotebookId(session.vocabNotebookId)
        _ui.update {
            it.copy(
                activeNotebookId = session.vocabNotebookId,
                settings = it.settings.copy(defaultNotebookId = session.vocabNotebookId),
            )
        }
        viewModelScope.launch { bootstrapSession() }
    }

    fun logout() {
        sessionStore.clear()
        _session.value = null
        repo.wipeCache()
        headsReady.clear()
        headsCache.clear()
        letterIndexByNotebook.clear()
        _login.value = LoginUi()
        _ui.update {
            it.copy(
                activeNotebookId = Notebook.DEFAULT_ID,
                lookupResult = null,
                listError = null,
            )
        }
        viewModelScope.launch { bootstrapGuestCatalogs() }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (sec in 60 downTo 1) {
                _login.update { it.copy(countdownSec = sec) }
                delay(1000)
            }
            _login.update { it.copy(countdownSec = 0) }
        }
    }

    fun stopAutoPlay() {
        autoPlayJob?.cancel()
        autoPlayJob = null
        speakJob?.cancel()
        speakJob = null
        pageSpeakJob?.cancel()
        pageSpeakJob = null
        tts.stop()
        _ui.update { it.copy(playing = false) }
    }

    private companion object {
        const val PAGE_LIMIT = 100
        /** First paint when switching into a large catalog (CET / 高考 / 中考). */
        const val HEADS_PREVIEW_COUNT = 64
        /** Append remaining stubs in chunks so Compose stays responsive. */
        const val HEADS_CHUNK = 400
        /** If the letter is farther than this many unloaded rows, jump via fromIndex. */
        const val SEEK_JUMP_GAP = 80
    }

    override fun onCleared() {
        stopAutoPlay()
        tts.shutdown()
        super.onCleared()
    }
}
