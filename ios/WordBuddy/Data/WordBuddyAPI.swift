import Foundation
import UIKit

struct DeviceInfo: Sendable {
    var model: String
    var systemVersion: String
    var machine: String

    @MainActor
    static func current() -> DeviceInfo {
        DeviceInfo(
            model: UIDevice.current.model,
            systemVersion: UIDevice.current.systemVersion,
            machine: machineIdentifier()
        )
    }

    private static func machineIdentifier() -> String {
        var systemInfo = utsname()
        uname(&systemInfo)
        let identifier = Mirror(reflecting: systemInfo.machine).children.reduce(into: "") { result, element in
            guard let value = element.value as? Int8, value != 0 else { return }
            result.append(Character(UnicodeScalar(UInt8(value))))
        }
        return identifier.isEmpty ? "iPhone" : identifier
    }
}

actor WordBuddyAPI {
    static let primaryBase = "http://47.95.111.238"
    static let fallbackBase = "http://47.95.111.238:8787"

    private let bases = [primaryBase, fallbackBase]
    private var baseURL = primaryBase
    private let device: DeviceInfo
    private let session: URLSession
    private let longSession: URLSession
    private let appVersion = "1.0"

    init(device: DeviceInfo) {
        self.device = device
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 15
        configuration.timeoutIntervalForResource = 20
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        session = URLSession(configuration: configuration)
        let long = URLSessionConfiguration.ephemeral
        long.timeoutIntervalForRequest = 90
        long.timeoutIntervalForResource = 120
        long.requestCachePolicy = .reloadIgnoringLocalCacheData
        longSession = URLSession(configuration: long)
    }

    func sendCode(phone: String) async throws -> String? {
        let root = try await request(
            method: "POST",
            path: "/auth/send-code",
            auth: nil,
            body: ["phone": phone]
        )
        return JSONValue.string(root, key: "debugCode")
    }

    func login(phone: String, code: String) async throws -> AuthResult {
        try parseAuth(await request(
            method: "POST",
            path: "/auth/login",
            auth: nil,
            body: ["phone": phone, "code": code]
        ))
    }

    func loginWithPassword(phone: String, password: String) async throws -> AuthResult {
        try parseAuth(await request(
            method: "POST",
            path: "/auth/login",
            auth: nil,
            body: ["phone": phone, "password": password]
        ))
    }

    func register(phone: String, code: String, password: String, inviteCode: String?) async throws -> AuthResult {
        var body: [String: Any] = [
            "phone": phone,
            "code": code,
            "password": password,
        ]
        let invite = inviteCode?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? ""
        if !invite.isEmpty {
            body["inviteCode"] = invite
        }
        return try parseAuth(await request(
            method: "POST",
            path: "/auth/register",
            auth: nil,
            body: body
        ))
    }

    func loginWithWechat(code: String) async throws -> AuthResult {
        try parseAuth(await request(
            method: "POST",
            path: "/auth/wechat",
            auth: nil,
            body: ["code": code]
        ))
    }

    func fetchAlipayLoginAuthInfo() async throws -> String {
        let root = try await request(
            method: "POST",
            path: "/auth/alipay/auth-info",
            auth: nil,
            body: [:]
        )
        let info = JSONValue.string(root, key: "authInfo") ?? ""
        if info.isEmpty {
            throw APIError(message: "无法发起支付宝登录")
        }
        return info
    }

    func loginWithAlipay(authCode: String) async throws -> AuthResult {
        try parseAuth(await request(
            method: "POST",
            path: "/auth/alipay",
            auth: nil,
            body: ["authCode": authCode]
        ))
    }

    func bindPhone(token: String, phone: String, code: String, password: String?) async throws -> AuthResult {
        var body: [String: Any] = ["phone": phone, "code": code]
        let trimmed = password?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmed.isEmpty {
            body["password"] = trimmed
        }
        return try parseAuth(await request(
            method: "POST",
            path: "/auth/bind-phone",
            auth: token,
            body: body
        ))
    }

    func createNotebook(token: String, name: String) async throws -> Notebook {
        let root = try await request(
            method: "POST",
            path: "/notebooks",
            auth: token,
            body: ["name": name]
        )
        guard let item = root["item"] as? [String: Any] else {
            throw APIError(message: "创建词本失败")
        }
        return parseNotebook(item)
    }

    func deleteNotebook(token: String, id: Int64) async throws {
        _ = try await request(method: "DELETE", path: "/notebooks/\(id)", auth: token, body: nil)
    }

    func deleteWord(token: String, id: Int64) async throws {
        _ = try await request(method: "DELETE", path: "/words/\(id)", auth: token, body: nil)
    }

    func listNotebooks(token: String) async throws -> [Notebook] {
        let root = try await request(method: "GET", path: "/notebooks", auth: token, body: nil)
        return JSONValue.array(root, key: "items").compactMap { item in
            (item as? [String: Any]).map(parseNotebook)
        }
    }

    func listWords(
        token: String,
        notebookId: Int64,
        cursor: String?,
        limit: Int = 100,
        fromIndex: Int = 0
    ) async throws -> WordPage {
        var path = "/notebooks/\(notebookId)/words?limit=\(limit)"
        if let cursor, !cursor.isEmpty {
            let allowed = CharacterSet.urlQueryAllowed
            let encoded = cursor.addingPercentEncoding(withAllowedCharacters: allowed) ?? cursor
            path += "&cursor=\(encoded)"
        } else if fromIndex > 0 {
            path += "&fromIndex=\(fromIndex)"
        }
        let root = try await request(method: "GET", path: path, auth: token, body: nil)
        let items = JSONValue.array(root, key: "items").compactMap { item in
            (item as? [String: Any]).map(parseWord)
        }
        return WordPage(
            items: items,
            total: JSONValue.int(root, key: "total"),
            nextCursor: JSONValue.string(root, key: "nextCursor"),
            fromIndex: JSONValue.int(root, key: "fromIndex", default: fromIndex)
        )
    }

    /// Absolute 0-based index of the first word for each initial letter (A–Z / #).
    func letterIndex(token: String, notebookId: Int64) async throws -> [Character: Int] {
        let root = try await request(method: "GET", path: "/notebooks/\(notebookId)/letter-index", auth: token, body: nil)
        guard let index = root["index"] as? [String: Any] else { return [:] }
        var map: [Character: Int] = [:]
        for (key, value) in index {
            guard let ch = key.uppercased().first else { continue }
            let n: Int
            if let i = value as? Int {
                n = i
            } else if let i = value as? NSNumber {
                n = i.intValue
            } else {
                continue
            }
            map[ch] = n
        }
        return map
    }

    /// Every word in notebook order (id, text, IPA). Definitions are not included.
    func listHeads(token: String, notebookId: Int64) async throws -> [WordHead] {
        let root = try await request(method: "GET", path: "/notebooks/\(notebookId)/heads", auth: token, body: nil)
        return JSONValue.array(root, key: "items").compactMap { item in
            guard let object = item as? [String: Any] else { return nil }
            let text = JSONValue.string(object, key: "text") ?? ""
            if text.isEmpty { return nil }
            return WordHead(
                id: JSONValue.int64(object, key: "id"),
                text: text,
                isPhrase: JSONValue.bool(object, key: "isPhrase"),
                ipaUk: JSONValue.string(object, key: "ipaUk"),
                ipaUs: JSONValue.string(object, key: "ipaUs"),
                sortOrder: JSONValue.int(object, key: "sortOrder")
            )
        }
    }

    func fetchShortsFeed(token: String?, limit: Int = 20, excludeIds: [String] = []) async throws -> [ShortClip] {
        var path = "/shorts/feed?limit=\(limit)"
        let exclude = excludeIds
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: ",")
        if !exclude.isEmpty {
            let encoded = exclude.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? exclude
            path += "&exclude=\(encoded)"
        }
        let root = try await request(method: "GET", path: path, auth: token, body: nil)
        return JSONValue.array(root, key: "items").compactMap { item in
            (item as? [String: Any]).map(parseShortClip)
        }
    }

    func setShortFavorite(token: String, videoId: String, favorited: Bool) async throws -> Bool {
        let root = try await request(
            method: "POST",
            path: "/shorts/\(pathComponent(videoId))/favorite",
            auth: token,
            body: ["favorited": favorited]
        )
        return JSONValue.bool(root, key: "favorited", default: favorited)
    }

    func reportShortWatch(token: String?, videoId: String, watchMs: Int, completed: Bool) async throws {
        _ = try await request(
            method: "POST",
            path: "/shorts/\(pathComponent(videoId))/watch",
            auth: token,
            body: [
                "watchMs": max(0, watchMs),
                "completed": completed,
            ]
        )
    }

    func createWord(token: String, notebookId: Int64, entry: VocabEntry) async throws -> VocabEntry {
        let root = try await request(
            method: "POST",
            path: "/notebooks/\(notebookId)/words",
            auth: token,
            body: wordBody(entry)
        )
        guard let item = root["item"] as? [String: Any] else {
            throw APIError(message: "保存失败")
        }
        return parseWord(item)
    }

    func updateWord(token: String, id: Int64, definitions: [Definition]) async throws -> VocabEntry {
        let body: [String: Any] = [
            "definitions": definitions.map { definition in
                [
                    "pos": definition.pos,
                    "meaning": definition.meaning,
                    "user": definition.isUserAdded,
                ] as [String: Any]
            },
        ]
        let root = try await request(method: "PATCH", path: "/words/\(id)", auth: token, body: body)
        guard let item = root["item"] as? [String: Any] else {
            throw APIError(message: "保存释义失败")
        }
        return parseWord(item)
    }

    private func parseShortClip(_ object: [String: Any]) -> ShortClip {
        let keywords = parseStringList(object["keywords"])
        let related = parseStringList(object["relatedWords"])
        let words = (keywords.isEmpty ? related : keywords).prefix(20).map { $0 }
        return ShortClip(
            id: JSONValue.string(object, key: "id") ?? "",
            videoUrl: absoluteMediaURL(JSONValue.string(object, key: "videoUrl")) ?? "",
            title: JSONValue.string(object, key: "title") ?? "",
            author: JSONValue.string(object, key: "author") ?? "词搭子",
            caption: JSONValue.string(object, key: "caption") ?? "",
            relatedWords: Array(words),
            category: JSONValue.string(object, key: "category") ?? "speaking",
            categoryName: JSONValue.string(object, key: "categoryName") ?? "口语",
            coverUrl: absoluteMediaURL(JSONValue.string(object, key: "coverUrl")),
            favorited: JSONValue.bool(object, key: "favorited")
        )
    }

    private func absoluteMediaURL(_ raw: String?) -> String? {
        guard let raw, !raw.isEmpty else { return nil }
        if raw.hasPrefix("http://") || raw.hasPrefix("https://") { return raw }
        if raw.hasPrefix("/") { return baseURL + raw }
        return raw
    }

    private func pathComponent(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? value
    }

    private func parseAuth(_ root: [String: Any]) throws -> AuthResult {
        let isNewUser = JSONValue.bool(root, key: "isNewUser")
        let needsPhoneBind = JSONValue.bool(root, key: "needsPhoneBind")
        guard let token = JSONValue.string(root, key: "token"),
              let user = root["user"] as? [String: Any] else {
            return AuthResult(session: nil, isNewUser: isNewUser, needsPhoneBind: needsPhoneBind)
        }
        let session = parseUserSession(token: token, root: root, user: user)
        return AuthResult(
            session: session,
            isNewUser: isNewUser,
            needsPhoneBind: needsPhoneBind || session.phone.isEmpty
        )
    }

    private func parseUserSession(token: String, root: [String: Any], user: [String: Any]) -> UserSession {
        let level = min(7, max(0, JSONValue.int(user, key: "level", default: JSONValue.int(root, key: "level"))))
        return UserSession(
            token: token,
            userId: JSONValue.int64(user, key: "id"),
            phone: JSONValue.string(user, key: "phone") ?? "",
            vocabNotebookId: JSONValue.int64(root, key: "vocabNotebookId"),
            avatarUrl: JSONValue.string(root, key: "avatarUrl") ?? JSONValue.string(user, key: "avatarUrl"),
            level: level,
            nickname: JSONValue.string(user, key: "nickname"),
            buddyId: JSONValue.string(user, key: "buddyId"),
            signature: JSONValue.string(user, key: "signature"),
            gender: JSONValue.string(user, key: "gender"),
            region: JSONValue.string(user, key: "region"),
            email: JSONValue.string(user, key: "email"),
            shippingName: JSONValue.string(user, key: "shippingName"),
            shippingPhone: JSONValue.string(user, key: "shippingPhone"),
            shippingDetail: JSONValue.string(user, key: "shippingDetail"),
            alipayAccount: JSONValue.string(user, key: "alipayAccount"),
            alipayName: JSONValue.string(user, key: "alipayName"),
            wechatAccount: JSONValue.string(user, key: "wechatAccount"),
            networkRegion: JSONValue.string(user, key: "networkRegion"),
            networkRegionDetail: JSONValue.string(user, key: "networkRegionDetail")
        )
    }

    func uploadAvatar(token: String, jpegData: Data) async throws -> String {
        let encoded = jpegData.base64EncodedString(options: [])
        let root = try await request(
            method: "POST",
            path: "/me/avatar",
            auth: token,
            body: ["imageBase64": encoded],
            long: true
        )
        guard let url = JSONValue.string(root, key: "avatarUrl"), !url.isEmpty else {
            throw APIError(message: "上传失败")
        }
        return url
    }

    func absoluteURL(for path: String) -> URL? {
        let trimmed = path.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://") {
            return URL(string: trimmed)
        }
        let base = baseURL.hasSuffix("/") ? String(baseURL.dropLast()) : baseURL
        if trimmed.hasPrefix("/") {
            return URL(string: base + trimmed)
        }
        return URL(string: base + "/" + trimmed)
    }

    func fetchAvatarData(avatarUrl: String) async throws -> Data {
        guard let url = absoluteURL(for: avatarUrl) else {
            throw APIError(message: "头像地址无效")
        }
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        var items = components?.queryItems ?? []
        items.append(URLQueryItem(name: "t", value: String(Int(Date().timeIntervalSince1970 * 1000))))
        components?.queryItems = items
        guard let stamped = components?.url else {
            throw APIError(message: "头像地址无效")
        }
        var request = URLRequest(url: stamped)
        request.httpMethod = "GET"
        request.setValue("image/*", forHTTPHeaderField: "Accept")
        applyDeviceHeaders(&request)
        let (data, response) = try await session.data(for: request)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(code), !data.isEmpty else {
            throw APIError(message: "头像加载失败", httpCode: code)
        }
        return data
    }

    func refreshNetworkRegion(token: String) async throws -> UserSession {
        let root = try await request(method: "POST", path: "/me/network-region/refresh", auth: token, body: [:])
        if let user = root["user"] as? [String: Any] {
            return parseUserSession(token: token, root: root, user: user)
        }
        return try await fetchMe(token: token)
    }

    func fetchShortFavorites(token: String) async throws -> [ShortClip] {
        let root = try await request(method: "GET", path: "/me/short-favorites?page=1&pageSize=40", auth: token, body: nil)
        return JSONValue.array(root, key: "items").compactMap { item in
            guard let object = item as? [String: Any] else { return nil }
            let id = JSONValue.string(object, key: "id") ?? ""
            let videoUrl = JSONValue.string(object, key: "videoUrl") ?? ""
            guard !id.isEmpty, !videoUrl.isEmpty else { return nil }
            let keywords = (object["keywords"] as? [Any] ?? object["relatedWords"] as? [Any] ?? [])
                .compactMap { ($0 as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            return ShortClip(
                id: id,
                videoUrl: videoUrl,
                title: JSONValue.string(object, key: "title") ?? "",
                author: JSONValue.string(object, key: "author") ?? "词搭子",
                caption: JSONValue.string(object, key: "caption") ?? "",
                relatedWords: keywords,
                category: JSONValue.string(object, key: "category") ?? "speaking",
                categoryName: JSONValue.string(object, key: "categoryName") ?? "口语",
                coverUrl: JSONValue.string(object, key: "coverUrl"),
                favorited: JSONValue.bool(object, key: "favorited", default: true)
            )
        }
    }

    func fetchMe(token: String) async throws -> UserSession {
        let root = try await request(method: "GET", path: "/me", auth: token, body: nil)
        guard let user = root["user"] as? [String: Any] else {
            throw APIError(message: "资料加载失败")
        }
        return parseUserSession(token: token, root: root, user: user)
    }

    func updateProfile(
        token: String,
        nickname: String? = nil,
        signature: String? = nil,
        gender: String? = nil,
        region: String? = nil,
        email: String? = nil,
        alipayAccount: String? = nil,
        alipayName: String? = nil,
        wechatAccount: String? = nil,
        shippingName: String? = nil,
        shippingPhone: String? = nil,
        shippingDetail: String? = nil
    ) async throws -> UserSession {
        var body: [String: Any] = [:]
        if let nickname { body["nickname"] = nickname }
        if let signature { body["signature"] = signature }
        if let gender { body["gender"] = gender }
        if let region { body["region"] = region }
        if let email { body["email"] = email }
        if let alipayAccount { body["alipayAccount"] = alipayAccount }
        if let alipayName { body["alipayName"] = alipayName }
        if let wechatAccount { body["wechatAccount"] = wechatAccount }
        if shippingName != nil || shippingPhone != nil || shippingDetail != nil {
            body["shipping"] = [
                "name": shippingName ?? "",
                "phone": shippingPhone ?? "",
                "detail": shippingDetail ?? "",
            ]
        }
        let root = try await request(method: "PATCH", path: "/me", auth: token, body: body)
        guard let user = root["user"] as? [String: Any] else {
            throw APIError(message: "保存资料失败")
        }
        return parseUserSession(token: token, root: root, user: user)
    }

    func changePassword(token: String, oldPassword: String, newPassword: String) async throws {
        _ = try await request(
            method: "POST",
            path: "/auth/change-password",
            auth: token,
            body: ["oldPassword": oldPassword, "newPassword": newPassword]
        )
    }

    func fetchCheckIn(token: String) async throws -> CheckInState {
        let root = try await request(method: "GET", path: "/me/checkin", auth: token, body: nil)
        return parseCheckIn(JSONValue.childObject(root, key: "checkIn") ?? [:])
    }

    func performCheckIn(token: String) async throws -> CheckInOutcome {
        let root = try await request(method: "POST", path: "/me/checkin", auth: token, body: [:])
        let state = parseCheckIn(JSONValue.childObject(root, key: "checkIn") ?? [:])
        let already = JSONValue.bool(root, key: "already") || !JSONValue.bool(root, key: "ok", default: true)
        return CheckInOutcome(
            already: already,
            pointsEarned: JSONValue.int(root, key: "pointsEarned", default: state.todayReward),
            streakDays: JSONValue.int(root, key: "streakDays", default: state.streakDays),
            totalPoints: JSONValue.int(root, key: "totalPoints", default: state.totalPoints),
            state: state
        )
    }

    func makeupCheckIn(token: String, date: String) async throws -> CheckInOutcome {
        let root = try await request(
            method: "POST",
            path: "/me/checkin/makeup",
            auth: token,
            body: ["date": date]
        )
        if !JSONValue.bool(root, key: "ok") {
            if JSONValue.bool(root, key: "already") {
                let state = try await fetchCheckIn(token: token)
                return CheckInOutcome(already: true, pointsEarned: 0, streakDays: state.streakDays, totalPoints: state.totalPoints, state: state)
            }
            throw APIError(message: JSONValue.string(root, key: "error") ?? "补签失败")
        }
        let state = try await fetchCheckIn(token: token)
        return CheckInOutcome(
            already: false,
            pointsEarned: JSONValue.int(root, key: "pointsEarned", default: 1),
            streakDays: JSONValue.int(root, key: "streakDays"),
            totalPoints: JSONValue.int(root, key: "totalPoints", default: state.totalPoints),
            state: state
        )
    }

    func fetchAccountDeletion(token: String) async throws -> AccountDeletionStatus {
        let root = try await request(method: "GET", path: "/me/deletion", auth: token, body: nil)
        return parseDeletion(root)
    }

    func sendDeletionCode(token: String, force: Bool) async throws -> String? {
        let root = try await request(
            method: "POST",
            path: "/me/deletion/send-code",
            auth: token,
            body: ["force": force]
        )
        return JSONValue.string(root, key: "debugCode")
    }

    func requestAccountDeletion(token: String, code: String, reason: String?, force: Bool) async throws -> AccountDeletionStatus {
        var body: [String: Any] = [
            "agreed": true,
            "code": code,
            "force": force,
            "forceAgreed": force,
        ]
        if let reason, !reason.isEmpty {
            body["reason"] = reason
        }
        let root = try await request(method: "POST", path: "/me/deletion", auth: token, body: body)
        return parseDeletion(root)
    }

    func cancelAccountDeletion(token: String) async throws -> AccountDeletionStatus {
        let root = try await request(method: "POST", path: "/me/deletion/cancel", auth: token, body: [:])
        return parseDeletion(root)
    }

    private func parseCheckIn(_ object: [String: Any]) -> CheckInState {
        let recent = JSONValue.array(object, key: "recentDates").compactMap { item -> String? in
            if let text = item as? String {
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                return trimmed.isEmpty ? nil : trimmed
            }
            return nil
        }
        let today = ShanghaiDate.todayString()
        return CheckInState(
            totalPoints: JSONValue.int(object, key: "totalPoints"),
            streakDays: JSONValue.int(object, key: "streakDays"),
            lastCheckInDate: JSONValue.string(object, key: "lastCheckInDate"),
            checkedInToday: JSONValue.bool(object, key: "checkedInToday") || recent.contains(today),
            todayReward: max(1, JSONValue.int(object, key: "todayReward", default: 1)),
            recentDates: recent
        )
    }

    private func parseDeletion(_ object: [String: Any]) -> AccountDeletionStatus {
        let conditions = JSONValue.array(object, key: "conditions").compactMap { item -> DeletionCondition? in
            guard let row = item as? [String: Any] else { return nil }
            return DeletionCondition(
                key: JSONValue.string(row, key: "key") ?? "",
                title: JSONValue.string(row, key: "title") ?? "",
                ok: JSONValue.bool(row, key: "ok"),
                detail: JSONValue.string(row, key: "detail") ?? ""
            )
        }
        return AccountDeletionStatus(
            pending: JSONValue.bool(object, key: "pending"),
            cooldownDays: JSONValue.int(object, key: "cooldownDays", default: 7),
            dueAtLabel: JSONValue.string(object, key: "dueAtLabel") ?? "",
            reason: JSONValue.string(object, key: "reason") ?? "",
            allPassed: JSONValue.bool(object, key: "allPassed"),
            remainingPoints: JSONValue.int(object, key: "remainingPoints"),
            phoneMasked: JSONValue.string(object, key: "phoneMasked") ?? "",
            conditions: conditions,
            immediate: JSONValue.bool(object, key: "immediate"),
            deleted: JSONValue.bool(object, key: "deleted")
        )
    }

    private func parseNotebook(_ object: [String: Any]) -> Notebook {
        Notebook(
            id: JSONValue.int64(object, key: "id"),
            name: JSONValue.string(object, key: "name") ?? Notebook.defaultName,
            sortOrder: JSONValue.int(object, key: "sortOrder"),
            createdAtMillis: JSONValue.int64(object, key: "createdAtMillis"),
            kind: JSONValue.string(object, key: "kind") ?? Notebook.kindUser,
            slug: JSONValue.string(object, key: "slug"),
            wordCount: JSONValue.int(object, key: "wordCount")
        )
    }

    private func parseWord(_ object: [String: Any]) -> VocabEntry {
        VocabEntry(
            id: JSONValue.int64(object, key: "id"),
            notebookId: JSONValue.int64(object, key: "notebookId"),
            text: JSONValue.string(object, key: "text") ?? "",
            isPhrase: JSONValue.bool(object, key: "isPhrase"),
            ipaUk: JSONValue.string(object, key: "ipaUk"),
            ipaUs: JSONValue.string(object, key: "ipaUs"),
            definitions: parseDefinitions(object["definitions"]),
            examples: parseExamples(object["examples"]),
            nearWords: parseStringList(object["nearWords"]),
            synonyms: parseStringList(object["synonyms"]),
            antonyms: parseStringList(object["antonyms"]),
            sortOrder: JSONValue.int(object, key: "sortOrder"),
            addedAtMillis: JSONValue.int64(object, key: "addedAtMillis")
        )
    }

    private func parseDefinitions(_ raw: Any?) -> [Definition] {
        let items = raw as? [Any] ?? []
        return items.compactMap { item in
            guard let object = item as? [String: Any] else { return nil }
            let meaning = JSONValue.string(object, key: "meaning") ?? ""
            if meaning.isEmpty { return nil }
            return Definition(
                pos: JSONValue.string(object, key: "pos") ?? "",
                meaning: meaning,
                isUserAdded: JSONValue.bool(object, key: "user")
            )
        }
    }

    private func parseExamples(_ raw: Any?) -> [ExampleSentence] {
        let items = raw as? [Any] ?? []
        return items.compactMap { item in
            guard let object = item as? [String: Any] else { return nil }
            let english = JSONValue.string(object, key: "english") ?? JSONValue.string(object, key: "en") ?? ""
            let chinese = JSONValue.string(object, key: "chinese") ?? JSONValue.string(object, key: "zh") ?? ""
            guard !english.isEmpty, !chinese.isEmpty else { return nil }
            return ExampleSentence(english: english, chinese: chinese)
        }
    }

    private func parseStringList(_ raw: Any?) -> [String] {
        (raw as? [Any] ?? []).compactMap { item in
            if let text = item as? String {
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                return trimmed.isEmpty ? nil : trimmed
            }
            return nil
        }
    }

    private func wordBody(_ entry: VocabEntry) -> [String: Any] {
        var body: [String: Any] = [
            "text": entry.text,
            "isPhrase": entry.isPhrase,
            "definitions": entry.definitions.map { definition in
                [
                    "pos": definition.pos,
                    "meaning": definition.meaning,
                    "user": definition.isUserAdded,
                ] as [String: Any]
            },
            "examples": entry.examples.map { example in
                [
                    "english": example.english,
                    "chinese": example.chinese,
                ]
            },
            "nearWords": entry.nearWords,
            "synonyms": entry.synonyms,
            "antonyms": entry.antonyms,
        ]
        if let ipaUk = entry.ipaUk { body["ipaUk"] = ipaUk }
        if let ipaUs = entry.ipaUs { body["ipaUs"] = ipaUs }
        return body
    }

    func fetchInviteInfo(token: String) async throws -> InviteInfo {
        let root = try await request(method: "GET", path: "/me/invite", auth: token, body: nil)
        let rewards = root["rewards"] as? [String: Any] ?? [:]
        return InviteInfo(
            canBindInvite: JSONValue.bool(root, key: "canBindInvite", default: true),
            invitedByBuddyId: JSONValue.string(root, key: "invitedByBuddyId"),
            inviteeReward: JSONValue.int(rewards, key: "inviteePoints", default: 10),
            inviterReward: JSONValue.int(rewards, key: "inviterPoints", default: 20),
            invitedCount: JSONValue.int(root, key: "invitedCount")
        )
    }

    func bindInviteCode(token: String, inviteCode: String) async throws -> (message: String, totalPoints: Int) {
        let root = try await request(
            method: "POST",
            path: "/me/invite/bind",
            auth: token,
            body: ["inviteCode": inviteCode.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()]
        )
        return (
            JSONValue.string(root, key: "message") ?? "邀请码已填写",
            JSONValue.int(root, key: "totalPoints")
        )
    }

    func listGiftCategories() async throws -> [GiftCategory] {
        let root = try await request(method: "GET", path: "/gifts/categories", auth: nil, body: nil)
        return JSONValue.array(root, key: "items").compactMap { item in
            guard let object = item as? [String: Any] else { return nil }
            let id = JSONValue.string(object, key: "id") ?? ""
            guard !id.isEmpty else { return nil }
            return GiftCategory(id: id, name: JSONValue.string(object, key: "name") ?? id)
        }
    }

    func listGifts(category: String, page: Int = 1) async throws -> [GiftItem] {
        let encoded = category.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? category
        let root = try await request(
            method: "GET",
            path: "/gifts?page=\(page)&pageSize=40&category=\(encoded)",
            auth: nil,
            body: nil
        )
        return JSONValue.array(root, key: "items").compactMap { ($0 as? [String: Any]).map(parseGift) }
    }

    func fetchGift(id: Int64) async throws -> GiftItem {
        let root = try await request(method: "GET", path: "/gifts/\(id)", auth: nil, body: nil)
        guard let item = root["item"] as? [String: Any] else {
            throw APIError(message: "礼品不存在")
        }
        return parseGift(item)
    }

    func redeemGift(
        token: String,
        giftId: Int64,
        name: String?,
        phone: String?,
        detail: String?
    ) async throws -> (message: String, totalPoints: Int) {
        var body: [String: Any] = [:]
        if let name, !name.isEmpty { body["name"] = name }
        if let phone, !phone.isEmpty { body["phone"] = phone }
        if let detail, !detail.isEmpty { body["detail"] = detail }
        let root = try await request(method: "POST", path: "/gifts/\(giftId)/redeem", auth: token, body: body)
        return (
            JSONValue.string(root, key: "message") ?? "兑换成功",
            JSONValue.int(root, key: "totalPoints")
        )
    }

    func listGiftOrders(token: String) async throws -> [GiftOrder] {
        let root = try await request(method: "GET", path: "/me/gift-orders?page=1&pageSize=50", auth: token, body: nil)
        return JSONValue.array(root, key: "items").compactMap { ($0 as? [String: Any]).map(parseGiftOrder) }
    }

    func fetchPointCatalog() async throws -> PointCatalog {
        let root = try await request(method: "GET", path: "/point-packages", auth: nil, body: nil)
        let items = JSONValue.array(root, key: "items").compactMap { item -> PointPackage? in
            guard let object = item as? [String: Any] else { return nil }
            let id = JSONValue.string(object, key: "id") ?? ""
            guard !id.isEmpty else { return nil }
            return PointPackage(
                id: id,
                title: JSONValue.string(object, key: "title") ?? id,
                subtitle: JSONValue.string(object, key: "subtitle") ?? "",
                priceFen: JSONValue.int(object, key: "priceFen"),
                points: JSONValue.int(object, key: "points"),
                badge: JSONValue.string(object, key: "badge")
            )
        }
        return PointCatalog(
            items: items,
            aiImagePointsCost: JSONValue.int(root, key: "aiImagePointsCost", default: 5),
            sandbox: JSONValue.bool(root, key: "sandbox", default: true)
        )
    }

    func purchasePointsSimulated(token: String, packageId: String) async throws -> Int {
        let created = try await request(
            method: "POST",
            path: "/me/point-orders",
            auth: token,
            body: ["packageId": packageId, "channel": "alipay"]
        )
        let order = created["order"] as? [String: Any] ?? [:]
        let orderId = JSONValue.int64(order, key: "id")
        guard orderId > 0 else { throw APIError(message: "下单失败") }
        let sandbox = JSONValue.bool(created, key: "sandbox", default: true)
        guard sandbox else {
            throw APIError(message: "服务器未开启模拟支付，微信和支付宝 iOS 支付即将支持")
        }
        let paid = try await request(
            method: "POST",
            path: "/me/point-orders/\(orderId)/simulate-pay",
            auth: token,
            body: nil
        )
        if let balance = JSONValue.optionalInt(paid, key: "balance") {
            return balance
        }
        throw APIError(message: "支付结果确认中，请稍后查看积分")
    }

    func fetchWithdrawConfig(token: String) async throws -> WithdrawConfig {
        let root = try await request(method: "GET", path: "/me/withdrawals/config", auth: token, body: nil)
        guard let config = root["config"] as? [String: Any] else {
            throw APIError(message: "提现配置加载失败")
        }
        let channels = JSONValue.array(config, key: "channels").compactMap { item -> WithdrawChannel? in
            guard let object = item as? [String: Any] else { return nil }
            let id = JSONValue.string(object, key: "id") ?? ""
            guard !id.isEmpty else { return nil }
            return WithdrawChannel(
                id: id,
                name: JSONValue.string(object, key: "name") ?? id,
                accountLabel: JSONValue.string(object, key: "accountLabel") ?? "账号",
                accountHint: JSONValue.string(object, key: "accountHint") ?? ""
            )
        }
        return WithdrawConfig(
            sandbox: JSONValue.bool(config, key: "sandbox", default: true),
            amountYuan: JSONValue.string(config, key: "amountYuan") ?? "0.01",
            pointsCost: JSONValue.int(config, key: "pointsCost", default: 1),
            note: JSONValue.string(config, key: "note") ?? "",
            channels: channels
        )
    }

    func listWithdrawals(token: String) async throws -> [WithdrawalItem] {
        let root = try await request(method: "GET", path: "/me/withdrawals?page=1&pageSize=50", auth: token, body: nil)
        return JSONValue.array(root, key: "items").compactMap { item in
            guard let object = item as? [String: Any] else { return nil }
            return WithdrawalItem(
                id: JSONValue.int64(object, key: "id"),
                channelLabel: JSONValue.string(object, key: "channelLabel") ?? JSONValue.string(object, key: "channel") ?? "",
                account: JSONValue.string(object, key: "account") ?? "",
                amountYuan: JSONValue.string(object, key: "amountYuan") ?? "0.00",
                pointsSpent: JSONValue.int(object, key: "pointsSpent"),
                statusLabel: JSONValue.string(object, key: "statusLabel") ?? JSONValue.string(object, key: "status") ?? "",
                createdAt: JSONValue.string(object, key: "createdAt")
            )
        }
    }

    func createWithdrawal(
        token: String,
        channel: String,
        account: String,
        realName: String?
    ) async throws -> (message: String, totalPoints: Int) {
        var body: [String: Any] = ["channel": channel, "account": account]
        if let realName, !realName.isEmpty { body["realName"] = realName }
        let root = try await request(method: "POST", path: "/me/withdrawals", auth: token, body: body)
        return (
            JSONValue.string(root, key: "message") ?? "提现成功",
            JSONValue.int(root, key: "totalPoints")
        )
    }

    func fetchHomophones(token: String?, word: String) async throws -> [WordHomophone] {
        let encoded = word.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? word
        let root = try await request(method: "GET", path: "/homophones?word=\(encoded)&limit=3", auth: token, body: nil)
        return JSONValue.array(root, key: "items").compactMap { ($0 as? [String: Any]).map(parseHomophone) }
    }

    func submitHomophone(token: String, word: String, body: String) async throws -> WordHomophone {
        let root = try await request(
            method: "POST",
            path: "/homophones",
            auth: token,
            body: ["word": word, "body": body]
        )
        guard let item = root["item"] as? [String: Any] else {
            throw APIError(message: "提交谐音失败")
        }
        return parseHomophone(item)
    }

    func toggleHomophoneLike(token: String, id: Int64) async throws -> WordHomophone {
        let root = try await request(method: "POST", path: "/homophones/\(id)/like", auth: token, body: nil)
        guard let item = root["item"] as? [String: Any] else {
            throw APIError(message: "点赞失败")
        }
        return parseHomophone(item)
    }

    func fetchHomophoneLikers(token: String, id: Int64, offset: Int) async throws -> HomophoneLikersPage {
        let root = try await request(
            method: "GET",
            path: "/homophones/\(id)/likes?limit=20&offset=\(offset)",
            auth: token,
            body: nil
        )
        let items = JSONValue.array(root, key: "items").compactMap { item -> HomophoneLiker? in
            guard let object = item as? [String: Any] else { return nil }
            return HomophoneLiker(
                userId: JSONValue.int64(object, key: "userId"),
                label: JSONValue.string(object, key: "label") ?? "用户"
            )
        }
        let next = JSONValue.optionalInt(root, key: "nextOffset")
        return HomophoneLikersPage(total: JSONValue.int(root, key: "total"), items: items, nextOffset: next)
    }

    func generateMnemonicImage(
        token: String,
        word: String,
        meaningHint: String,
        provider: ImageProvider
    ) async throws -> (image: Data, balance: Int?, pointsSpent: Int) {
        let root = try await request(
            method: "POST",
            path: "/mnemonic-images",
            auth: token,
            body: [
                "word": word,
                "meaningHint": meaningHint,
                "provider": provider.rawValue,
            ],
            long: true
        )
        guard let encoded = JSONValue.string(root, key: "imageBase64"),
              let data = Data(base64Encoded: encoded, options: .ignoreUnknownCharacters),
              !data.isEmpty else {
            throw APIError(message: "服务器没有返回图片")
        }
        return (
            data,
            JSONValue.optionalInt(root, key: "balance"),
            JSONValue.int(root, key: "pointsSpent")
        )
    }

    private func parseGift(_ object: [String: Any]) -> GiftItem {
        GiftItem(
            id: JSONValue.int64(object, key: "id"),
            title: JSONValue.string(object, key: "title") ?? "礼品",
            subtitle: JSONValue.string(object, key: "subtitle") ?? "",
            coverEmoji: JSONValue.string(object, key: "coverEmoji") ?? "🎁",
            coverColor: JSONValue.string(object, key: "coverColor") ?? "#1B6CA8",
            pointsCost: JSONValue.int(object, key: "pointsCost"),
            cashFen: JSONValue.int(object, key: "cashFen"),
            cashYuan: JSONValue.string(object, key: "cashYuan") ?? "0.00",
            originalPriceYuan: JSONValue.string(object, key: "originalPriceYuan"),
            pointsOffsetYuan: JSONValue.string(object, key: "pointsOffsetYuan"),
            redeemedCount: JSONValue.int(object, key: "redeemedCount"),
            needAddress: JSONValue.bool(object, key: "needAddress"),
            description: JSONValue.string(object, key: "description") ?? ""
        )
    }

    private func parseGiftOrder(_ object: [String: Any]) -> GiftOrder {
        GiftOrder(
            id: JSONValue.int64(object, key: "id"),
            giftTitle: JSONValue.string(object, key: "giftTitle") ?? "礼品",
            coverEmoji: JSONValue.string(object, key: "coverEmoji") ?? "🎁",
            pointsSpent: JSONValue.int(object, key: "pointsSpent"),
            cashFen: JSONValue.int(object, key: "cashFen"),
            cashYuan: JSONValue.string(object, key: "cashYuan") ?? "0.00",
            status: JSONValue.string(object, key: "status") ?? "completed",
            createdAt: JSONValue.string(object, key: "createdAt")
        )
    }

    private func parseHomophone(_ object: [String: Any]) -> WordHomophone {
        let likers = JSONValue.array(object, key: "likers")
        let first = (likers.first as? [String: Any]).flatMap { JSONValue.string($0, key: "label") }
        return WordHomophone(
            id: JSONValue.int64(object, key: "id"),
            word: JSONValue.string(object, key: "word") ?? "",
            body: JSONValue.string(object, key: "body") ?? "",
            likeCount: JSONValue.int(object, key: "likeCount"),
            likedByMe: JSONValue.bool(object, key: "likedByMe"),
            isMine: JSONValue.bool(object, key: "isMine"),
            likerLabel: first
        )
    }

    private func request(
        method: String,
        path: String,
        auth: String?,
        body: [String: Any]?,
        long: Bool = false
    ) async throws -> [String: Any] {
        var lastError: Error?
        let order = [baseURL] + bases.filter { $0 != baseURL }
        for base in order {
            do {
                let json = try await requestOnce(
                    base: base,
                    method: method,
                    path: path,
                    auth: auth,
                    body: body,
                    long: long
                )
                baseURL = base
                return json
            } catch let error as APIError {
                throw error
            } catch {
                lastError = error
            }
        }
        throw friendlyNetworkError(lastError)
    }

    private func requestOnce(
        base: String,
        method: String,
        path: String,
        auth: String?,
        body: [String: Any]?,
        long: Bool = false
    ) async throws -> [String: Any] {
        guard let url = URL(string: base + path) else {
            throw APIError(message: "请求地址无效")
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        applyDeviceHeaders(&request)
        if let auth, !auth.isEmpty {
            request.setValue("Bearer \(auth)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let data: Data
        let response: URLResponse
        do {
            let client = long ? longSession : session
            (data, response) = try await client.data(for: request)
        } catch {
            throw error
        }
        let http = response as? HTTPURLResponse
        let code = http?.statusCode ?? 0
        let json = JSONValue.object(from: data)
        if !(200...299).contains(code) {
            let errCode = JSONValue.string(json, key: "code")
            let message = JSONValue.string(json, key: "error") ?? "http \(code)"
            throw APIError(message: message, code: errCode, httpCode: code)
        }
        return json
    }

    private func applyDeviceHeaders(_ request: inout URLRequest) {
        let model = device.model.isEmpty ? "iPhone" : device.model
        let machine = device.machine
        let osVersion = device.systemVersion
        request.setValue(
            "WordBuddy/\(appVersion) (iOS \(osVersion); \(model); machine/\(machine))",
            forHTTPHeaderField: "User-Agent"
        )
        request.setValue("iOS", forHTTPHeaderField: "X-Device-Platform")
        request.setValue("Apple", forHTTPHeaderField: "X-Device-Brand")
        request.setValue("Apple", forHTTPHeaderField: "X-Device-Manufacturer")
        request.setValue(machine, forHTTPHeaderField: "X-Device-Model")
        request.setValue(osVersion, forHTTPHeaderField: "X-Device-Os-Version")
        request.setValue(appVersion, forHTTPHeaderField: "X-App-Version")
    }

    private func friendlyNetworkError(_ error: Error?) -> APIError {
        if error is CancellationError {
            return APIError(message: "已取消", code: "cancelled")
        }
        if let urlError = error as? URLError {
            switch urlError.code {
            case .cancelled:
                return APIError(message: "已取消", code: "cancelled")
            case .timedOut:
                return APIError(message: "连接服务器超时，请检查网络后重试")
            case .notConnectedToInternet, .networkConnectionLost, .cannotConnectToHost, .cannotFindHost, .dnsLookupFailed:
                return APIError(message: "无法连接服务器，请检查网络后重试")
            default:
                break
            }
        }
        let message = error?.localizedDescription ?? ""
        if message.isEmpty {
            return APIError(message: "无法连接服务器")
        }
        return APIError(message: message)
    }
}
