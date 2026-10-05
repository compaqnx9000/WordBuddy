import PhotosUI
import SwiftUI
import UIKit

struct CardModeView: View {
    @EnvironmentObject private var model: AppModel
    var entries: [VocabEntry]
    var startIndex: Int
    var onClose: () -> Void

    @State private var currentId: Int64?
    @State private var shuffled = false
    @State private var shuffledDeck: [VocabEntry] = []
    @State private var naturalIndexById: [Int64: Int] = [:]
    @State private var shuffleBusy = false
    @State private var hydrateTask: Task<Void, Never>?
    /// Word to keep visible while the list window catches up after leaving shuffle.
    @State private var pinnedWordId: Int64?
    /// Word to keep on screen after prepending the previous page.
    @State private var earlierAnchor: Int64?
    @State private var playing = false
    @State private var playTask: Task<Void, Never>?
    @State private var phonicsTask: Task<Void, Never>?
    @State private var showSeek = false
    @State private var seeking = false
    @State private var seekValue = 0.0
    /// Index the thumb was released on. Kept until the card actually lands there.
    @State private var pendingSeek: Int?
    @State private var liveSeekTarget: Int?
    @State private var liveSeekTask: Task<Void, Never>?
    @State private var editingEntry: VocabEntry?
    @State private var cardTips: [WordHomophone] = []
    @State private var cardNotes: [Definition] = []
    @State private var aidsWordKey = ""
    @State private var imageEntry: VocabEntry?
    @State private var imageError: String?
    @State private var imageBusyId: Int64?
    @State private var previewWord: String?

    private var deck: [VocabEntry] {
        shuffled ? shuffledDeck : entries
    }

