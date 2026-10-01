import SwiftUI
import UIKit

struct LookupView: View {
    @EnvironmentObject private var model: AppModel
    @State private var query = ""
    @State private var entry: VocabEntry?
    @State private var lookingUp = false
    @State private var starring = false
    @State private var errorMessage: String?
    @State private var relatedTab = 0
    @State private var previewWord: String?
    @State private var editingEntry: VocabEntry?
    @State private var mnemonicImage: UIImage?
    @State private var imageBusy = false
    @State private var tips: [WordHomophone] = []

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Text("词搭子")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(Theme.cyanSoft)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)

                ScrollView {
                    VStack(alignment: .leading, spacing: 22) {
                        searchBox

                        if lookingUp {
                            ProgressView()
                                .tint(Theme.cyan)
                                .frame(maxWidth: .infinity)
                                .padding(.top, 24)
                        } else if let errorMessage, entry == nil {
                            Text(errorMessage)
                                .font(.system(size: 15))
                                .foregroundStyle(Theme.onSurfaceVariant)
                        } else if let entry {
                            result(entry)
                        } else {
                            Text("搜索单词，查看音标、释义和例句。")
                                .font(.system(size: 15))
                                .foregroundStyle(Theme.onSurfaceVariant)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 24)
                }
                .scrollDismissesKeyboard(.interactively)
            }
            .stellarScreenBackground()
            .navigationBarHidden(true)
            .onAppear { consumePendingLookup() }
            .onChange(of: model.pendingLookup) { _, _ in consumePendingLookup() }
            .overlay {
                if let item = editingEntry {
                    MeaningEditSheet(entry: item, onClose: { editingEntry = nil }) { updated in
                        if var current = entry {
                            current.definitions = updated.definitions
                            entry = current
                        }
                        Task { await loadTips(item.text) }
                    }
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
    }

    private var searchBox: some View {
        HStack(spacing: 10) {
            Button {
                Task { await search() }
            } label: {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 18))
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            .buttonStyle(.plain)
            TextField("搜索单词…", text: $query)
                .font(.system(size: 16))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .onSubmit { Task { await search() } }
                .foregroundStyle(Theme.onSurface)
            if !query.isEmpty {
                Button {
                    query = ""
                    entry = nil
                    errorMessage = nil
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .glassPanel(neon: true)
    }

    private func result(_ entry: VocabEntry) -> some View {
        let saved = model.isFavorited(entry.text)
        let tabs = relatedTabs(entry)
        return VStack(alignment: .leading, spacing: 22) {
            wordHeader(entry, saved: saved)
            definitionCard(entry)
            if !tabs.isEmpty {
                relatedBlock(tabs)
            }
            if let example = entry.examples.first {
                exampleCard(word: entry.text, example: example)
            }
            if let mnemonicImage {
                Image(uiImage: mnemonicImage)
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: .infinity)
                    .frame(height: 160)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            aiButton
            if let errorMessage {
                Text(errorMessage)
                    .font(.footnote)
                    .foregroundStyle(Theme.pink)
            }
        }
        .onAppear { reloadMnemonic(entry.text) }
        .onChange(of: model.mnemonicRevision) { _, _ in reloadMnemonic(entry.text) }
        .task(id: entry.text.lowercased()) { await loadTips(entry.text) }
    }

    private func wordHeader(_ entry: VocabEntry, saved: Bool) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 8) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(entry.text)
                        .font(.system(size: 40, weight: .heavy))
                        .foregroundStyle(Theme.cyanSoft)
                        .shadow(color: Theme.cyan.opacity(0.55), radius: 12)
                    HStack(spacing: 16) {
                        if let uk = entry.ipaUk, !uk.isEmpty {
                            Text("UK [\(uk)]")
                                .font(.system(size: 14))
                                .foregroundStyle(Theme.onSurfaceVariant)
                        }
                        if let us = entry.ipaUs, !us.isEmpty {
                            Text("US [\(us)]")
                                .font(.system(size: 14))
                                .foregroundStyle(Theme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(minLength: 8)
                Button {
                    Task { await toggleStar(entry) }
                } label: {
                    Image(systemName: saved ? "star.fill" : "star")
                        .font(.system(size: 26))
                        .foregroundStyle(Theme.gold)
                }
                .buttonStyle(.plain)
                .disabled(starring)
                .accessibilityLabel(saved ? "移出生词本" : "加入生词本")
            }
            HStack(spacing: 12) {
                Button {
                    Speech.speak(entry.text, accent: model.accent)
                } label: {
                    Image(systemName: "speaker.wave.2.fill")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.onPrimary)
                        .frame(width: 40, height: 40)
                        .background(Theme.cyanBright, in: Circle())
                }
                .buttonStyle(.plain)
                HStack(spacing: 0) {
                    accentChip("US", selected: model.accent == .us) { model.accent = .us }
                    accentChip("UK", selected: model.accent == .uk) { model.accent = .uk }
                }
                .padding(3)
                .background(Theme.surfaceHigh, in: Capsule())
                .overlay(Capsule().stroke(Theme.outline.opacity(0.4), lineWidth: 1))
            }
        }
    }

    private func accentChip(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 11, weight: .bold))
                .tracking(0.6)
                .foregroundStyle(selected ? Theme.onPrimary : Theme.onSurfaceVariant)
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .background(selected ? Theme.cyan : Color.clear, in: Capsule())
        }
        .buttonStyle(.plain)
    }

    private func definitionCard(_ entry: VocabEntry) -> some View {
        let originals = entry.definitions.filter { !$0.isUserAdded }
        let notes = entry.definitions.filter(\.isUserAdded)
        return VStack(alignment: .leading, spacing: 0) {
            if entry.definitions.isEmpty {
                Text("暂无释义")
                    .font(.system(size: 16))
                    .foregroundStyle(Theme.onSurfaceVariant)
            } else {
                ForEach(Array(originals.enumerated()), id: \.offset) { index, definition in
                    if index > 0 { Spacer().frame(height: 12) }
                    DefinitionLine(definition: definition)
                }
                if !notes.isEmpty {
                    ThemeHairline().padding(.vertical, 12)
                    Text("我的补充")
                        .font(.system(size: 11, weight: .bold))
                        .tracking(0.8)
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .padding(.bottom, 10)
                    ForEach(Array(notes.enumerated()), id: \.offset) { index, definition in
                        if index > 0 { Spacer().frame(height: 12) }
                        DefinitionLine(definition: definition)
                    }
                }
            }
            if !tips.isEmpty {
                ThemeHairline(color: Theme.gold).padding(.vertical, 12)
                Text("谐音助记")
                    .font(.system(size: 11, weight: .bold))
                    .tracking(0.8)
                    .foregroundStyle(Theme.gold)
                    .padding(.bottom, 8)
                ForEach(tips.prefix(3)) { tip in
                    Text(tip.body)
                        .font(.subheadline)
                        .foregroundStyle(Theme.onSurface)
                        .padding(.bottom, 6)
                }
            }
            ThemeHairline().padding(.top, 14)
            HStack {
                Spacer()
                Button {
                    openMeaningEditor(entry)
                } label: {
                    Label("编辑释义", systemImage: "pencil")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Theme.cyan)
                }
                .buttonStyle(.plain)
                .padding(.top, 12)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private func relatedTabs(_ entry: VocabEntry) -> [(String, [String])] {
        var tabs: [(String, [String])] = []
        if !entry.synonyms.isEmpty { tabs.append(("近义词", entry.synonyms)) }
        if !entry.nearWords.isEmpty { tabs.append(("形近词", entry.nearWords)) }
        if !entry.antonyms.isEmpty { tabs.append(("反义词", entry.antonyms)) }
        return tabs
    }

    private func relatedBlock(_ tabs: [(String, [String])]) -> some View {
        let index = min(relatedTab, tabs.count - 1)
        let words = tabs[index].1
        let rows = stride(from: 0, to: words.count, by: 2).map { start in
            Array(words[start..<min(start + 2, words.count)])
        }
        return VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                ForEach(Array(tabs.enumerated()), id: \.offset) { offset, tab in
                    let selected = offset == index
                    Button {
                        relatedTab = offset
                    } label: {
                        Text(tab.0)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(selected ? Theme.onPrimary : Theme.onSurfaceVariant)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 7)
                            .background(selected ? Theme.cyan : Color.clear, in: Capsule())
                            .overlay(
                                Capsule().stroke(selected ? Color.clear : Theme.outline, lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
            VStack(spacing: 10) {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    HStack(spacing: 10) {
                        ForEach(row, id: \.self) { word in
                            Button {
                                previewWord = word
                            } label: {
                                Text(word)
                                    .font(.system(size: 16, weight: .medium))
                                    .foregroundStyle(Theme.onSurface)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 12)
                                    .background(Theme.surfaceHigh.opacity(0.95), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                                            .stroke(Theme.outline.opacity(0.55), lineWidth: 1)
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                        if row.count == 1 {
                            Color.clear.frame(maxWidth: .infinity)
                        }
                    }
                }
            }
        }
    }

    private func exampleCard(word: String, example: ExampleSentence) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("例句")
                .font(.system(size: 11, weight: .bold))
                .tracking(1)
                .foregroundStyle(Theme.onSurfaceVariant)
            VStack(alignment: .leading, spacing: 10) {
                highlightedSentence(example.english, word: word)
                    .font(.system(size: 16))
                    .lineSpacing(4)
                Text(example.chinese)
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.85))
                    .lineSpacing(3)
                Button {
                    Speech.speak(example.english, accent: model.accent)
                } label: {
                    Label("朗读", systemImage: "play.circle.fill")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Theme.cyan)
                }
                .buttonStyle(.plain)
                .padding(.top, 4)
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glassPanel()
        }
    }

    private func highlightedSentence(_ sentence: String, word: String) -> Text {
        let needle = word.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !needle.isEmpty, let range = sentence.range(of: needle, options: .caseInsensitive) else {
            return Text(sentence).foregroundStyle(Theme.onSurface)
        }
        let before = String(sentence[..<range.lowerBound])
        let hit = String(sentence[range])
        let after = String(sentence[range.upperBound...])
        return Text(before).foregroundStyle(Theme.onSurface)
            + Text(hit).foregroundStyle(Theme.headword).bold()
            + Text(after).foregroundStyle(Theme.onSurface)
    }

    private var aiButton: some View {
        Button {
            Task { await generateImage() }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "sparkles")
                    .foregroundStyle(Theme.pink)
                Text(imageBusy ? "生成中…" : "AI 助记配图")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Theme.onSurface)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(Theme.glass, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.55), style: StrokeStyle(lineWidth: 1.5, dash: [7, 5]))
            )
        }
        .buttonStyle(.plain)
        .disabled(imageBusy || entry == nil)
    }

    private func consumePendingLookup() {
        guard let word = model.pendingLookup else { return }
        model.pendingLookup = nil
        query = word
        Task { await search() }
    }

    private func search() async {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        lookingUp = true
        errorMessage = nil
        relatedTab = 0
        defer { lookingUp = false }
        do {
            entry = try await model.lookup(text)
            if let entry { reloadMnemonic(entry.text) }
        } catch {
            entry = nil
            errorMessage = error.localizedDescription
        }
    }

    private func toggleStar(_ entry: VocabEntry) async {
        starring = true
        defer { starring = false }
        if let saved = await model.toggleCatalogFavorite(entry), saved {
            let key = entry.text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            if let id = model.favoritedByText[key], var current = self.entry {
                current.id = id
                self.entry = current
            }
        }
    }

    private func openMeaningEditor(_ entry: VocabEntry) {
        var copy = entry
        let key = entry.text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if let id = model.favoritedByText[key], id > 0 {
            copy.id = id
        }
        editingEntry = copy
    }

    private func reloadMnemonic(_ word: String) {
        if let data = MnemonicImageStore.load(word: word) {
            mnemonicImage = UIImage(data: data)
        } else {
            mnemonicImage = nil
        }
    }

    private func generateImage() async {
        guard let entry else { return }
        guard model.session != nil else {
            model.showLogin = true
            return
        }
        imageBusy = true
        defer { imageBusy = false }
        let hint = entry.definitions.first?.label ?? ""
        _ = await model.generateMnemonicImage(word: entry.text, meaningHint: hint)
        reloadMnemonic(entry.text)
    }

    private func loadTips(_ word: String) async {
        tips = (try? await model.api.fetchHomophones(token: model.session?.token, word: word)) ?? []
    }
}
