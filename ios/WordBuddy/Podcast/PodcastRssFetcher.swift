import Foundation

actor PodcastRssFetcher {
    static let shared = PodcastRssFetcher()

    private var cache: [String: (Date, [PodcastEpisode])] = [:]
    private let ttl: TimeInterval = 20 * 60
    private let session: URLSession

    init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 12
        configuration.timeoutIntervalForResource = 18
        session = URLSession(configuration: configuration)
    }

    func loadEpisodes(for show: PodcastShow, limit: Int = 12) async -> [PodcastEpisode] {
        if let cached = cache[show.id],
           Date().timeIntervalSince(cached.0) < ttl,
           !cached.1.isEmpty {
            return Array(cached.1.prefix(limit))
        }
        var remote: [PodcastEpisode] = []
        if let rssUrl = show.rssUrl {
            remote = (try? await fetch(url: rssUrl, showId: show.id, limit: limit)) ?? []
        }
        let result = remote.isEmpty ? Array(show.fallbackEpisodes.prefix(limit)) : remote
        if !result.isEmpty {
            cache[show.id] = (Date(), result)
        }
        return result
    }

    private func fetch(url: String, showId: String, limit: Int) async throws -> [PodcastEpisode] {
        guard let target = URL(string: url) else { return [] }
        var request = URLRequest(url: target)
        request.setValue("WordBuddyPodcast/1.0", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await session.data(for: request)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(code), !data.isEmpty else { return [] }
        let parser = RssEpisodeParser(showId: showId, limit: limit)
        return parser.parse(data)
    }
}

private final class RssEpisodeParser: NSObject, XMLParserDelegate {
    private let showId: String
    private let limit: Int
    private var episodes: [PodcastEpisode] = []
    private var inItem = false
    private var text = ""
    private var title = ""
    private var summary = ""
    private var audioUrl = ""
    private var duration = ""
    private var guid = ""

    init(showId: String, limit: Int) {
        self.showId = showId
        self.limit = limit
    }

    func parse(_ data: Data) -> [PodcastEpisode] {
        let parser = XMLParser(data: data)
        parser.delegate = self
        parser.parse()
        return episodes
    }

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        let name = localName(qName ?? elementName)
        text = ""
        if name == "item" || name == "entry" {
            inItem = true
            title = ""
            summary = ""
            audioUrl = ""
            duration = ""
            guid = ""
            return
        }
        guard inItem else { return }
        if name == "enclosure" || name == "content" {
            let url = attributeDict["url"] ?? ""
            let type = attributeDict["type"] ?? ""
            let medium = attributeDict["medium"] ?? ""
            if audioUrl.isEmpty, isAudio(url, type: type, medium: medium) {
                audioUrl = url
            }
        } else if name == "link" {
            let href = attributeDict["href"] ?? ""
            let rel = attributeDict["rel"] ?? ""
            let type = attributeDict["type"] ?? ""
            if audioUrl.isEmpty, isAudio(href, type: type, rel: rel) {
                audioUrl = href
            }
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        text += string
    }

    func parser(
        _ parser: XMLParser,
        didEndElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        let name = localName(qName ?? elementName)
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard inItem else { return }
        switch name {
        case "title":
            if title.isEmpty { title = value }
        case "description", "summary":
            if summary.isEmpty { summary = stripMarkup(value) }
        case "guid":
            guid = value
        case "duration":
            duration = formatDuration(value)
        case "link":
            if audioUrl.isEmpty, isAudio(value, type: "", medium: "") {
                audioUrl = value
            }
        case "item", "entry":
            flush()
            inItem = false
        default:
            break
        }
        text = ""
    }

    private func flush() {
        let audio = audioUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        let name = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !audio.isEmpty, !name.isEmpty, episodes.count < limit else { return }
        let identity = guid.isEmpty ? String(audio.hashValue) : guid
        episodes.append(PodcastEpisode(
            id: "\(showId)-\(identity)",
            title: name,
            audioUrl: audio,
            summary: String(summary.prefix(180)),
            durationLabel: duration
        ))
    }

    private func localName(_ raw: String) -> String {
        raw.split(separator: ":").last.map { String($0).lowercased() } ?? raw.lowercased()
    }

    private func isAudio(_ url: String, type: String, medium: String = "", rel: String = "") -> Bool {
        let link = url.lowercased()
        guard !link.isEmpty else { return false }
        let kind = type.lowercased()
        return kind.contains("audio")
            || medium.lowercased() == "audio"
            || rel.lowercased().contains("enclosure")
            || link.contains(".mp3")
            || link.contains(".m4a")
            || link.contains(".m3u8")
    }

    private func formatDuration(_ raw: String) -> String {
        if raw.contains(":") { return raw }
        guard let seconds = Int(raw), seconds > 0 else { return raw }
        return String(format: "%d:%02d", seconds / 60, seconds % 60)
    }

    private func stripMarkup(_ raw: String) -> String {
        let withoutTags = raw.replacingOccurrences(
            of: "<[^>]+>",
            with: " ",
            options: .regularExpression
        )
        return withoutTags
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