    private var pageTotal: Int {
        if shuffled { return shuffledDeck.count }
        return max(model.wordTotal, deck.count)
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            if deck.isEmpty {
                Spacer()
                Text("没有可学习的单词")
                    .foregroundStyle(Theme.onSurfaceVariant)
                Spacer()
            } else {
                ScrollViewReader { proxy in
                    ScrollView(.horizontal) {
                        LazyHStack(spacing: 0) {
                            ForEach(deck) { entry in
                                card(entry)
                                    .containerRelativeFrame(.horizontal)
                                    .id(entry.id)
                            }
                        }
                        .scrollTargetLayout()
                    }
                    .scrollTargetBehavior(.paging)
                    .scrollPosition(id: $currentId)
                    .scrollIndicators(.hidden)
                    .onChange(of: earlierAnchor) { _, anchor in
                        guard let anchor else { return }
                        var transaction = Transaction()
                        transaction.disablesAnimations = true
                        withTransaction(transaction) {
                            proxy.scrollTo(anchor, anchor: .leading)
                            currentId = anchor
                        }
                    }
                }
            }
            controlBar
        }
        .stellarScreenBackground()
        .onAppear {
            let index = min(max(0, startIndex), max(0, entries.count - 1))
            currentId = entries.isEmpty ? nil : entries[index].id
            syncSeekValue()
            if let id = currentId, let entry = entries.first(where: { $0.id == id }) {
                Task { await loadCardAids(entry) }
            }
            prefetchEarlierCards()
        }
        .onChange(of: currentId) { _, id in
            syncSeekValue()
            if let id, let entry = deck.first(where: { $0.id == id }) {
                Task { await loadCardAids(entry) }
            }
            guard !seeking else { return }
            guard let id, let entry = deck.first(where: { $0.id == id }) else { return }
            if model.speakOnPageChange || playing {
                Speech.speak(entry.text, accent: model.accent)
            }
            if shuffled {
                if !seeking { hydrateCurrent() }
            } else if let index = deck.firstIndex(where: { $0.id == id }), index >= deck.count - 2 {
                Task { await model.loadMoreWords() }
            }
            prefetchEarlierCards()
            if playing { scheduleAdvance() }
        }
        .onChange(of: entries.map(\.id)) { _, ids in
            if let pinnedWordId {
                if ids.contains(pinnedWordId) {
                    currentId = pinnedWordId
                    self.pinnedWordId = nil
                }
                return
            }
            guard !shuffled else { return }
            guard let currentId, !ids.contains(currentId) else { return }
            self.currentId = ids.first
        }
        .onDisappear { stopPlay() }
        .overlay {
            if let entry = editingEntry {
                MeaningEditSheet(entry: entry, onClose: { editingEntry = nil }) { updated in
                    aidsWordKey = updated.text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                    cardNotes = updated.definitions.filter(\.isUserAdded)
                    Task { await loadCardAids(updated) }
                }
            }
        }
        .overlay {
            if let entry = imageEntry {
                MnemonicImageSheet(
                    entry: entry,
                    busy: imageBusyId == entry.id,
                    error: imageError,
                    onClose: {
                        guard imageBusyId == nil else { return }
                        imageEntry = nil
                        imageError = nil
                    },
                    onGenerate: { meaning in
                        Task { await generateImage(for: entry, meaning: meaning) }
                    },
                    onPickPhoto: { data in
                        MnemonicImageStore.save(word: entry.text, data: data)
                        model.mnemonicRevision += 1
                        imageEntry = nil
                        imageError = nil
                    }
                )
                .id(entry.id)
            }
        }
        .overlay {
            if let previewWord {
                RelatedWordPreview(word: previewWord) {
                    self.previewWord = nil
                }
            }
        }
    }

    private var notebookTitle: String {
        let name = model.activeNotebook?.name ?? ""
        return name.isEmpty ? "生词本" : name
    }

    private var header: some View {
        HStack(spacing: 0) {
            Button(action: onClose) {
                Image(systemName: "chevron.left")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .frame(width: 48, height: 48)
            }
            .buttonStyle(.plain)
            VStack(spacing: 2) {
                Text(notebookTitle)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Theme.cyanSoft)
                    .lineLimit(1)
                Text("卡片模式")
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            .frame(maxWidth: .infinity)
            Color.clear.frame(width: 48, height: 48)
        }
        .frame(height: 56)
        .background(Theme.surfaceContainer.opacity(0.72))
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.cyan.opacity(0.2)).frame(height: 1)
        }
    }

    private func card(_ entry: VocabEntry) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 16) {
                    CardWordBlock(
                        entry: entry,
                        accent: model.accent,
                        imageBusy: imageBusyId == entry.id,
                        imageRevision: model.mnemonicRevision,
                        onSpeak: { Speech.speak(entry.text, accent: model.accent) },
                        onAccent: { accent in
                            model.accent = accent
                            Speech.speak(entry.text, accent: accent)
                        },
                        onPhonics: {
                            phonicsTask?.cancel()
                            let parts = NaturalPhonics.syllables(entry.text)
                            let word = entry.text
                            phonicsTask = Task { await Speech.speakPhonics(parts: parts, word: word, accent: model.accent) }
                        },
                        onImage: {
                            imageError = nil
                            imageEntry = entry
                        }
                    )
                    definitionCard(entry)
                    relatedBlock(entry)
                }
                if !entry.examples.isEmpty {
                    CardExamplePanel(word: entry.text, examples: entry.examples, accent: model.accent)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 16)
        }
    }

    private func definitionCard(_ entry: VocabEntry) -> some View {
        let key = entry.text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let showingAids = aidsWordKey == key
        let originals = entry.definitions.filter { !$0.isUserAdded }
        let localNotes = entry.definitions.filter(\.isUserAdded)
        let notes = showingAids && !cardNotes.isEmpty ? cardNotes : localNotes
        let tips = showingAids ? cardTips : []
        return VStack(alignment: .leading, spacing: 0) {
            if originals.isEmpty && notes.isEmpty && tips.isEmpty {
                Text("暂无释义")
                    .font(.body)
                    .foregroundStyle(Theme.onSurfaceVariant)
            } else {
                if !originals.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(Array(originals.enumerated()), id: \.offset) { _, definition in
                            DefinitionLine(definition: definition)
                        }
                    }
                }
                if !notes.isEmpty {
                    if !originals.isEmpty {
                        ThemeHairline().padding(.vertical, 14)
                    }
                    Text("我的补充")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .padding(.bottom, 10)
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(Array(notes.enumerated()), id: \.offset) { _, definition in
                            DefinitionLine(definition: definition)
                        }
                    }
                }
            }
            if !tips.isEmpty {
                ThemeHairline(color: Theme.gold).padding(.top, 14)
                Text("谐音助记")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Theme.gold)
                    .padding(.top, 10)
                    .padding(.bottom, 10)
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(tips.prefix(3)) { tip in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(alignment: .center, spacing: 8) {
                                Text(tip.body)
                                    .font(.subheadline)
                                    .foregroundStyle(Theme.onSurface)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                Button {
                                    Task { await toggleCardLike(tip) }
                                } label: {
                                    HStack(spacing: 4) {
                                        Image(systemName: tip.likedByMe ? "hand.thumbsup.fill" : "hand.thumbsup")
                                            .font(.system(size: 12))
                                        Text("\(tip.likeCount)")
                                            .font(.system(size: 12, weight: .semibold))
                                    }
                                    .foregroundStyle(tip.likedByMe ? Theme.gold : Theme.onSurfaceVariant)
                                }
                                .buttonStyle(.plain)
                            }
                            if !tip.likerSummary.isEmpty {
                                Text(tip.likerSummary)
                                    .font(.system(size: 12))
                                    .foregroundStyle(Theme.gold.opacity(0.9))
                            }
                        }
                    }
                }
            }
            ThemeHairline().padding(.top, 14)
            Button {
                var copy = entry
                if showingAids {
                    copy.definitions = originals + notes
                }
                editingEntry = copy
            } label: {
                HStack(spacing: 4) {
                    Spacer()
                    Image(systemName: "pencil")
                        .font(.system(size: 12, weight: .semibold))
                    Text("编辑释义")
                        .font(.system(size: 11, weight: .bold))
                }
                .foregroundStyle(Theme.cyan)
                .padding(.top, 12)
            }
            .buttonStyle(.plain)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private func loadCardAids(_ entry: VocabEntry) async {
        let key = entry.text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !key.isEmpty else { return }
        if aidsWordKey != key {
            cardTips = []
            cardNotes = []
            aidsWordKey = key
        }
        let tips = (try? await model.api.fetchHomophones(token: model.session?.token, word: entry.text)) ?? []
        var notes: [Definition] = []
        if let token = model.session?.token {
            notes = (try? await model.api.fetchWordNotes(token: token, word: entry.text)) ?? []
        }
        guard aidsWordKey == key else { return }
        cardTips = tips
        cardNotes = notes
    }

    private func toggleCardLike(_ tip: WordHomophone) async {
        guard let token = model.session?.token else {
            model.showLogin = true
            return
        }
        guard let updated = try? await model.api.toggleHomophoneLike(token: token, id: tip.id),
              let index = cardTips.firstIndex(where: { $0.id == tip.id }) else { return }
        cardTips[index] = updated
    }

    @ViewBuilder
    private func relatedBlock(_ entry: VocabEntry) -> some View {
        let tabs = relatedTabs(entry)
        if !tabs.isEmpty {
            CardRelatedBlock(tabs: tabs, accent: model.accent) { word in
                previewWord = word
            }
        }
    }

    private func relatedTabs(_ entry: VocabEntry) -> [(String, [String])] {
        var tabs: [(String, [String])] = []
        if !entry.synonyms.isEmpty { tabs.append(("近义词", entry.synonyms)) }
        if !entry.nearWords.isEmpty { tabs.append(("形近词", entry.nearWords)) }
        if !entry.antonyms.isEmpty { tabs.append(("反义词", entry.antonyms)) }
        return tabs
    }

    private var controlBar: some View {
        VStack(spacing: 4) {
            ZStack {
                HStack(spacing: 12) {
                    Button { Task { await step(-1) } } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 14, weight: .semibold))
                    }
                    Text(pageLabel)
                        .font(.system(size: 11, weight: .bold))
                        .tracking(1)
                        .frame(minWidth: 72)
                    Button { Task { await step(1) } } label: {
                        Image(systemName: "chevron.right")
                            .font(.system(size: 14, weight: .semibold))
                    }
                }
                .foregroundStyle(Theme.onSurfaceVariant)
                .buttonStyle(.plain)
                HStack {
                    Spacer()
                    Button {
                        showSeek.toggle()
                        if !showSeek { seeking = false }
                    } label: {
                        Image(systemName: "line.3.horizontal")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundStyle(showSeek ? Theme.cyan : Theme.onSurfaceVariant)
                    }
                    .buttonStyle(.plain)
                    .disabled(pageTotal <= 1)
                }
            }
            if showSeek, pageTotal > 1 {
                Slider(
                    value: $seekValue,
                    in: 0...Double(max(pageTotal - 1, 0)),
                    step: 1,
                    onEditingChanged: { editing in
                        if editing {
                            seeking = true
                            pendingSeek = nil
                        } else {
                            let target = clampedSeekIndex(seekValue)
                            pendingSeek = target
                            seekValue = Double(target)
                            stopLiveSeek()
                            Task { await seek(to: target) }
                            seeking = false
                        }
                    }
                )
                .onChange(of: seekValue) { _, value in
                    guard seeking else { return }
                    scheduleLiveSeek(clampedSeekIndex(value))
                }
                .tint(Theme.cyan)
            }
            HStack {
                barIcon(shuffled ? "shuffle" : "shuffle", size: 22, tint: shuffled ? Theme.cyan : Theme.onSurfaceVariant) {
                    toggleShuffle()
                }
                barIcon("backward.end.fill", size: 28, tint: Theme.onSurface) {
                    Task { await step(-1) }
                }
                Button {
                    togglePlay()
                } label: {
                    Image(systemName: playing ? "pause.fill" : "play.fill")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(Theme.onPrimary)
                        .offset(x: playing ? 0 : 1)
                        .frame(width: 52, height: 52)
                        .background(Theme.cyanSoft, in: Circle())
                        .shadow(color: Theme.cyanSoft.opacity(0.4), radius: 10, y: 2)
                }
                .buttonStyle(.plain)
                barIcon("forward.end.fill", size: 28, tint: Theme.onSurface) {
                    Task { await step(1) }
                }
                barIcon(
                    model.speakOnPageChange ? "speaker.wave.2" : "speaker.slash",
                    size: 22,
                    tint: model.speakOnPageChange ? Theme.cyan : Theme.onSurfaceVariant
                ) {
                    model.setSpeakOnPageChange(!model.speakOnPageChange)
                }
            }
            .padding(.top, 4)
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 8)
        .background {
            UnevenRoundedRectangle(topLeadingRadius: 20, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: 20)
                .fill(Theme.surfaceContainer.opacity(0.96))
                .ignoresSafeArea(edges: .bottom)
        }
    }

    private func barIcon(_ name: String, size: CGFloat, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: name)
                .font(.system(size: size * 0.72, weight: .semibold))
                .foregroundStyle(tint)
                .frame(maxWidth: .infinity)
                .frame(height: 36)
        }
        .buttonStyle(.plain)
    }

    private var pageLabel: String {
        guard pageTotal > 0 else { return "0 / 0" }
        let index = displayIndex
        return "\(index + 1) / \(pageTotal)"
    }

    private var displayIndex: Int {
        if seeking, showSeek {
            return min(max(0, Int(seekValue.rounded())), max(0, pageTotal - 1))
        }
        if let pendingSeek {
            return min(max(0, pendingSeek), max(0, pageTotal - 1))
        }
        return absoluteDisplayIndex
    }

    private func syncSeekValue() {
        if seeking { return }
        if let pendingSeek {
            seekValue = Double(pendingSeek)
            let landed = absoluteDisplayIndex == pendingSeek
            if landed { self.pendingSeek = nil }
            return
        }
        seekValue = Double(absoluteDisplayIndex)
    }

    /// Original notebook position for the word now on screen. Shuffle playback uses this, so the counter jumps instead of counting 1253, 1254.
    private func wordId(atNatural index: Int) -> Int64? {
        naturalIndexById.first { $0.value == index }?.key
    }

    /// Card position without the slider hold, so a late window update cannot pull the thumb back to 0.
    private var absoluteDisplayIndex: Int {
        guard let currentId, let local = deck.firstIndex(where: { $0.id == currentId }) else { return 0 }
        if shuffled, let natural = naturalIndexById[currentId] {
            return min(natural, max(0, pageTotal - 1))
        }
        return min(model.listWindowStart + local, max(0, pageTotal - 1))
    }

    private func step(_ delta: Int) async {
        pendingSeek = nil
        guard let currentId else { return }
        if shuffled {
            guard let index = deck.firstIndex(where: { $0.id == currentId }) else { return }
            let next = index + delta
            guard deck.indices.contains(next) else {
                if delta > 0 { stopPlay() }
                return
            }
            withAnimation(.easeOut(duration: 0.32)) {
                self.currentId = deck[next].id
            }
            return
        }
        guard let index = model.words.firstIndex(where: { $0.id == currentId }) else { return }
        let next = index + delta
        if model.words.indices.contains(next) {
            self.currentId = model.words[next].id
            return
        }
        if delta > 0, model.nextCursor != nil {
            await model.loadMoreWords()
            if let index = model.words.firstIndex(where: { $0.id == currentId }),
               index + 1 < model.words.count {
                self.currentId = model.words[index + 1].id
                return
            }
        }
        let absolute = model.listWindowStart + next
        if delta > 0, absolute < pageTotal {
            await model.jumpToAbsoluteIndex(absolute)
            if model.words.indices.contains(0) {
                self.currentId = model.words[0].id
            }
            return
        }
        if delta < 0, model.listWindowStart > 0 {
            let target = max(0, model.listWindowStart - 1)
            await model.jumpToAbsoluteIndex(target)
            let local = target - model.listWindowStart
            if model.words.indices.contains(local) {
                self.currentId = model.words[local].id
            } else {
                self.currentId = model.words.first?.id
            }
            return
        }
        if delta > 0 { stopPlay() }
    }

    /// The number on the card is the place in the whole book. The pager only holds the loaded window, so a word at the front of that window has no previous page until the earlier words are prepended.
    private func prefetchEarlierCards() {
        guard !shuffled, earlierAnchor == nil, model.listWindowStart > 0 else { return }
        guard let currentId, let index = deck.firstIndex(where: { $0.id == currentId }), index < 3 else { return }
        let keep = currentId
        Task { @MainActor in
            let result = await model.loadEarlierWords()
            guard case .prepended(_, let token) = result else { return }
            defer { model.finishEarlierLoad(token: token) }
            earlierAnchor = keep
            await Task.yield()
            earlierAnchor = nil
        }
    }

    private func clampedSeekIndex(_ value: Double) -> Int {
        min(max(0, Int(value.rounded())), max(0, pageTotal - 1))
    }

    /// Follow the thumb while it moves. Nearby words update immediately. A far jump is throttled so a fast drag does not request every index.
    private func scheduleLiveSeek(_ target: Int) {
        if shuffled {
            stopLiveSeek()
            guard let id = wordId(atNatural: target) else { return }
            if currentId != id { currentId = id }
            return
        }
        if target >= model.listWindowStart, target < model.listWindowStart + model.words.count {
            stopLiveSeek()
            let local = target - model.listWindowStart
            guard model.words.indices.contains(local) else { return }
            let id = model.words[local].id
            if currentId != id { currentId = id }
            return
        }
        liveSeekTarget = target
        guard liveSeekTask == nil else { return }
        liveSeekTask = Task { @MainActor in
            while !Task.isCancelled, seeking {
                try? await Task.sleep(nanoseconds: 90_000_000)
                guard !Task.isCancelled, seeking, let next = liveSeekTarget else { break }
                liveSeekTarget = nil
                await seek(to: next)
                if liveSeekTarget == nil { break }
            }
            if !Task.isCancelled {
                liveSeekTask = nil
            }
        }
    }

    private func stopLiveSeek() {
        liveSeekTarget = nil
        liveSeekTask?.cancel()
        liveSeekTask = nil
    }

    private func seek(to absolute: Int) async {
        if shuffled {
            guard let id = wordId(atNatural: absolute) else { return }
            currentId = id
            return
        }
        let target = min(max(0, absolute), max(0, pageTotal - 1))
        if target >= model.listWindowStart, target < model.listWindowStart + model.words.count {
            let local = target - model.listWindowStart
            currentId = model.words[local].id
            return
        }
        await model.jumpToAbsoluteIndex(target)
        guard !Task.isCancelled else { return }
        currentId = model.words.first?.id
    }

    private func toggleShuffle() {
        stopPlay()
        stopLiveSeek()
        guard !shuffleBusy else { return }
        if shuffled {
            Task { await disableShuffle() }
        } else {
            Task { await enableShuffle() }
        }
    }

    /// Leave shuffle without changing the word on screen.
    private func disableShuffle() async {
        let id = currentId
        let natural = id.flatMap { naturalIndexById[$0] }
        shuffleBusy = true
        defer { shuffleBusy = false }
        hydrateTask?.cancel()
        pinnedWordId = id
        if let natural {
            await model.jumpToAbsoluteIndex(natural)
        }
        shuffled = false
        shuffledDeck = []
        naturalIndexById = [:]
        if let id {
            currentId = id
            if let natural {
                pendingSeek = natural
                seekValue = Double(natural)
            }
        }
        syncSeekValue()
        if let id, model.words.contains(where: { $0.id == id }) || entries.contains(where: { $0.id == id }) {
            pinnedWordId = nil
        }
    }

    /// Shuffle every word in the notebook, keeping the word currently on screen.
    private func enableShuffle() async {
        guard let notebookId = model.activeNotebookId else { return }
        let keep = currentId
        shuffleBusy = true
        defer { shuffleBusy = false }
        let heads = await model.loadWordHeads(notebookId: notebookId)
        guard !heads.isEmpty else {
            model.banner = "暂时无法读取全部单词"
            return
        }
        var fullById: [Int64: VocabEntry] = [:]
        for word in entries {
            fullById[word.id] = word
        }
        for word in model.words where !word.definitions.isEmpty {
            if fullById[word.id]?.definitions.isEmpty != false {
                fullById[word.id] = word
            }
        }
        var indexById: [Int64: Int] = [:]
        indexById.reserveCapacity(heads.count)
        var stubs: [VocabEntry] = []
        stubs.reserveCapacity(heads.count)
        for (index, head) in heads.enumerated() {
            indexById[head.id] = index
            if let full = fullById[head.id] {
                stubs.append(full)
            } else {
                stubs.append(VocabEntry(
                    id: head.id,
                    notebookId: notebookId,
                    text: head.text,
                    isPhrase: head.isPhrase,
                    ipaUk: head.ipaUk,
                    ipaUs: head.ipaUs,
                    definitions: [],
                    sortOrder: head.sortOrder
                ))
            }
        }
        stubs.shuffle()
        naturalIndexById = indexById
        shuffledDeck = stubs
        shuffled = true
        if let keep, stubs.contains(where: { $0.id == keep }) {
            currentId = keep
        } else {
            currentId = stubs.first?.id
        }
        if let currentId, let natural = indexById[currentId] {
            pendingSeek = natural
            seekValue = Double(natural)
        }
        syncSeekValue()
        hydrateCurrent()
    }

    private func hydrateCurrent() {
        guard shuffled, let id = currentId, let notebookId = model.activeNotebookId else { return }
        guard let slot = shuffledDeck.firstIndex(where: { $0.id == id }) else { return }
        if !shuffledDeck[slot].definitions.isEmpty { return }
        if let loaded = model.words.first(where: { $0.id == id && !$0.definitions.isEmpty }) {
            shuffledDeck[slot] = loaded
            return
        }
        guard let natural = naturalIndexById[id] else { return }
        hydrateTask?.cancel()
        hydrateTask = Task { @MainActor in
            let full = await model.fetchNotebookWord(notebookId: notebookId, naturalIndex: natural)
            guard !Task.isCancelled, currentId == id, let full, full.id == id else { return }
            guard let slot = shuffledDeck.firstIndex(where: { $0.id == id }) else { return }
            shuffledDeck[slot] = full
        }
    }

    private func togglePlay() {
        if playing {
            stopPlay()
            return
        }
        playing = true
        scheduleAdvance()
    }

    private func stopPlay() {
        playing = false
        playTask?.cancel()
        playTask = nil
    }

    private func scheduleAdvance() {
        playTask?.cancel()
        playTask = Task {
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            guard !Task.isCancelled, playing else { return }
            await step(1)
        }
    }

    private func generateImage(for entry: VocabEntry, meaning: String) async {
        imageError = nil
        imageBusyId = entry.id
        defer { imageBusyId = nil }
        let message = await model.generateMnemonicImage(
            word: entry.text,
            meaningHint: meaning,
            announceFailure: false
        )
        if let message {
            imageError = message
        } else {
            imageEntry = nil
        }
    }
}

