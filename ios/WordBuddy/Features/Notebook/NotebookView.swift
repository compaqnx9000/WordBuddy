import SwiftUI
import UIKit

struct NotebookView: View {
    var onBack: () -> Void = {}

    @EnvironmentObject private var model: AppModel
    @State private var filter: WordFilter = .all
    @State private var sortMode: SortMode = .manual
    @State private var revealed: Set<Int64> = []
    @State private var showCreate = false
    @State private var newName = ""
    @State private var pendingDelete: Notebook?
    @State private var chipLongPressed = false
    @State private var showCards = false
    @State private var cardStart = 0
    @State private var showMore = false
    @State private var showStats = false
    @State private var selectionMode = false
    @State private var selectedIds: Set<Int64> = []
    @State private var showDeleteSelected = false
    @State private var showMove = false
    @State private var showFavoriteTo = false
    @State private var pendingFavorite: VocabEntry?
    @State private var scrollTarget: Int64?
    @State private var openSwipeId: Int64?
    /// Last word the user spoke, revealed, or swiped in the list.
    @State private var lastTouchedId: Int64?
    @State private var alphabetSeekTask: Task<Void, Never>?

    /// Same fixed row height for every notebook (user + catalog), matching Android 3-line meaning block.
    private static let wordRowHeight: CGFloat = 88
    private static let chipHeight: CGFloat = 36

    private var visibleWords: [VocabEntry] {
        var list = model.words
        switch filter {
        case .all: break
        case .words: list = list.filter { !$0.isPhrase }
        case .phrases: list = list.filter(\.isPhrase)
        }
        switch sortMode {
        case .manual:
            list.sort {
                if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                return $0.id < $1.id
            }
        case .timeDesc:
            list.sort { $0.addedAtMillis > $1.addedAtMillis }
        case .timeAsc:
            list.sort { $0.addedAtMillis < $1.addedAtMillis }
        case .alpha:
            list.sort { $0.text.localizedCaseInsensitiveCompare($1.text) == .orderedAscending }
        }
        return list
    }

    private var orderedNotebooks: [Notebook] {
        Self.orderNotebookChips(model.notebooks)
    }

    private var headerTitle: String {
        model.activeNotebook?.name.nilIfEmpty ?? "生词本"
    }

