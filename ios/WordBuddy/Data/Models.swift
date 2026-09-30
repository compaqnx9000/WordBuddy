import Foundation

enum Accent: String, Codable, CaseIterable, Identifiable {
    case uk
    case us

    var id: String { rawValue }

    var label: String {
        switch self {
        case .uk: "英音"
        case .us: "美音"
        }
    }

    var speechLanguage: String {
        switch self {
        case .uk: "en-GB"
        case .us: "en-US"
        }
    }
}

struct Definition: Codable, Equatable, Hashable {
    var pos: String
    var meaning: String
    var isUserAdded: Bool = false

    var label: String {
        pos.isEmpty ? meaning : "\(pos) \(meaning)"
    }
}

struct ExampleSentence: Codable, Equatable, Hashable {
    var english: String
    var chinese: String
}

struct VocabEntry: Codable, Equatable, Identifiable, Hashable {
    var id: Int64 = 0
    var notebookId: Int64 = Notebook.defaultId
    var text: String
    var isPhrase: Bool = false
    var ipaUk: String?
    var ipaUs: String?
    var definitions: [Definition]
    var examples: [ExampleSentence] = []
    var nearWords: [String] = []
    var synonyms: [String] = []
    var antonyms: [String] = []
    var sortOrder: Int = 0
    var addedAtMillis: Int64 = 0

    var definitionLine: String {
        definitions.map(\.label).joined(separator: "  ")
    }

    func ipa(for accent: Accent) -> String? {
        switch accent {
        case .uk: ipaUk ?? ipaUs
        case .us: ipaUs ?? ipaUk
        }
    }
}

struct Notebook: Codable, Equatable, Identifiable, Hashable {
    static let defaultId: Int64 = 0
    static let defaultName = "生词本"
    static let kindUser = "user"
    static let kindCatalog = "catalog"

    var id: Int64
    var name: String
    var sortOrder: Int = 0
    var createdAtMillis: Int64 = 0
    var kind: String = kindUser
    var slug: String?
    var wordCount: Int = 0

    var isSystem: Bool { kind == Self.kindCatalog }
}

enum WordFilter: String, CaseIterable, Identifiable {
    case all
    case words
    case phrases

    var id: String { rawValue }

    var label: String {
        switch self {
        case .all: "全部"
        case .words: "单词"
        case .phrases: "短语"
        }
    }
}

enum SortMode: String, CaseIterable, Identifiable {
    case manual
    case timeDesc
    case timeAsc
    case alpha

    var id: String { rawValue }

    var label: String {
        switch self {
        case .manual: "手动"
        case .timeDesc: "最新"
        case .timeAsc: "最早"
        case .alpha: "字母"
        }
    }

    var next: SortMode {
        switch self {
        case .manual: .timeDesc
        case .timeDesc: .timeAsc
        case .timeAsc: .alpha
        case .alpha: .manual
        }
    }
}

struct WordHead: Equatable, Identifiable {
    var id: Int64
    var text: String
    var isPhrase: Bool = false
    var ipaUk: String?
    var ipaUs: String?
    var sortOrder: Int = 0
}

struct WordPage: Equatable {
    var items: [VocabEntry]
    var total: Int
    var nextCursor: String?
    var fromIndex: Int = 0
}

struct AuthResult: Equatable {
    var session: UserSession?
    var isNewUser: Bool = false
    var needsPhoneBind: Bool = false
}

struct UserSession: Equatable {
    var token: String
    var userId: Int64
    var phone: String
    var vocabNotebookId: Int64
    var avatarUrl: String?
    var level: Int = 0
    var nickname: String?
    var buddyId: String?
    var signature: String?
    var gender: String?
    var region: String?
    var email: String?
    var shippingName: String? = nil
    var shippingPhone: String? = nil
    var shippingDetail: String? = nil
    var alipayAccount: String? = nil
    var alipayName: String? = nil
    var wechatAccount: String? = nil
    var networkRegion: String? = nil
    var networkRegionDetail: String? = nil

    var hasShipping: Bool {
        !(shippingDetail?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true)
    }

    var shortShippingLabel: String {
        let detail = shippingDetail?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if detail.isEmpty { return "去填写" }
        return detail.count > 18 ? String(detail.prefix(18)) + "…" : detail
    }

    var displayNickname: String {
        let trimmed = nickname?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? "词搭子" : trimmed
    }

    var maskedPhone: String {
        guard phone.count == 11 else { return phone }
        let start = phone.prefix(3)
        let end = phone.suffix(4)
        return "\(start)****\(end)"
    }
}