private struct MnemonicImageSheet: View {
    var entry: VocabEntry
    var busy: Bool
    var error: String?
    var onClose: () -> Void
    var onGenerate: (String) -> Void
    var onPickPhoto: (Data) -> Void

    @State private var selectedIndex = 0
    @State private var showPicker = false
    @State private var photoItem: PhotosPickerItem?

    private var definitions: [Definition] {
        entry.definitions.filter { !$0.meaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.55)
                .ignoresSafeArea()
                .onTapGesture { if !busy { onClose() } }
            VStack(alignment: .leading, spacing: 0) {
                Text("记忆图")
                    .font(.system(size: 26, weight: .bold))
                    .foregroundStyle(Theme.cyanSoft)
                Text("可以从相册选图，或用 AI 生成。AI 生图前请选择要表达的词义。")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.85))
                    .padding(.top, 8)
                if !definitions.isEmpty {
                    Text(definitions.count > 1 ? "选择词义" : "词义")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Theme.onSurface)
                        .padding(.top, 20)
                    ScrollView {
                        VStack(spacing: 8) {
                            ForEach(Array(definitions.enumerated()), id: \.offset) { index, definition in
                                meaningOption(definition.label, selected: index == selectedIndex) {
                                    selectedIndex = index
                                }
                            }
                        }
                        .padding(.top, 12)
                    }
                    .frame(maxHeight: 280)
                }
                if busy {
                    HStack(spacing: 10) {
                        ProgressView().tint(Theme.cyan)
                        Text("AI 生图中…")
                            .font(.system(size: 14))
                            .foregroundStyle(Theme.onSurface)
                    }
                    .padding(.top, 16)
                }
                if let error, !error.isEmpty, !busy {
                    Text(error)
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.pink)
                        .padding(.top, 10)
                }
                HStack(spacing: 10) {
                    Button {
                        showPicker = true
                    } label: {
                        Text("相册选图")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundStyle(Theme.cyanSoft)
                            .frame(maxWidth: .infinity, minHeight: 46)
                            .background(Theme.cyan.opacity(0.08), in: Capsule())
                            .overlay { Capsule().stroke(Theme.cyan.opacity(0.55), lineWidth: 1.5) }
                    }
                    .buttonStyle(.plain)
                    .disabled(busy)
                    Button {
                        let index = min(max(0, selectedIndex), max(0, definitions.count - 1))
                        let meaning = definitions.isEmpty ? "" : definitions[index].label
                        onGenerate(meaning)
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "sparkles")
                            Text("AI 生图")
                        }
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Theme.onPrimary)
                        .frame(maxWidth: .infinity, minHeight: 46)
                        .background(Theme.cyanSoft, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .disabled(busy || definitions.isEmpty)
                }
                .padding(.top, 20)
            }
            .padding(22)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surfaceContainer.opacity(0.98), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.35), lineWidth: 1)
            }
            .padding(.horizontal, 20)
        }
        .photosPicker(isPresented: $showPicker, selection: $photoItem, matching: .images)
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task { await loadPhoto(item) }
        }
    }

    private func meaningOption(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: 12) {
                ZStack {
                    Circle()
                        .stroke(selected ? Theme.cyan : Theme.onSurfaceVariant, lineWidth: 2)
                        .frame(width: 22, height: 22)
                    if selected {
                        Circle()
                            .fill(Theme.cyan)
                            .frame(width: 10, height: 10)
                    }
                }
                .padding(.top, 2)
                Text(label)
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.onSurface)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
            .background(
                (selected ? Theme.cyan.opacity(0.12) : Theme.surfaceHigh.opacity(0.85)),
                in: RoundedRectangle(cornerRadius: 14, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(selected ? Theme.cyan : Theme.outline.opacity(0.55), lineWidth: 1.5)
            }
        }
        .buttonStyle(.plain)
        .disabled(busy)
    }

    private func loadPhoto(_ item: PhotosPickerItem) async {
        guard let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data),
              let jpeg = image.jpegData(compressionQuality: 0.85) else { return }
        onPickPhoto(jpeg)
    }
}