    var body: some View {
        NavigationStack {
            wordList
            .stellarScreenBackground()
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(.hidden, for: .navigationBar)
            .toolbar {
                if selectionMode && model.session != nil {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("取消") {
                            selectionMode = false
                            selectedIds = []
                        }
                        .foregroundStyle(Theme.onSurfaceVariant)
                    }
                    ToolbarItem(placement: .principal) {
                        Text("已选 \(selectedIds.count)")
                            .font(.headline.weight(.bold))
                            .foregroundStyle(Theme.cyanSoft)
                    }
                } else {
                    ToolbarItem(placement: .topBarLeading) {
                        backButton
                    }
                    ToolbarItem(placement: .principal) {
                        VStack(spacing: 1) {
                            Text(headerTitle)
                                .font(.headline.weight(.bold))
                                .foregroundStyle(Theme.cyanSoft)
                                .lineLimit(1)
                            Text("列表模式")
                                .font(.caption2)
                                .foregroundStyle(Theme.onSurfaceVariant)
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            showMore = true
                        } label: {
                            Text("···")
                                .font(.title3.weight(.bold))
                                .foregroundStyle(Theme.cyan)
                        }
                    }
                }
            }
        }
        .alert("新建生词本", isPresented: $showCreate) {
            TextField("名称", text: $newName)
            Button("创建") {
                let name = newName
                newName = ""
                Task { await model.createNotebook(name: name) }
            }
            Button("取消", role: .cancel) { newName = "" }
        } message: {
            Text("最多 20 个字")
        }
        .alert("统计", isPresented: $showStats) {
            Button("确定", role: .cancel) {}
        } message: {
            Text("「\(headerTitle)」共 \(model.wordTotal > 0 ? model.wordTotal : visibleWords.count) 个单词")
        }
        .alert("删除生词本", isPresented: deletePresented) {
            Button("取消", role: .cancel) { pendingDelete = nil }
            Button("删除", role: .destructive) {
                guard let id = pendingDelete?.id else { return }
                pendingDelete = nil
                Task { await model.deleteNotebook(id) }
            }
        } message: {
            Text(deleteNotebookMessage)
        }
        .confirmationDialog("更多", isPresented: $showMore, titleVisibility: .hidden) {
            Button("编辑") {
                guard model.session != nil else {
                    model.showLogin = true
                    return
                }
                let canEdit = model.activeNotebook?.isSystem != true && !visibleWords.isEmpty
                if canEdit {
                    selectionMode = true
                    selectedIds = []
                    openSwipeId = nil
                } else {
                    model.banner = model.activeNotebook?.isSystem == true
                        ? "系统词书不可编辑"
                        : "当前词本没有单词"
                }
            }
            Button("统计") { showStats = true }
            Button("取消", role: .cancel) {}
        }
        .confirmationDialog(
            "删除选中的 \(selectedIds.count) 个单词？",
            isPresented: $showDeleteSelected,
            titleVisibility: .visible
        ) {
            Button("删除", role: .destructive) {
                let ids = selectedIds
                selectedIds = []
                selectionMode = false
                Task {
                    for id in ids {
                        await model.deleteWord(id)
                    }
                }
            }
            Button("取消", role: .cancel) {}
        }
        .overlay {
            if showMove {
                NotebookTargetPickerDialog(
                    title: "移动到生词本",
                    subtitle: moveTargets.isEmpty ? "没有其他生词本" : "已选择 \(selectedIds.count) 个词条",
                    notebooks: moveTargets,
                    onSelect: { notebook in
                        let ids = selectedIds
                        selectedIds = []
                        selectionMode = false
                        showMove = false
                        Task { await model.moveWords(ids: ids, to: notebook.id) }
                    },
                    onDismiss: { showMove = false }
                )
            } else if showFavoriteTo {
                NotebookTargetPickerDialog(
                    title: "收藏到生词本",
                    subtitle: favoriteTargets.isEmpty ? "还没有生词本" : "已选择 1 个词条",
                    notebooks: favoriteTargets,
                    onSelect: { notebook in
                        let entry = pendingFavorite
                        pendingFavorite = nil
                        showFavoriteTo = false
                        guard let entry else { return }
                        Task { _ = await model.favoriteCatalogWord(entry, to: notebook.id) }
                    },
                    onDismiss: {
                        showFavoriteTo = false
                        pendingFavorite = nil
                    }
                )
            }
        }
        .fullScreenCover(isPresented: $showCards) {
            CardModeView(entries: visibleWords, startIndex: cardStart) {
                showCards = false
            }
            .environmentObject(model)
        }
        .task(id: model.session?.userId) {
            await model.loadNotebooks()
        }
    }

    private var backButton: some View {
        Button(action: onBack) {
            Image(systemName: "chevron.left")
                .font(.body.weight(.semibold))
                .foregroundStyle(Theme.onSurfaceVariant)
        }
    }

    private var deletePresented: Binding<Bool> {
        Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } })
    }

    private var deleteNotebookMessage: String {
        guard let notebook = pendingDelete else { return "" }
        let count = notebook.id == model.activeNotebookId
            ? max(notebook.wordCount, model.wordTotal)
            : notebook.wordCount
        if count > 0 {
            return "确定删除「\(notebook.name)」吗？其中的 \(count) 个词条将一并删除，此操作不可撤销。"
        }
        return "确定删除「\(notebook.name)」吗？此操作不可撤销。"
    }

    private var wordList: some View {
        VStack(spacing: 0) {
            notebookChips
            content
            bottomBar
        }
    }

    private var notebookChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                Button {
                    guard model.session != nil else {
                        model.showLogin = true
                        return
                    }
                    newName = ""
                    showCreate = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "plus")
                            .font(.caption.weight(.bold))
                        Text("新建")
                            .font(.subheadline.weight(.medium))
                    }
                    .foregroundStyle(Theme.cyan)
                    .padding(.horizontal, 12)
                    .frame(height: Self.chipHeight)
                    .overlay(Capsule().stroke(Theme.outline.opacity(0.45), lineWidth: 1))
                }
                .buttonStyle(.plain)

                ForEach(orderedNotebooks) { notebook in
                    notebookChip(notebook)
                }
            }
            .frame(height: Self.chipHeight)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
        }
    }

    private func notebookChip(_ notebook: Notebook) -> some View {
        let selected = model.activeNotebookId == notebook.id
        let canDelete = !notebook.isLocked
        let style = selected
            ? CatalogChipStyle(background: Theme.cyan, foreground: Theme.onPrimary, dashed: nil)
            : (Self.catalogChipStyle(notebook) ?? CatalogChipStyle(
                background: Theme.surfaceHigh,
                foreground: Theme.onSurfaceVariant,
                dashed: nil
            ))
        return Button {
            if chipLongPressed {
                chipLongPressed = false
                return
            }
            Task { await model.selectNotebook(notebook.id) }
        } label: {
            HStack(spacing: 4) {
                Text(notebook.name)
                    .font(.subheadline.weight(selected ? .bold : .medium))
                    .lineLimit(1)
                if notebook.isLocked {
                    Image(systemName: "lock.fill")
                        .font(.system(size: 10, weight: .semibold))
                }
            }
            .foregroundStyle(style.foreground)
            .padding(.horizontal, 12)
            .frame(height: Self.chipHeight)
            .background(style.background, in: Capsule())
            .clipShape(Capsule())
            .overlay {
                if let dashed = style.dashed, !selected {
                    Capsule()
                        .strokeBorder(
                            dashed,
                            style: StrokeStyle(lineWidth: 1, dash: [5, 4])
                        )
                } else if !selected && style.dashed == nil {
                    Capsule().stroke(Theme.outline.opacity(0.35), lineWidth: 1)
                }
            }
        }
        .buttonStyle(.plain)
        .frame(height: Self.chipHeight)
        .simultaneousGesture(
            LongPressGesture(minimumDuration: 0.45).onEnded { _ in
                guard canDelete else { return }
                chipLongPressed = true
                guard model.session != nil else {
                    model.showLogin = true
                    return
                }
                pendingDelete = notebook
            }
        )
    }

    @ViewBuilder
    private var content: some View {
        if model.wordsLoading && model.words.isEmpty {
            Spacer()
            ProgressView("加载中")
                .tint(Theme.cyan)
                .foregroundStyle(Theme.onSurfaceVariant)
            Spacer()
        } else if let wordsError = model.wordsError, model.words.isEmpty {
            Spacer()
            VStack(spacing: 12) {
                Text(wordsError)
                    .foregroundStyle(Color(hex: 0xFF8A80))
                    .multilineTextAlignment(.center)
                Button("重试") { Task { await model.loadNotebooks() } }
                    .buttonStyle(PrimaryButtonStyle())
                    .padding(.horizontal, 80)
            }
            .padding(.horizontal, 24)
            Spacer()
        } else if visibleWords.isEmpty {
            Spacer()
            Text(model.words.isEmpty ? "还没有生词，去首页查词并点星星收藏" : "没有匹配的单词")
                .font(.subheadline)
                .foregroundStyle(Theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 28)
            Spacer()
        } else {
            GeometryReader { geo in
                let wordColumn = min(geo.size.width * 0.36, 148)
                ZStack(alignment: .trailing) {
                    ScrollViewReader { proxy in
                        List {
                            ForEach(visibleWords) { word in
                                let catalog = model.activeNotebook?.isSystem == true
                                let favorited = model.isFavorited(word.text)
                                let selected = selectedIds.contains(word.id)
                                Group {
                                    if selectionMode {
                                        Button {
                                            noteTouch(word.id)
                                            if selected {
                                                selectedIds.remove(word.id)
                                            } else {
                                                selectedIds.insert(word.id)
                                            }
                                        } label: {
                                            HStack(spacing: 10) {
                                                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                                                    .font(.title3)
                                                    .foregroundStyle(selected ? Theme.cyanSoft : Theme.onSurfaceVariant)
                                                    .frame(width: 28)
                                                wordRow(word, wordColumnWidth: wordColumn, selectionMode: true)
                                            }
                                        }
                                        .buttonStyle(.plain)
                                    } else {
                                        SwipeRevealRow(
                                            revealed: openSwipeId == word.id,
                                            enabled: true,
                                            actions: catalog
                                                ? [
                                                    SwipeActionItem(
                                                        id: "favorite",
                                                        label: "收藏到…",
                                                        icon: "star",
                                                        color: Theme.gold.opacity(0.88),
                                                        width: 96
                                                    ) {
                                                        noteTouch(word.id)
                                                        openSwipeId = nil
                                                        guard model.session != nil else {
                                                            model.showLogin = true
                                                            return
                                                        }
                                                        guard !favoriteTargets.isEmpty else {
                                                            model.banner = "还没有生词本"
                                                            return
                                                        }
                                                        pendingFavorite = word
                                                        showFavoriteTo = true
                                                    },
                                                ]
                                                : [
                                                    SwipeActionItem(
                                                        id: "move",
                                                        label: "移动到",
                                                        color: Theme.cyan.opacity(0.95),
                                                        width: 76
                                                    ) {
                                                        noteTouch(word.id)
                                                        openSwipeId = nil
                                                        guard model.session != nil else {
                                                            model.showLogin = true
                                                            return
                                                        }
                                                        guard !moveTargets.isEmpty else {
                                                            model.banner = "没有其他生词本"
                                                            return
                                                        }
                                                        selectedIds = [word.id]
                                                        showMove = true
                                                    },
                                                    SwipeActionItem(
                                                        id: "delete",
                                                        label: "删除",
                                                        color: Theme.pink.opacity(0.92),
                                                        width: 76
                                                    ) {
                                                        noteTouch(word.id)
                                                        openSwipeId = nil
                                                        guard model.session != nil else {
                                                            model.showLogin = true
                                                            return
                                                        }
                                                        Task { await model.deleteWord(word.id) }
                                                    },
                                                ],
                                            onRevealChange: { open in
                                                noteTouch(word.id)
                                                openSwipeId = open ? word.id : (openSwipeId == word.id ? nil : openSwipeId)
                                            }
                                        ) {
                                            wordRow(word, wordColumnWidth: wordColumn)
                                        }
                                    }
                                }
                                .id(word.id)
                                .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 22))
                                .listRowSeparator(.hidden)
                                .listRowBackground(selected && selectionMode ? Theme.cyan.opacity(0.12) : Color.clear)
                                .contextMenu {
                                    if !selectionMode {
                                        Button("卡片学习") {
                                            noteTouch(word.id)
                                            if let index = visibleWords.firstIndex(where: { $0.id == word.id }) {
                                                openCards(at: index)
                                            }
                                        }
                                        if catalog {
                                            Button("收藏到…") {
                                                noteTouch(word.id)
                                                guard model.session != nil else {
                                                    model.showLogin = true
                                                    return
                                                }
                                                guard !favoriteTargets.isEmpty else {
                                                    model.banner = "还没有生词本"
                                                    return
                                                }
                                                pendingFavorite = word
                                                showFavoriteTo = true
                                            }
                                            if favorited {
                                                Button("取消收藏", role: .destructive) {
                                                    noteTouch(word.id)
                                                    guard model.session != nil else {
                                                        model.showLogin = true
                                                        return
                                                    }
                                                    Task { _ = await model.toggleCatalogFavorite(word) }
                                                }
                                            }
                                        } else {
                                            Button("移动到…") {
                                                noteTouch(word.id)
                                                guard model.session != nil else {
                                                    model.showLogin = true
                                                    return
                                                }
                                                guard !moveTargets.isEmpty else {
                                                    model.banner = "没有其他生词本"
                                                    return
                                                }
                                                selectedIds = [word.id]
                                                showMove = true
                                            }
                                            Button("删除", role: .destructive) {
                                                noteTouch(word.id)
                                                guard model.session != nil else {
                                                    model.showLogin = true
                                                    return
                                                }
                                                Task { await model.deleteWord(word.id) }
                                            }
                                        }
                                    }
                                }
                            }
                            if model.nextCursor != nil {
                                Button {
                                    Task { await model.loadMoreWords() }
                                } label: {
                                    HStack {
                                        Spacer()
                                        if model.wordsLoading {
                                            ProgressView().tint(Theme.cyan)
                                        } else {
                                            Text("加载更多").foregroundStyle(Theme.cyan)
                                        }
                                        Spacer()
                                    }
                                }
                                .listRowBackground(Color.clear)
                                .listRowSeparator(.hidden)
                            }
                        }
                        .listStyle(.plain)
                        .scrollContentBackground(.hidden)
                        .refreshable { await model.loadWords() }
                        .onChange(of: scrollTarget) { _, target in
                            guard let target else { return }
                            withAnimation(.easeOut(duration: 0.15)) {
                                proxy.scrollTo(target, anchor: .top)
                            }
                            scrollTarget = nil
                        }
                        .onChange(of: model.pendingScrollWordId) { _, id in
                            guard let id else { return }
                            scrollTarget = id
                            model.pendingScrollWordId = nil
                        }
                        .onChange(of: model.activeNotebookId) { _, _ in
                            openSwipeId = nil
                            lastTouchedId = nil
                            selectionMode = false
                            selectedIds = []
                            alphabetSeekTask?.cancel()
                            alphabetSeekTask = nil
                        }
                    }

                    AlphabetIndexRail { letter in
                        jumpToLetter(letter)
                    }
                    .frame(width: 108)
                    .padding(.vertical, 4)
                }
            }
        }
    }

    private func wordRow(_ word: VocabEntry, wordColumnWidth: CGFloat, selectionMode: Bool = false) -> some View {
        let shown = !model.hideDefinitions || revealed.contains(word.id)
        return HStack(spacing: 0) {
            Rectangle()
                .fill(Theme.cyan)
                .frame(width: 3)

            Group {
                if selectionMode {
                    wordColumn(word, width: wordColumnWidth)
                } else {
                    Button {
                        noteTouch(word.id)
                        Speech.speak(word.text, accent: model.accent)
                    } label: {
                        wordColumn(word, width: wordColumnWidth)
                    }
                    .buttonStyle(.plain)
                }
            }

            meaningArea(word, shown: shown, selectionMode: selectionMode)
        }
        .frame(height: Self.wordRowHeight)
        .clipped()
        .background(Theme.surface)
    }

    @ViewBuilder
    private func meaningArea(_ word: VocabEntry, shown: Bool, selectionMode: Bool) -> some View {
        let body = ZStack(alignment: .leading) {
            if shown {
                meaningText(word)
                    .lineLimit(3)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            } else {
                DefinitionMask()
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)

        if selectionMode {
            body
        } else {
            Button {
                noteTouch(word.id)
                if model.hideDefinitions {
                    if revealed.contains(word.id) {
                        revealed.remove(word.id)
                    } else {
                        revealed.insert(word.id)
                    }
                } else if let index = visibleWords.firstIndex(where: { $0.id == word.id }) {
                    openCards(at: index)
                }
            } label: {
                body.contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    private func wordColumn(_ word: VocabEntry, width: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(word.text)
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(Theme.cyanSoft)
                .lineLimit(1)
            HStack(spacing: 6) {
                Image(systemName: "speaker.wave.2")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Theme.cyan)
                if let ipa = slashIpa(word) {
                    Text(ipa)
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .lineLimit(1)
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .frame(width: width, alignment: .leading)
        .frame(maxHeight: .infinity, alignment: .center)
        .background(Theme.surfaceContainer)
    }

    @ViewBuilder
    private func meaningText(_ word: VocabEntry) -> some View {
        if word.definitions.isEmpty {
            Text("—")
                .font(.subheadline)
                .foregroundStyle(Theme.onSurfaceVariant)
        } else {
            Text(compactMeaning(word.definitions))
                .font(.subheadline)
                .multilineTextAlignment(.leading)
        }
    }

    private func compactMeaning(_ definitions: [Definition]) -> AttributedString {
        var result = AttributedString()
        for (index, def) in definitions.enumerated() {
            if index > 0 {
                result.append(AttributedString("  "))
            }
            let pos = formatPos(def.pos)
            if !pos.isEmpty {
                var posRun = AttributedString(pos + " ")
                posRun.foregroundColor = def.isUserAdded ? Theme.pink : Theme.posColor(def.pos)
                posRun.font = .subheadline.weight(.semibold)
                result.append(posRun)
            }
            var meaning = AttributedString(def.meaning.trimmingCharacters(in: .whitespacesAndNewlines))
            meaning.foregroundColor = def.isUserAdded ? Theme.pink.opacity(0.92) : Theme.onSurface
            meaning.font = .subheadline
            result.append(meaning)
        }
        return result
    }

    private var bottomBar: some View {
        Group {
            if selectionMode {
                HStack(spacing: 8) {
                    selectionPill(
                        title: selectedIds.count == visibleWords.count && !visibleWords.isEmpty ? "取消全选" : "全选",
                        fill: Theme.cyanSoft,
                        foreground: Theme.onPrimary,
                        enabled: !visibleWords.isEmpty
                    ) {
                        if selectedIds.count == visibleWords.count {
                            selectedIds = []
                        } else {
                            selectedIds = Set(visibleWords.map(\.id))
                        }
                    }
                    selectionPill(
                        title: "移动到…",
                        fill: Theme.cyanSoft,
                        foreground: Theme.onPrimary,
                        enabled: !selectedIds.isEmpty && !moveTargets.isEmpty
                    ) {
                        showMove = true
                    }
                    selectionPill(
                        title: "删除",
                        fill: Theme.pink,
                        foreground: .white,
                        enabled: !selectedIds.isEmpty
                    ) {
                        showDeleteSelected = true
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
            } else {
                HStack(spacing: 0) {
                    bottomAction(
                        systemImage: model.hideDefinitions ? "eye" : "eye.slash",
                        title: model.hideDefinitions ? "显示释义" : "隐藏释义"
                    ) {
                        model.setHideDefinitions(!model.hideDefinitions)
                        revealed.removeAll()
                    }
                    bottomAction(
                        systemImage: "rectangle.stack",
                        title: "卡片模式",
                        enabled: !visibleWords.isEmpty
                    ) {
                        openCardsAtLastTouch()
                    }
                }
            }
        }
        .padding(.top, 8)
        .padding(.bottom, 8)
        .background {
            Theme.surface
                .overlay(alignment: .top) {
                    Rectangle()
                        .fill(Theme.cyan.opacity(0.2))
                        .frame(height: 1)
                }
                .ignoresSafeArea(edges: .bottom)
        }
    }

    private var moveTargets: [Notebook] {
        model.notebooks.filter { !$0.isSystem && $0.id != model.activeNotebookId }
    }

    private var favoriteTargets: [Notebook] {
        model.notebooks.filter { !$0.isSystem }
    }

    private func selectionPill(
        title: String,
        fill: Color,
        foreground: Color,
        enabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(enabled ? foreground : Theme.onSurfaceVariant.opacity(0.45))
                .lineLimit(1)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(enabled ? fill : Theme.surfaceHigh, in: Capsule())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    private func bottomAction(
        systemImage: String,
        title: String,
        enabled: Bool = true,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 3) {
                Image(systemName: systemImage)
                    .font(.system(size: 20, weight: .medium))
                Text(title)
                    .font(.caption.weight(.bold))
            }
            .foregroundStyle(enabled ? Theme.cyan : Theme.onSurfaceVariant.opacity(0.4))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    private func openCards(at index: Int) {
        cardStart = index
        showCards = true
    }

    private func openCardsAtLastTouch() {
        if let id = lastTouchedId, let index = visibleWords.firstIndex(where: { $0.id == id }) {
            openCards(at: index)
        } else {
            openCards(at: 0)
        }
    }

    private func noteTouch(_ id: Int64) {
        lastTouchedId = id
    }

    private func jumpToLetter(_ letter: Character) {
        let upper = Character(letter.uppercased())
        // Exact initial only. A later letter already on screen must not block the real jump.
        if let index = visibleWords.firstIndex(where: { Self.wordInitialLetter($0.text) == upper }) {
            scrollTarget = visibleWords[index].id
            return
        }
        if let abs = model.alphabetLetterIndex[upper] {
            let local = abs - model.listWindowStart
            if local >= 0, local < visibleWords.count,
               Self.wordInitialLetter(visibleWords[local].text) == upper {
                scrollTarget = visibleWords[local].id
                return
            }
        }
        alphabetSeekTask?.cancel()
        alphabetSeekTask = Task {
            await model.seekAlphabetLetter(upper)
        }
    }

    private func slashIpa(_ word: VocabEntry) -> String? {
        let raw = (word.ipaUk?.nilIfEmpty ?? word.ipaUs?.nilIfEmpty) ?? nil
        guard let raw else { return nil }
        let core = raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .split(separator: ";").first
            .map(String.init)?
            .split(separator: ",").first
            .map(String.init)?
            .trimmingCharacters(in: CharacterSet(charactersIn: "/ ").union(.whitespaces))
            ?? ""
        guard !core.isEmpty else { return nil }
        return "/\(core)/"
    }

    private func formatPos(_ pos: String) -> String {
        let trimmed = pos.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return "" }
        if trimmed.hasSuffix(".") || trimmed.hasPrefix("【") { return trimmed }
        return "\(trimmed)."
    }

    private static func orderNotebookChips(_ notebooks: [Notebook]) -> [Notebook] {
        let catalogOrder = ["zhongkao", "gaokao", "cet4", "cet6", "toefl", "ielts"]
        let users = notebooks
            .filter { !$0.isSystem }
            .sorted {
                if $0.createdAtMillis != $1.createdAtMillis {
                    return $0.createdAtMillis > $1.createdAtMillis
                }
                return $0.id > $1.id
            }
        var catalogBySlug: [String: Notebook] = [:]
        for book in notebooks where book.isSystem {
            guard let slug = book.slug, catalogBySlug[slug] == nil else { continue }
            catalogBySlug[slug] = book
        }
        var orderedCatalogs: [Notebook] = []
        for slug in catalogOrder {
            if let book = catalogBySlug.removeValue(forKey: slug) {
                orderedCatalogs.append(book)
            }
        }
        orderedCatalogs += catalogBySlug.values.sorted {
            if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
            return $0.id < $1.id
        }
        return users + orderedCatalogs
    }

    private static func catalogChipStyle(_ notebook: Notebook) -> CatalogChipStyle? {
        let slug = notebook.slug ?? ""
        let name = notebook.name
        if slug == "zhongkao" || name.contains("中考") {
            return CatalogChipStyle(
                background: Color(hex: 0x2C3348),
                foreground: Color(hex: 0xD4D8E8),
                dashed: Color(hex: 0x96A0C0).opacity(0.7)
            )
        }
        if slug == "gaokao" || name.contains("高考") {
            return CatalogChipStyle(
                background: Color(hex: 0x3A2C3A),
                foreground: Color(hex: 0xE4D0DE),
                dashed: Color(hex: 0xB894AD).opacity(0.7)
            )
        }
        if slug == "cet4" || name.contains("四级") {
            return CatalogChipStyle(
                background: Color(hex: 0x243F3F),
                foreground: Color(hex: 0xD5E3E2),
                dashed: Color(hex: 0x8FAEAD).opacity(0.7)
            )
        }
        if slug == "cet6" || name.contains("六级") {
            return CatalogChipStyle(
                background: Color(hex: 0x3A3529),
                foreground: Color(hex: 0xD8CEB6),
                dashed: Color(hex: 0xB0A584).opacity(0.7)
            )
        }
        return nil
    }

    private static func wordInitialLetter(_ text: String) -> Character {
        guard let first = text.trimmingCharacters(in: .whitespacesAndNewlines).uppercased().first else {
            return "#"
        }
        return ("A"..."Z").contains(first) ? first : "#"
    }
}

private struct NotebookTargetPickerDialog: View {
    var title: String
    var subtitle: String
    var notebooks: [Notebook]
    var onSelect: (Notebook) -> Void
    var onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.55)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)

            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Theme.onSurface)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .padding(.top, 8)

                if !notebooks.isEmpty {
                    VStack(spacing: 8) {
                        ForEach(notebooks) { notebook in
                            Button {
                                onSelect(notebook)
                            } label: {
                                Text(notebook.name)
                                    .font(.body.weight(.bold))
                                    .foregroundStyle(Theme.cyanSoft)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 14)
                                    .background(Theme.surfaceHigh, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.top, 14)
                }

                HStack {
                    Spacer()
                    Button("取消", action: onDismiss)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                }
                .padding(.top, 18)
            }
            .padding(.horizontal, 22)
            .padding(.vertical, 20)
            .frame(maxWidth: 340)
            .background(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(Theme.surfaceContainer.opacity(0.98))
                    .overlay(
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .stroke(Theme.cyan.opacity(0.45), lineWidth: 1)
                    )
                    .shadow(color: Theme.cyan.opacity(0.28), radius: 24, y: 8)
            )
            .padding(.horizontal, 28)
        }
    }
}

private struct SwipeActionItem: Identifiable {
    var id: String
    var label: String
    var icon: String? = nil
    var color: Color
    var width: CGFloat = 76
    var action: () -> Void
}

private struct SwipeRevealRow<Content: View>: View {
    var revealed: Bool
    var enabled: Bool
    var actions: [SwipeActionItem]
    var onRevealChange: (Bool) -> Void
    @ViewBuilder var content: () -> Content

    @State private var offset: CGFloat = 0
    @State private var dragStart: CGFloat = 0
    @State private var dragging = false

    private var actionWidth: CGFloat {
        max(actions.reduce(0) { $0 + $1.width }, 1)
    }

    var body: some View {
        ZStack(alignment: .trailing) {
            HStack(spacing: 0) {
                ForEach(actions) { item in
                    Button(action: item.action) {
                        VStack(spacing: 2) {
                            if let icon = item.icon {
                                Image(systemName: icon)
                                    .font(.system(size: 18, weight: .semibold))
                            }
                            Text(item.label)
                                .font(.subheadline.weight(.medium))
                        }
                        .foregroundStyle(.white)
                        .frame(width: item.width)
                        .frame(maxHeight: .infinity)
                        .background(item.color)
                    }
                    .buttonStyle(.plain)
                }
            }
            .opacity(offset < -0.5 ? 1 : 0)
            .allowsHitTesting(offset < -actionWidth * 0.6)

            content()
                .background(Theme.surface)
                .offset(x: offset)
                .highPriorityGesture(
                    DragGesture(minimumDistance: 16, coordinateSpace: .local)
                        .onChanged { value in
                            guard enabled else { return }
                            if !dragging {
                                dragging = true
                                dragStart = offset
                            }
                            let next = dragStart + value.translation.width
                            offset = min(0, max(-actionWidth, next))
                        }
                        .onEnded { _ in
                            guard enabled else { return }
                            dragging = false
                            let open = offset <= -actionWidth / 2
                            withAnimation(.easeOut(duration: 0.18)) {
                                offset = open ? -actionWidth : 0
                            }
                            onRevealChange(open)
                        },
                    including: enabled ? .gesture : .subviews
                )
        }
        .clipped()
        .onChange(of: revealed) { _, open in
            withAnimation(.easeOut(duration: 0.18)) {
                offset = open ? -actionWidth : 0
            }
        }
        .onChange(of: actionWidth) { _, _ in
            offset = revealed ? -actionWidth : 0
        }
        .onAppear {
            offset = revealed ? -actionWidth : 0
        }
    }
}

private struct CatalogChipStyle {
    var background: Color
    var foreground: Color
    var dashed: Color?
}

private struct DefinitionMask: View {
    var body: some View {
        Canvas { context, size in
            let base = Self.rgb(Theme.surfaceContainer) ?? (0.06, 0.32, 0.21)
            let accent = Self.rgb(Theme.cyan) ?? (0.60, 0.85, 0.29)
            let t = 0.22
            let fill = Color(
                red: base.r + (accent.r - base.r) * t,
                green: base.g + (accent.g - base.g) * t,
                blue: base.b + (accent.b - base.b) * t,
                opacity: 1
            )
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(fill))
            let step: CGFloat = 12
            let stroke: CGFloat = 8
            var x: CGFloat = -size.height
            while x < size.width + size.height {
                var line = Path()
                line.move(to: CGPoint(x: x, y: size.height))
                line.addLine(to: CGPoint(x: x + size.height, y: 0))
                context.stroke(
                    line,
                    with: .color(Color(red: accent.r, green: accent.g, blue: accent.b, opacity: 0.55)),
                    lineWidth: stroke
                )
                x += step
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
        .allowsHitTesting(false)
    }

    private static func rgb(_ color: Color) -> (r: Double, g: Double, b: Double)? {
        let ui = UIColor(color).resolvedColor(with: UITraitCollection(userInterfaceStyle: .dark))
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        guard ui.getRed(&r, green: &g, blue: &b, alpha: &a) else { return nil }
        return (Double(r), Double(g), Double(b))
    }
}

/// UIKit touch tracking so list reloads cannot cancel an in-progress finger slide.
private struct AlphabetIndexRail: UIViewRepresentable {
    var onSelect: (Character) -> Void

    func makeUIView(context: Context) -> AlphabetRailView {
        let view = AlphabetRailView()
        view.onSelect = onSelect
        return view
    }

    func updateUIView(_ uiView: AlphabetRailView, context: Context) {
        uiView.onSelect = onSelect
    }
}

private final class AlphabetRailView: UIView {
    var onSelect: ((Character) -> Void)?

    private let letters: [Character] = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ") + ["#"]
    private var labels: [UILabel] = []
    private let bubble = UILabel()
    private var current: Character?

    override init(frame: CGRect) {
        super.init(frame: frame)
        isMultipleTouchEnabled = false
        clipsToBounds = false
        backgroundColor = .clear
        for letter in letters {
            let label = UILabel()
            label.text = String(letter)
            label.font = .systemFont(ofSize: 9, weight: .semibold)
            label.textAlignment = .center
            label.textColor = UIColor(Theme.onSurfaceVariant)
            labels.append(label)
            addSubview(label)
        }
        bubble.font = .systemFont(ofSize: 28, weight: .bold)
        bubble.textAlignment = .center
        bubble.textColor = UIColor(Theme.onSurface)
        bubble.backgroundColor = UIColor(Theme.surfaceHigh).withAlphaComponent(0.95)
        bubble.layer.cornerRadius = 28
        bubble.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner, .layerMinXMaxYCorner]
        bubble.clipsToBounds = true
        bubble.isHidden = true
        addSubview(bubble)
    }

    required init?(coder: NSCoder) { nil }

    /// Letters sit in the trailing strip. The rest of the view only draws the bubble and must not steal taps on definitions.
    private let railWidth: CGFloat = 24

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        point.x >= bounds.width - railWidth - 4
    }

    override func hitTest(_ location: CGPoint, with event: UIEvent?) -> UIView? {
        guard point(inside: location, with: event) else { return nil }
        return self
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let count = CGFloat(labels.count)
        let row = bounds.height / max(count, 1)
        for (index, label) in labels.enumerated() {
            label.frame = CGRect(
                x: bounds.width - railWidth,
                y: CGFloat(index) * row,
                width: railWidth,
                height: row
            )
        }
        layoutBubble()
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        select(touches)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        select(touches)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        finish()
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        finish()
    }

    private func select(_ touches: Set<UITouch>) {
        guard let point = touches.first?.location(in: self), bounds.height > 1 else { return }
        let t = min(0.999, max(0, point.y / bounds.height))
        let index = min(letters.count - 1, Int(t * CGFloat(letters.count)))
        let letter = letters[index]
        guard letter != current else { return }
        current = letter
        for (i, label) in labels.enumerated() {
            let selected = i == index
            label.font = .systemFont(ofSize: selected ? 10 : 9, weight: .semibold)
            label.textColor = selected ? UIColor(Theme.onPrimary) : UIColor(Theme.onSurfaceVariant)
            label.backgroundColor = selected ? UIColor(Theme.cyan) : .clear
            label.layer.cornerRadius = selected ? 8 : 0
            label.clipsToBounds = true
        }
        bubble.text = String(letter)
        bubble.isHidden = false
        layoutBubble()
        onSelect?(letter)
    }

    private func layoutBubble() {
        guard let current, let index = letters.firstIndex(of: current) else { return }
        let size: CGFloat = 56
        let row = bounds.height / CGFloat(max(letters.count, 1))
        let midY = CGFloat(index) * row + row / 2
        let y = min(max(0, midY - size / 2), max(0, bounds.height - size))
        bubble.frame = CGRect(x: bounds.width - railWidth - 8 - size, y: y, width: size, height: size)
    }

    private func finish() {
        current = nil
        bubble.isHidden = true
        for label in labels {
            label.font = .systemFont(ofSize: 9, weight: .semibold)
            label.textColor = UIColor(Theme.onSurfaceVariant)
            label.backgroundColor = .clear
        }
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}

private extension Color {
    func opaque() -> Color {
        let ui = UIColor(self)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        guard ui.getRed(&r, green: &g, blue: &b, alpha: &a) else { return self }
        return Color(red: Double(r), green: Double(g), blue: Double(b), opacity: 1)
    }

    func mix(with other: Color, amount: Double) -> Color {
        let t = amount.clamped(to: 0...1)
        let ui = UIColor(self)
        let ou = UIColor(other)
        var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
        var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
        ui.getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        ou.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        return Color(
            red: Double(r1 + (r2 - r1) * t),
            green: Double(g1 + (g2 - g1) * t),
            blue: Double(b1 + (b2 - b1) * t),
            opacity: Double(a1 + (a2 - a1) * t)
        )
    }
}

private extension String {
    var nilIfEmpty: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
