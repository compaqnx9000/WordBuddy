import Foundation
import UIKit

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
    /// Notebook id → full ordered heads, used to shuffle the whole catalog.
    private var headsCache: [Int64: [WordHead]] = [:]
    @Published var showLogin = false
    @Published var banner: String?
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
    /// Lowercased word text → id in the user's 生词本 (for catalog swipe favorite).
    @Published private(set) var favoritedByText: [String: Int64] = [:]
    @Published var checkIn = CheckInState()
    @Published var rewardVideo = RewardVideoOffer()
    @Published var rewardBusy = false
    @Published var accounts: [RememberedAccount] = []
    @Published var pendingLookup: String?

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

    func login(phone: String, code: String) async throws -> AuthResult {
        try await api.login(phone: phone, code: code)
    }

    func loginWithPassword(phone: String, password: String) async throws -> AuthResult {
        try await api.loginWithPassword(phone: phone, password: password)
    }

    func register(phone: String, code: String, password: String, inviteCode: String?) async throws -> AuthResult {
        try await api.register(phone: phone, code: code, password: password, inviteCode: inviteCode)
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
        accounts = AccountStore.list()
    }

    func setAccent(_ value: Accent) {
        accent = value
        SettingsStore.accent = value
    }

    func setAccentStyle(_ value: AccentStyle) {
        SettingsStore.accentStyle = value
        accentStyle = value
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

    func generateMnemonicImage(word: String, meaningHint: String) async -> Bool {
        guard let session else {
            showLogin = true
            return false
        }
        let text = word.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return false }
        do {
            let result = try await api.generateMnemonicImage(
                token: session.token,
                word: text,
                meaningHint: meaningHint,
                provider: imageProvider
            )
            MnemonicImageStore.save(word: text, data: result.image)
            if let balance = result.balance {
                applyPoints(balance)
            }
            mnemonicRevision += 1
            banner = result.pointsSpent > 0 ? "配图已生成，消耗 \(result.pointsSpent) 积分" : "配图已生成"
            return true
        } catch {
            if !noteSessionError(error) {
                let apiError = error as? APIError
                if apiError?.httpCode == 402 || apiError?.code == "INSUFFICIENT_POINTS" {
                    let message = apiError?.message ?? "积分不足"
                    banner = message.contains("购买") ? message : message + "，请先购买积分"
                } else {
                    banner = error.localizedDescription
                }
            }
            return false
        }
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
            try await api.changePassword(token: session.token, oldPassword: oldPassword, newPassword: newPassword)
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
        guard let notebookId = exportNotebookId, let notebook = notebooks.first(where: { $0.id == notebookId }) else {
            banner = "还没有可导出的生词本"
            return nil
        }
        do {
            let entries = try await allWords(token: session.token, notebookId: notebookId)
            return try VocabTransfer.exportJSON(notebook: notebook, entries: entries)
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
            return nil
        }
    }

    func importNotebook(data: Data) async {
        guard session != nil else {
            showLogin = true
            return
        }
        guard let notebookId = saveNotebookId else {
            banner = "还没有可导入的生词本"
            return
        }
        if notebooks.first(where: { $0.id == notebookId })?.isSystem == true {
            banner = "系统词书不能导入"
            return
        }
        do {
            let entries = try VocabTransfer.importEntries(from: data)
            guard !entries.isEmpty else {
                banner = "备份里没有单词"
                return
            }
            if activeNotebookId != notebookId {
                activeNotebookId = notebookId
                await loadWords()
            }
            var known = Set(words.map { $0.text.lowercased() })
            var added = 0
            for entry in entries {
                let key = entry.text.lowercased()
                if known.contains(key) { continue }
                _ = try await save(entry)
                known.insert(key)
                added += 1
            }
            banner = added == 0 ? "这些单词已经在生词本里" : "已导入 \(added) 个单词"
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
    }

    private var exportNotebookId: Int64? {
        if let active = activeNotebook, !active.isSystem { return active.id }
        return saveNotebookId
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
        guard !key.isEmpty else { return false }
        if favoritedByText[key] != nil { return true }
        let target = saveNotebookId
        return words.contains { entry in
            entry.text.caseInsensitiveCompare(text) == .orderedSame &&
                (target == nil || entry.notebookId == target)
        }
    }

    func refreshFavoritedIndex() async {
        guard let session, let notebookId = saveNotebookId else {
            favoritedByText = [:]
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
        } catch {
            // Keep previous index on refresh failure.
            if noteSessionError(error) { return }
        }
    }

    /// Toggle whether [entry] lives in the user's 生词本. Returns resulting favorited state, or nil on failure.
    func toggleCatalogFavorite(_ entry: VocabEntry) async -> Bool? {
        guard session != nil else {
            showLogin = true
            return nil
        }
        let key = entry.text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !key.isEmpty else { return nil }
        if let existingId = favoritedByText[key] {
            do {
                try await api.deleteWord(token: session!.token, id: existingId)
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
        guard let session, let notebookId = favoriteNotebookId else {
            banner = "还没有可用的生词本"
            return nil
        }
        do {
            var toSave = entry
            toSave.id = 0
            toSave.notebookId = notebookId
            let created = try await api.createWord(token: session.token, notebookId: notebookId, entry: toSave)
            favoritedByText[key] = created.id
            if let index = notebooks.firstIndex(where: { $0.id == notebookId }) {
                notebooks[index].wordCount += 1
            }
            banner = "已加入生词本"
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

    /// First launch stores 0, so no chip is highlighted. Pick 生词本, then any user notebook.
    private func ensureDefaultNotebook() {
        let userBooks = notebooks.filter { !$0.isSystem }
        guard !userBooks.isEmpty else { return }
        if defaultNotebookId > 0, userBooks.contains(where: { $0.id == defaultNotebookId }) {
            return
        }
        let fallback = userBooks.first { $0.name == Notebook.defaultName }
            ?? userBooks.first { $0.id == session?.vocabNotebookId }
            ?? userBooks[0]
        setDefaultNotebookId(fallback.id)
    }

    func loadNotebooks() async {
        guard let session else { return }
        do {
            let items = try await api.listNotebooks(token: session.token)
            notebooks = items
            let preferred = session.vocabNotebookId
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
            await refreshFavoritedIndex()
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
        guard let session, let notebook = notebooks.first(where: { $0.id == id }), !notebook.isSystem else { return }
        do {
            try await api.deleteNotebook(token: session.token, id: id)
            if activeNotebookId == id {
                activeNotebookId = nil
            }
            await loadNotebooks()
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
        }
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

    func updateDefinitions(id: Int64, definitions: [Definition]) async -> Bool {
        guard let session else {
            showLogin = true
            return false
        }
        let cleaned = definitions.filter { !$0.meaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        guard !cleaned.isEmpty else {
            banner = "请至少保留一条释义"
            return false
        }
        do {
            let updated = try await api.updateWord(token: session.token, id: id, definitions: cleaned)
            if let index = words.firstIndex(where: { $0.id == id }) {
                words[index].definitions = updated.definitions
            }
            return true
        } catch {
            if !noteSessionError(error) {
                banner = error.localizedDescription
            }
            return false
        }
    }

    /// Ordered heads for the whole notebook. Cached until the notebook changes.
    func loadWordHeads(notebookId: Int64) async -> [WordHead] {
        if let cached = headsCache[notebookId], !cached.isEmpty { return cached }
        guard let token = session?.token else { return [] }
        do {
            let heads = try await api.listHeads(token: token, notebookId: notebookId)
            if !heads.isEmpty { headsCache[notebookId] = heads }
            return heads
        } catch {
            return []
        }
    }

    /// One full word at a natural index, without replacing the list window.
    func fetchNotebookWord(notebookId: Int64, naturalIndex: Int) async -> VocabEntry? {
        guard let token = session?.token else { return nil }
        do {
            let page = try await api.listWords(
                token: token,
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
        guard let session, let notebookId = activeNotebookId else { return }
        let target = max(0, index)
        if target >= listWindowStart, target < listWindowStart + words.count { return }
        await loadSeekWindow(notebookId: notebookId, token: session.token, fromIndex: target)
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
        guard let session, let notebookId = activeNotebookId else {
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
            let page = try await api.listWords(token: session.token, notebookId: notebookId, cursor: nil)
            words = page.items
            wordTotal = page.total
            nextCursor = page.nextCursor
            listWindowStart = 0
            if alphabetLetterIndex.isEmpty {
                await refreshLetterIndex()
            }
        } catch {
            words = []
            handle(error, fallback: "单词加载失败")
        }
    }

    func loadMoreWords() async {
        guard let session, let notebookId = activeNotebookId, let cursor = nextCursor, !wordsLoading else { return }
        wordsLoading = true
        defer { wordsLoading = false }
        do {
            let page = try await api.listWords(token: session.token, notebookId: notebookId, cursor: cursor)
            let existing = Set(words.map(\.id))
            words.append(contentsOf: page.items.filter { !existing.contains($0.id) })
            wordTotal = page.total
            nextCursor = page.nextCursor
        } catch {
            handle(error, fallback: "加载更多失败")
        }
    }

    func refreshLetterIndex() async {
        guard let session, let notebookId = activeNotebookId else {
            alphabetLetterIndex = [:]
            return
        }
        do {
            alphabetLetterIndex = try await api.letterIndex(token: session.token, notebookId: notebookId)
        } catch {
            if Self.isCancellation(error) || Task.isCancelled { return }
            if noteSessionError(error) { return }
            // Keep previous index if refresh fails.
        }
    }

    /// Fast alphabet seek — jump via `fromIndex` when the letter is outside the loaded window.
    func seekAlphabetLetter(_ letter: Character) async {
        guard let session, let notebookId = activeNotebookId else { return }
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
        await loadSeekWindow(notebookId: notebookId, token: session.token, fromIndex: max(0, target))
        guard !Task.isCancelled, activeNotebookId == notebookId else { return }
        if let local = localIndex(for: upper) {
            pendingScrollWordId = words[local].id
        } else if !words.isEmpty {
            pendingScrollWordId = words[0].id
        }
    }

    private func loadSeekWindow(notebookId: Int64, token: String, fromIndex: Int) async {
        windowSeekGeneration += 1
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