private struct CardWordBlock: View {
    var entry: VocabEntry
    var accent: Accent
    var imageBusy: Bool
    var imageRevision: Int
    var onSpeak: () -> Void
    var onAccent: (Accent) -> Void
    var onPhonics: () -> Void
    var onImage: () -> Void

    @State private var phonicsOn = false
    @State private var mnemonic: UIImage?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 8) {
                    wordLine
                    HStack(spacing: 12) {
                        if let ipa = entry.ipaUk, !ipa.isEmpty {
                            Text("UK [\(ipa)]")
                        }
                        if let ipa = entry.ipaUs, !ipa.isEmpty {
                            Text("US [\(ipa)]")
                        }
                    }
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.onSurfaceVariant)
                    chipRow
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                mnemonicThumb
            }
            HStack(spacing: CardMetrics.sdp(12)) {
                Button(action: onSpeak) {
                    Image(systemName: "speaker.wave.2")
                        .font(.system(size: CardMetrics.sdp(15), weight: .semibold))
                        .foregroundStyle(Theme.onPrimary)
                        .frame(width: CardMetrics.sdp(40), height: CardMetrics.sdp(40))
                        .background(Theme.cyanBright, in: Circle())
                }
                .buttonStyle(.plain)
                HStack(spacing: 0) {
                    accentChip("US", selected: accent == .us) { onAccent(.us) }
                    accentChip("UK", selected: accent == .uk) { onAccent(.uk) }
                }
                .padding(3)
                .background(Theme.surfaceHigh, in: Capsule())
                .overlay(Capsule().stroke(Theme.outline.opacity(0.4), lineWidth: 1))
            }
        }
        .onAppear { reloadImage() }
        .onChange(of: imageRevision) { _, _ in reloadImage() }
        .onChange(of: entry.text) { _, _ in
            phonicsOn = false
            reloadImage()
        }
    }

    private var wordLine: some View {
        let size = wordSize
        return ZStack(alignment: .topLeading) {
            Text(entry.text)
                .font(.system(size: size, weight: .heavy))
                .foregroundStyle(Theme.cyanSoft)
                .shadow(color: Theme.cyan.opacity(0.55), radius: 8)
                .multilineTextAlignment(.leading)
                .lineLimit(3)
                .minimumScaleFactor(0.72)
                .frame(maxWidth: .infinity, alignment: .leading)
                .opacity(phonicsOn ? 0 : 1)
            Text(phonicsAttributed)
                .font(.system(size: size, weight: .heavy))
                .multilineTextAlignment(.leading)
                .lineLimit(3)
                .minimumScaleFactor(0.72)
                .frame(maxWidth: .infinity, alignment: .leading)
                .opacity(phonicsOn ? 1 : 0)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .overlay {
            VStack(spacing: 0) {
                dashedGuide
                Spacer(minLength: 0)
                dashedGuide
            }
            .opacity(phonicsOn ? 1 : 0)
        }
        .animation(nil, value: phonicsOn)
    }

    private var dashedGuide: some View {
        GeometryReader { geo in
            Path { path in
                path.move(to: .zero)
                path.addLine(to: CGPoint(x: geo.size.width, y: 0))
            }
            .stroke(Theme.outline.opacity(0.28), style: StrokeStyle(lineWidth: 1, dash: [4, 5]))
        }
        .frame(height: 1)
    }

    private var wordSize: CGFloat {
        if entry.text.count >= 14 { return 28 }
        if entry.text.count >= 10 { return 32 }
        return 36
    }

    private var phonicsAttributed: AttributedString {
        let parts = NaturalPhonics.syllables(entry.text)
        let colors = [Theme.cyanSoft, Theme.pink, Theme.gold, Theme.cyanBright]
        var result = AttributedString()
        for (index, part) in parts.enumerated() {
            if index > 0 {
                var dot = AttributedString("·")
                dot.foregroundColor = Theme.onSurfaceVariant.opacity(0.75)
                result.append(dot)
            }
            var piece = AttributedString(part)
            piece.foregroundColor = colors[index % colors.count]
            result.append(piece)
        }
        return result
    }

    private var chipRow: some View {
        HStack(spacing: 0) {
            modeChip("正常", selected: !phonicsOn) { phonicsOn = false }
            modeChip("自然拼读", selected: phonicsOn) {
                phonicsOn = true
                onPhonics()
            }
        }
        .padding(3)
        .background(Theme.surfaceHigh, in: Capsule())
        .overlay(Capsule().stroke(Theme.outline.opacity(0.4), lineWidth: 1))
    }

    private func modeChip(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(selected ? Theme.onPrimary : Theme.onSurfaceVariant)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(selected ? Theme.cyan : Color.clear, in: Capsule())
        }
        .buttonStyle(.plain)
    }

    private func accentChip(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(selected ? Theme.onPrimary : Theme.onSurfaceVariant)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(selected ? Theme.cyan : Color.clear, in: Capsule())
        }
        .buttonStyle(.plain)
    }

    private var mnemonicThumb: some View {
        Button(action: onImage) {
            ZStack {
                if imageBusy {
                    ProgressView().tint(Theme.cyan)
                } else if let mnemonic {
                    Image(uiImage: mnemonic)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 96, height: 96)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                } else {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(Theme.cyan.opacity(0.28), style: StrokeStyle(lineWidth: 1.5, dash: [7, 6]))
                        .frame(width: 96, height: 96)
                    Text("+")
                        .font(.system(size: 36, weight: .light))
                        .foregroundStyle(Theme.cyanSoft.opacity(0.85))
                }
            }
            .frame(width: 108, height: 108)
            .padding(6)
            .glassPanel()
        }
        .buttonStyle(.plain)
        .disabled(imageBusy)
    }

    private func reloadImage() {
        if let data = MnemonicImageStore.load(word: entry.text) {
            mnemonic = UIImage(data: data)
        } else {
            mnemonic = nil
        }
    }
}

