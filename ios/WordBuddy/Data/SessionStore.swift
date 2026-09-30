import Foundation

enum SessionStore {
    private static let key = "hotwords_session"
    private static let defaults = UserDefaults.standard

    static func load() -> UserSession? {
        guard let data = defaults.data(forKey: key) else { return nil }
        guard let session = try? JSONDecoder().decode(UserSession.self, from: data) else { return nil }
        guard !session.token.isEmpty, session.userId > 0 else { return nil }
        return session
    }

    static func save(_ session: UserSession) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        defaults.set(data, forKey: key)
    }

    static func clear() {
        defaults.removeObject(forKey: key)
    }
}
