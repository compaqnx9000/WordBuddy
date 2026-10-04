import SwiftUI

struct PodcastView: View {
    @EnvironmentObject private var player: PodcastPlayer
    @EnvironmentObject private var model: AppModel
    @State private var selected: PodcastShow?

    var body: some View {
        let palette = StellarPalettes.palette(for: model.accentStyle)
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    Text("播客唱片架")
                        .font(.title2.weight(.bold))
                        .foregroundStyle(palette.cyanSoft)
                    Text("英文电台直播 + 播客唱片，沉浸听力")
                        .font(.subheadline)
                        .foregroundStyle(Theme.onSurfaceVariant)
                    LazyVGrid(
                        columns: [GridItem(.adaptive(minimum: 148), spacing: 14)],
                        spacing: 18
                    ) {
                        ForEach(PodcastCatalog.shows) { show in
                            Button {
                                selected = show
                            } label: {
                                ShowShelfItem(
                                    show: show,
                                    selected: selected?.id == show.id || player.showId == show.id,
                                    spinning: player.showId == show.id && player.isPlaying
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.top, 12)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .stellarScreenBackground()
            .navigationBarHidden(true)
        }
        .sheet(item: $selected) { show in
            PodcastPlayerSheet(show: show, player: player)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
    }
}

private struct ShowShelfItem: View {
    var show: PodcastShow
    var selected: Bool
    var spinning: Bool

    var body: some View {
        VStack(spacing: 10) {
            VinylDisc(show: show, spinning: spinning)
                .frame(maxWidth: .infinity)
                .aspectRatio(1, contentMode: .fit)
            Text(show.title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.onSurface)
                .lineLimit(1)
            Text(show.host)
                .font(.caption)
                .foregroundStyle(Theme.onSurfaceVariant)
                .lineLimit(1)
        }
        .padding(12)
        .frame(maxWidth: .infinity)
        .background(Color(hex: 0x2A1F18).opacity(0.55), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(selected ? Theme.cyan : Color.clear, lineWidth: selected ? 1.5 : 0)
        )
    }
}

private struct VinylDisc: View {
    var show: PodcastShow
    var spinning: Bool
    var showTonearm: Bool = false
    var tonearmDown: Bool = false

    var body: some View {
        GeometryReader { geo in
            let side = min(geo.size.width, geo.size.height)
            ZStack {
                TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !spinning)) { context in
                    let turns = spinning ? context.date.timeIntervalSinceReferenceDate / 3.6 : 0
                    disc(side: side)
                        .rotationEffect(.degrees(turns * 360))
                }
                .frame(width: side, height: side)

                if showTonearm {
                    TonearmOverlay(lowered: tonearmDown)
                        .frame(width: side, height: side)
                }
            }
            .frame(width: side, height: side)
            .position(x: geo.size.width / 2, y: geo.size.height / 2)
        }
        .shadow(color: .black.opacity(0.35), radius: 8, y: 4)
    }

    private func disc(side: CGFloat) -> some View {
        ZStack {
            Circle()
                .fill(Color(hex: show.vinyl))
                .overlay {
                    ForEach(1..<15, id: \.self) { i in
                        Circle()
                            .stroke(Color.white.opacity(0.12), lineWidth: 1)
                            .padding(side * (0.28 + CGFloat(i) * 0.022) / 2)
                    }
                    Circle().stroke(Color.white.opacity(0.08), lineWidth: side * 0.04)
                }
            Circle()
                .fill(Color(hex: show.label))
                .padding(side * 0.3)
            Circle()
                .fill(Color.black)
                .frame(width: side * 0.05, height: side * 0.05)
            Text(String(show.title.prefix(10)))
                .font(.system(size: max(9, side * 0.07), weight: .bold))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .frame(width: side * 0.36)
        }
        .frame(width: side, height: side)
    }
}

/// Matches Android `TonearmOverlay`: rest −18°, playing 28°, 700ms linear pivot animation.
private struct TonearmOverlay: View {
    var lowered: Bool

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            Canvas { context, size in
                let pivot = CGPoint(x: size.width * 0.86, y: size.height * 0.14)
                let tip = CGPoint(x: size.width * 0.55, y: size.height * 0.52)
                var arm = Path()
                arm.move(to: pivot)
                arm.addLine(to: tip)

                context.fill(
                    Path(ellipseIn: CGRect(
                        x: pivot.x - size.width * 0.035,
                        y: pivot.y - size.width * 0.035,
                        width: size.width * 0.07,
                        height: size.width * 0.07
                    )),
                    with: .color(Color(hex: 0xC0C4CC))
                )
                context.stroke(
                    arm,
                    with: .color(Color(hex: 0xB8BCC4)),
                    style: StrokeStyle(lineWidth: size.width * 0.022, lineCap: .round)
                )
                context.fill(
                    Path(ellipseIn: CGRect(
                        x: tip.x - size.width * 0.028,
                        y: tip.y - size.width * 0.028,
                        width: size.width * 0.056,
                        height: size.width * 0.056
                    )),
                    with: .color(Color(hex: 0xE8EAED))
                )
                var needle = Path()
                needle.move(to: tip)
                needle.addLine(to: CGPoint(x: tip.x - size.width * 0.04, y: tip.y + size.height * 0.03))
                context.stroke(
                    needle,
                    with: .color(Color(hex: 0xFFC107)),
                    style: StrokeStyle(lineWidth: size.width * 0.01, lineCap: .round)
                )
            }
            .rotationEffect(
                .degrees(lowered ? 28 : -18),
                anchor: UnitPoint(x: 0.86, y: 0.14)
            )
            .animation(.linear(duration: 0.7), value: lowered)
            .frame(width: w, height: h)
        }
        .allowsHitTesting(false)
    }
}