extension UserSession: Codable {
    enum CodingKeys: String, CodingKey {
        case token, userId, phone, vocabNotebookId, avatarUrl, level, nickname, buddyId
        case signature, gender, region, email
        case shippingName, shippingPhone, shippingDetail
        case alipayAccount, alipayName, wechatAccount
        case networkRegion, networkRegionDetail
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        token = try container.decode(String.self, forKey: .token)
        userId = try container.decode(Int64.self, forKey: .userId)
        phone = try container.decodeIfPresent(String.self, forKey: .phone) ?? ""
        vocabNotebookId = try container.decodeIfPresent(Int64.self, forKey: .vocabNotebookId) ?? 0
        avatarUrl = try container.decodeIfPresent(String.self, forKey: .avatarUrl)
        level = try container.decodeIfPresent(Int.self, forKey: .level) ?? 0
        nickname = try container.decodeIfPresent(String.self, forKey: .nickname)
        buddyId = try container.decodeIfPresent(String.self, forKey: .buddyId)
        signature = try container.decodeIfPresent(String.self, forKey: .signature)
        gender = try container.decodeIfPresent(String.self, forKey: .gender)
        region = try container.decodeIfPresent(String.self, forKey: .region)
        email = try container.decodeIfPresent(String.self, forKey: .email)
        shippingName = try container.decodeIfPresent(String.self, forKey: .shippingName)
        shippingPhone = try container.decodeIfPresent(String.self, forKey: .shippingPhone)
        shippingDetail = try container.decodeIfPresent(String.self, forKey: .shippingDetail)
        alipayAccount = try container.decodeIfPresent(String.self, forKey: .alipayAccount)
        alipayName = try container.decodeIfPresent(String.self, forKey: .alipayName)
        wechatAccount = try container.decodeIfPresent(String.self, forKey: .wechatAccount)
        networkRegion = try container.decodeIfPresent(String.self, forKey: .networkRegion)
        networkRegionDetail = try container.decodeIfPresent(String.self, forKey: .networkRegionDetail)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(token, forKey: .token)
        try container.encode(userId, forKey: .userId)
        try container.encode(phone, forKey: .phone)
        try container.encode(vocabNotebookId, forKey: .vocabNotebookId)
        try container.encodeIfPresent(avatarUrl, forKey: .avatarUrl)
        try container.encode(level, forKey: .level)
        try container.encodeIfPresent(nickname, forKey: .nickname)
        try container.encodeIfPresent(buddyId, forKey: .buddyId)
        try container.encodeIfPresent(signature, forKey: .signature)
        try container.encodeIfPresent(gender, forKey: .gender)
        try container.encodeIfPresent(region, forKey: .region)
        try container.encodeIfPresent(email, forKey: .email)
        try container.encodeIfPresent(shippingName, forKey: .shippingName)
        try container.encodeIfPresent(shippingPhone, forKey: .shippingPhone)
        try container.encodeIfPresent(shippingDetail, forKey: .shippingDetail)
        try container.encodeIfPresent(alipayAccount, forKey: .alipayAccount)
        try container.encodeIfPresent(alipayName, forKey: .alipayName)
        try container.encodeIfPresent(wechatAccount, forKey: .wechatAccount)
        try container.encodeIfPresent(networkRegion, forKey: .networkRegion)
        try container.encodeIfPresent(networkRegionDetail, forKey: .networkRegionDetail)
    }
}

struct CheckInState: Equatable {
    var totalPoints: Int = 0
    var streakDays: Int = 0
    var lastCheckInDate: String?
    var checkedInToday: Bool = false
    var todayReward: Int = 1
    var recentDates: [String] = []
}

struct CheckInOutcome: Equatable {
    var already: Bool
    var pointsEarned: Int
    var streakDays: Int
    var totalPoints: Int
    var state: CheckInState
}

struct DeletionCondition: Identifiable, Equatable {
    var key: String
    var title: String
    var ok: Bool
    var detail: String

    var id: String { key.isEmpty ? title : key }
}

struct AccountDeletionStatus: Equatable {
    var pending: Bool = false
    var cooldownDays: Int = 7
    var dueAtLabel: String = ""
    var reason: String = ""
    var allPassed: Bool = true
    var remainingPoints: Int = 0
    var phoneMasked: String = ""
    var conditions: [DeletionCondition] = []
    var immediate: Bool = false
    var deleted: Bool = false
}

struct ShortClip: Identifiable, Equatable, Hashable {
    var id: String
    var videoUrl: String
    var title: String
    var author: String
    var caption: String
    var relatedWords: [String]
    var category: String
    var categoryName: String
    var coverUrl: String?
    var favorited: Bool