private struct CardRelatedBlock: View {
    var tabs: [(String, [String])]
    var accent: Accent
    var onSelect: (String) -> Void
    @State private var selected = 0

    var body: some View {
        let index = min(selected, max(0, tabs.count - 1))
        let words = tabs[index].1
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                ForEach(Array(tabs.enumerated()), id: \.offset) { offset, tab in
                    let on = offset == index
                    Button {
                        selected = offset
                    } label: {
                        Text(tab.0)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(on ? Theme.onPrimary : Theme.onSurfaceVariant)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 7)
                            .background(on ? Theme.cyan : Color.clear, in: Capsule())
                            .overlay {
                                if !on {
                                    Capsule().stroke(Theme.outline, lineWidth: 1)
                                }
                            }
                    }
                    .buttonStyle(.plain)
                }
            }
            let rows = stride(from: 0, to: words.count, by: 2).map { start in
                Array(words[start..<min(start + 2, words.count)])
            }
            VStack(spacing: 10) {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    HStack(spacing: 10) {
                        ForEach(Array(row.enumerated()), id: \.offset) { _, word in
                            Button {
                                onSelect(word)
                            } label: {
                                Text(word)
                                    .font(.body)
                                    .foregroundStyle(Theme.onSurface)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 12)
                                    .background(Theme.surfaceHigh.opacity(0.95), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                    .overlay {
                                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                                            .stroke(Theme.outline.opacity(0.55), lineWidth: 1)
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .onChange(of: tabs.map(\.0)) { _, _ in selected = 0 }
    }
}

struct RelatedWordPreview: View {
    @EnvironmentObject private var model: AppModel
    var word: String
    var onDismiss: () -> Void

    @State private var entry: VocabEntry?
    @State private var loading = true
    @State private var error: String?
    @State private var starred = false
    @State private var starring = false

    var body: some View {
        ZStack {
            Color.black.opacity(0.55)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
            panel
                .padding(.horizontal, CardMetrics.sdp(20))
        }
        .task(id: word) { await load() }
    }

    private var panel: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 12) {
                Text(word)
                    .font(.system(size: CardMetrics.sdp(32), weight: .heavy))
                    .foregroundStyle(Theme.cyanSoft)
                    .shadow(color: Theme.cyan.opacity(0.55), radius: 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button {
                    Task { await toggleStar() }
                } label: {
                    Image(systemName: starred ? "star.fill" : "star")
                        .font(.system(size: CardMetrics.sdp(22), weight: .semibold))
                        .foregroundStyle(Theme.gold)
                        .frame(width: CardMetrics.sdp(28), height: CardMetrics.sdp(28))
                }
                .buttonStyle(.plain)
                .disabled(entry == nil || starring)
            }
            if loading {
                ProgressView()
                    .tint(Theme.cyan)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, CardMetrics.sdp(28))
            } else if let error {
                Text(error)
                    .font(.system(size: CardMetrics.sdp(14)))
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .padding(.top, CardMetrics.sdp(16))
            } else if let entry {
                HStack(alignment: .center, spacing: CardMetrics.sdp(12)) {
                    VStack(alignment: .leading, spacing: 2) {
                        if let ipa = entry.ipaUk, !ipa.isEmpty {
                            Text("UK [\(ipa)]")
                        }
                        if let ipa = entry.ipaUs, !ipa.isEmpty {
                            Text("US [\(ipa)]")
                        }
                    }
                    .font(.system(size: CardMetrics.sdp(13)))
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Button {
                        Speech.speak(entry.text, accent: model.accent)
                    } label: {
                        Image(systemName: "speaker.wave.2")
                            .font(.system(size: CardMetrics.sdp(14), weight: .semibold))
                            .foregroundStyle(Theme.onPrimary)
                            .frame(width: CardMetrics.sdp(36), height: CardMetrics.sdp(36))
                            .background(Theme.cyanBright, in: Circle())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, CardMetrics.sdp(12))
                VStack(alignment: .leading, spacing: 12) {
                    if entry.definitions.isEmpty {
                        Text("暂无释义")
                            .foregroundStyle(Theme.onSurfaceVariant)
                    } else {
                        ForEach(Array(entry.definitions.enumerated()), id: \.offset) { _, definition in
                            DefinitionLine(definition: definition)
                        }
                    }
                }
                .padding(CardMetrics.sdp(16))
                .frame(maxWidth: .infinity, alignment: .leading)
                .glassPanel()
                .padding(.top, CardMetrics.sdp(16))
            }
        }
        .padding(CardMetrics.sdp(20))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surfaceContainer.opacity(0.98), in: RoundedRectangle(cornerRadius: CardMetrics.sdp(20), style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: CardMetrics.sdp(20), style: .continuous)
                .stroke(Theme.cyan.opacity(0.35), lineWidth: 1)
        }
    }

    private func load() async {
        loading = true
        error = nil
        entry = nil
        starred = model.isFavorited(word)
        do {
            let looked = try await model.lookup(word)
            entry = looked
            starred = model.isFavorited(word)
            loading = false
        } catch {
            self.error = "暂时查不到释义"
            loading = false
        }
    }

    private func toggleStar() async {
        guard let entry, !starring else { return }
        starring = true
        defer { starring = false }
        if let saved = await model.toggleCatalogFavorite(entry) {
            starred = saved
        }
    }
}

