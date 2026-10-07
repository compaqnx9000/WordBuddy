import AVFoundation
import SwiftUI

enum ShortFeedItem: Identifiable {
    case clip(ShortClip, cycle: Int)
    case draw(String)

    var id: String {
        switch self {
        case .clip(let clip, let cycle):
            return "clip-\(cycle)-\(clip.id)"
        case .draw(let key):
            return "draw-\(key)"
        }
    }

    var clip: ShortClip? {
        if case .clip(let clip, _) = self { return clip }
        return nil
    }

    var isDraw: Bool {
        if case .draw = self { return true }
        return false
    }
}

struct ShortsView: View {
    @EnvironmentObject private var model: AppModel
    var isSelected: Bool
    @Binding var chromeHidden: Bool

    @State private var clips: [ShortClip] = []
    @State private var feed: [ShortFeedItem] = []
    @State private var currentId: String?
    @ObservedObject private var drawPool = DrawAdPool.shared
    @State private var loading = false
    @State private var loadingMore = false
    @State private var feedCycle = 0
    @State private var errorMessage: String?
    @State private var fullscreen = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if feed.isEmpty {
                emptyState
            } else {
                ScrollView(.vertical) {
                    LazyVStack(spacing: 0) {
                        ForEach(feed) { item in
                            feedPage(item)
                                .containerRelativeFrame([.horizontal, .vertical])
                                .id(item.id)
                        }
                    }
                    .scrollTargetLayout()
                }
                .scrollTargetBehavior(.paging)
                .scrollPosition(id: $currentId)
                .scrollIndicators(.hidden)
                .ignoresSafeArea()
            }
        }
        .statusBarHidden(fullscreen)
        .task {
            if clips.isEmpty { await loadInitial() }
            if isSelected { drawPool.preload() }
        }
        .onChange(of: currentId) { _, _ in
            insertUpcomingDraw()
            Task { await loadMoreIfNeeded() }
        }
        .onChange(of: drawPool.revision) { _, _ in
            insertUpcomingDraw()
        }
        .onChange(of: fullscreen) { _, hidden in
            chromeHidden = hidden
        }
        .onChange(of: isSelected) { _, selected in
            if selected {
                model.podcast.pause()
                drawPool.preload()
            } else {
                fullscreen = false
                chromeHidden = false
            }
        }
        .overlay {
            if let word = model.shortsWordLookup {
                LookupView(embeddedWord: word) {
                    model.shortsWordLookup = nil
                }
                .environmentObject(model)
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 14) {
            if loading {
                ProgressView("加载短视频…")
                    .tint(.white)
                    .foregroundStyle(.white.opacity(0.85))
            } else {
                Text(errorMessage ?? "暂无短视频")
                    .foregroundStyle(.white.opacity(0.85))
                    .multilineTextAlignment(.center)
                Button("重试") {
                    Task { await loadInitial() }
                }
                .buttonStyle(PrimaryButtonStyle())
                .padding(.horizontal, 48)
            }
        }
        .padding(.horizontal, 28)
    }

    private func loadInitial() async {
        loading = true
        errorMessage = nil
        defer { loading = false }
        do {
            let items = playable(try await model.api.fetchShortsFeed(token: model.session?.token))
            clips = items
            feedCycle = 0
            feed = items.map { .clip($0, cycle: 0) }
            if currentId == nil || !feed.contains(where: { $0.id == currentId }) {
                currentId = feed.first?.id
            }
            drawPool.preload()
            insertUpcomingDraw()
        } catch {
            if !model.noteSessionError(error) {
                errorMessage = error.localizedDescription
            }
        }
    }

    private func loadMoreIfNeeded() async {
        guard !loading, !loadingMore, !clips.isEmpty else { return }
        guard let currentId, let index = feed.firstIndex(where: { $0.id == currentId }) else { return }
        guard index >= feed.count - 3 else { return }
        loadingMore = true
        defer { loadingMore = false }
        do {
            let tailId = feed.reversed().compactMap(\.clip).first?.id
            let more = playable(
                try await model.api.fetchShortsFeed(
                    token: model.session?.token,
                    excludeIds: clips.map(\.id),
                    afterId: tailId
                )
            )
            // After every published video has been shown, the server continues from
            // the video on screen. An empty page replays the pool without placing
            // the same video twice in a row.
            let incoming = withoutConsecutiveDuplicate(more.isEmpty ? clips : more, after: tailId)
            guard !incoming.isEmpty else { return }
            if !more.isEmpty {
                var merged = clips
                let known = Set(merged.map(\.id))
                for clip in more where !known.contains(clip.id) {
                    merged.append(clip)
                }
                clips = merged
            }
            appendCycle(incoming)
        } catch {
            let tailId = feed.reversed().compactMap(\.clip).first?.id
            let incoming = withoutConsecutiveDuplicate(clips, after: tailId)
            if !incoming.isEmpty {
                appendCycle(incoming)
            } else if clips.isEmpty {
                _ = model.noteSessionError(error)
            }
        }
    }

    private func withoutConsecutiveDuplicate(_ items: [ShortClip], after tailId: String?) -> [ShortClip] {
        var previous = tailId
        var kept: [ShortClip] = []
        kept.reserveCapacity(items.count)
        for clip in items where clip.id != previous {
            kept.append(clip)
            previous = clip.id
        }
        return kept
    }

    private func appendCycle(_ items: [ShortClip]) {
        guard !items.isEmpty else { return }
        feedCycle += 1
        let cycle = feedCycle
        feed.append(contentsOf: items.map { .clip($0, cycle: cycle) })
        insertUpcomingDraw()
    }

    private func playable(_ clips: [ShortClip]) -> [ShortClip] {
        clips.filter { !$0.id.isEmpty && !$0.videoUrl.isEmpty }
    }

    private func toggleFavorite(_ clip: ShortClip) {
        guard let token = model.session?.token, !token.isEmpty else {
            model.banner = "登录后可收藏短视频"
            model.showLogin = true
            return
        }
        let next = !clip.favorited
        setFavorite(clip.id, favorited: next)
        Task {
            do {
                let saved = try await model.api.setShortFavorite(token: token, videoId: clip.id, favorited: next)
                setFavorite(clip.id, favorited: saved)
            } catch {
                setFavorite(clip.id, favorited: !next)
                if !model.noteSessionError(error) {
                    model.banner = "收藏失败，请稍后重试"
                }
            }
        }
    }

    private func setFavorite(_ id: String, favorited: Bool) {
        guard let index = clips.firstIndex(where: { $0.id == id }) else { return }
        clips[index].favorited = favorited
        let updated = clips[index]
        for feedIndex in feed.indices {
            if case .clip(let existing, let cycle) = feed[feedIndex], existing.id == id {
                feed[feedIndex] = .clip(updated, cycle: cycle)
            }
        }
    }

    @ViewBuilder
    private func feedPage(_ item: ShortFeedItem) -> some View {
        switch item {
        case .clip(let clip, _):
            ShortPage(
                clip: clip,
                engaged: isSelected && item.id == currentId,
                pausedForLookup: model.shortsWordLookup != nil,
                fullscreen: fullscreen,
                metaStartsVisible: model.shortsMetaVisibleDefault,
                onToggleFullscreen: { fullscreen.toggle() },
                onOpenWord: { model.shortsWordLookup = $0 },
                onToggleFavorite: { toggleFavorite(clip) },
                onWatch: { reportWatch(clip: clip, watchMs: $0) }
            )
        case .draw(let key):
            DrawAdPage(adKey: key, active: isSelected && item.id == currentId)
        }
    }

    private func insertUpcomingDraw() {
        guard drawPool.hasReady, !feed.isEmpty else { return }
        let currentIndex = feed.firstIndex(where: { $0.id == currentId }) ?? 0
        let adsAhead = feed.dropFirst(currentIndex + 1).filter(\.isDraw).count
        guard adsAhead < 1, let key = drawPool.takeReady() else { return }
        let firstAd = !feed.contains(where: \.isDraw)
        var skip = firstAd ? 2 : Int.random(in: 2...3)
        var index = currentIndex + 1
        while skip > 0, index < feed.count {
            if !feed[index].isDraw { skip -= 1 }
            index += 1
        }
        feed.insert(.draw(key), at: min(index, feed.count))
    }

    private func reportWatch(clip: ShortClip, watchMs: Int) {
        guard watchMs >= 500 else { return }
        let token = model.session?.token
        Task {
            try? await model.api.reportShortWatch(
                token: token,
                videoId: clip.id,
                watchMs: watchMs,
                completed: watchMs >= 12_000
            )
        }
    }
}

