import Foundation

enum ShanghaiDate {
    static func todayString(_ date: Date = Date()) -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = TimeZone(identifier: "Asia/Shanghai")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    static func recentDayStrings(count: Int) -> [String] {
        let calendar = Calendar(identifier: .gregorian)
        var shanghai = calendar
        shanghai.timeZone = TimeZone(identifier: "Asia/Shanghai") ?? .current
        let today = Date()
        return (1..<count).compactMap { offset in
            guard let date = shanghai.date(byAdding: .day, value: -offset, to: today) else { return nil }
            return todayString(date)
        }
    }

    static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Shanghai") ?? .current
        return calendar
    }

    static func parseDay(_ text: String) -> Date? {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = TimeZone(identifier: "Asia/Shanghai")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: text)
    }

    static func monthDayStrings(around date: Date = Date()) -> [String] {
        let calendar = calendar
        guard let interval = calendar.dateInterval(of: .month, for: date) else { return [] }
        var days: [String] = []
        var cursor = interval.start
        while cursor < interval.end {
            days.append(todayString(cursor))
            guard let next = calendar.date(byAdding: .day, value: 1, to: cursor) else { break }
            cursor = next
        }
        return days
    }

    static func monthValue(of date: Date = Date()) -> Int {
        calendar.component(.month, from: date)
    }

    static func dayValue(of text: String) -> Int {
        guard let date = parseDay(text) else { return 0 }
        return calendar.component(.day, from: date)
    }

    static func shift(_ text: String, days: Int) -> String {
        guard let date = parseDay(text),
              let next = calendar.date(byAdding: .day, value: days, to: date) else { return text }
        return todayString(next)
    }

    static func days(from start: String, to end: String) -> Int {
        guard let startDate = parseDay(start), let endDate = parseDay(end) else { return 0 }
        return calendar.dateComponents([.day], from: startDate, to: endDate).day ?? 0
    }
}

enum SettingsStore {
    private static let defaults = UserDefaults.standard
    private static let accentKey = "hotwords_accent"
    private static let speakKey = "hotwords_speak_on_page"
    private static let hideKey = "hotwords_hide_definitions"
    private static let imageProviderKey = "hotwords_image_provider"
    private static let accentStyleKey = "hotwords_accent_style"

    static var accent: Accent {
        get { Accent(rawValue: defaults.string(forKey: accentKey) ?? "") ?? .us }
        set { defaults.set(newValue.rawValue, forKey: accentKey) }
    }

    static var speakOnPageChange: Bool {
        get { defaults.object(forKey: speakKey) as? Bool ?? true }
        set { defaults.set(newValue, forKey: speakKey) }
    }

    static var hideDefinitions: Bool {
        get { defaults.object(forKey: hideKey) as? Bool ?? true }
        set { defaults.set(newValue, forKey: hideKey) }
    }

    static var imageProvider: ImageProvider {
        get { ImageProvider(rawValue: defaults.string(forKey: imageProviderKey) ?? "") ?? .pollinations }
        set { defaults.set(newValue.rawValue, forKey: imageProviderKey) }
    }

    static var accentStyle: AccentStyle {
        get { AccentStyle(rawValue: defaults.string(forKey: accentStyleKey) ?? "") ?? .cyberNeon }
        set { defaults.set(newValue.rawValue, forKey: accentStyleKey) }
    }

    static var dailyReminder: Bool {
        get { defaults.object(forKey: "hotwords_daily_reminder") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "hotwords_daily_reminder") }
    }

    static var podcastPlayWhenScreenOff: Bool {
        get { defaults.object(forKey: "hotwords_podcast_screen_off") as? Bool ?? false }
        set { defaults.set(newValue, forKey: "hotwords_podcast_screen_off") }
    }

    static var shortsMetaVisibleDefault: Bool {
        get { defaults.object(forKey: "hotwords_shorts_meta_default") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "hotwords_shorts_meta_default") }
    }

    static var shortVideoLoop: Bool {
        get { defaults.object(forKey: "hotwords_short_video_loop") as? Bool ?? false }
        set { defaults.set(newValue, forKey: "hotwords_short_video_loop") }
    }

    static var biometricLogin: Bool {
        get { defaults.object(forKey: "hotwords_biometric_login") as? Bool ?? false }
        set { defaults.set(newValue, forKey: "hotwords_biometric_login") }
    }

    static var defaultNotebookId: Int64 {
        get { Int64(defaults.integer(forKey: "hotwords_default_notebook_id")) }
        set { defaults.set(Int(newValue), forKey: "hotwords_default_notebook_id") }
    }
}