private struct ExamplePaneWidthKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

private struct CardExamplePanel: View {
    var word: String
    var examples: [ExampleSentence]
    var accent: Accent
    @State private var page: Int? = 0
    @State private var paneWidth: CGFloat = 0

    var body: some View {
        let example = examples[safePage]
        VStack(spacing: CardMetrics.sdp(16)) {
            Text("例句")
                .font(.system(size: CardMetrics.sdp(11), weight: .bold))
                .tracking(1.2)
                .foregroundStyle(Theme.onSurfaceVariant)
            sentencePager
                .background {
                    GeometryReader { geo in
                        Color.clear
                            .preference(key: ExamplePaneWidthKey.self, value: geo.size.width)
                    }
                }
                .onPreferenceChange(ExamplePaneWidthKey.self) { paneWidth = $0 }
            if examples.count > 1 {
                HStack(spacing: CardMetrics.sdp(6)) {
                    ForEach(examples.indices, id: \.self) { index in
                        Circle()
                            .fill(index == safePage ? Theme.cyan : Theme.outline)
                            .frame(
                                width: CardMetrics.sdp(index == safePage ? 7 : 6),
                                height: CardMetrics.sdp(index == safePage ? 7 : 6)
                            )
                    }
                }
            }
            HStack(spacing: CardMetrics.sdp(16)) {
                exampleButton {
                    Speech.speak(example.english, accent: accent)
                } label: {
                    Image(systemName: "speaker.wave.2")
                        .font(.system(size: CardMetrics.sdp(15), weight: .semibold))
                        .foregroundStyle(Theme.cyan)
                }
                exampleButton {
                    Speech.speak(example.english, accent: accent, slow: true)
                } label: {
                    Text("慢速")
                        .font(.system(size: CardMetrics.sdp(10), weight: .bold))
                        .foregroundStyle(Theme.cyan)
                }
            }
        }
        .padding(.horizontal, CardMetrics.sdp(20))
        .padding(.vertical, CardMetrics.sdp(22))
        .frame(maxWidth: .infinity)
        .glassPanel()
        .onChange(of: word) { _, _ in page = 0 }
    }