private struct ShortPage: View {
    @EnvironmentObject private var model: AppModel
    var clip: ShortClip
    var engaged: Bool
    var pausedForLookup: Bool = false
    var fullscreen: Bool
    var onToggleFullscreen: () -> Void
    var onOpenWord: (String) -> Void
    var onToggleFavorite: () -> Void
    var onWatch: (Int) -> Void

    @State private var userPaused = false
    @State private var metaVisible: Bool
    @State private var watchStart: Date?

    init(
        clip: ShortClip,
        engaged: Bool,
        pausedForLookup: Bool = false,
        fullscreen: Bool,
        metaStartsVisible: Bool,
        onToggleFullscreen: @escaping () -> Void,
        onOpenWord: @escaping (String) -> Void,
        onToggleFavorite: @escaping () -> Void,
        onWatch: @escaping (Int) -> Void
    ) {
        self.clip = clip
        self.engaged = engaged
        self.pausedForLookup = pausedForLookup
        self.fullscreen = fullscreen
        self.onToggleFullscreen = onToggleFullscreen
        self.onOpenWord = onOpenWord
        self.onToggleFavorite = onToggleFavorite
        self.onWatch = onWatch
        _userPaused = State(initialValue: false)
        _metaVisible = State(initialValue: metaStartsVisible)
        _watchStart = State(initialValue: nil)
    }

