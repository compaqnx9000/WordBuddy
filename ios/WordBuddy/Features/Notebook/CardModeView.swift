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
    @State private var playing = false
    @State private var playTask: Task<Void, Never>?
    @State private var showSeek = false
    @State private var seeking = false
    @State private var seekValue = 0.0
    /// Index the thumb was released on. Kept until the card actually lands there.
    @State private var pendingSeek: Int?
    @State private var liveSeekTarget: Int?
    @State private var liveSeekTask: Task<Void, Never>?
    @State private var editingEntry: VocabEntry?
    @State private var imageEntry: VocabEntry?
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
            } else if shuffled, let entry = shuffledDeck.first(where: { $0.id == currentId }) ?? shuffledDeck.first {
                card(entry)
            } else {
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
            }
            controlBar
        }
        .stellarScreenBackground()
        .onAppear {
            let index = min(max(0, startIndex), max(0, entries.count - 1))
            currentId = entries.isEmpty ? nil : entries[index].id
            syncSeekValue()
        }
        .onChange(of: currentId) { _, id in
            syncSeekValue()
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
        .sheet(item: $editingEntry) { entry in
            MeaningEditSheet(entry: entry)
                .environmentObject(model)
        }
        .confirmationDialog("助记配图", isPresented: imageDialogPresented, titleVisibility: .visible) {
            Button("生成助记配图（\(model.aiImagePointsCost) 积分）") {
                guard let entry = imageEntry else { return }
                Task { await generateImage(for: entry) }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text(imageEntry?.text ?? "")
        }
        .overlay {
            if let previewWord {
                RelatedWordPreview(word: previewWord) {
                    self.previewWord = nil
                }
            }
        }
    }

    private var imageDialogPresented: Binding<Bool> {
        Binding(get: { imageEntry != nil }, set: { if !$0 { imageEntry = nil } })
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
                            let parts = NaturalPhonics.syllables(entry.text)
                            Task { await speakSyllables(parts) }
                        },
                        onImage: { imageEntry = entry }
                    )
                    definitionCard(entry)
                    relatedBlock(entry)
                }
                .modifier(ShuffleCardDrag(enabled: shuffled) { delta in
                    Task { await step(delta) }
                })
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
        VStack(alignment: .leading, spacing: 0) {
            if entry.definitions.isEmpty {
                Text("暂无释义")
                    .font(.body)
                    .foregroundStyle(Theme.onSurfaceVariant)
            } else {
                let originals = entry.definitions.filter { !$0.isUserAdded }
                let notes = entry.definitions.filter(\.isUserAdded)
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(Array(originals.enumerated()), id: \.offset) { _, definition in
                        DefinitionLine(definition: definition)
                    }
                }
                if !notes.isEmpty {
                    if !originals.isEmpty {
                        divider.padding(.vertical, 14)
                        Text("我的补充")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Theme.onSurfaceVariant)
                            .padding(.bottom, 10)
                    }
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(Array(notes.enumerated()), id: \.offset) { _, definition in
                            DefinitionLine(definition: definition)
                        }
                    }
                }
            }
            divider.padding(.top, 14)
            Button {
                editingEntry = entry
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

    private var divider: some View {
        Rectangle()
            .fill(Theme.outline.opacity(0.35))
            .frame(height: 1)
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
        guard let currentId, let local = deck.firstIndex(where: { $0.id == currentId }) else { return 0 }
        if shuffled { return local }
        return min(model.listWindowStart + local, max(0, pageTotal - 1))
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

    /// Card position without the slider hold, so a late window update cannot pull the thumb back to 0.
    private var absoluteDisplayIndex: Int {
        guard let currentId, let local = deck.firstIndex(where: { $0.id == currentId }) else { return 0 }
        if shuffled { return local }
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
            self.currentId = deck[next].id
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
            self.currentId = model.words.last?.id
            return
        }
        if delta > 0 { stopPlay() }
    }

    private func clampedSeekIndex(_ value: Double) -> Int {
        min(max(0, Int(value.rounded())), max(0, pageTotal - 1))
    }

    /// Follow the thumb while it moves. Nearby words update immediately. A far jump is throttled so a fast drag does not request every index.
    private func scheduleLiveSeek(_ target: Int) {
        if shuffled {
            stopLiveSeek()
            guard deck.indices.contains(target) else { return }
            let id = deck[target].id
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
            guard deck.indices.contains(absolute) else { return }
            currentId = deck[absolute].id
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
        if let currentId, let pos = stubs.firstIndex(where: { $0.id == currentId }) {
            pendingSeek = pos
            seekValue = Double(pos)
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

    private func speakSyllables(_ parts: [String]) async {
        for part in parts {
            Speech.speak(part, accent: model.accent)
            try? await Task.sleep(nanoseconds: 420_000_000)
        }
    }

    private func generateImage(for entry: VocabEntry) async {
        imageBusyId = entry.id
        defer { imageBusyId = nil }
        _ = await model.generateMnemonicImage(
            word: entry.text,
            meaningHint: entry.definitions.first?.meaning ?? ""
        )
    }
}

private struct ShuffleCardDrag: ViewModifier {
    var enabled: Bool
    var onStep: (Int) -> Void

    @ViewBuilder
    func body(content: Content) -> some View {
        if enabled {
            content.simultaneousGesture(
                DragGesture(minimumDistance: 28).onEnded { value in
                    let dx = value.translation.width
                    let dy = value.translation.height
                    guard abs(dx) > abs(dy) * 1.4, abs(dx) > 48 else { return }
                    onStep(dx < 0 ? 1 : -1)
                }
            )
        } else {
            content
        }
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
        let slot = size * 1.2 + 16
        return ZStack(alignment: .leading) {
            Text(entry.text)
                .font(.system(size: size, weight: .heavy))
                .foregroundStyle(Theme.cyanSoft)
                .shadow(color: Theme.cyan.opacity(0.55), radius: 8)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .opacity(phonicsOn ? 0 : 1)
            Text(phonicsAttributed)
                .font(.system(size: size, weight: .heavy))
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .opacity(phonicsOn ? 1 : 0)
        }
        .padding(.horizontal, 10)
        .frame(height: slot, alignment: Alignment(horizontal: .leading, vertical: .center))
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

private struct CardExamplePanel: View {
    var word: String
    var examples: [ExampleSentence]
    var accent: Accent
    @State private var page = 0
    @State private var dragX: CGFloat = 0
    @State private var pageWidth: CGFloat = 1
    @State private var settling = false
    @State private var dragAnimated = false

    var body: some View {
        let example = examples[safePage]
        VStack(spacing: CardMetrics.sdp(16)) {
            VStack(spacing: CardMetrics.sdp(16)) {
                Text("例句")
                    .font(.system(size: CardMetrics.sdp(11), weight: .bold))
                    .tracking(1.2)
                    .foregroundStyle(Theme.onSurfaceVariant)
                sentencePager
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
            }
            .frame(maxWidth: .infinity)
            .overlay {
                ExampleSwipeCatcher(
                    enabled: examples.count > 1 && !settling,
                    onMove: { translation in
                        guard !settling else { return }
                        dragAnimated = false
                        var transaction = Transaction()
                        transaction.disablesAnimations = true
                        withTransaction(transaction) {
                            dragX = resisted(translation)
                        }
                    },
                    onFinish: { translation, velocity in
                        settle(translation: translation, velocity: velocity)
                    }
                )
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

    private var sentencePager: some View {
        sentenceBlock(examples[safePage])
            .opacity(0)
            .overlay {
                GeometryReader { geo in
                    let width = max(geo.size.width, 1)
                    let hasPrevious = safePage > 0
                    HStack(alignment: .top, spacing: 0) {
                        if hasPrevious {
                            sentenceBlock(examples[safePage - 1]).frame(width: width)
                        }
                        sentenceBlock(examples[safePage]).frame(width: width)
                        if safePage + 1 < examples.count {
                            sentenceBlock(examples[safePage + 1]).frame(width: width)
                        }
                    }
                    .offset(x: (hasPrevious ? -width : 0) + dragX)
                    .animation(dragAnimated ? .easeOut(duration: 0.32) : nil, value: dragX)
                }
            }
            .clipped()
            .background {
                GeometryReader { geo in
                    Color.clear.preference(key: ExampleWidthKey.self, value: geo.size.width)
                }
            }
            .onPreferenceChange(ExampleWidthKey.self) { pageWidth = $0 }
    }

    private func sentenceBlock(_ example: ExampleSentence) -> some View {
        VStack(spacing: CardMetrics.sdp(10)) {
            Text(highlighted(example.english))
                .font(.system(size: CardMetrics.sdp(22)))
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
            Text(example.chinese)
                .font(.system(size: CardMetrics.sdp(16)))
                .foregroundStyle(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
    }

    private func resisted(_ translation: CGFloat) -> CGFloat {
        let atStart = safePage == 0 && translation > 0
        let atEnd = safePage >= examples.count - 1 && translation < 0
        if atStart || atEnd { return translation * 0.28 }
        return translation
    }

    private func settle(translation: CGFloat, velocity: CGFloat) {
        guard !settling else { return }
        let width = max(pageWidth, 1)
        let forward = safePage + 1 < examples.count && (translation < -width * 0.22 || velocity < -700)
        let backward = safePage > 0 && (translation > width * 0.22 || velocity > 700)
        if forward {
            finishSettle(to: -width) { page += 1 }
        } else if backward {
            finishSettle(to: width) { page -= 1 }
        } else {
            finishSettle(to: 0, commit: {})
        }
    }

    /// Continues from the finger's release point until the outgoing sentence is fully offscreen.
    private func finishSettle(to target: CGFloat, commit: @escaping () -> Void) {
        settling = true
        dragAnimated = true
        DispatchQueue.main.async {
            withAnimation(.easeOut(duration: 0.32)) {
                dragX = target
            }
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 340_000_000)
                dragAnimated = false
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) {
                    commit()
                    dragX = 0
                    settling = false
                }
            }
        }
    }

    private var safePage: Int {
        min(max(0, page), max(0, examples.count - 1))
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

/// Transparent hit target over the example card. Horizontal drags change the example
/// and are required to fail before the word pager's scroll view can move.
private struct ExampleWidthKey: PreferenceKey {
    static var defaultValue: CGFloat = 1
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

private struct ExampleSwipeCatcher: UIViewRepresentable {
    var enabled: Bool
    var onMove: (CGFloat) -> Void
    var onFinish: (CGFloat, CGFloat) -> Void

    func makeUIView(context: Context) -> ExampleSwipeSurface {
        let view = ExampleSwipeSurface()
        view.onMove = onMove
        view.onFinish = onFinish
        view.swipeEnabled = enabled
        return view
    }

    func updateUIView(_ uiView: ExampleSwipeSurface, context: Context) {
        uiView.onMove = onMove
        uiView.onFinish = onFinish
        uiView.swipeEnabled = enabled
    }
}

private final class ExampleSwipeSurface: UIView, UIGestureRecognizerDelegate {
    var onMove: ((CGFloat) -> Void)?
    var onFinish: ((CGFloat, CGFloat) -> Void)?
    var swipeEnabled = true
    private let pan = UIPanGestureRecognizer()
    private var locked: [UIScrollView] = []
    private var axisDecided = false
    private var horizontal = false
    private var linkedScrolls = Set<ObjectIdentifier>()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isUserInteractionEnabled = true
        pan.addTarget(self, action: #selector(handlePan(_:)))
        pan.delegate = self
        pan.cancelsTouchesInView = false
        addGestureRecognizer(pan)
    }

    required init?(coder: NSCoder) {
        nil
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        linkAncestorScrollViews()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        linkAncestorScrollViews()
    }

    override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        swipeEnabled
    }

    @objc private func handlePan(_ pan: UIPanGestureRecognizer) {
        let translation = pan.translation(in: self)
        switch pan.state {
        case .began:
            axisDecided = false
            horizontal = false
        case .changed:
            if !axisDecided {
                if abs(translation.x) < 10, abs(translation.y) < 10 { return }
                axisDecided = true
                horizontal = abs(translation.x) > abs(translation.y)
                if horizontal {
                    lockAncestorScrollViews()
                } else {
                    pan.isEnabled = false
                    pan.isEnabled = true
                    return
                }
            }
            if horizontal {
                onMove?(translation.x)
            }
        case .ended:
            if horizontal {
                onFinish?(translation.x, pan.velocity(in: self).x)
            }
            unlockAncestorScrollViews()
        case .cancelled, .failed:
            if horizontal {
                onFinish?(0, 0)
            }
            unlockAncestorScrollViews()
        default:
            break
        }
    }

    /// Outer word pager must wait until this pan fails, so a sideways drag stays on the example.
    private func linkAncestorScrollViews() {
        var current: UIView? = superview
        while let view = current {
            if let scroll = view as? UIScrollView {
                let key = ObjectIdentifier(scroll)
                if !linkedScrolls.contains(key) {
                    scroll.panGestureRecognizer.require(toFail: pan)
                    linkedScrolls.insert(key)
                }
            }
            current = view.superview
        }
    }

    private func lockAncestorScrollViews() {
        var current: UIView? = superview
        while let view = current {
            if let scroll = view as? UIScrollView, scroll.isScrollEnabled {
                scroll.isScrollEnabled = false
                locked.append(scroll)
            }
            current = view.superview
        }
    }

    private func unlockAncestorScrollViews() {
        for scroll in locked {
            scroll.isScrollEnabled = true
        }
        locked.removeAll()
    }
}

struct MeaningEditSheet: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    var entry: VocabEntry
    @State private var rows: [Definition]
    @State private var saving = false

    init(entry: VocabEntry) {
        self.entry = entry
        _rows = State(initialValue: entry.definitions.isEmpty
            ? [Definition(pos: "", meaning: "", isUserAdded: true)]
            : entry.definitions)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    ForEach(rows.indices, id: \.self) { index in
                        HStack(spacing: 8) {
                            TextField("词性", text: $rows[index].pos)
                                .frame(width: 64)
                            TextField("释义", text: $rows[index].meaning)
                            if rows[index].isUserAdded {
                                Button {
                                    rows.remove(at: index)
                                } label: {
                                    Image(systemName: "minus.circle")
                                        .foregroundStyle(Theme.pink)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(12)
                        .background(Theme.surfaceHigh, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    Button {
                        rows.append(Definition(pos: "", meaning: "", isUserAdded: true))
                    } label: {
                        Text("添加一条")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Theme.cyan)
                    }
                    .buttonStyle(.plain)
                }
                .padding(20)
            }
            .stellarScreenBackground()
            .navigationTitle("编辑释义")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(saving ? "保存中" : "保存") {
                        Task { await save() }
                    }
                    .disabled(saving)
                }
            }
        }
    }

    private func save() async {
        saving = true
        defer { saving = false }
        let ok = await model.updateDefinitions(id: entry.id, definitions: rows)
        if ok { dismiss() }
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