struct RememberedAccount: Identifiable, Equatable {
    var session: UserSession
    var lastUsedAt: TimeInterval

    var id: Int64 { session.userId }
}

enum AccountStore {
    private static let key = "hotwords_accounts"
    private static let defaults = UserDefaults.standard
    private static let maxAccounts = 8

    static func list() -> [RememberedAccount] {
        guard let data = defaults.data(forKey: key),
              let raw = try? JSONDecoder().decode([StoredAccount].self, from: data) else {
            return []
        }
        return raw
            .filter { $0.userId > 0 && !$0.token.isEmpty }
            .map { stored in
                RememberedAccount(
                    session: UserSession(
                        token: stored.token,
                        userId: stored.userId,
                        phone: stored.phone,
                        vocabNotebookId: stored.vocabNotebookId,
                        avatarUrl: stored.avatarUrl,
                        level: stored.level,
                        nickname: stored.nickname,
                        buddyId: stored.buddyId,
                        signature: stored.signature,
                        gender: stored.gender,
                        region: stored.region,
                        email: stored.email,
                        shippingName: stored.shippingName,
                        shippingPhone: stored.shippingPhone,
                        shippingDetail: stored.shippingDetail,
                        alipayAccount: stored.alipayAccount,
                        alipayName: stored.alipayName,
                        wechatAccount: stored.wechatAccount,
                        networkRegion: stored.networkRegion,
                        networkRegionDetail: stored.networkRegionDetail
                    ),
                    lastUsedAt: stored.lastUsedAt
                )
            }
            .sorted { $0.lastUsedAt > $1.lastUsedAt }
    }

    static func upsert(_ session: UserSession) {
        guard session.userId > 0, !session.token.isEmpty else { return }
        var accounts = list().filter { $0.session.userId != session.userId }
        accounts.insert(RememberedAccount(session: session, lastUsedAt: Date().timeIntervalSince1970), at: 0)
        save(Array(accounts.prefix(maxAccounts)))
    }

    static func remove(userId: Int64) {
        save(list().filter { $0.session.userId != userId })
    }

    private static func save(_ accounts: [RememberedAccount]) {
        let stored = accounts.map { account in
            StoredAccount(
                token: account.session.token,
                userId: account.session.userId,
                phone: account.session.phone,
                vocabNotebookId: account.session.vocabNotebookId,
                avatarUrl: account.session.avatarUrl,
                level: account.session.level,
                nickname: account.session.nickname,
                buddyId: account.session.buddyId,
                signature: account.session.signature,
                gender: account.session.gender,
                region: account.session.region,
                email: account.session.email,
                shippingName: account.session.shippingName,
                shippingPhone: account.session.shippingPhone,
                shippingDetail: account.session.shippingDetail,
                alipayAccount: account.session.alipayAccount,
                alipayName: account.session.alipayName,
                wechatAccount: account.session.wechatAccount,
                networkRegion: account.session.networkRegion,
                networkRegionDetail: account.session.networkRegionDetail,
                lastUsedAt: account.lastUsedAt
            )
        }
        if let data = try? JSONEncoder().encode(stored) {
            defaults.set(data, forKey: key)
        }
    }

    private struct StoredAccount: Codable {
        var token: String
        var userId: Int64
        var phone: String
        var vocabNotebookId: Int64
        var avatarUrl: String?
        var level: Int
        var nickname: String?
        var buddyId: String?
        var signature: String?
        var gender: String?
        var region: String?
        var email: String?
        var shippingName: String?
        var shippingPhone: String?
        var shippingDetail: String?
        var alipayAccount: String?
        var alipayName: String?
        var wechatAccount: String?
        var networkRegion: String?
        var networkRegionDetail: String?
        var lastUsedAt: TimeInterval
    }
}