    var body: some View {
        ZStack {
            Color.black
            if engaged, let url = URL(string: clip.videoUrl) {
                ShortPlayer(
                    url: url,
                    playing: !userPaused && !pausedForLookup,
                    loop: model.shortVideoLoop
                )
            } else if let cover = clip.coverUrl, let url = URL(string: cover) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFit()
                } placeholder: {
                    Color.black
                }
            }
            LinearGradient(
                colors: [
                    .clear,
                    .clear,
                    .black.opacity(metaVisible ? 0.72 : 0.28),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .allowsHitTesting(false)
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture { userPaused.toggle() }
            if engaged && userPaused {
                Image(systemName: "pause.fill")
                    .font(.system(size: 48))
                    .foregroundStyle(.white.opacity(0.85))
                    .allowsHitTesting(false)
            }
        }
        .overlay(alignment: .bottomLeading) {
            if metaVisible { meta }
        }
        .overlay(alignment: .bottomTrailing) {
            actions
        }
        .onAppear {
            if engaged { beginWatch() }
        }
        .onChange(of: engaged) { was, now in
            if now {
                userPaused = false
                beginWatch()
            } else if was {
                endWatch()
            }
        }
        .onDisappear { endWatch() }
    }

    private var meta: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("@\(clip.author)")
                .font(.subheadline.weight(.bold))
                .onTapGesture { metaVisible = false }
            Text(clip.title)
                .font(.title3.weight(.bold))
                .lineLimit(1)
                .onTapGesture { metaVisible = false }
            if !clip.caption.isEmpty {
                Text(clip.caption)
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.88))
                    .lineLimit(2)
                    .onTapGesture { metaVisible = false }
            }
            if !clip.relatedWords.isEmpty {
                FlowLayout(spacing: 8) {
                    ForEach(clip.relatedWords, id: \.self) { word in
                        Button {
                            onOpenWord(word)
                        } label: {
                            Text(word)
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Theme.isLight ? Theme.cyan : Theme.onPrimary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(
                                    Theme.isLight ? Color.white.opacity(0.94) : Theme.cyan,
                                    in: Capsule()
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.top, 4)
            }
        }
        .foregroundStyle(.white)
        .padding(.leading, 16)
        .padding(.trailing, 78)
        .padding(.bottom, fullscreen ? 28 : 18)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var actions: some View {
        VStack(spacing: 18) {
            ShortActionButton(
                systemImage: clip.favorited ? "bookmark.fill" : "bookmark",
                title: clip.favorited ? "已收藏" : "收藏",
                tint: clip.favorited ? Theme.gold : .white,
                action: onToggleFavorite
            )
            if let code = model.session?.buddyId?.trimmingCharacters(in: .whitespacesAndNewlines), !code.isEmpty {
                ShareLink(item: clip.shareText(inviteCode: code)) {
                    ShortActionLabel(systemImage: "square.and.arrow.up", title: "分享", tint: .white)
                }
                .buttonStyle(.plain)
            } else {
                ShortActionButton(
                    systemImage: "square.and.arrow.up",
                    title: "分享",
                    action: {
                        if model.session == nil {
                            model.showLogin = true
                        } else {
                            model.banner = "请等待搭子号分配"
                        }
                    }
                )
            }
            ShortActionButton(
                systemImage: metaVisible ? "eye.slash" : "eye",
                title: metaVisible ? "藏文案" : "文案",
                action: { metaVisible.toggle() }
            )
            ShortActionButton(
                systemImage: fullscreen ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right",
                title: fullscreen ? "退出" : "全屏",
                action: onToggleFullscreen
            )
        }
        .padding(.trailing, 12)
        .padding(.bottom, fullscreen ? 36 : 24)
    }

    private func beginWatch() {
        if watchStart == nil { watchStart = Date() }
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .moviePlayback)
        try? session.setActive(true)
    }

    private func endWatch() {
        guard let start = watchStart else { return }
        watchStart = nil
        let ms = Int(Date().timeIntervalSince(start) * 1000)
        onWatch(max(0, ms))
    }
}