    /// Nested paging scroll view: it owns horizontal drags, so the word pager stays
    /// put until the sentence has fully settled, the same as Android's inner pager.
    private var sentencePager: some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 0) {
                ForEach(Array(examples.enumerated()), id: \.offset) { index, example in
                    sentenceBlock(example, width: paneWidth)
                        .id(index)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollPosition(id: $page)
        .scrollIndicators(.hidden)
        .scrollDisabled(examples.count < 2)
    }

    private func sentenceBlock(_ example: ExampleSentence, width: CGFloat) -> some View {
        let englishSize = CardMetrics.sdp(22)
        let chineseSize = CardMetrics.sdp(16)
        let textWidth = width > 1 ? width : nil
        return VStack(spacing: CardMetrics.sdp(10)) {
            Text(highlighted(example.english))
                .font(.system(size: englishSize))
                .lineSpacing(Self.leading(fontSize: englishSize, lineHeight: CardMetrics.sdp(30)))
                .foregroundStyle(Theme.onSurface)
                .multilineTextAlignment(.center)
                .frame(width: textWidth, alignment: .center)
            Text(example.chinese)
                .font(.system(size: chineseSize))
                .lineSpacing(Self.leading(fontSize: chineseSize, lineHeight: CardMetrics.sdp(24)))
                .foregroundStyle(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .frame(width: textWidth, alignment: .center)
        }
        .frame(width: textWidth)
    }

    private static func leading(fontSize: CGFloat, lineHeight: CGFloat) -> CGFloat {
        let font = UIFont.systemFont(ofSize: fontSize)
        return max(0, lineHeight - font.lineHeight)
    }

    private var safePage: Int {
        min(max(0, page ?? 0), max(0, examples.count - 1))
    }

    private func exampleButton<Label: View>(action: @escaping () -> Void, @ViewBuilder label: () -> Label) -> some View {
        Button(action: action) {
            label()
                .frame(width: CardMetrics.sdp(40), height: CardMetrics.sdp(40))
                .background(Theme.surfaceHigh, in: Circle())
        }
        .buttonStyle(.plain)
    }

    private func highlighted(_ sentence: String) -> AttributedString {
        let token = word.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !token.isEmpty else {
            var plain = AttributedString(sentence)
            plain.foregroundColor = Theme.onSurface
            return plain
        }
        var result = AttributedString()
        let lower = sentence.lowercased()
        let needle = token.lowercased()
        var cursor = sentence.startIndex
        while cursor < sentence.endIndex,
              let found = lower.range(of: needle, range: cursor..<sentence.endIndex) {
            let prefix = String(sentence[cursor..<found.lowerBound])
            if !prefix.isEmpty {
                var part = AttributedString(prefix)
                part.foregroundColor = Theme.onSurface
                result.append(part)
            }
            var hit = AttributedString(String(sentence[found]))
            hit.foregroundColor = Theme.headword
            hit.font = .system(size: CardMetrics.sdp(22), weight: .bold)
            result.append(hit)
            cursor = found.upperBound
        }
        let tail = String(sentence[cursor...])
        if !tail.isEmpty {
            var part = AttributedString(tail)
            part.foregroundColor = Theme.onSurface
            result.append(part)
        }
        return result
    }
}

struct MeaningEditSheet: View {
    @EnvironmentObject private var model: AppModel
    var entry: VocabEntry
    var onClose: () -> Void = {}
    var onSaved: (VocabEntry) -> Void = { _ in }

    private static let posOptions = [
        "n.", "v.", "vt.", "vi.", "adj.", "adv.", "prep.", "conj.",
        "pron.", "num.", "art.", "aux.", "interj.", "abbr.", "phr."
    ]

    @State private var notes: [Definition]
    @State private var selectedPos = "n."
    @State private var noteText = ""
    @State private var homophoneText = ""
    @State private var tips: [WordHomophone] = []
    @State private var saving = false

    init(entry: VocabEntry, onClose: @escaping () -> Void = {}, onSaved: @escaping (VocabEntry) -> Void = { _ in }) {
        self.entry = entry
        self.onClose = onClose
        self.onSaved = onSaved
        _notes = State(initialValue: entry.definitions.filter(\.isUserAdded))
    }

    private var originals: [Definition] {
        entry.definitions.filter { !$0.isUserAdded }
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.45)
                .onTapGesture { onClose() }
            GeometryReader { geo in
                VStack(alignment: .leading, spacing: 0) {
                    Text("补充释义")
                        .font(.system(size: 26, weight: .bold))
                        .foregroundStyle(Theme.cyanSoft)
                    Text("词典释义不可修改。补充笔记仅自己可见；谐音助记全网共享，按点赞展示前 3 条。")
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.onSurfaceVariant.opacity(0.85))
                        .padding(.top, 8)
                    ScrollView {
                        VStack(alignment: .leading, spacing: 0) {
                            if !originals.isEmpty {
                                Text("词典释义")
                                    .font(.system(size: 13, weight: .medium))
                                    .foregroundStyle(Theme.onSurfaceVariant)
                                VStack(alignment: .leading, spacing: 12) {
                                    ForEach(Array(originals.enumerated()), id: \.offset) { _, definition in
                                        DefinitionLine(definition: definition)
                                    }
                                }
                                .padding(14)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .overlay {
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(Theme.outline.replacingOpacity(0.55), lineWidth: 1)
                                }
                                .padding(.top, 8)
                            }
                            Text("我的补充")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Theme.pink)
                                .padding(.top, 18)
                            Text("词性")
                                .font(.system(size: 12))
                                .foregroundStyle(Theme.onSurfaceVariant)
                                .padding(.top, 8)
                            MeaningPosWrap(spacing: 8) {
                                ForEach(Self.posOptions, id: \.self) { pos in
                                    posChip(pos)
                                }
                            }
                            .padding(.top, 8)
                            TextField("输入补充释义…", text: $noteText, axis: .vertical)
                                .lineLimit(3...6)
                                .font(.system(size: 15))
                                .foregroundStyle(Theme.onSurface)
                                .padding(14)
                                .frame(maxWidth: .infinity, minHeight: 72, alignment: .topLeading)
                                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .overlay {
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(Theme.pink.opacity(0.35), lineWidth: 1)
                                }
                                .padding(.top, 12)
                            HStack {
                                Spacer()
                                Button("添加本条", action: commitNote)
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundStyle(Theme.cyan)
                                    .padding(.top, 10)
                            }
                            if !notes.isEmpty {
                                VStack(spacing: 8) {
                                    ForEach(notes.indices, id: \.self) { index in
                                        HStack(spacing: 8) {
                                            Text(notes[index].label)
                                                .font(.system(size: 14))
                                                .foregroundStyle(Theme.onSurface)
                                                .frame(maxWidth: .infinity, alignment: .leading)
                                            Button {
                                                notes.remove(at: index)
                                            } label: {
                                                Image(systemName: "xmark")
                                                    .font(.system(size: 12, weight: .semibold))
                                                    .foregroundStyle(Theme.onSurfaceVariant)
                                            }
                                            .buttonStyle(.plain)
                                        }
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 10)
                                        .background(Theme.pink.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                    }
                                }
                                .padding(.top, 12)
                            }
                            Text("谐音助记")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Theme.gold)
                                .padding(.top, 20)
                            Text("所有用户可见，点赞最多的前 3 条会显示在释义下方。")
                                .font(.system(size: 12))
                                .foregroundStyle(Theme.onSurfaceVariant.opacity(0.8))
                                .padding(.top, 4)
                            if !tips.isEmpty {
                                VStack(alignment: .leading, spacing: 10) {
                                    ForEach(tips.prefix(3)) { tip in
                                        VStack(alignment: .leading, spacing: 4) {
                                            HStack(alignment: .center, spacing: 8) {
                                                Text(tip.body)
                                                    .font(.system(size: 14))
                                                    .foregroundStyle(Theme.onSurface)
                                                    .frame(maxWidth: .infinity, alignment: .leading)
                                                Button {
                                                    Task { await toggleLike(tip) }
                                                } label: {
                                                    HStack(spacing: 4) {
                                                        Image(systemName: "hand.thumbsup")
                                                            .font(.system(size: 12))
                                                        Text("\(tip.likeCount)")
                                                            .font(.system(size: 12, weight: .semibold))
                                                    }
                                                    .foregroundStyle(tip.likedByMe ? Theme.gold : Theme.onSurfaceVariant)
                                                }
                                                .buttonStyle(.plain)
                                            }
                                            if !tip.likerSummary.isEmpty {
                                                Text(tip.likerSummary)
                                                    .font(.system(size: 12))
                                                    .foregroundStyle(Theme.gold.opacity(0.9))
                                                    .frame(maxWidth: .infinity, alignment: .leading)
                                            }
                                        }
                                    }
                                }
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .overlay {
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(Theme.gold.opacity(0.35), lineWidth: 1)
                                }
                                .padding(.top, 8)
                            }
                            TextField("输入谐音帮助记忆，如「about ≈ 额抱他」…", text: $homophoneText, axis: .vertical)
                                .lineLimit(2...5)
                                .font(.system(size: 15))
                                .foregroundStyle(Theme.onSurface)
                                .onChange(of: homophoneText) { _, value in
                                    if value.count > 120 { homophoneText = String(value.prefix(120)) }
                                }
                                .padding(14)
                                .frame(maxWidth: .infinity, minHeight: 64, alignment: .topLeading)
                                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .overlay {
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(Theme.gold.opacity(0.4), lineWidth: 1)
                                }
                                .padding(.top, 8)
                            if !entry.text.isEmpty {
                                Text("针对单词：\(entry.text)")
                                    .font(.system(size: 11))
                                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.65))
                                    .padding(.top, 6)
                            }
                        }
                        .padding(.top, 16)
                        .padding(.bottom, 8)
                        .padding(.trailing, 20)
                    }
                    .scrollIndicators(.visible, axes: .vertical)
                    ThemeHairline(alpha: 0.55).padding(.top, 8)
                    HStack(spacing: 10) {
                        Spacer()
                        Button("取消") { onClose() }
                            .font(.system(size: 15, weight: .medium))
                            .foregroundStyle(Theme.onSurface)
                            .frame(minWidth: 88, minHeight: 42)
                            .overlay {
                                Capsule().stroke(Theme.outline.replacingOpacity(0.9), lineWidth: 1.5)
                            }
                        Button(saving ? "保存中" : "保存") {
                            Task { await save() }
                        }
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Theme.onPrimary)
                        .frame(minWidth: 88, minHeight: 42)
                        .background(Theme.isLight ? Theme.cyan : Theme.cyanSoft, in: Capsule())
                        .disabled(saving)
                    }
                    .padding(.top, 14)
                }
                .padding(22)
                .frame(maxWidth: .infinity, maxHeight: geo.size.height * 0.86, alignment: .top)
                .background(Theme.surfaceContainer.replacingOpacity(0.97), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .stroke(Theme.cyan.opacity(0.45), lineWidth: 1)
                }
                .padding(.horizontal, 20)
                .padding(.top, geo.safeAreaInsets.top + 36)
                .padding(.bottom, max(geo.safeAreaInsets.bottom, 16))
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task { await loadTips() }
    }

    private func posChip(_ pos: String) -> some View {
        let selected = pos == selectedPos
        return Button {
            selectedPos = pos
        } label: {
            Text(pos)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(selected ? Theme.onPrimary : Theme.onSurface)
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(selected ? Theme.cyan : Theme.surfaceHigh, in: Capsule())
                .overlay {
                    Capsule().stroke(selected ? Theme.cyan : Theme.outline.replacingOpacity(0.55), lineWidth: 1)
                }
        }
        .buttonStyle(.plain)
    }

    private func commitNote() {
        let meaning = noteText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !meaning.isEmpty else { return }
        notes.append(Definition(pos: selectedPos, meaning: meaning, isUserAdded: true))
        noteText = ""
    }

    private func loadTips() async {
        tips = (try? await model.api.fetchHomophones(token: model.session?.token, word: entry.text)) ?? []
    }

    private func toggleLike(_ tip: WordHomophone) async {
        guard let token = model.session?.token else {
            model.showLogin = true
            return
        }
        if let updated = try? await model.api.toggleHomophoneLike(token: token, id: tip.id),
           let index = tips.firstIndex(where: { $0.id == tip.id }) {
            tips[index] = updated
        }
    }

    /// Personal-notebook words can store private notes. Catalog and unsaved lookup words cannot.
    private var canSyncDefinitions: Bool {
        guard entry.id > 0 else { return false }
        if let notebook = model.notebooks.first(where: { $0.id == entry.notebookId }) {
            return !notebook.isSystem
        }
        return entry.notebookId == 0 && model.favoritedByText.values.contains(entry.id)
    }

    private func save() async {
        commitNote()
        saving = true
        defer { saving = false }
        let merged = originals + notes.map { Definition(pos: $0.pos, meaning: $0.meaning, isUserAdded: true) }
        let tip = homophoneText.trimmingCharacters(in: .whitespacesAndNewlines)
        if canSyncDefinitions {
            let ok = await model.updateDefinitions(id: entry.id, definitions: merged, silent: !tip.isEmpty || !notes.isEmpty)
            if !ok, tip.isEmpty, notes.isEmpty { return }
        }
        let userNotes = notes.map { Definition(pos: $0.pos, meaning: $0.meaning, isUserAdded: true) }
        if let token = model.session?.token {
            do {
                try await model.api.saveWordNotes(token: token, word: entry.text, definitions: userNotes)
            } catch {
                if !model.noteSessionError(error) {
                    model.banner = error.localizedDescription
                }
                if tip.isEmpty { return }
            }
        } else if !userNotes.isEmpty || !tip.isEmpty {
            model.showLogin = true
            return
        }
        if !tip.isEmpty {
            guard let token = model.session?.token else {
                model.showLogin = true
                return
            }
            do {
                _ = try await model.api.submitHomophone(token: token, word: entry.text, body: tip)
            } catch {
                if !model.noteSessionError(error) {
                    model.banner = error.localizedDescription
                }
                return
            }
        }
        var updated = entry
        updated.definitions = merged
        onSaved(updated)
        onClose()
    }
}

