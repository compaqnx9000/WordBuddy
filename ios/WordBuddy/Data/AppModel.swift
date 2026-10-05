import Foundation
import UIKit

struct CatalogCopyProgress: Equatable {
    var title: String
    var copied: Int
    var total: Int
}

enum EarlierWordsResult {
    case busy
    case unchanged
    /// Words were inserted above [anchorId]. Keep that row on screen, then call finish.
    case prepended(anchorId: Int64, token: Int)
}

@MainActor
final class AppModel: ObservableObject {
    @Published var session: UserSession?
    @Published var notebooks: [Notebook] = []
    @Published var activeNotebookId: Int64?
    @Published var words: [VocabEntry] = []
    @Published var wordTotal = 0
    @Published var nextCursor: String?
    @Published var wordsLoading = false
    @Published var wordsError: String?
    /// Absolute letter → first word index in the active notebook (server-built).
    @Published private(set) var alphabetLetterIndex: [Character: Int] = [:]
    /// When list is a seek window, absolute offset of `words[0]`.
    @Published private(set) var listWindowStart = 0
    /// Latest alphabet seek scroll target (word id).
    @Published var pendingScrollWordId: Int64?
    /// Bumps on each window jump so a slower response cannot overwrite a newer seek.
    private var windowSeekGeneration = 0
    /// Prepending the page above a letter jump. A newer seek invalidates it.
    private var earlierLoadInFlight = false
    private var earlierLoadToken = 0
    /// Notebook id → full ordered heads, used to shuffle the whole catalog.
    private var headsCache: [Int64: [WordHead]] = [:]
    @Published var showLogin = false
    @Published var banner: String?
    /// Whole-notebook favorite. Shown as a live progress card, not the alert banner.
    @Published var catalogCopyProgress: CatalogCopyProgress?
    @Published var accent: Accent = .us
    @Published var accentStyle: AccentStyle = .cyberNeon
    @Published var hideDefinitions = true
    @Published var speakOnPageChange = true
    @Published var imageProvider: ImageProvider = .pollinations
    @Published var aiImagePointsCost = 5
    @Published var dailyReminder = true
    @Published var aiImageAutoGen = false
    @Published var podcastPlayWhenScreenOff = false
    @Published var shortsMetaVisibleDefault = true
    @Published var biometricLogin = false
    @Published var biometricUnlocked = false
    @Published var defaultNotebookId: Int64 = 0
    @Published var mnemonicRevision = 0
    @Published var avatarImage: UIImage?
    @Published var avatarBusy = false
    /// Lowercased word text → id in the default favorite notebook.
    @Published private(set) var favoritedByText: [String: Int64] = [:]
    /// Notebook the `favoritedByText` index was loaded from.
    private var favoritedIndexNotebookId: Int64?
    @Published var checkIn = CheckInState()
    @Published var rewardVideo = RewardVideoOffer()
    @Published var rewardBusy = false
    @Published var accounts: [RememberedAccount] = []
    @Published var pendingLookup: String?
    /// Word opened from the short that is still on screen. Keeps that video paused underneath.
    @Published var shortsWordLookup: String?

    let api: WordBuddyAPI
    let podcast = PodcastPlayer()
    private let dictionary = DictionaryClient()

    init() {
        api = WordBuddyAPI(device: DeviceInfo.current())
        accent = SettingsStore.accent
        accentStyle = SettingsStore.accentStyle
        hideDefinitions = SettingsStore.hideDefinitions
        speakOnPageChange = SettingsStore.speakOnPageChange
        imageProvider = SettingsStore.imageProvider
        dailyReminder = SettingsStore.dailyReminder
        aiImageAutoGen = SettingsStore.aiImageAutoGen
        podcastPlayWhenScreenOff = SettingsStore.podcastPlayWhenScreenOff
        shortsMetaVisibleDefault = SettingsStore.shortsMetaVisibleDefault
        biometricLogin = SettingsStore.biometricLogin
        defaultNotebookId = SettingsStore.defaultNotebookId
        session = SessionStore.load()
        if let session {
            AccountStore.upsert(session)
        }
        accounts = AccountStore.list()
    }

    var activeNotebook: Notebook? {
        guard let activeNotebookId else { return nil }
        return notebooks.first { $0.id == activeNotebookId }
    }

    var favoriteNotebookId: Int64? {
        if defaultNotebookId > 0,
           let notebook = notebooks.first(where: { $0.id == defaultNotebookId }),
           !notebook.isSystem {
            return notebook.id
        }
        return saveNotebookId
    }

    var saveNotebookId: Int64? {
        if let active = activeNotebook, !active.isSystem {
            return active.id
        }
        if let vocabId = session?.vocabNotebookId, vocabId > 0 {
            return vocabId
        }
        return notebooks.first { !$0.isSystem }?.id
    }

    func bootstrap() async {
        guard session != nil else { return }
        await loadNotebooks()
    }

    func sendCode(phone: String) async throws -> String? {
        try await api.sendCode(phone: phone)
    }

    func verifyLoginPassword(_ password: String) async throws {
        guard let session else { throw APIError(message: "请先登录") }
        guard password.count >= 6 else { throw APIError(message: "请输入当前密码") }
        try await api.verifyPassword(token: session.token, password: password)
    }

    func sendChangePhoneCode(newPhone: String) async throws {
        let digits = newPhone.filter(\.isNumber)
        guard Self.isMainlandPhone(digits) else {
            throw APIError(message: "请输入正确的新手机号")
        }
        let current = session?.phone.filter(\.isNumber) ?? ""
        if digits == current {
            throw APIError(message: "新手机号不能与当前号码相同")
        }
        _ = try await api.sendCode(phone: digits)
    }

