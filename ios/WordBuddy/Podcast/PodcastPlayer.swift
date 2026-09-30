import AVFoundation
import Foundation

@MainActor
final class PodcastPlayer: ObservableObject {
    @Published var showId: String?
    @Published var episodeId: String?
    @Published var title = ""
    @Published var isPlaying = false
    @Published var buffering = false
    @Published var isLive = false
    @Published var current: Double = 0
    @Published var duration: Double = 0
    @Published var sleepMinutes = 0
    @Published var sleepRemaining: TimeInterval = 0
    @Published var errorMessage: String?
    @Published private(set) var scrubbing = false

    private var player: AVPlayer?
    private var queue: [PodcastEpisode] = []
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var statusObserver: NSKeyValueObservation?
    private var timeControlObserver: NSKeyValueObservation?
    private var sleepTask: Task<Void, Never>?

    func play(show: PodcastShow, episode: PodcastEpisode, queue: [PodcastEpisode]) {
        guard let url = URL(string: episode.audioUrl) else {
            errorMessage = "音频地址无效"
            return
        }
        self.queue = queue.isEmpty ? [episode] : queue
        showId = show.id
        episodeId = episode.id
        title = episode.title
        isLive = show.isLive
        errorMessage = nil
        current = 0
        duration = 0
        let avPlayer = ensurePlayer()
        attach(item: AVPlayerItem(url: url), to: avPlayer)
        activateAudio()
        avPlayer.play()
    }

    func toggle() {
        guard let player else { return }
        if isPlaying {
            player.pause()
        } else {
            activateAudio()
            player.play()
        }
    }

    func pause() {
        player?.pause()
    }

    func seek(to seconds: Double) {
        guard !isLive, let player else { return }
        let time = CMTime(seconds: max(0, seconds), preferredTimescale: 600)
        player.seek(to: time, toleranceBefore: .zero, toleranceAfter: .zero)
        current = max(0, seconds)
    }

    func setScrubbing(_ scrubbing: Bool) {
        self.scrubbing = scrubbing
    }

    func next() {
        guard let episodeId, let index = queue.firstIndex(where: { $0.id == episodeId }) else { return }
        let nextIndex = index + 1
        guard nextIndex < queue.count, let showId, let show = PodcastCatalog.shows.first(where: { $0.id == showId }) else { return }
        play(show: show, episode: queue[nextIndex], queue: queue)
    }

    func previous() {
        guard let player else { return }
        if !isLive, current > 3 {
            seek(to: 0)
            return
        }
        guard let episodeId, let index = queue.firstIndex(where: { $0.id == episodeId }), index > 0,
              let showId, let show = PodcastCatalog.shows.first(where: { $0.id == showId }) else {
            seek(to: 0)
            return
        }
        play(show: show, episode: queue[index - 1], queue: queue)
    }

    func setSleep(minutes: Int) {
        sleepTask?.cancel()
        sleepMinutes = minutes
        guard minutes > 0 else {
            sleepRemaining = 0
            return
        }
        let end = Date().addingTimeInterval(TimeInterval(minutes * 60))
        sleepTask = Task { [weak self] in
            while !Task.isCancelled {
                let left = end.timeIntervalSinceNow
                if left <= 0 {
                    self?.pause()
                    self?.sleepMinutes = 0
                    self?.sleepRemaining = 0
                    return
                }
                self?.sleepRemaining = left
                try? await Task.sleep(nanoseconds: 1_000_000_000)
            }
        }
    }

    private func ensurePlayer() -> AVPlayer {
        if let player { return player }
        let player = AVPlayer()
        self.player = player
        timeControlObserver = player.observe(\.timeControlStatus, options: [.initial, .new]) { [weak self] player, _ in
            Task { @MainActor in
                self?.isPlaying = player.timeControlStatus == .playing
                self?.buffering = player.timeControlStatus == .waitingToPlayAtSpecifiedRate
            }
        }
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            Task { @MainActor in
                guard let self, !self.scrubbing else { return }
                self.current = time.seconds.isFinite ? max(0, time.seconds) : 0
                let length = player.currentItem?.duration.seconds ?? 0
                self.duration = length.isFinite ? max(0, length) : 0
            }
        }
        return player
    }

    private func attach(item: AVPlayerItem, to player: AVPlayer) {
        if let endObserver {
            NotificationCenter.default.removeObserver(endObserver)
        }
        statusObserver?.invalidate()
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.next()
            }
        }
        statusObserver = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            Task { @MainActor in
                if item.status == .failed {
                    self?.errorMessage = item.error?.localizedDescription ?? "暂时无法播放"
                    self?.buffering = false
                }
            }
        }
        player.replaceCurrentItem(with: item)
    }

    private func activateAudio() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .spokenAudio)
        try? session.setActive(true)
    }
}