private struct MeaningPosWrap: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            rowHeight = max(rowHeight, size.height)
            x += size.width + spacing
        }
        return CGSize(width: width, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX, x + size.width > bounds.maxX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            rowHeight = max(rowHeight, size.height)
            x += size.width + spacing
        }
    }
}

/// Same canvas as Android `sdp`: 1 design point at a 440pt-wide window.
private enum CardMetrics {
    static var scale: CGFloat {
        let width = UIScreen.main.bounds.width
        return min(max(width / 440, 0.72), 1.6)
    }

    static func sdp(_ value: CGFloat) -> CGFloat {
        value * scale
    }
}

private enum NaturalPhonics {
    private static let vowels: Set<Character> = ["a", "e", "i", "o", "u"]

    static func syllables(_ raw: String) -> [String] {
        let letters = Array(raw.filter(\.isLetter))
        if letters.isEmpty { return [raw] }
        let lower = letters.map { Character(String($0).lowercased()) }
        let silent = lower.indices.map { isSilentE(lower, $0) }
        let nuclei = lower.indices.filter { isNucleus(lower, $0, silent) }
        let parts = split(lower, nuclei)
        var cursor = 0
        return parts.map { part in
            let slice = letters[cursor..<(cursor + part.count)]
            cursor += part.count
            return String(slice)
        }
    }

    private static func isVowel(_ word: [Character], _ index: Int) -> Bool {
        let ch = word[index]
        if vowels.contains(ch) { return true }
        if ch == "y", index > 0 { return true }
        return false
    }

    private static func isSilentE(_ word: [Character], _ index: Int) -> Bool {
        guard word[index] == "e", index >= 2 else { return false }
        if vowels.contains(word[index - 1]) || word[index - 1] == "y" { return false }
        if !vowels.contains(word[index - 2]) && word[index - 2] != "y" { return false }
        if index + 1 < word.count, vowels.contains(word[index + 1]) { return false }
        return true
    }

    private static func isNucleus(_ word: [Character], _ index: Int, _ silent: [Bool]) -> Bool {
        if silent[index] { return false }
        if !isVowel(word, index) { return false }
        if index > 0, isVowel(word, index - 1), !silent[index - 1] { return false }
        return true
    }

    private static func split(_ word: [Character], _ nuclei: [Int]) -> [String] {
        if nuclei.isEmpty { return [String(word)] }
        var starts = Array(repeating: 0, count: nuclei.count)
        for slot in 1..<nuclei.count {
            let previous = nuclei[slot - 1]
            let current = nuclei[slot]
            let lastBetween = current - 1
            starts[slot] = lastBetween > previous ? lastBetween : current
        }
        return nuclei.indices.compactMap { slot in
            let start = starts[slot]
            let end = slot + 1 < nuclei.count ? starts[slot + 1] : word.count
            guard start < end else { return nil }
            return String(word[start..<end])
        }
    }
}