    func changePhone(password: String, newPhone: String, code: String) async throws {
        guard let session else { throw APIError(message: "请先登录") }
        let digits = newPhone.filter(\.isNumber)
        let sms = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard password.count >= 6 else { throw APIError(message: "请输入当前密码") }
        guard Self.isMainlandPhone(digits) else { throw APIError(message: "请输入正确的新手机号") }
        guard sms.range(of: "^\\d{6}$", options: .regularExpression) != nil else {
            throw APIError(message: "请输入6位验证码")
        }
        do {
            var latest = try await api.changePhone(
                token: session.token,
                password: password,
                newPhone: digits,
                code: sms
            )
            if latest.token.isEmpty { latest.token = session.token }
            if latest.phone.isEmpty { latest.phone = digits }
            if latest.userId == 0 { latest.userId = session.userId }
            if latest.vocabNotebookId == 0 { latest.vocabNotebookId = session.vocabNotebookId }
            if latest.avatarUrl == nil { latest.avatarUrl = session.avatarUrl }
            if latest.nickname == nil { latest.nickname = session.nickname }
            if latest.buddyId == nil { latest.buddyId = session.buddyId }
            if latest.signature == nil { latest.signature = session.signature }
            if latest.gender == nil { latest.gender = session.gender }
            if latest.region == nil { latest.region = session.region }
            if latest.email == nil { latest.email = session.email }
            if latest.shippingName == nil { latest.shippingName = session.shippingName }
            if latest.shippingPhone == nil { latest.shippingPhone = session.shippingPhone }
            if latest.shippingDetail == nil { latest.shippingDetail = session.shippingDetail }
            if latest.alipayAccount == nil { latest.alipayAccount = session.alipayAccount }
            if latest.alipayName == nil { latest.alipayName = session.alipayName }
            if latest.wechatAccount == nil { latest.wechatAccount = session.wechatAccount }
            if latest.networkRegion == nil { latest.networkRegion = session.networkRegion }
            if latest.networkRegionDetail == nil { latest.networkRegionDetail = session.networkRegionDetail }
            self.session = latest
            SessionStore.save(latest)
            AccountStore.upsert(latest)
            accounts = AccountStore.list()
            banner = "手机号已更新"
        } catch {
            if noteSessionError(error) { throw error }
            throw error
        }
    }

    private static func isMainlandPhone(_ digits: String) -> Bool {
        digits.range(of: "^1[3-9]\\d{9}$", options: .regularExpression) != nil
    }

    func login(phone: String, code: String) async throws -> AuthResult {
        try await api.login(phone: phone, code: code)
    }

    func loginWithPassword(phone: String, password: String) async throws -> AuthResult {
        try await api.loginWithPassword(phone: phone, password: password)
    }

    func register(phone: String, code: String, password: String, inviteCode: String?) async throws -> AuthResult {
        try await api.register(phone: phone, code: code, password: password, inviteCode: inviteCode)
    }