    var shareText: String {
        let words = relatedWords.prefix(20).joined(separator: " · ")
        var lines = ["【词搭子】\(title.isEmpty ? "英语短视频" : title)"]
        let captionText = caption.trimmingCharacters(in: .whitespacesAndNewlines)
        if !captionText.isEmpty { lines.append(captionText) }
        if !words.isEmpty { lines.append("关键词：\(words)") }
        lines.append("@\(author.isEmpty ? "词搭子" : author)")
        if !videoUrl.isEmpty { lines.append(videoUrl) }
        return lines.joined(separator: "\n")
    }
}

enum ImageProvider: String, CaseIterable, Identifiable {
    case pollinations
    case siliconflow

    var id: String { rawValue }

    var label: String {
        switch self {
        case .pollinations: "Pollinations"
        case .siliconflow: "硅基流动"
        }
    }
}

struct WordHomophone: Identifiable, Equatable {
    var id: Int64
    var word: String
    var body: String
    var likeCount: Int
    var likedByMe: Bool
    var isMine: Bool
    var likerLabel: String?
}

struct HomophoneLiker: Identifiable, Equatable {
    var userId: Int64
    var label: String
    var id: Int64 { userId }
}

struct HomophoneLikersPage: Equatable {
    var total: Int
    var items: [HomophoneLiker]
    var nextOffset: Int?
}

struct InviteInfo: Equatable {
    var canBindInvite: Bool
    var invitedByBuddyId: String?
    var inviteeReward: Int
    var inviterReward: Int
    var invitedCount: Int
}

struct GiftCategory: Identifiable, Equatable {
    var id: String
    var name: String
}

struct GiftItem: Identifiable, Equatable {
    var id: Int64
    var title: String
    var subtitle: String
    var coverEmoji: String
    var coverColor: String
    var pointsCost: Int
    var cashFen: Int
    var cashYuan: String
    var originalPriceYuan: String?
    var pointsOffsetYuan: String?
    var redeemedCount: Int
    var needAddress: Bool
    var description: String

    var priceLabel: String {
        cashFen > 0 ? "\(pointsCost)积分 + \(cashYuan)元" : "\(pointsCost)积分"
    }

    var redeemedLabel: String {
        if redeemedCount >= 10_000 { return "已兑\(redeemedCount / 10_000)万+" }
        if redeemedCount >= 1000 { return String(format: "已兑%.1f千", Double(redeemedCount) / 1000) }
        return "已兑\(redeemedCount)"
    }
}

struct GiftOrder: Identifiable, Equatable {
    var id: Int64
    var giftTitle: String
    var coverEmoji: String
    var pointsSpent: Int
    var cashFen: Int
    var cashYuan: String
    var status: String
    var createdAt: String?

    var statusLabel: String {
        switch status {
        case "pending_cash": "待付现金"
        case "pending_ship": "待发货"
        case "shipped": "已发货"
        case "completed": "已完成"
        case "cancelled": "已取消"
        default: status
        }
    }

    var priceLabel: String {
        cashFen > 0 ? "\(pointsSpent)积分 + \(cashYuan)元" : "\(pointsSpent)积分"
    }
}

struct PointPackage: Identifiable, Equatable {
    var id: String
    var title: String
    var subtitle: String
    var priceFen: Int
    var points: Int
    var badge: String?

    var amountYuan: String { String(format: "%.2f", Double(priceFen) / 100) }
}

struct PointCatalog: Equatable {
    var items: [PointPackage]
    var aiImagePointsCost: Int
    var sandbox: Bool
}

struct WithdrawChannel: Identifiable, Equatable {
    var id: String
    var name: String
    var accountLabel: String
    var accountHint: String
}

struct WithdrawConfig: Equatable {
    var sandbox: Bool
    var amountYuan: String
    var pointsCost: Int
    var note: String
    var channels: [WithdrawChannel]
}

struct WithdrawalItem: Identifiable, Equatable {
    var id: Int64
    var channelLabel: String
    var account: String
    var amountYuan: String
    var pointsSpent: Int
    var statusLabel: String
    var createdAt: String?
}

struct APIError: LocalizedError, Equatable {
    var message: String
    var code: String?
    var httpCode: Int = 0

    var errorDescription: String? { message }

    var isSessionInvalid: Bool {
        code == Self.sessionReplaced || code == Self.accountDeleted || httpCode == 401
    }

    static let sessionReplaced = "SESSION_REPLACED"
    static let accountDeleted = "ACCOUNT_DELETED"
}
