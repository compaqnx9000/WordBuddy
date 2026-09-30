import AVFoundation
import SwiftUI

struct ShortsView: View {
    @EnvironmentObject private var model: AppModel
    var isSelected: Bool
    @Binding var chromeHidden: Bool

    @State private var clips: [ShortClip] = []
    @State private var currentId: String?
    @State private var loading = false
    @State private var loadingMore = false
    @State private var errorMessage: String?
    @State private var fullscreen = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if clips.isEmpty {
                emptyState
            } else {
                ScrollView(.vertical) {
                    LazyVStack(spacing: 0) {
                        ForEach(clips) { clip in
                            ShortPage(
                                clip: clip,
                                engaged: isSelected && clip.id == currentId,
                                fullscreen: fullscreen,
                                onToggleFullscreen: { fullscreen.toggle() },
                                onOpenWord: { model.openWord($0) },
                                onToggleFavorite: { toggleFavorite(clip) },
                                onWatch: { reportWatch(clip: clip, watchMs: $0) }
                            )
                            .containerRelativeFrame([.horizontal, .vertical])
                            .id(clip.id)
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
        }
        .onChange(of: currentId) { _, _ in
            Task { await loadMoreIfNeeded() }
        }
        .onChange(of: fullscreen) { _, hidden in
            chromeHidden = hidden
        }
        .onChange(of: isSelected) { _, selected in
            if selected {
                model.podcast.pause()
            } else {
                fullscreen = false
                chromeHidden = false
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
            if currentId == nil || !items.contains(where: { $0.id == currentId }) {
                currentId = items.first?.id
            }
        } catch {
            if !model.noteSessionError(error) {
                errorMessage = error.localizedDescription
            }
        }
    }

    private func loadMoreIfNeeded() async {
        guard !loading, !loadingMore, !clips.isEmpty else { return }
        guard let currentId, let index = clips.firstIndex(where: { $0.id == currentId }) else { return }
        guard index >= clips.count - 3 else { return }
        loadingMore = true
        defer { loadingMore = false }
        do {
            let known = Set(clips.map(\.id))
            let more = playable(
                try await model.api.fetchShortsFeed(token: model.session?.token, excludeIds: clips.map(\.id))
            ).filter { !known.contains($0.id) }
            clips.append(contentsOf: more)
        } catch {
            _ = model.noteSessionError(error)
        }
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
    var clip: ShortClip
    var engaged: Bool
    var fullscreen: Bool
    var onToggleFullscreen: () -> Void
    var onOpenWord: (String) -> Void
    var onToggleFavorite: () -> Void
    var onWatch: (Int) -> Void

    @State private var userPaused = false
    @State private var metaVisible = true
    @State private var watchStart: Date?

    var body: some View {
        ZStack {
            Color.black
            if engaged, let url = URL(string: clip.videoUrl) {
                ShortPlayer(url: url, playing: !userPaused)
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
                                .foregroundStyle(Theme.cyan)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(.white.opacity(0.12), in: Capsule())
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
            ShareLink(item: clip.shareText) {
                ShortActionLabel(systemImage: "square.and.arrow.up", title: "分享", tint: .white)
            }
            .buttonStyle(.plain)
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

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> PlayerLayerView {
        let view = PlayerLayerView()
        let item = AVPlayerItem(url: url)
        let player = AVPlayer(playerItem: item)
        player.actionAtItemEnd = .none
        context.coordinator.player = player
        context.coordinator.playing = playing
        context.coordinator.observe(item)
        view.playerLayer.player = player
        view.playerLayer.videoGravity = .resizeAspect
        view.backgroundColor = .black
        if playing { player.play() }
        return view
    }

    func updateUIView(_ uiView: PlayerLayerView, context: Context) {
        context.coordinator.playing = playing
        if playing {
            context.coordinator.player?.play()
        } else {
            context.coordinator.player?.pause()
        }
    }

    static func dismantleUIView(_ uiView: PlayerLayerView, coordinator: Coordinator) {
        coordinator.stop()
        uiView.playerLayer.player = nil
    }

    final class Coordinator {
        var player: AVPlayer?
        var playing = false
        private var token: NSObjectProtocol?

        func observe(_ item: AVPlayerItem) {
            token = NotificationCenter.default.addObserver(
                forName: .AVPlayerItemDidPlayToEndTime,
                object: item,
                queue: .main
            ) { [weak self] _ in
                guard let self, self.playing else { return }
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