    /// Opens WeChat, then binds that OpenID to the signed-in account. Same path as Android.
    func bindWechat() async {
        guard let session else {
            showLogin = true
            return
        }
        do {
            let code = try await SocialAuth.shared.signInWechat()
            var latest = try await api.bindWechat(token: session.token, authCode: code)
            if latest.vocabNotebookId == 0 {
                latest.vocabNotebookId = session.vocabNotebookId
            }
            if latest.phone.isEmpty {
                latest.phone = session.phone
            }
            self.session = latest
            SessionStore.save(latest)
            AccountStore.upsert(latest)
            accounts = AccountStore.list()
            banner = "微信已绑定"
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    func openLogin() {
        if showLogin {
            showLogin = false
            Task { @MainActor in
                await Task.yield()
                showLogin = true
            }
        } else {
            showLogin = true
        }
    }

    func enter(_ session: UserSession) async {
        self.session = session
        SessionStore.save(session)
        biometricUnlocked = true
        showLogin = false
        banner = nil
        AccountStore.upsert(session)
        accounts = AccountStore.list()
        await loadNotebooks()
        await loadAvatar()
    }

    func logout() {
        session = nil
        biometricUnlocked = false
        SessionStore.clear()
        notebooks = []
        activeNotebookId = nil
        words = []
        wordTotal = 0
        nextCursor = nil
        wordsError = nil
        alphabetLetterIndex = [:]
        listWindowStart = 0
        pendingScrollWordId = nil
        checkIn = CheckInState()
        rewardVideo = RewardVideoOffer()
        avatarImage = nil
        avatarBusy = false
        favoritedByText = [:]
        favoritedIndexNotebookId = nil
        accounts = AccountStore.list()
    }

    func setAccent(_ value: Accent) {
        accent = value
        SettingsStore.accent = value
    }

    func setAccentStyle(_ value: AccentStyle) {
        SettingsStore.accentStyle = value
        accentStyle = value
        Theme.applyInterfaceStyle(value)
        Theme.applyTabBar()
    }

    func setHideDefinitions(_ value: Bool) {
        hideDefinitions = value
        SettingsStore.hideDefinitions = value
    }

    func setSpeakOnPageChange(_ value: Bool) {
        speakOnPageChange = value
        SettingsStore.speakOnPageChange = value
    }

    func setImageProvider(_ provider: ImageProvider) {
        imageProvider = provider
        SettingsStore.imageProvider = provider
    }

    func setDailyReminder(_ value: Bool) {
        dailyReminder = value
        SettingsStore.dailyReminder = value
        StudyReminder.sync(enabled: value, ask: value)
    }

    func setAiImageAutoGen(_ value: Bool) {
        aiImageAutoGen = value
        SettingsStore.aiImageAutoGen = value
    }

    func setPodcastPlayWhenScreenOff(_ value: Bool) {
        podcastPlayWhenScreenOff = value
        SettingsStore.podcastPlayWhenScreenOff = value
    }

    func setShortsMetaVisibleDefault(_ value: Bool) {
        shortsMetaVisibleDefault = value
        SettingsStore.shortsMetaVisibleDefault = value
    }

    func setBiometricLogin(_ value: Bool) {
        biometricLogin = value
        SettingsStore.biometricLogin = value
        if value { biometricUnlocked = true }
    }

    func setDefaultNotebookId(_ value: Int64) {
        defaultNotebookId = value
        SettingsStore.defaultNotebookId = value
    }

    /// Home-screen star saves into this personal notebook.
    func setDefaultFavoriteNotebook(_ id: Int64) {
        guard let notebook = notebooks.first(where: { $0.id == id }), !notebook.isSystem else { return }
        setDefaultNotebookId(id)
        assignVocabNotebook(id)
        Task { await refreshFavoritedIndex() }
    }

    func applyPoints(_ points: Int) {
        checkIn.totalPoints = max(0, points)
    }

    func refreshPointCatalog() async {
        if let catalog = try? await api.fetchPointCatalog() {
            aiImagePointsCost = max(1, catalog.aiImagePointsCost)
        }
    }

    func purchasePoints(packageId: String, points: Int, channel: String) async {
        guard let session else {
            showLogin = true
            return
        }
        do {
            let balance = try await api.purchasePointsSimulated(token: session.token, packageId: packageId, channel: channel)
            applyPoints(balance)
            banner = "已到账 \(points) 积分"
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    /// `nil` means the picture was saved. A string is the error to show in place.
    /// The global alert is optional because presenting it dismisses the card cover.
    @discardableResult
    func generateMnemonicImage(word: String, meaningHint: String, announceFailure: Bool = true) async -> String? {
        guard let session else {
            showLogin = true
            return "请先登录"
        }
        let text = word.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return "请填写单词" }
        do {
            let result = try await api.generateMnemonicImage(
                token: session.token,
                word: text,
                meaningHint: meaningHint,
                provider: imageProvider
            )
            guard MnemonicImageStore.save(word: text, data: result.image),
                  UIImage(data: result.image) != nil else {
                return "图片已返回，但没有显示出来，请再试一次"
            }
            if let balance = result.balance {
                applyPoints(balance)
            }
            mnemonicRevision += 1
            return nil
        } catch {
            if noteSessionError(error) { return "请先登录" }
            let apiError = error as? APIError
            let message: String
            if apiError?.httpCode == 402 || apiError?.code == "INSUFFICIENT_POINTS" {
                let raw = apiError?.message ?? "积分不足"
                message = raw.contains("购买") ? raw : raw + "，请先购买积分"
            } else {
                message = friendlyImageMessage(apiError?.message ?? error.localizedDescription)
            }
            if announceFailure {
                banner = message
            }
            return message
        }
    }

    private func friendlyImageMessage(_ raw: String) -> String {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = text.lowercased()
        if lower.contains("timeout") || lower.contains("timed out") || text.contains("超时") {
            return "生图超时，请稍后再试"
        }
        if text.isEmpty || text == "{}" || text.hasPrefix("{") || text.hasPrefix("<")
            || lower.contains("429") || lower.contains("internal server error")
            || lower.contains("rpm") || lower.contains("rate limit") {
            return "生图服务正忙，请稍后再试"
        }
        if text.count > 48 {
            return "生图失败，请稍后再试"
        }
        return text
    }

    func switchAccount(_ remembered: RememberedAccount) async {
        await enter(remembered.session)
    }

    func forgetAccount(userId: Int64) {
        AccountStore.remove(userId: userId)
        accounts = AccountStore.list()
        if session?.userId == userId {
            logout()
        }
    }

    func refreshMe() async {
        guard let session else { return }
        do {
            let latest = try await api.fetchMe(token: session.token)
            self.session = latest
            SessionStore.save(latest)
            AccountStore.upsert(latest)
            accounts = AccountStore.list()
            checkIn = try await api.fetchCheckIn(token: latest.token)
            if let offer = try? await api.fetchRewardVideo(token: latest.token) {
                rewardVideo = offer
            }
            if notebooks.isEmpty {
                await loadNotebooks()
            }
            await loadAvatar()
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    func loadAvatar() async {
        guard let url = session?.avatarUrl, !url.isEmpty else {
            avatarImage = nil
            return
        }
        do {
            let data = try await api.fetchAvatarData(avatarUrl: url)
            avatarImage = UIImage(data: data)
        } catch {
            // Keep previous image if refresh fails.
        }
    }

    func uploadAvatar(image: UIImage) async {
        guard let session else {
            showLogin = true
            return
        }
        guard let jpeg = AvatarImageCodec.jpegData(from: image, maxEdge: 512) else {
            banner = "无法读取图片"
            return
        }
        avatarBusy = true
        defer { avatarBusy = false }
        do {
            let url = try await api.uploadAvatar(token: session.token, jpegData: jpeg)
            var updated = session
            updated.avatarUrl = url
            self.session = updated
            SessionStore.save(updated)
            AccountStore.upsert(updated)
            accounts = AccountStore.list()
            avatarImage = UIImage(data: jpeg) ?? image
            banner = "头像已更新"
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    func refreshNetworkRegion() async {
        guard let session else {
            showLogin = true
            return
        }
        do {
            let latest = try await api.refreshNetworkRegion(token: session.token)
            self.session = latest
            SessionStore.save(latest)
            AccountStore.upsert(latest)
            accounts = AccountStore.list()
            banner = "网络属地已刷新"
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    @discardableResult
    func patchProfile(
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
        shippingDetail: String? = nil,
        successMessage: String = "资料已保存"
    ) async -> Bool {
        guard let session else {
            showLogin = true
            return false
        }
        do {
            let latest = try await api.updateProfile(
                token: session.token,
                nickname: nickname,
                signature: signature,
                gender: gender,
                region: region,
                email: email,
                alipayAccount: alipayAccount,
                alipayName: alipayName,
                wechatAccount: wechatAccount,
                shippingName: shippingName,
                shippingPhone: shippingPhone,
                shippingDetail: shippingDetail
            )
            self.session = latest
            SessionStore.save(latest)
            AccountStore.upsert(latest)
            accounts = AccountStore.list()
            banner = successMessage
            return true
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
            return false
        }
    }

    func saveProfile(nickname: String, signature: String, gender: String, region: String, email: String) async {
        _ = await patchProfile(
            nickname: nickname,
            signature: signature,
            gender: gender,
            region: region,
            email: email
        )
    }

    func changePassword(oldPassword: String, newPassword: String) async {
        guard let session else {
            showLogin = true
            return
        }
        do {
            let fresh = try await api.changePassword(
                token: session.token,
                oldPassword: oldPassword,
                newPassword: newPassword
            )
            // The server invalidates every old token on change; keep this device signed in.
            if let fresh, var updated = self.session {
                updated.token = fresh
                self.session = updated
                SessionStore.save(updated)
                AccountStore.upsert(updated)
            }
            banner = "密码已更新"
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    func checkInToday() async {
        guard let session else {
            showLogin = true
            return
        }
        do {
            let outcome = try await api.performCheckIn(token: session.token)
            checkIn = outcome.state.checkedInToday || !outcome.already ? outcome.state : try await api.fetchCheckIn(token: session.token)
            if outcome.already {
                banner = "今天已经签到过了"
            } else {
                checkIn.checkedInToday = true
                checkIn.totalPoints = outcome.totalPoints
                checkIn.streakDays = outcome.streakDays
                banner = "签到成功，+\(outcome.pointsEarned) 积分"
            }
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    func makeupAfterAd(date: String) async {
        guard !rewardBusy else { return }
        rewardBusy = true
        let result = await PangleAds.shared.showReward(userId: session.map { String($0.userId) })
        switch result {
        case .rewarded:
            await makeupCheckIn(date: date)
        case .skipped:
            banner = "需看完广告才能补签"
        case .failed(let message):
            banner = message
        }
        rewardBusy = false
    }

    func watchRewardVideo() async {
        guard session != nil else {
            showLogin = true
            return
        }
        if rewardVideo.remaining <= 0 {
            banner = "今日奖励视频已达 \(rewardVideo.dailyLimit) 次"
            return
        }
        guard !rewardBusy else { return }
        rewardBusy = true
        let result = await PangleAds.shared.showReward(userId: session.map { String($0.userId) })
        switch result {
        case .rewarded:
            await claimRewardVideo()
        case .skipped:
            banner = "需看完广告才能领积分"
        case .failed(let message):
            banner = message
        }
        rewardBusy = false
    }

    private func claimRewardVideo() async {
        guard let session else { return }
        do {
            let claim = try await api.claimRewardVideo(token: session.token)
            rewardVideo = claim.offer
            if claim.offer.totalPoints > 0 {
                checkIn.totalPoints = claim.offer.totalPoints
            }
            banner = "获得 \(claim.points) 积分，今日剩余 \(claim.offer.remaining)/\(claim.offer.dailyLimit)"
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    func makeupCheckIn(date: String) async {
        guard let session else {
            showLogin = true
            return
        }
        do {
            let outcome = try await api.makeupCheckIn(token: session.token, date: date)
            checkIn = outcome.state
            banner = outcome.already ? "该日已经签到过了" : "已补签 \(date)，+\(outcome.pointsEarned) 积分"
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    func loadDeletion() async -> AccountDeletionStatus? {
        guard let session else { return nil }
        do {
            return try await api.fetchAccountDeletion(token: session.token)
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
            return nil
        }
    }

    func sendDeletionCode(force: Bool) async -> String? {
        guard let session else { return nil }
        do {
            return try await api.sendDeletionCode(token: session.token, force: force)
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
            return nil
        }
    }

    func submitDeletion(code: String, reason: String?, force: Bool) async -> AccountDeletionStatus? {
        guard let session else { return nil }
        do {
            let status = try await api.requestAccountDeletion(token: session.token, code: code, reason: reason, force: force)
            if status.deleted || status.immediate {
                AccountStore.remove(userId: session.userId)
                logout()
                banner = "账号已注销"
            }
            return status
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
            return nil
        }
    }

    func cancelDeletion() async -> AccountDeletionStatus? {
        guard let session else { return nil }
        do {
            let status = try await api.cancelAccountDeletion(token: session.token)
            banner = "已撤销注销申请"
            return status
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
            return nil
        }
    }

    func exportNotebookData() async -> Data? {
        guard let session else {
            showLogin = true
            return nil
        }
        let books = notebooks.filter { !$0.isSystem }
        guard !books.isEmpty else {
            banner = "还没有可导出的生词本"
            return nil
        }
        do {
            var payload: [VocabBackupNotebook] = []
            for book in books {
                let entries = try await allWords(token: session.token, notebookId: book.id)
                payload.append(VocabBackupNotebook(name: book.name, entries: entries))
            }
            return try VocabTransfer.exportLibrary(payload)
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
            return nil
        }
    }

    func importNotebook(data: Data) async {
        guard let token = session?.token else {
            showLogin = true
            return
        }
        do {
            let books = try VocabTransfer.importLibrary(from: data)
            guard !books.isEmpty else {
                banner = "备份中没有生词本"
                return
            }
            var restored = 0
            var added = 0
            var firstId: Int64?
            for book in books {
                let notebookId = try await notebookId(named: book.name, token: token)
                if firstId == nil { firstId = notebookId }
                let existing = try await allWords(token: token, notebookId: notebookId)
                var known = Set(existing.map { $0.text.lowercased() })
                for entry in book.entries {
                    let key = entry.text.lowercased()
                    if known.contains(key) { continue }
                    var toSave = entry
                    toSave.id = 0
                    toSave.notebookId = notebookId
                    _ = try await api.createWord(token: token, notebookId: notebookId, entry: toSave)
                    known.insert(key)
                    added += 1
                }
                restored += 1
            }
            await loadNotebooks()
            if let firstId, notebooks.contains(where: { $0.id == firstId }) {
                activeNotebookId = firstId
            }
            await loadWords()
            banner = "已恢复 \(restored) 个生词本，新增 \(added) 个单词"
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    private func notebookId(named name: String, token: String) async throws -> Int64 {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if let existing = userNotebook(named: trimmed) {
            return existing.id
        }
        do {
            let created = try await api.createNotebook(token: token, name: trimmed)
            notebooks.append(created)
            return created.id
        } catch {
            await loadNotebooks()
            if let existing = userNotebook(named: trimmed) {
                return existing.id
            }
            throw error
        }
    }

    private func userNotebook(named name: String) -> Notebook? {
        notebooks.first {
            !$0.isSystem && $0.name.compare(name, options: .caseInsensitive) == .orderedSame
        }
    }

    private func allWords(token: String, notebookId: Int64) async throws -> [VocabEntry] {
        var cursor: String?
        var collected: [VocabEntry] = []
        var seen = Set<Int64>()
        for _ in 0..<40 {
            let page = try await api.listWords(token: token, notebookId: notebookId, cursor: cursor)
            for item in page.items where seen.insert(item.id).inserted {
                collected.append(item)
            }
            guard let next = page.nextCursor, !next.isEmpty else { break }
            cursor = next
        }
        return collected
    }

    func openWord(_ word: String) {
        let text = word.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        pendingLookup = text
    }

    func noteSessionError(_ error: Error) -> Bool {
        guard let apiError = error as? APIError, apiError.isSessionInvalid else { return false }
        handle(apiError, fallback: "登录已失效")
        return true
    }

    func lookup(_ query: String) async throws -> VocabEntry {
        try await dictionary.lookup(query)
    }

    func contains(_ text: String) -> Bool {
        isFavorited(text)
    }

    func isFavorited(_ text: String) -> Bool {
        let key = text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !key.isEmpty, let notebookId = favoriteNotebookId else { return false }
        if favoritedIndexNotebookId == notebookId {
            return favoritedByText[key] != nil
        }
        guard activeNotebookId == notebookId else { return false }
        return words.contains { $0.notebookId == notebookId && $0.text.caseInsensitiveCompare(text) == .orderedSame }
    }

    /// Load the default notebook's words once so lookup can show a filled star.
    func ensureFavoriteIndex() async {
        guard let notebookId = favoriteNotebookId else {
            favoritedByText = [:]
            favoritedIndexNotebookId = nil
            return
        }
        guard favoritedIndexNotebookId != notebookId else { return }
        await refreshFavoritedIndex()
    }

    func refreshFavoritedIndex() async {
        guard let session, let notebookId = favoriteNotebookId else {
            favoritedByText = [:]
            favoritedIndexNotebookId = nil
            return
        }
        var map: [String: Int64] = [:]
        var cursor: String?
        do {
            for _ in 0..<40 {
                let page = try await api.listWords(token: session.token, notebookId: notebookId, cursor: cursor, limit: 200)
                for item in page.items {
                    let key = item.text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                    if !key.isEmpty {
                        map[key] = item.id
                    }
                }
                guard let next = page.nextCursor, !next.isEmpty else { break }
                cursor = next
            }
            favoritedByText = map
            favoritedIndexNotebookId = notebookId
        } catch {
            // Keep previous index on refresh failure.
            if noteSessionError(error) { return }
        }
    }

    /// Toggle whether [entry] lives in the default favorite notebook. Returns the resulting state, or nil on failure.
    func toggleCatalogFavorite(_ entry: VocabEntry) async -> Bool? {
        guard session != nil else {
            showLogin = true
            return nil
        }
        await ensureFavoriteIndex()
        let key = entry.text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !key.isEmpty else { return nil }
        // Re-read after the await above: the session can be gone by now.
        guard let token = session?.token else {
            showLogin = true
            return nil
        }
        if let existingId = favoritedByText[key] {
            do {
                try await api.deleteWord(token: token, id: existingId)
                favoritedByText.removeValue(forKey: key)
                if let notebookId = favoriteNotebookId, let index = notebooks.firstIndex(where: { $0.id == notebookId }) {
                    notebooks[index].wordCount = max(0, notebooks[index].wordCount - 1)
                }
                if activeNotebookId == favoriteNotebookId {
                    words.removeAll { $0.id == existingId }
                    wordTotal = max(0, wordTotal - 1)
                }
                banner = "已移出生词本"
                return false
            } catch {
                if !noteSessionError(error) {
                    banner = error.localizedDescription
                }
                return nil
            }
        }
        guard let notebookId = favoriteNotebookId else {
            banner = "还没有可用的生词本"
            return nil
        }
        return await favoriteCatalogWord(entry, to: notebookId)
    }

    /// Add a catalog word into a user notebook. Returns true on success.
    @discardableResult
    func favoriteCatalogWord(_ entry: VocabEntry, to notebookId: Int64) async -> Bool? {
        guard let session else {
            showLogin = true
            return nil
        }
        guard let notebook = notebooks.first(where: { $0.id == notebookId && !$0.isSystem }) else {
            banner = "还没有可用的生词本"
            return nil
        }
        let key = entry.text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !key.isEmpty else { return nil }
        do {
            var toSave = entry
            toSave.id = 0
            toSave.notebookId = notebookId
            let created = try await api.createWord(token: session.token, notebookId: notebookId, entry: toSave)
            if notebookId == favoriteNotebookId {
                favoritedByText[key] = created.id
            }
            if let index = notebooks.firstIndex(where: { $0.id == notebookId }) {
                notebooks[index].wordCount += 1
            }
            headsCache[notebookId] = nil
            banner = "已收藏到「\(notebook.name)」"
            if aiImageAutoGen {
                let word = entry.text
                let hint = entry.definitions.first?.label ?? ""
                Task { _ = await generateMnemonicImage(word: word, meaningHint: hint) }
            }
            return true
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
            return nil
        }
    }

    func copyCatalogNotebook(from sourceId: Int64, to notebookId: Int64, excluding: Set<Int64>) async {
        guard let session else {
            showLogin = true
            return
        }
        guard let notebook = notebooks.first(where: { $0.id == notebookId && !$0.isSystem }) else {
            banner = "还没有可用的生词本"
            return
        }
        guard catalogCopyProgress == nil else { return }
        catalogCopyProgress = CatalogCopyProgress(title: "正在收藏到「\(notebook.name)」", copied: 0, total: 0)
        do {
            var copied = 0
            let excluded = Array(excluding)
            while true {
                let page = try await api.copyNotebookWords(
                    token: session.token,
                    targetId: notebookId,
                    sourceId: sourceId,
                    excludeWordIds: excluded,
                    limit: 400
                )
                copied += page.added
                let total = max(copied + page.remaining, copied)
                catalogCopyProgress = CatalogCopyProgress(
                    title: "正在收藏到「\(notebook.name)」",
                    copied: copied,
                    total: total
                )
                if page.remaining <= 0 || page.added == 0 { break }
            }
            if copied > 0, let index = notebooks.firstIndex(where: { $0.id == notebookId }) {
                notebooks[index].wordCount += copied
            }
            headsCache[notebookId] = nil
            catalogCopyProgress = nil
            banner = copied == 0 ? "没有新单词可收藏" : "已收藏 \(copied) 个词到「\(notebook.name)」"
        } catch {
            catalogCopyProgress = nil
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    func favoriteCatalogWords(_ entries: [VocabEntry], to notebookId: Int64) async {
        if entries.count == 1, let entry = entries.first {
            _ = await favoriteCatalogWord(entry, to: notebookId)
            return
        }
        guard let session else {
            showLogin = true
            return
        }
        guard let notebook = notebooks.first(where: { $0.id == notebookId && !$0.isSystem }) else {
            banner = "还没有可用的生词本"
            return
        }
        var added = 0
        for entry in entries {
            let key = entry.text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            guard !key.isEmpty else { continue }
            var toSave = entry
            toSave.id = 0
            toSave.notebookId = notebookId
            do {
                let created = try await api.createWord(token: session.token, notebookId: notebookId, entry: toSave)
                if notebookId == favoriteNotebookId {
                    favoritedByText[key] = created.id
                }
                added += 1
            } catch {
                if noteSessionError(error) { return }
            }
        }
        if added > 0, let index = notebooks.firstIndex(where: { $0.id == notebookId }) {
            notebooks[index].wordCount += added
        }
        headsCache[notebookId] = nil
        banner = added == 0 ? "没有新单词可收藏" : "已收藏 \(added) 个词到「\(notebook.name)」"
    }

    func save(_ entry: VocabEntry) async throws -> VocabEntry {
        guard let session else {
            showLogin = true
            throw APIError(message: "登录后可收藏到生词本")
        }
        guard let notebookId = saveNotebookId else {
            throw APIError(message: "还没有可用的生词本")
        }
        let created: VocabEntry
        do {
            created = try await api.createWord(token: session.token, notebookId: notebookId, entry: entry)
        } catch {
            if let apiError = error as? APIError, apiError.isSessionInvalid {
                handle(apiError, fallback: "登录已失效")
            }
            throw error
        }
        let key = created.text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !key.isEmpty {
            favoritedByText[key] = created.id
        }
        headsCache[notebookId] = nil
        if activeNotebookId != notebookId {
            activeNotebookId = notebookId
            await loadWords()
        } else if !words.contains(where: { $0.id == created.id }) {
            words.insert(created, at: 0)
            wordTotal += 1
        }
        if let index = notebooks.firstIndex(where: { $0.id == notebookId }) {
            notebooks[index].wordCount += 1
        }
        return created
    }

    /// First launch stores 0, so no chip is highlighted. After a book is deleted, pick a remaining personal book.
    private func ensureDefaultNotebook() {
        let userBooks = notebooks.filter { !$0.isSystem }
        guard !userBooks.isEmpty else { return }
        if defaultNotebookId > 0, userBooks.contains(where: { $0.id == defaultNotebookId }) {
            return
        }
        let fallback = userBooks.first { $0.id == session?.vocabNotebookId && (session?.vocabNotebookId ?? 0) > 0 }
            ?? userBooks[0]
        setDefaultNotebookId(fallback.id)
        assignVocabNotebook(fallback.id)
    }

    /// Drop the in-memory IELTS head list once after the catalog was reordered and again after blank meanings were filled.
    private func invalidateIeltsOrderCacheIfNeeded() {
        let key = "hotwords_ielts_alpha_revision"
        guard UserDefaults.standard.integer(forKey: key) < 2 else { return }
        for book in notebooks where book.slug == "ielts" {
            headsCache[book.id] = nil
            if activeNotebookId == book.id {
                alphabetLetterIndex = [:]
            }
        }
        UserDefaults.standard.set(2, forKey: key)
    }

    func loadNotebooks() async {
        do {
            let items: [Notebook]
            if let session {
                items = try await api.listNotebooks(token: session.token)
            } else {
                items = try await api.listCatalogs()
            }
            notebooks = items
            invalidateIeltsOrderCacheIfNeeded()
            let preferred = session?.vocabNotebookId ?? 0
            if let current = activeNotebookId, items.contains(where: { $0.id == current }) {
                // keep current selection
            } else if preferred > 0, items.contains(where: { $0.id == preferred }) {
                activeNotebookId = preferred
            } else {
                activeNotebookId = items.first { !$0.isSystem }?.id ?? items.first?.id
            }
            wordsError = nil
            ensureDefaultNotebook()
            await loadWords()
            if session != nil {
                await refreshFavoritedIndex()
            } else {
                favoritedByText = [:]
            }
        } catch {
            handle(error, fallback: "词本加载失败")
        }
    }

    func createNotebook(name: String) async {
        guard let session else {
            showLogin = true
            return
        }
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            banner = "请输入生词本名称"
            return
        }
        guard trimmed.count <= 20 else {
            banner = "名称最多 20 个字"
            return
        }
        guard trimmed != Notebook.defaultName else {
            banner = "不能新建默认生词本"
            return
        }
        do {
            let created = try await api.createNotebook(token: session.token, name: trimmed)
            await loadNotebooks()
            if notebooks.contains(where: { $0.id == created.id }) {
                activeNotebookId = created.id
                await loadWords()
            }
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    func deleteNotebook(_ id: Int64) async {
        guard let session, let notebook = notebooks.first(where: { $0.id == id }), !notebook.isLocked else {
            if notebooks.first(where: { $0.id == id })?.name == Notebook.defaultName {
                banner = "默认生词本不能删除"
            } else if notebooks.first(where: { $0.id == id })?.isLocked == true {
                banner = "系统词书不能删除"
            }
            return
        }
        do {
            try await api.deleteNotebook(token: session.token, id: id)
            if activeNotebookId == id {
                activeNotebookId = nil
            }
            if defaultNotebookId == id {
                setDefaultNotebookId(0)
            }
            if session.vocabNotebookId == id {
                assignVocabNotebook(0)
            }
            headsCache[id] = nil
            await loadNotebooks()
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    private func assignVocabNotebook(_ id: Int64) {
        guard var session, session.vocabNotebookId != id else { return }
        session.vocabNotebookId = id
        self.session = session
        SessionStore.save(session)
        AccountStore.upsert(session)
        accounts = AccountStore.list()
    }

    func moveWords(ids: Set<Int64>, to targetId: Int64) async {
        guard let session else {
            showLogin = true
            return
        }
        guard activeNotebook?.isSystem != true else {
            banner = "系统词书不能修改"
            return
        }
        guard let target = notebooks.first(where: { $0.id == targetId }), !target.isSystem else {
            banner = "不能移动到系统词书"
            return
        }
        var moved = 0
        for id in ids {
            guard let entry = words.first(where: { $0.id == id }) else { continue }
            do {
                var copy = entry
                copy.id = 0
                copy.notebookId = targetId
                _ = try await api.createWord(token: session.token, notebookId: targetId, entry: copy)
                try await api.deleteWord(token: session.token, id: id)
                words.removeAll { $0.id == id }
                if let key = favoritedByText.first(where: { $0.value == id })?.key {
                    favoritedByText.removeValue(forKey: key)
                }
                moved += 1
            } catch {
                if noteSessionError(error) { return }
                banner = error.localizedDescription
                break
            }
        }
        wordTotal = max(0, wordTotal - moved)
        if let sourceId = activeNotebookId, let index = notebooks.firstIndex(where: { $0.id == sourceId }) {
            notebooks[index].wordCount = max(0, notebooks[index].wordCount - moved)
        }
        if let index = notebooks.firstIndex(where: { $0.id == targetId }) {
            notebooks[index].wordCount += moved
        }
        banner = moved == 0 ? "没有可移动的单词" : "已移动 \(moved) 个单词"
    }

    func updateDefinitions(id: Int64, definitions: [Definition], silent: Bool = false) async -> Bool {
        guard let session else {
            if !silent { showLogin = true }
            return false
        }
        let cleaned = definitions.filter { !$0.meaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        guard !cleaned.isEmpty else {
            if !silent { banner = "请至少保留一条释义" }
            return false
        }
        do {
            let updated = try await api.updateWord(token: session.token, id: id, definitions: cleaned)
            if let index = words.firstIndex(where: { $0.id == id }) {
                words[index].definitions = updated.definitions
            }
            return true
        } catch {
            if !silent, !noteSessionError(error) {
                banner = error.localizedDescription
            }
            return false
        }
    }

    /// Ordered heads for the whole notebook. Cached until the notebook changes.
    func loadWordHeads(notebookId: Int64) async -> [WordHead] {
        if let cached = headsCache[notebookId], !cached.isEmpty { return cached }
        guard canReadNotebook(notebookId) else { return [] }
        do {
            let heads = try await api.listHeads(token: session?.token, notebookId: notebookId)
            if !heads.isEmpty { headsCache[notebookId] = heads }
            return heads
        } catch {
            return []
        }
    }

    /// One full word at a natural index, without replacing the list window.
    func fetchNotebookWord(notebookId: Int64, naturalIndex: Int) async -> VocabEntry? {
        guard canReadNotebook(notebookId) else { return nil }
        do {
            let page = try await api.listWords(
                token: session?.token,
                notebookId: notebookId,
                cursor: nil,
                limit: 1,
                fromIndex: max(0, naturalIndex)
            )
            return page.items.first
        } catch {
            return nil
        }
    }

    /// Jump the loaded window so `words[0]` is the notebook entry at `index`.
    func jumpToAbsoluteIndex(_ index: Int) async {
        guard let notebookId = activeNotebookId, canReadNotebook(notebookId) else { return }
        let target = max(0, index)
        if target >= listWindowStart, target < listWindowStart + words.count { return }
        await loadSeekWindow(notebookId: notebookId, token: session?.token, fromIndex: target)
    }

    func deleteEntireNotebook(excluding: Set<Int64>) async {
        guard let session, let notebookId = activeNotebookId, let notebook = activeNotebook, !notebook.isSystem else {
            return
        }
        guard catalogCopyProgress == nil else { return }
        catalogCopyProgress = CatalogCopyProgress(title: "正在删除「\(notebook.name)」", copied: 0, total: 0)
        do {
            let deleted = try await api.deleteNotebookWords(
                token: session.token,
                notebookId: notebookId,
                excludeWordIds: Array(excluding)
            )
            catalogCopyProgress = nil
            headsCache[notebookId] = nil
            if let index = notebooks.firstIndex(where: { $0.id == notebookId }) {
                notebooks[index].wordCount = max(0, notebooks[index].wordCount - deleted)
            }
            await loadWords()
            banner = deleted == 0 ? "没有可删除的单词" : "已删除 \(deleted) 个单词"
        } catch {
            catalogCopyProgress = nil
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    func moveEntireNotebook(to targetId: Int64, excluding: Set<Int64>) async {
        guard let sourceId = activeNotebookId, let source = activeNotebook, !source.isSystem else { return }
        guard let target = notebooks.first(where: { $0.id == targetId && !$0.isSystem }) else {
            banner = "不能移动到系统词书"
            return
        }
        guard catalogCopyProgress == nil else { return }
        catalogCopyProgress = CatalogCopyProgress(title: "正在移动到「\(target.name)」", copied: 0, total: 0)
        do {
            var copied = 0
            let excluded = Array(excluding)
            guard let session else {
                catalogCopyProgress = nil
                showLogin = true
                return
            }
            while true {
                let page = try await api.copyNotebookWords(
                    token: session.token,
                    targetId: targetId,
                    sourceId: sourceId,
                    excludeWordIds: excluded,
                    limit: 400
                )
                copied += page.added
                let total = max(copied + page.remaining, copied)
                catalogCopyProgress = CatalogCopyProgress(
                    title: "正在移动到「\(target.name)」",
                    copied: copied,
                    total: total
                )
                if page.remaining <= 0 || page.added == 0 { break }
            }
            let deleted = try await api.deleteNotebookWords(
                token: session.token,
                notebookId: sourceId,
                excludeWordIds: excluded
            )
            headsCache[sourceId] = nil
            headsCache[targetId] = nil
            if let index = notebooks.firstIndex(where: { $0.id == sourceId }) {
                notebooks[index].wordCount = max(0, notebooks[index].wordCount - deleted)
            }
            if let index = notebooks.firstIndex(where: { $0.id == targetId }) {
                notebooks[index].wordCount += deleted
            }
            catalogCopyProgress = nil
            await loadWords()
            banner = deleted == 0 ? "没有可移动的单词" : "已移动 \(deleted) 个单词"
        } catch {
            catalogCopyProgress = nil
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    func deleteWord(_ id: Int64) async {
        guard let session, activeNotebook?.isSystem != true else { return }
        do {
            try await api.deleteWord(token: session.token, id: id)
            if let notebookId = activeNotebookId {
                headsCache[notebookId] = nil
            }
            if let key = favoritedByText.first(where: { $0.value == id })?.key {
                favoritedByText.removeValue(forKey: key)
            }
            words.removeAll { $0.id == id }
            wordTotal = max(0, wordTotal - 1)
            if let notebookId = activeNotebookId, let index = notebooks.firstIndex(where: { $0.id == notebookId }) {
                notebooks[index].wordCount = max(0, notebooks[index].wordCount - 1)
            }
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    func selectNotebook(_ id: Int64) async {
        guard activeNotebookId != id else { return }
        activeNotebookId = id
        alphabetLetterIndex = [:]
        listWindowStart = 0
        pendingScrollWordId = nil
        await loadWords()
        await refreshLetterIndex()
    }

    func loadWords() async {
        guard let notebookId = activeNotebookId, canReadNotebook(notebookId) else {
            words = []
            wordTotal = 0
            nextCursor = nil
            listWindowStart = 0
            return
        }
        wordsLoading = true
        wordsError = nil
        defer { wordsLoading = false }
        do {
            let page = try await api.listWords(token: session?.token, notebookId: notebookId, cursor: nil)
        words = page.items
        wordTotal = page.total
        nextCursor = page.nextCursor
        listWindowStart = 0
        earlierLoadToken += 1
        earlierLoadInFlight = false
        if alphabetLetterIndex.isEmpty {
                await refreshLetterIndex()
            }
        } catch {
            words = []
            handle(error, fallback: "单词加载失败")
        }
    }

    func loadMoreWords() async {
        guard let notebookId = activeNotebookId, canReadNotebook(notebookId), let cursor = nextCursor, !wordsLoading else { return }
        wordsLoading = true
        defer { wordsLoading = false }
        do {
            let page = try await api.listWords(token: session?.token, notebookId: notebookId, cursor: cursor)
            let existing = Set(words.map(\.id))
            words.append(contentsOf: page.items.filter { !existing.contains($0.id) })
            if page.total > 0 { wordTotal = page.total }
            nextCursor = page.nextCursor
        } catch {
            handle(error, fallback: "加载更多失败")
        }
    }

    /// Pull the page above a letter-jump window so scrolling up reaches earlier letters.
    /// The returned anchor is the word that was at the top; keep it on screen after prepending.
    func loadEarlierWords() async -> EarlierWordsResult {
        guard !earlierLoadInFlight, listWindowStart > 0 else { return .busy }
        guard let notebookId = activeNotebookId, canReadNotebook(notebookId) else { return .unchanged }
        earlierLoadInFlight = true
        earlierLoadToken += 1
        let token = earlierLoadToken
        let generation = windowSeekGeneration
        let pageSize = 100
        let start = max(0, listWindowStart - pageSize)
        let limit = listWindowStart - start
        let anchorId = words.first?.id
        guard limit > 0, let anchorId else {
            earlierLoadInFlight = false
            return .unchanged
        }
        do {
            let page = try await api.listWords(
                token: session?.token,
                notebookId: notebookId,
                cursor: nil,
                limit: limit,
                fromIndex: start
            )
            guard token == earlierLoadToken, generation == windowSeekGeneration, activeNotebookId == notebookId else {
                if token == earlierLoadToken { earlierLoadInFlight = false }
                return .unchanged
            }
            let existing = Set(words.map(\.id))
            let fresh = page.items.filter { !existing.contains($0.id) }
            guard !fresh.isEmpty else {
                if start == 0 { listWindowStart = 0 }
                earlierLoadInFlight = false
                return .unchanged
            }
            if page.total > 0 { wordTotal = page.total }
            words.insert(contentsOf: fresh, at: 0)
            listWindowStart = start
            return .prepended(anchorId: anchorId, token: token)
        } catch {
            if token == earlierLoadToken { earlierLoadInFlight = false }
            if Self.isCancellation(error) || Task.isCancelled { return .unchanged }
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
            return .unchanged
        }
    }

    func finishEarlierLoad(token: Int) {
        guard token == earlierLoadToken else { return }
        earlierLoadInFlight = false
    }

    func refreshLetterIndex() async {
        guard let notebookId = activeNotebookId, canReadNotebook(notebookId) else {
            alphabetLetterIndex = [:]
            return
        }
        do {
            alphabetLetterIndex = try await api.letterIndex(token: session?.token, notebookId: notebookId)
        } catch {
            if Self.isCancellation(error) || Task.isCancelled { return }
            if noteSessionError(error) { return }
            // Keep previous index if refresh fails.
        }
    }

    /// Fast alphabet seek — jump via `fromIndex` when the letter is outside the loaded window.
    func seekAlphabetLetter(_ letter: Character) async {
        guard let notebookId = activeNotebookId, canReadNotebook(notebookId) else { return }
        if alphabetLetterIndex.isEmpty {
            await refreshLetterIndex()
        }
        guard !Task.isCancelled else { return }
        let upper = Character(letter.uppercased())
        if let local = localIndex(for: upper) {
            pendingScrollWordId = words[local].id
            return
        }
        guard let target = absoluteIndex(for: upper) else { return }
        guard !Task.isCancelled else { return }

        // Always jump with fromIndex — never page-walk thousands of rows (that froze the UI).
        await loadSeekWindow(notebookId: notebookId, token: session?.token, fromIndex: max(0, target))
        guard !Task.isCancelled, activeNotebookId == notebookId else { return }
        if let local = localIndex(for: upper) {
            pendingScrollWordId = words[local].id
        } else if !words.isEmpty {
            pendingScrollWordId = words[0].id
        }
    }

    /// System word books can be read without a session. Personal books cannot.
    private func canReadNotebook(_ notebookId: Int64) -> Bool {
        if session != nil { return true }
        return notebooks.first { $0.id == notebookId }?.isSystem == true
    }

    private func loadSeekWindow(notebookId: Int64, token: String?, fromIndex: Int) async {
        windowSeekGeneration += 1
        earlierLoadToken += 1
        earlierLoadInFlight = false
        let generation = windowSeekGeneration
        do {
            let page = try await api.listWords(
                token: token,
                notebookId: notebookId,
                cursor: nil,
                fromIndex: fromIndex
            )
            guard generation == windowSeekGeneration else { return }
            guard !Task.isCancelled, activeNotebookId == notebookId else { return }
            listWindowStart = page.fromIndex > 0 ? page.fromIndex : fromIndex
            words = page.items
            wordTotal = page.total
            nextCursor = page.nextCursor
        } catch {
            // Finger moved to another letter — cancelling the prior seek is expected.
            if Self.isCancellation(error) || Task.isCancelled { return }
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    private static func isCancellation(_ error: Error) -> Bool {
        if error is CancellationError { return true }
        if let urlError = error as? URLError, urlError.code == .cancelled { return true }
        if let apiError = error as? APIError {
            if apiError.code == "cancelled" { return true }
            let message = apiError.message
            if message == "已取消" || message.lowercased().contains("cancel") { return true }
        }
        let ns = error as NSError
        return ns.domain == NSURLErrorDomain && ns.code == NSURLErrorCancelled
    }

    private func absoluteIndex(for letter: Character) -> Int? {
        if let exact = alphabetLetterIndex[letter] { return exact }
        let key = letterSortKey(letter)
        let next = alphabetLetterIndex
            .filter { letterSortKey($0.key) > key }
            .min(by: { letterSortKey($0.key) < letterSortKey($1.key) })
        return next?.value
    }

    private func localIndex(for letter: Character) -> Int? {
        let texts = words.map(\.text)
        if let exact = texts.firstIndex(where: { wordInitialLetter($0) == letter }) {
            return exact
        }
        if let abs = absoluteIndex(for: letter) {
            let local = abs - listWindowStart
            if local >= 0, local < words.count { return local }
        }
        // Do not fall through to "next letter in window" — that falsely reports a hit
        // and prevents fromIndex jumps for letters not yet loaded.
        return nil
    }

    private func wordInitialLetter(_ text: String) -> Character {
        guard let first = text.trimmingCharacters(in: .whitespacesAndNewlines).uppercased().first else {
            return "#"
        }
        return ("A"..."Z").contains(first) ? first : "#"
    }

    private func letterSortKey(_ letter: Character) -> Int {
        letter == "#" ? 26 : Int(letter.asciiValue ?? 65) - 65
    }

    private func handle(_ error: Error, fallback: String) {
        if let apiError = error as? APIError, apiError.isSessionInvalid {
            let message = apiError.message.isEmpty ? "登录已失效，请重新登录" : apiError.message
            logout()
            banner = message
            showLogin = true
            return
        }
        wordsError = (error as? LocalizedError)?.errorDescription ?? fallback
    }
}