private struct ShortActionButton: View {
    var systemImage: String
    var title: String
    var tint: Color = .white
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            ShortActionLabel(systemImage: systemImage, title: title, tint: tint)
        }
        .buttonStyle(.plain)
    }
}

private struct ShortActionLabel: View {
    var systemImage: String
    var title: String
    var tint: Color

    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: systemImage)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: 44, height: 44)
                .background(.black.opacity(0.28), in: Circle())
            Text(title)
                .font(.caption2)
                .foregroundStyle(.white)
        }
    }
}

private struct ShortPlayer: UIViewRepresentable {
    var url: URL
    var playing: Bool
    var loop: Bool

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> PlayerLayerView {
        let view = PlayerLayerView()
        let item = AVPlayerItem(url: url)
        let player = AVPlayer(playerItem: item)
        player.actionAtItemEnd = .pause
        context.coordinator.player = player
        context.coordinator.playing = playing
        context.coordinator.loop = loop
        context.coordinator.observe(item)
        view.playerLayer.player = player
        view.playerLayer.videoGravity = .resizeAspect
        view.backgroundColor = .black
        if playing { player.play() }
        return view
    }

    func updateUIView(_ uiView: PlayerLayerView, context: Context) {
        context.coordinator.playing = playing
        context.coordinator.loop = loop
        guard let player = context.coordinator.player else { return }
        if playing {
            let finished = player.currentItem.map { item in
                item.duration.isNumeric && item.currentTime() >= item.duration
            } ?? false
            if finished {
                if loop {
                    player.seek(to: .zero)
                    player.play()
                }
            } else {
                player.play()
            }
        } else {
            player.pause()
        }
    }

    static func dismantleUIView(_ uiView: PlayerLayerView, coordinator: Coordinator) {
        coordinator.stop()
        uiView.playerLayer.player = nil
    }

    final class Coordinator {
        var player: AVPlayer?
        var playing = false
        var loop = false
        private var token: NSObjectProtocol?

        func observe(_ item: AVPlayerItem) {
            token = NotificationCenter.default.addObserver(
                forName: .AVPlayerItemDidPlayToEndTime,
                object: item,
                queue: .main
            ) { [weak self] _ in
                guard let self, self.playing, self.loop else { return }
                self.player?.seek(to: .zero)
                self.player?.play()
            }
        }

        func stop() {
            if let token {
                NotificationCenter.default.removeObserver(token)
            }
            token = nil
            player?.pause()
            player?.replaceCurrentItem(with: nil)
            player = nil
        }
    }
}

private final class PlayerLayerView: UIView {
    override static var layerClass: AnyClass { AVPlayerLayer.self }

    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}

private struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        let rows = rows(for: subviews, width: width)
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(0, rows.count - 1))
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in rows(for: subviews, width: bounds.width) {
            var x = bounds.minX
            for item in row.items {
                item.view.place(
                    at: CGPoint(x: x, y: y),
                    proposal: ProposedViewSize(width: item.size.width, height: item.size.height)
                )
                x += item.size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private func rows(for subviews: Subviews, width: CGFloat) -> [Row] {
        var rows: [Row] = []
        var current: [Row.Item] = []
        var rowWidth: CGFloat = 0
        var rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            let nextWidth = current.isEmpty ? size.width : rowWidth + spacing + size.width
            if !current.isEmpty, width > 0, nextWidth > width {
                rows.append(Row(items: current, height: rowHeight))
                current = []
                rowWidth = 0
                rowHeight = 0
            }
            current.append(Row.Item(view: view, size: size))
            rowWidth = current.count == 1 ? size.width : rowWidth + spacing + size.width
            rowHeight = max(rowHeight, size.height)
        }
        if !current.isEmpty {
            rows.append(Row(items: current, height: rowHeight))
        }
        return rows
    }

    private struct Row {
        struct Item {
            var view: LayoutSubview
            var size: CGSize
        }

        var items: [Item]
        var height: CGFloat
    }
}