private struct PodcastPlayerSheet: View {
    var show: PodcastShow
    @ObservedObject var player: PodcastPlayer
    @Environment(\.dismiss) private var dismiss
    @State private var episodes: [PodcastEpisode] = []
    @State private var loading = false
    @State private var loadError: String?
    @State private var dragProgress: Double?

    private var playingThisShow: Bool { player.showId == show.id }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                header
                VinylDisc(
                    show: show,
                    spinning: playingThisShow && player.isPlaying,
                    showTonearm: true,
                    tonearmDown: playingThisShow && player.isPlaying
                )
                .frame(maxWidth: .infinity)
                .aspectRatio(1.15, contentMode: .fit)
                .padding(.horizontal, 28)
                if playingThisShow {
                    playback
                }
                Text("曲目")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.white.opacity(0.7))
                episodeList
            }
            .padding(18)
        }
        .background(
            LinearGradient(
                colors: [Color(hex: 0x1A1410), Color(hex: 0x0E0C0B)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
        )
        .task(id: show.id) {
            await loadEpisodes()
        }
    }

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text(show.title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.white)
                Text(show.blurb)
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.65))
            }
            Spacer()
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .foregroundStyle(.white.opacity(0.8))
                    .frame(width: 32, height: 32)
            }
            .buttonStyle(.plain)
        }
    }

    private var playback: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(player.title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
                .lineLimit(2)
            if let error = player.errorMessage {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(Color(hex: 0xFF8A80))
            }
            progress
            HStack {
                transportButton("backward.end.fill", action: player.previous)
                Spacer()
                Button(action: player.toggle) {
                    ZStack {
                        Circle().fill(Theme.cyan).frame(width: 56, height: 56)
                        if player.buffering && !player.isPlaying {
                            ProgressView().tint(Theme.onPrimary)
                        } else {
                            Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                                .font(.title2)
                                .foregroundStyle(Theme.onPrimary)
                        }
                    }
                }
                .buttonStyle(.plain)
                Spacer()
                transportButton("forward.end.fill", action: player.next)
            }
            sleepRow
        }
    }

    private var progress: some View {
        VStack(spacing: 4) {
            if player.isLive || player.duration <= 0 {
                HStack {
                    Text("● LIVE")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(Color(hex: 0xFF5252))
                    Spacer()
                    Text("已收听 \(format(player.current))")
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.55))
                }
                .padding(.vertical, 8)
            } else {
                Slider(
                    value: Binding(
                        get: { dragProgress ?? (player.duration > 0 ? player.current / player.duration : 0) },
                        set: { dragProgress = $0 }
                    ),
                    in: 0...1,
                    onEditingChanged: { editing in
                        player.setScrubbing(editing)
                        if !editing, let dragProgress {
                            player.seek(to: dragProgress * player.duration)
                            self.dragProgress = nil
                        }
                    }
                )
                .tint(Theme.cyan)
                HStack {
                    Text(format((dragProgress ?? (player.duration > 0 ? player.current / player.duration : 0)) * player.duration))
                    Spacer()
                    Text(format(player.duration))
                }
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.55))
            }
        }
    }

    private var sleepRow: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "timer")
                    .foregroundStyle(Theme.cyanSoft)
                Text(player.sleepRemaining > 0 ? "定时关闭 · 剩余 \(format(player.sleepRemaining))" : "定时关闭")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.7))
            }
            HStack(spacing: 8) {
                ForEach([0, 15, 30, 45, 60], id: \.self) { minutes in
                    let selected = player.sleepMinutes == minutes
                    Button(minutes == 0 ? "关" : "\(minutes)分") {
                        player.setSleep(minutes: minutes)
                    }
                    .font(.caption.weight(.medium))
                    .foregroundStyle(selected ? Theme.onPrimary : .white.opacity(0.85))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(selected ? Theme.cyan : Color.white.opacity(0.1), in: Capsule())
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @ViewBuilder
    private var episodeList: some View {
        if loading && episodes.isEmpty {
            ProgressView()
                .tint(Theme.cyan)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)
        } else if let loadError, episodes.isEmpty {
            Text(loadError)
                .font(.subheadline)
                .foregroundStyle(Color(hex: 0xFF8A80))
        } else {
            VStack(spacing: 6) {
                ForEach(episodes) { episode in
                    episodeRow(episode)
                }
            }
        }
    }

    private func episodeRow(_ episode: PodcastEpisode) -> some View {
        let active = player.episodeId == episode.id && playingThisShow
        return Button {
            player.play(show: show, episode: episode, queue: episodes)
        } label: {
            HStack(spacing: 10) {
                if active && player.buffering && !player.isPlaying {
                    ProgressView().tint(Theme.cyan).frame(width: 20, height: 20)
                } else {
                    Image(systemName: active && player.isPlaying ? "pause.fill" : "play.fill")
                        .foregroundStyle(active ? Theme.cyan : .white.opacity(0.8))
                        .frame(width: 20)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(episode.title)
                        .font(.subheadline)
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.leading)
                        .lineLimit(2)
                    if !episode.durationLabel.isEmpty {
                        Text(episode.durationLabel)
                            .font(.caption2)
                            .foregroundStyle(.white.opacity(0.5))
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                active ? Theme.cyan.opacity(0.18) : Color.white.opacity(0.06),
                in: RoundedRectangle(cornerRadius: 10, style: .continuous)
            )
        }
        .buttonStyle(.plain)
    }

    private func transportButton(_ systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.title3)
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
    }

    private func loadEpisodes() async {
        loading = true
        loadError = nil
        let loaded = await PodcastRssFetcher.shared.loadEpisodes(for: show)
        episodes = loaded
        loading = false
        if loaded.isEmpty {
            loadError = "暂时无法获取节目，请检查网络后重试"
        }
    }

    private func format(_ seconds: Double) -> String {
        let total = Int(seconds.rounded(.down))
        guard total > 0 else { return "0:00" }
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}
