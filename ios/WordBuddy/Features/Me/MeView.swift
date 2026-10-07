import AVFoundation
import AVKit
import LocalAuthentication
import SwiftUI
import UIKit
import UniformTypeIdentifiers
import UserNotifications

struct MeView: View {
    var mallCoversTabBar: Binding<Bool> = .constant(false)

    @EnvironmentObject private var model: AppModel
    @State private var showExport = false
    @State private var showImport = false
    @State private var exportDocument = VocabFile(data: Data())
    @State private var transferring = false
    @State private var showHelp = false
    @State private var showAbout = false
    @State private var showNetworkRegion = false
    @State private var networkRefreshBusy = false
    @State private var makeupDate: String?
    @State private var path: [MeRoute] = []

    private let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(spacing: 14) {
                    heroCard
                    checkInCard
                    rewardsMenu
                    // 上架前关闭「工具」。恢复时取消下一行注释。
                    // toolsMenu
                    systemMenu
                    Text("版本 \(appVersion)")
                        .font(.caption)
                        .foregroundStyle(Theme.onSurfaceVariant.opacity(0.65))
                        .frame(maxWidth: .infinity)
                        .padding(.top, 4)
                        .padding(.bottom, 8)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .stellarScreenBackground()
            .navigationTitle("我的")
            .navigationBarTitleDisplayMode(.inline)
            .themeNavigationBar()
            .navigationDestination(for: MeRoute.self) { route in
                switch route {
                case .profile: ProfileEditView()
                case .settings: SettingsView()
                case .buyPoints: BuyPointsView()
                case .mall: PointsMallView()
                case .invite: InviteView()
                case .favorites: ShortFavoritesView()
                case .tools: ToolsPlaceholderView()
                case .deletion: AccountDeletionView()
                case .switchAccount: SwitchAccountView()
                case .withdraw: WithdrawView()
                }
            }
        }
        .onChange(of: path) { _, routes in
            mallCoversTabBar.wrappedValue = routes.contains(.mall)
        }
        .task(id: model.session?.userId) {
            await model.refreshMe()
        }
        .fileExporter(
            isPresented: $showExport,
            document: exportDocument,
            contentType: .json,
            defaultFilename: "wordbuddy-vocab"
        ) { result in
            if case .failure(let error) = result {
                model.banner = error.localizedDescription
            }
        }
        .fileImporter(isPresented: $showImport, allowedContentTypes: [.json]) { result in
            switch result {
            case .success(let url):
                Task { await importFile(url) }
            case .failure(let error):
                model.banner = error.localizedDescription
            }
        }
        // 上架前关闭「看广告补签」。恢复时取消下面这段注释，并把 CheckInDaySlot.build 里的 canMakeup 改回可补签。
        // .alert("补签", isPresented: makeupPresented) {
        //     Button("看广告补签") {
        //         if let makeupDate {
        //             let date = makeupDate
        //             self.makeupDate = nil
        //             Task { await model.makeupAfterAd(date: date) }
        //         }
        //     }
        //     Button("取消", role: .cancel) { makeupDate = nil }
        // } message: {
        //     Text("看完激励视频后补签 \(makeupDate.map { ShanghaiDate.dayValue(of: $0) } ?? 0) 日。")
        // }
        .sheet(isPresented: $showHelp) {
            infoSheet(
                title: "帮助与反馈",
                body: "词搭子用于查词、生词本、卡片背诵、短视频和播客。底部五个 Tab 即可使用这些功能。登录后可以同步生词、签到和积分兑礼。\n\n遇到问题请发邮件至 hi@wordbuddy.cc，或拨打 18500090601。也可在「关于词搭子」中查看《隐私保护指引》。"
            )
        }
        .fullScreenCover(isPresented: $showAbout) {
            AboutWordBuddyView()
        }
        .overlay {
            if showNetworkRegion {
                NetworkRegionDialog(
                    regionLabel: model.session?.networkRegion,
                    regionDetail: model.session?.networkRegionDetail,
                    avatarImage: model.avatarImage,
                    busy: networkRefreshBusy,
                    onDismiss: {
                        guard !networkRefreshBusy else { return }
                        showNetworkRegion = false
                    },
                    onRefresh: {
                        Task {
                            networkRefreshBusy = true
                            await model.refreshNetworkRegion()
                            networkRefreshBusy = false
                        }
                    }
                )
            }
        }
    }

    private var makeupPresented: Binding<Bool> {
        Binding(get: { makeupDate != nil }, set: { if !$0 { makeupDate = nil } })
    }

    private var loggedIn: Bool { model.session != nil }

    private var profileSignature: String? {
        guard loggedIn else { return nil }
        let text = model.session?.signature?
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard let text, !text.isEmpty else { return nil }
        return text
    }

    /// Words saved in every personal notebook, not only the open one.
    private var collectedWordCount: Int {
        model.notebooks.reduce(0) { total, book in
            guard !book.isSystem else { return total }
            let count = book.id == model.activeNotebookId
                ? max(book.wordCount, model.wordTotal)
                : book.wordCount
            return total + count
        }
    }

    private var heroCard: some View {
        VStack(spacing: 0) {
            ZStack {
                LinearGradient(
                    colors: [
                        Theme.cyan.opacity(0.55),
                        Theme.pink.opacity(0.35),
                        Theme.cyanBright.opacity(0.4),
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                DashedCircles()
                    .stroke(Color.white.opacity(0.22), style: StrokeStyle(lineWidth: 1.5, dash: [8, 7]))
                    .allowsHitTesting(false)
            }
            .frame(height: 88)
            .clipped()
            .allowsHitTesting(false)

            VStack(spacing: 0) {
                ZStack(alignment: .bottomTrailing) {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [Theme.cyan.opacity(0.9), Theme.cyanBright.opacity(0.7)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .overlay {
                            if let image = model.avatarImage {
                                Image(uiImage: image)
                                    .resizable()
                                    .scaledToFill()
                            } else {
                                Image(systemName: "face.smiling")
                                    .font(.system(size: 34, weight: .semibold))
                                    .foregroundStyle(Theme.onPrimary.opacity(0.85))
                            }
                        }
                        .overlay(Circle().stroke(Theme.surfaceContainer, lineWidth: 3))
                        .clipShape(Circle())
                        .frame(width: 84, height: 84)
                    Circle()
                        .fill(Theme.surfaceHigh)
                        .overlay {
                            Image(systemName: "pencil")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Theme.onSurfaceVariant)
                        }
                        .overlay(Circle().stroke(Theme.outline.opacity(0.4), lineWidth: 1))
                        .frame(width: 26, height: 26)
                        .offset(x: 2, y: 2)
                }
                .offset(y: -36)
                .padding(.bottom, -22)
                .onTapGesture {
                    if loggedIn {
                        path.append(MeRoute.profile)
                    } else {
                        model.openLogin()
                    }
                }

                HStack(spacing: 8) {
                    Text(model.session?.displayNickname ?? "未登录")
                        .font(.title2.weight(.bold))
                        .foregroundStyle(Theme.onSurface)
                        .lineLimit(1)
                    if loggedIn {
                        Text("Lv.\(model.session?.level ?? 0)")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.cyan)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Theme.cyan.opacity(0.16), in: Capsule())
                            .overlay(Capsule().stroke(Theme.cyan.opacity(0.45), lineWidth: 1))
                    }
                }
                .padding(.top, 4)

                Text(loggedIn ? "@\(model.session?.phone ?? "")" : "@未登录")
                    .font(.subheadline)
                    .foregroundStyle(Theme.cyan)
                    .padding(.top, 6)

                if let signature = profileSignature {
                    Text(signature)
                        .font(.caption)
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                        .padding(.top, 6)
                        .padding(.horizontal, 8)
                }

                Text(
                    loggedIn
                        ? "已收藏 \(collectedWordCount) 词 · 积分 \(model.checkIn.totalPoints)"
                        : "登录后同步收藏与积分"
                )
                .font(.caption)
                .foregroundStyle(Theme.onSurfaceVariant)
                .padding(.top, 6)

                HStack(spacing: 10) {
                    pillButton(icon: "person", title: loggedIn ? "个人资料" : "登录账号") {
                        if loggedIn {
                            path.append(MeRoute.profile)
                        } else {
                            model.openLogin()
                        }
                    }
                    pillButton(icon: "gearshape", title: "设置") {
                        path.append(MeRoute.settings)
                    }
                }
                .padding(.top, 12)
                .padding(.bottom, 16)
            }
            .padding(.horizontal, 18)
        }
        .glassPanel()
    }

    private func pillButton(icon: String, title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.subheadline)
                Text(title)
                    .font(.subheadline.weight(.medium))
            }
            .foregroundStyle(Theme.onSurface)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(Theme.surfaceHigh.opacity(0.45), in: Capsule())
            .overlay(Capsule().stroke(Theme.outline.opacity(0.45), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var checkInCard: some View {
        let month = ShanghaiDate.monthValue()
        let slots = checkInSlots
        return VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Theme.gold.opacity(0.18))
                    .frame(width: 36, height: 36)
                    .overlay {
                        Image(systemName: "calendar")
                            .foregroundStyle(Theme.gold)
                    }
                VStack(alignment: .leading, spacing: 2) {
                    Text("每日签到 · \(month)月")
                        .font(.headline)
                        .foregroundStyle(Theme.onSurface)
                    Text(checkInSubtitle)
                        .font(.caption)
                        .foregroundStyle(Theme.onSurfaceVariant.opacity(0.9))
                }
                Spacer(minLength: 8)
                Button(checkInButtonTitle) {
                    if !loggedIn {
                        model.showLogin = true
                    } else if !model.checkIn.checkedInToday {
                        Task { await model.checkInToday() }
                    }
                }
                .font(.caption.weight(.bold))
                .foregroundStyle(checkInButtonEnabled ? Theme.onPrimary : Theme.onSurfaceVariant)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(checkInButtonEnabled ? Theme.gold : Theme.surfaceHigh, in: Capsule())
                .disabled(!checkInButtonEnabled && loggedIn)
                .buttonStyle(.plain)
            }

            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(slots) { slot in
                            checkInDayCell(slot)
                                .frame(width: 48)
                                .id(slot.id)
                        }
                    }
                    .padding(.horizontal, 2)
                }
                .onAppear {
                    if let today = slots.first(where: \.isToday)?.id {
                        proxy.scrollTo(today, anchor: .center)
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .glassPanel()
    }

    private var checkInSubtitle: String {
        if !loggedIn { return "登录后签到，积分将同步到云端" }
        if model.checkIn.streakDays > 0 {
            return "已连签 \(model.checkIn.streakDays) 天 · 左右滑动查看本月 · 累计 \(model.checkIn.totalPoints) 分"
        }
        return "左右滑动查看本月 · 累计 \(model.checkIn.totalPoints) 分"
    }

    private var checkInButtonTitle: String {
        if !loggedIn { return "登录签到" }
        if model.checkIn.checkedInToday { return "已签到" }
        return "签到 +\(model.checkIn.todayReward)"
    }

    private var checkInButtonEnabled: Bool {
        !loggedIn || !model.checkIn.checkedInToday
    }

    private var checkInSlots: [CheckInDaySlot] {
        CheckInDaySlot.build(state: model.checkIn)
    }

    private func checkInDayCell(_ slot: CheckInDaySlot) -> some View {
        Button {
            if !loggedIn {
                model.showLogin = true
            } else if slot.isClaimTarget {
                Task { await model.checkInToday() }
            }
            // 上架前不提供补签。恢复时取消下面三行注释。
            // else if slot.canMakeup {
            //     makeupDate = slot.date
            // }
        } label: {
            VStack(spacing: 6) {
                Text(slot.isToday ? "今天" : "\(slot.day)日")
                    .font(.system(size: 10, weight: slot.isToday ? .bold : .medium))
                    .foregroundStyle(slot.isToday ? Theme.cyan : Theme.onSurfaceVariant)
                ZStack {
                    Circle()
                        .fill(slot.circleColor)
                        .frame(width: 28, height: 28)
                    if slot.claimed {
                        Image(systemName: "checkmark")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(Theme.onPrimary)
                    } else {
                        Text("\(slot.reward)")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(slot.rewardColor)
                    }
                }
                Text(slot.footer)
                    .font(.system(size: 9))
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.8))
                    .lineLimit(1)
            }
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity)
            .background(slot.background, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay {
                if slot.isToday {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(Theme.cyan.opacity(0.55), lineWidth: 1)
                }
            }
        }
        .buttonStyle(.plain)
        .disabled(slot.claimed || slot.isFuture)
    }

    // 上架前关闭「看视频领积分」。恢复时取消注释。
    // private var rewardVideoSubtitle: String {
    //     guard loggedIn else { return "登录后每日可领" }
    //     return "剩余 \(model.rewardVideo.remaining)/\(model.rewardVideo.dailyLimit) · +\(model.rewardVideo.pointsPerWatch)"
    // }

    private var rewardsMenu: some View {
        menuCard {
            // 上架前关闭「充值积分」。恢复时取消下面两行注释。
            // menuRow("creditcard", Theme.pink, "充值积分", "测试价 ¥0.10") {
            //     guardRequireLogin { path.append(MeRoute.buyPoints) }
            // }
            // menuDivider()
            // menuRow("play.circle", Theme.gold, "看视频领积分", rewardVideoSubtitle) {
            //     Task { await model.watchRewardVideo() }
            // }
            // menuDivider()
            menuRow("gift", Theme.gold, "积分兑礼", "可用 \(model.checkIn.totalPoints) 分") {
                path.append(MeRoute.mall)
            }
            menuDivider()
            menuRow("bookmark", Theme.cyan, "短视频收藏", "可取消收藏") {
                guardRequireLogin { path.append(MeRoute.favorites) }
            }
            menuDivider()
            menuRow("person.badge.plus", Theme.cyan, "邀请好友", "各得积分") {
                if let buddyId = model.session?.buddyId, !buddyId.isEmpty {
                    path.append(MeRoute.invite)
                } else if loggedIn {
                    model.banner = "请等待搭子号分配"
                } else {
                    model.showLogin = true
                }
            }
        }
    }

    private var toolsMenu: some View {
        menuCard {
            menuRow("wrench.and.screwdriver", Theme.cyan, "工具", nil) {
                path.append(MeRoute.tools)
            }
        }
    }

    private var systemMenu: some View {
        menuCard {
            Button {
                Task { await exportNotebook() }
            } label: {
                menuRowLabel("square.and.arrow.up", Theme.cyan, "导出词库", transferring ? "导出中…" : "全部生词本，不含图片")
            }
            .buttonStyle(.plain)
            .disabled(transferring)
            menuDivider()
            Button {
                showImport = true
            } label: {
                menuRowLabel("square.and.arrow.down", Theme.pink, "导入词库", "恢复生词本")
            }
            .buttonStyle(.plain)
            .disabled(transferring)
            menuDivider()
            Button { showHelp = true } label: {
                menuRowLabel("questionmark.circle", Theme.gold, "帮助与反馈", nil)
            }
            .buttonStyle(.plain)
            menuDivider()
            Button { showAbout = true } label: {
                menuRowLabel("info.circle", Theme.cyan, "关于词搭子", "v\(appVersion)")
            }
            .buttonStyle(.plain)
            menuDivider()
            Button {
                if loggedIn {
                    showNetworkRegion = true
                } else {
                    model.showLogin = true
                }
            } label: {
                menuRowLabel(
                    "globe",
                    Theme.cyan,
                    "网络属地",
                    model.session?.networkRegion?.nilIfEmpty ?? "查看说明"
                )
            }
            .buttonStyle(.plain)
        }
    }

    private func menuCard<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(spacing: 0) {
            content()
        }
        .padding(.vertical, 2)
        .glassPanel()
    }

    private func menuDivider() -> some View {
        Divider()
            .overlay(Theme.outline.opacity(0.45))
            .padding(.leading, 56)
    }

    private func menuRow(
        _ icon: String,
        _ tint: Color,
        _ title: String,
        _ trailing: String?,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            menuRowLabel(icon, tint, title, trailing)
        }
        .buttonStyle(.plain)
    }

    private func menuRowLabel(_ icon: String, _ tint: Color, _ title: String, _ trailing: String?) -> some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(tint.opacity(0.16))
                .frame(width: 32, height: 32)
                .overlay {
                    Image(systemName: icon)
                        .font(.subheadline)
                        .foregroundStyle(tint)
                }
            Text(title)
                .font(.body)
                .foregroundStyle(Theme.onSurface)
            Spacer()
            if let trailing, !trailing.isEmpty {
                Text(trailing)
                    .font(.subheadline)
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.85))
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .contentShape(Rectangle())
    }

    private func guardRequireLogin(_ action: () -> Void) {
        guard loggedIn else {
            model.showLogin = true
            return
        }
        action()
    }

    private func infoSheet(title: String, body: String) -> some View {
        NavigationStack {
            ScrollView {
                Text(body)
                    .foregroundStyle(Theme.onSurface)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
            }
            .stellarScreenBackground()
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .themeNavigationBar()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") {
                        showHelp = false
                        showAbout = false
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func exportNotebook() async {
        transferring = true
        defer { transferring = false }
        guard let data = await model.exportNotebookData() else { return }
        exportDocument = VocabFile(data: data)
        showExport = true
    }

    private func importFile(_ url: URL) async {
        transferring = true
        defer { transferring = false }
        let accessed = url.startAccessingSecurityScopedResource()
        defer { if accessed { url.stopAccessingSecurityScopedResource() } }
        do {
            let data = try Data(contentsOf: url)
            await model.importNotebook(data: data)
        } catch {
            model.banner = error.localizedDescription
        }
    }
}

private enum MeRoute: Hashable {
    case profile, settings, buyPoints, mall, invite, favorites, tools, deletion, switchAccount, withdraw
}

private struct DashedCircles: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addEllipse(in: CGRect(
            x: rect.width * 0.15 - rect.height * 0.55,
            y: rect.height * 0.2 - rect.height * 0.55,
            width: rect.height * 1.1,
            height: rect.height * 1.1
        ))
        path.addEllipse(in: CGRect(
            x: rect.width * 0.15 - rect.height * 0.85,
            y: rect.height * 0.2 - rect.height * 0.85,
            width: rect.height * 1.7,
            height: rect.height * 1.7
        ))
        path.addEllipse(in: CGRect(
            x: rect.width * 0.92 - rect.height * 0.7,
            y: rect.height * 1.1 - rect.height * 0.7,
            width: rect.height * 1.4,
            height: rect.height * 1.4
        ))
        return path
    }
}

private struct CheckInDaySlot: Identifiable {
    var date: String
    var day: Int
    var reward: Int
    var isToday: Bool
    var claimed: Bool
    var isClaimTarget: Bool
    var canMakeup: Bool
    var isFuture: Bool

    var id: String { date }

    var background: Color {
        if isClaimTarget { return Theme.gold.opacity(0.14) }
        if claimed { return Theme.cyan.opacity(0.10) }
        if canMakeup { return Theme.surfaceHigh.opacity(0.9) }
        return Theme.surfaceHigh.opacity(0.55)
    }

    var circleColor: Color {
        if claimed { return Theme.cyan }
        if isClaimTarget { return Theme.gold }
        if canMakeup { return Theme.onSurfaceVariant.opacity(0.28) }
        return Theme.surfaceContainer
    }

    var rewardColor: Color {
        if isClaimTarget { return Theme.onPrimary }
        if canMakeup { return Theme.onSurfaceVariant }
        return Theme.onSurfaceVariant.opacity(0.7)
    }

    var footer: String {
        if claimed { return "已领取" }
        if isClaimTarget { return "+\(reward)分" }
        if canMakeup { return "补签" }
        if isFuture { return "待签到" }
        return "未签"
    }

    static func build(state: CheckInState) -> [CheckInDaySlot] {
        let today = ShanghaiDate.todayString()
        let claimed = Set(state.recentDates + [state.lastCheckInDate].compactMap { $0 })
        let days = ShanghaiDate.monthDayStrings()
        let todayReward = max(1, state.todayReward)
        return days.map { date in
            let isToday = date == today
            let isFuture = date > today
            let isClaimed = claimed.contains(date) || (isToday && state.checkedInToday)
            let isClaimTarget = isToday && !isClaimed && !state.checkedInToday
            // 上架前关闭补签入口。恢复时改回 `!isClaimed && date < today`。
            let canMakeup = false && !isClaimed && date < today
            let reward: Int
            if isClaimTarget {
                reward = todayReward
            } else if canMakeup {
                reward = 1
            } else if isFuture {
                reward = min(7, todayReward + 1)
            } else {
                reward = 1
            }
            return CheckInDaySlot(
                date: date,
                day: ShanghaiDate.dayValue(of: date),
                reward: reward,
                isToday: isToday,
                claimed: isClaimed,
                isClaimTarget: isClaimTarget,
                canMakeup: canMakeup,
                isFuture: isFuture
            )
        }
    }
}

private extension String {
    var nilIfEmpty: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

struct VocabFile: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

struct NetworkRegionDialog: View {
    var regionLabel: String?
    var regionDetail: String?
    var avatarImage: UIImage?
    var busy: Bool
    var onDismiss: () -> Void
    var onRefresh: () -> Void

    private var label: String {
        let trimmed = regionLabel?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? "暂未识别" : trimmed
    }

    private var detailLine: String? {
        let detail = regionDetail?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !detail.isEmpty, detail != label else { return nil }
        return "当前识别：\(detail)"
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture { if !busy { onDismiss() } }

            VStack(spacing: 0) {
                HStack {
                    Spacer()
                    Button(action: onDismiss) {
                        Image(systemName: "xmark")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Theme.onSurfaceVariant)
                            .frame(width: 28, height: 28)
                    }
                    .buttonStyle(.plain)
                    .disabled(busy)
                }

                HStack(spacing: 8) {
                    ZStack {
                        Circle()
                            .fill(Theme.cyan.opacity(0.2))
                        if let avatarImage {
                            Image(uiImage: avatarImage)
                                .resizable()
                                .scaledToFill()
                        } else {
                            Image(systemName: "bubble.left.fill")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Theme.cyan)
                        }
                    }
                    .frame(width: 28, height: 28)
                    .clipShape(Circle())

                    Text(label)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.onSurface)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Theme.surfaceHigh, in: Capsule())
                .overlay(Capsule().stroke(Theme.outline.opacity(0.4), lineWidth: 1))
                .padding(.top, 2)

                Text("网络属地说明")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Theme.cyanSoft)
                    .padding(.top, 16)

                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("为让交流更透明可信，个人页会展示账号当前的网络属地。该结果来自运营商对接入网络的判定，不能手工改写，也不能关闭展示。")
                            .font(.subheadline)
                            .foregroundStyle(Theme.onSurfaceVariant)
                            .lineSpacing(4)
                            .fixedSize(horizontal: false, vertical: true)

                        if let detailLine {
                            Text(detailLine)
                                .font(.caption)
                                .foregroundStyle(Theme.onSurfaceVariant.opacity(0.85))
                                .padding(.top, 8)
                        }

                        Text("若觉得位置不准，可以依次尝试：")
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(Theme.onSurface)
                            .padding(.top, 12)

                        tip("点下方按钮，按当前网络重新校准")
                        tip("关闭随身 Wi‑Fi、流量卡或虚拟卡后再刷新")
                        tip("在 Wi‑Fi 与移动数据之间切换一次")
                        tip("重启手机，或开关飞行模式后再校准")

                        Text("仍有偏差时，可向运营商确认当前接入归属。")
                            .font(.footnote)
                            .foregroundStyle(Theme.onSurfaceVariant)
                            .padding(.top, 8)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(maxHeight: 360)
                .padding(.top, 12)

                Button(action: onRefresh) {
                    HStack(spacing: 8) {
                        if busy {
                            ProgressView()
                                .tint(Theme.cyan)
                                .scaleEffect(0.9)
                        } else {
                            Image(systemName: "arrow.clockwise")
                                .font(.body.weight(.semibold))
                            Text("重新校准属地")
                                .font(.subheadline.weight(.semibold))
                        }
                    }
                    .foregroundStyle(Theme.onSurface)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .overlay(Capsule().stroke(Theme.outline.opacity(0.55), lineWidth: 1))
                }
                .buttonStyle(.plain)
                .disabled(busy)
                .padding(.top, 16)
            }
            .padding(.horizontal, 20)
            .padding(.top, 14)
            .padding(.bottom, 18)
            .background(Theme.surfaceContainer.opacity(0.98), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.4), lineWidth: 1)
            )
            .padding(.horizontal, 24)
            .shadow(color: Theme.cyan.opacity(0.22), radius: 20)
        }
    }

    private func tip(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 0) {
            Text("·  ")
                .font(.subheadline)
                .foregroundStyle(Theme.onSurfaceVariant)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(Theme.onSurfaceVariant)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.top, 6)
    }
}

struct ShortFavoritesView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var items: [ShortClip] = []
    @State private var selected: Set<String> = []
    @State private var removing: Set<String> = []
    @State private var loading = true
    @State private var playing: ShortClip?

    private let columns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12)
    ]

    var body: some View {
        VStack(spacing: 0) {
            header
            if loading {
                Spacer()
                ProgressView().tint(Theme.cyan)
                Spacer()
            } else if items.isEmpty {
                Spacer()
                Text("还没有收藏的短视频")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.onSurfaceVariant)
                Spacer()
            } else {
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(items) { clip in
                            FavoriteVideoCard(
                                clip: clip,
                                selected: selected.contains(clip.id),
                                busy: removing.contains(clip.id),
                                onToggleSelect: { toggleSelect(clip.id) },
                                onUnfavorite: { Task { await unfavorite(ids: [clip.id]) } },
                                onOpen: { playing = clip }
                            )
                        }
                    }
                    .padding(16)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .stellarScreenBackground()
        .toolbar(.hidden, for: .navigationBar)
        .fullScreenCover(item: $playing) { clip in
            FavoriteClipPlayer(clip: clip) { playing = nil }
        }
        .task { await load() }
    }

    private var header: some View {
        HStack(spacing: 4) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.onSurface)
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.plain)
            Text("短视频收藏")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.onSurface)
            Spacer()
            if !items.isEmpty {
                let allSelected = selected.count == items.count
                Button(allSelected ? "取消全选" : "全选") {
                    selected = allSelected ? [] : Set(items.map(\.id))
                }
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.cyan)
                .disabled(!removing.isEmpty)
                if !selected.isEmpty {
                    Button(removing.isEmpty ? "全部取消" : "取消中") {
                        Task { await unfavorite(ids: Array(selected)) }
                    }
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.pink)
                    .disabled(!removing.isEmpty)
                }
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
    }

    private func toggleSelect(_ id: String) {
        guard removing.isEmpty else { return }
        if selected.contains(id) {
            selected.remove(id)
        } else {
            selected.insert(id)
        }
    }

    private func load() async {
        guard let token = model.session?.token else {
            model.showLogin = true
            loading = false
            return
        }
        var all: [ShortClip] = []
        var page = 1
        while page <= 20 {
            let batch = (try? await model.api.fetchShortFavorites(token: token, page: page, pageSize: 60)) ?? []
            all.append(contentsOf: batch)
            if batch.count < 60 { break }
            page += 1
        }
        items = all
        selected = selected.intersection(Set(all.map(\.id)))
        loading = false
    }

    private func unfavorite(ids: [String]) async {
        guard let token = model.session?.token else {
            model.showLogin = true
            return
        }
        let targets = Array(Set(ids.filter { !$0.isEmpty }))
        guard !targets.isEmpty, removing.isEmpty else { return }
        removing = Set(targets)
        let api = model.api
        var removed: [String] = []
        var failed = 0
        for chunk in stride(from: 0, to: targets.count, by: 4) {
            let slice = Array(targets[chunk..<min(chunk + 4, targets.count)])
            await withTaskGroup(of: (String, Bool).self) { group in
                for id in slice {
                    group.addTask {
                        let favorited = try? await api.setShortFavorite(token: token, videoId: id, favorited: false)
                        return (id, favorited == false)
                    }
                }
                for await (id, ok) in group {
                    if ok { removed.append(id) } else { failed += 1 }
                }
            }
        }
        let gone = Set(removed)
        items.removeAll { gone.contains($0.id) }
        selected.subtract(gone)
        removing = []
        if failed > 0 {
            model.banner = "有 \(failed) 个取消失败"
        }
    }
}

private struct FavoriteVideoCard: View {
    var clip: ShortClip
    var selected: Bool
    var busy: Bool
    var onToggleSelect: () -> Void
    var onUnfavorite: () -> Void
    var onOpen: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack {
                FavoriteThumb(clip: clip)
                VStack {
                    HStack {
                        overlayIcon(selected ? "checkmark.circle.fill" : "circle", tint: selected ? Theme.cyan : .white) {
                            onToggleSelect()
                        }
                        Spacer()
                        overlayIcon("bookmark.slash", tint: .white) {
                            onUnfavorite()
                        }
                    }
                    Spacer()
                }
                .padding(8)
                if busy {
                    ProgressView().tint(.white)
                }
            }
            .aspectRatio(9 / 16, contentMode: .fit)
            .background(Color.black)
            .clipped()
            VStack(alignment: .leading, spacing: 2) {
                Text(clip.title.isEmpty ? "短视频" : clip.title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.onSurface)
                    .lineLimit(1)
                Text("@\(clip.author.isEmpty ? "词搭子" : clip.author)")
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .lineLimit(1)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
        }
        .background(Theme.glass, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Theme.glassBorder, lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .onTapGesture(perform: onOpen)
    }

    private func overlayIcon(_ name: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: name)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: 32, height: 32)
                .background(Color.black.opacity(0.45), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(busy)
    }
}

private struct FavoriteThumb: View {
    var clip: ShortClip
    @State private var frame: UIImage?

    var body: some View {
        ZStack {
            if let cover = clip.coverUrl, let url = URL(string: cover) {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    } else {
                        frameOrPlaceholder
                    }
                }
            } else {
                frameOrPlaceholder
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
        .task(id: clip.id) { await loadFrame() }
    }

    @ViewBuilder
    private var frameOrPlaceholder: some View {
        if let frame {
            Image(uiImage: frame)
                .resizable()
                .scaledToFill()
        } else {
            ZStack {
                LinearGradient(colors: [Color(white: 0.12), Color(white: 0.04)], startPoint: .top, endPoint: .bottom)
                Image(systemName: "play.circle")
                    .font(.system(size: 36))
                    .foregroundStyle(.white.opacity(0.55))
            }
        }
    }

    private func loadFrame() async {
        guard frame == nil, let url = URL(string: clip.videoUrl) else { return }
        let image = await Task.detached(priority: .utility) { () -> UIImage? in
            let asset = AVURLAsset(url: url)
            let generator = AVAssetImageGenerator(asset: asset)
            generator.appliesPreferredTrackTransform = true
            generator.maximumSize = CGSize(width: 480, height: 854)
            let time = CMTime(seconds: 0.2, preferredTimescale: 600)
            guard let cg = try? generator.copyCGImage(at: time, actualTime: nil) else { return nil }
            return UIImage(cgImage: cg)
        }.value
        if let image { frame = image }
    }
}

private struct FavoriteClipPlayer: View {
    var clip: ShortClip
    var onClose: () -> Void
    @State private var player: AVPlayer?

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.black.ignoresSafeArea()
            if let player {
                VideoPlayer(player: player)
                    .ignoresSafeArea()
            }
            Button(action: onClose) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 40, height: 40)
                    .background(Color.black.opacity(0.45), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .padding(.top, 12)
            .padding(.leading, 12)
        }
        .onAppear {
            guard player == nil, let url = URL(string: clip.videoUrl) else { return }
            let next = AVPlayer(url: url)
            player = next
            next.play()
        }
        .onDisappear {
            player?.pause()
            player = nil
        }
    }
}

struct ToolsPlaceholderView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("工具")
                .font(.title2.weight(.bold))
                .foregroundStyle(Theme.cyanSoft)
            Text("抖音/快手「解析分享链接并保存到相册」在 Android 上依赖穿山甲与第三方解析，容易被 App Store 拒审。iOS 版暂不提供下载实现。")
                .foregroundStyle(Theme.onSurfaceVariant)
            Spacer()
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .stellarScreenBackground()
        .navigationTitle("工具")
        .navigationBarTitleDisplayMode(.inline)
        .themeNavigationBar()
    }
}

private struct ThemeSwatchView: View {
    var style: AccentStyle
    var selected: Bool

    var body: some View {
        let size: CGFloat = selected ? 42 : 36
        ZStack {
            if let wallpaper = StellarPalettes.palette(for: style).wallpaperImageName {
                Image(wallpaper)
                    .resizable()
                    .scaledToFill()
            } else {
                style.swatch
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay {
            if selected {
                Circle().stroke(Theme.background, lineWidth: 2)
                Circle().stroke(style.swatch, lineWidth: 3)
            }
        }
        .shadow(color: selected ? style.swatch.opacity(0.55) : .clear, radius: 8)
    }
}

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var showPassword = false
    @State private var oldPassword = ""
    @State private var newPassword = ""
    @State private var cacheLabel = "计算中…"
    @State private var cacheBusy = false
    @State private var showClearCache = false
    @State private var showLogout = false
    @State private var deletionPending = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("配置你的沉浸式学习体验。")
                    .font(.system(size: 16))
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.85))
                    .padding(.top, 8)
                    .padding(.bottom, 8)

                aestheticsCard
                preferencesCard
                storageCard
                if model.session != nil {
                    accountCard
                    accountActions
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 28)
        }
        .stellarScreenBackground()
        .navigationTitle("设置")
        .navigationBarTitleDisplayMode(.inline)
        .themeNavigationBar()
        .tint(Theme.cyan)
        .task { await refreshSideInfo() }
        .alert("修改密码", isPresented: $showPassword) {
            SecureField("当前密码", text: $oldPassword)
            SecureField("新密码", text: $newPassword)
            Button("保存") {
                let oldValue = oldPassword
                let newValue = newPassword
                oldPassword = ""
                newPassword = ""
                Task { await model.changePassword(oldPassword: oldValue, newPassword: newValue) }
            }
            Button("取消", role: .cancel) {
                oldPassword = ""
                newPassword = ""
            }
        } message: {
            Text("新密码需要 6 到 32 位")
        }
        .overlay {
            if showClearCache {
                LogoutConfirmDialog(
                    title: "清除本地缓存",
                    message: "将清除短视频与播客的本地媒体缓存（约 \(cacheLabel)）。下次播放会重新从网络加载。不影响账号与词库数据。",
                    confirmTitle: cacheBusy ? "清除中…" : "清除",
                    onCancel: { if !cacheBusy { showClearCache = false } },
                    onConfirm: {
                        guard !cacheBusy else { return }
                        Task { await clearCache() }
                    }
                )
            }
            if showLogout {
                LogoutConfirmDialog(
                    onCancel: { showLogout = false },
                    onConfirm: {
                        showLogout = false
                        model.logout()
                        dismiss()
                    }
                )
            }
        }
    }

    private var aestheticsCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            sectionTitle("paintpalette.fill", Theme.pink, "外观主题")
            HStack(spacing: 8) {
                Text("当前主题：")
                    .foregroundStyle(Theme.onSurfaceVariant)
                Text(model.accentStyle.label)
                    .fontWeight(.bold)
                    .foregroundStyle(model.accentStyle.swatch)
            }
            .font(.system(size: 15))
            ChipFlow(spacing: 14) {
                ForEach(AccentStyle.allCases) { style in
                    Button {
                        model.setAccentStyle(style)
                    } label: {
                        ThemeSwatchView(style: style, selected: style == model.accentStyle)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(style.label)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private var favoriteNotebooks: [Notebook] {
        model.notebooks
            .filter { !$0.isSystem }
            .sorted { lhs, rhs in
                if lhs.createdAtMillis != rhs.createdAtMillis {
                    return lhs.createdAtMillis > rhs.createdAtMillis
                }
                return lhs.id > rhs.id
            }
    }

    private var preferencesCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionTitle("slider.horizontal.3", Theme.cyan, "偏好设置")
                .padding(.bottom, 18)
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    Image(systemName: "photo")
                        .font(.system(size: 18))
                        .foregroundStyle(Theme.onSurfaceVariant.opacity(0.65))
                        .frame(width: 22)
                    Text("文生图接口")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Theme.onSurface)
                }
                ChipFlow(spacing: 8) {
                    ForEach(ImageProvider.allCases) { provider in
                        choiceChip(provider.label, selected: provider == model.imageProvider) {
                            model.setImageProvider(provider)
                        }
                    }
                }
                .padding(.leading, 34)
            }
            preferenceDivider()
            preferenceToggle(
                icon: "speaker.wave.2",
                title: "自动朗读",
                subtitle: "翻到生词时自动播放发音",
                isOn: Binding(get: { model.speakOnPageChange }, set: { model.setSpeakOnPageChange($0) })
            )
            preferenceDivider()
            preferenceToggle(
                icon: "bell.fill",
                title: "每日提醒",
                subtitle: "提醒你坚持背单词",
                isOn: Binding(get: { model.dailyReminder }, set: { model.setDailyReminder($0) })
            )
            preferenceDivider()
            preferenceToggle(
                icon: "headphones",
                title: "息屏后仍可后台播放",
                subtitle: "关闭后锁屏即暂停播客/电台，默认关闭",
                isOn: Binding(get: { model.podcastPlayWhenScreenOff }, set: { model.setPodcastPlayWhenScreenOff($0) })
            )
            preferenceDivider()
            preferenceToggle(
                icon: "eye",
                title: "短视频默认显示文案",
                subtitle: "关闭后播放时默认藏文案，仍可单击点开",
                isOn: Binding(get: { model.shortsMetaVisibleDefault }, set: { model.setShortsMetaVisibleDefault($0) })
            )
            preferenceDivider()
            preferenceToggle(
                icon: "repeat",
                title: "循环播放当前短视频",
                subtitle: "停留在同一条时播完再从头播，默认关闭",
                isOn: Binding(get: { model.shortVideoLoop }, set: { model.setShortVideoLoop($0) })
            )
            if !favoriteNotebooks.isEmpty {
                preferenceDivider()
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 10) {
                        Image(systemName: "book")
                            .font(.system(size: 18))
                            .foregroundStyle(Theme.cyan)
                            .frame(width: 22)
                        Text("默认收藏生词本")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Theme.onSurface)
                    }
                    Text("首页查词点星星时，词条会保存到所选生词本")
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .padding(.leading, 32)
                    ChipFlow(spacing: 8) {
                        ForEach(favoriteNotebooks) { notebook in
                            choiceChip(notebook.name, selected: notebook.id == model.defaultNotebookId) {
                                model.setDefaultFavoriteNotebook(notebook.id)
                            }
                        }
                    }
                    .padding(.leading, 32)
                    .padding(.top, 6)
                }
                .padding(.top, 16)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private var storageCard: some View {
        VStack(alignment: .leading, spacing: 18) {
            sectionTitle("externaldrive.fill", Theme.cyan, "存储与缓存")
            Button {
                showClearCache = true
            } label: {
                preferenceAction(
                    icon: "trash",
                    title: "清除媒体缓存",
                    subtitle: cacheBusy ? "正在清除…" : "短视频 / 播客本地缓存 · 当前 \(cacheLabel)",
                    destructive: false
                )
            }
            .buttonStyle(.plain)
            .disabled(cacheBusy)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private var accountCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionTitle("shield.fill", Theme.cyan, "账号与安全")
                .padding(.bottom, 18)
            Button {
                showPassword = true
            } label: {
                preferenceAction(
                    icon: "lock.fill",
                    title: "修改密码",
                    subtitle: "用当前密码设置新密码",
                    destructive: false
                )
            }
            .buttonStyle(.plain)
            preferenceDivider()
            preferenceToggle(
                icon: BiometricAuth.iconName,
                title: BiometricAuth.title,
                subtitle: BiometricAuth.subtitle,
                isOn: Binding(
                    get: { model.biometricLogin },
                    set: { enabled in Task { await setBiometric(enabled) } }
                )
            )
            preferenceDivider()
            NavigationLink {
                AccountDeletionView()
            } label: {
                preferenceAction(
                    icon: "person.slash",
                    title: "注销账号",
                    subtitle: deletionPending ? "注销冷静期中，可随时撤销" : "阅读须知并验证后进入7天冷静期",
                    destructive: true
                )
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private var accountActions: some View {
        VStack(spacing: 0) {
            NavigationLink {
                SwitchAccountView()
            } label: {
                Text("切换账号")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(Theme.onSurface)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
            }
            .buttonStyle(.plain)
            Rectangle()
                .fill(Theme.outline.opacity(0.45))
                .frame(height: 0.5)
            Button {
                showLogout = true
            } label: {
                Text("退出登录")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(Theme.pink)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
            }
            .buttonStyle(.plain)
        }
        .glassPanel()
    }

    private func sectionTitle(_ icon: String, _ color: Color, _ title: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundStyle(color)
            Text(title)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Theme.onSurface)
        }
    }

    private func preferenceDivider() -> some View {
        Rectangle()
            .fill(Theme.outline.opacity(0.45))
            .frame(height: 0.5)
            .padding(.vertical, 12)
    }

    private func preferenceToggle(icon: String, title: String, subtitle: String, isOn: Binding<Bool>) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundStyle(Theme.onSurfaceVariant.opacity(0.65))
                .frame(width: 22)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.onSurface)
                Text(subtitle)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.85))
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            Toggle("", isOn: isOn)
                .labelsHidden()
                .tint(Theme.cyan)
                .fixedSize()
        }
    }

    private func preferenceAction(icon: String, title: String, subtitle: String, destructive: Bool) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundStyle(Theme.onSurfaceVariant.opacity(0.65))
                .frame(width: 22)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(destructive ? Theme.pink : Theme.onSurface)
                Text(subtitle)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.85))
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            Image(systemName: "chevron.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.onSurfaceVariant.opacity(0.55))
        }
        .padding(.vertical, 2)
        .contentShape(Rectangle())
    }

    private func choiceChip(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: selected ? .bold : .medium))
                .foregroundStyle(selected ? Theme.onPrimary : Theme.onSurfaceVariant)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? Theme.cyanSoft : Theme.surfaceHigh, in: Capsule())
        }
        .buttonStyle(.plain)
    }

    private func refreshSideInfo() async {
        let label = await Task.detached(priority: .utility) {
            MediaCache.formattedSize()
        }.value
        cacheLabel = label
        guard let token = model.session?.token else { return }
        deletionPending = (try? await model.api.fetchAccountDeletion(token: token))?.pending == true
    }

    private func clearCache() async {
        cacheBusy = true
        await Task.detached(priority: .userInitiated) {
            MediaCache.clear()
        }.value
        cacheLabel = await Task.detached(priority: .utility) {
            MediaCache.formattedSize()
        }.value
        cacheBusy = false
        showClearCache = false
        model.banner = "缓存已清除"
    }

    private func setBiometric(_ enabled: Bool) async {
        if !enabled {
            model.setBiometricLogin(false)
            return
        }
        guard BiometricAuth.available else {
            model.banner = BiometricAuth.unavailableMessage
            return
        }
        let ok = await BiometricAuth.authenticate(reason: "验证后，下次打开应用将需要\(BiometricAuth.methodName)解锁")
        if ok {
            model.setBiometricLogin(true)
            model.banner = "已开启\(BiometricAuth.title)"
        }
    }
}

struct SwitchAccountView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        List {
            ForEach(model.accounts) { account in
                Button {
                    Task { await model.switchAccount(account) }
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(account.session.displayNickname)
                                .foregroundStyle(Theme.onSurface)
                            Text(account.session.maskedPhone)
                                .font(.footnote)
                                .foregroundStyle(Theme.onSurfaceVariant)
                        }
                        Spacer()
                        if account.session.userId == model.session?.userId {
                            Text("当前")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Theme.onPrimary)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Theme.cyan, in: Capsule())
                        }
                    }
                }
                .listRowBackground(Theme.surface)
                .swipeActions {
                    Button("移除", role: .destructive) {
                        model.forgetAccount(userId: account.session.userId)
                    }
                }
            }
            Button {
                model.showLogin = true
            } label: {
                Label("登录其他账号", systemImage: "plus")
                    .foregroundStyle(Theme.cyan)
            }
            .listRowBackground(Theme.surface)
        }
        .scrollContentBackground(.hidden)
        .stellarScreenBackground()
        .navigationTitle("切换账号")
        .navigationBarTitleDisplayMode(.inline)
        .themeNavigationBar()
    }
}

private struct ChipFlow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            rowHeight = max(rowHeight, size.height)
            x += size.width + spacing
        }
        return CGSize(width: width, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX, x + size.width > bounds.maxX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            rowHeight = max(rowHeight, size.height)
            x += size.width + spacing
        }
    }
}

enum MediaCache {
    static func formattedSize() -> String {
        format(usedBytes())
    }

    static func clear() {
        URLCache.shared.removeAllCachedResponses()
        guard let root = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else { return }
        let children = (try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil)) ?? []
        for url in children {
            try? FileManager.default.removeItem(at: url)
        }
    }

    private static func usedBytes() -> Int64 {
        guard let root = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else { return 0 }
        guard let enumerator = FileManager.default.enumerator(
            at: root,
            includingPropertiesForKeys: [.fileSizeKey, .isRegularFileKey],
            options: [.skipsHiddenFiles]
        ) else { return 0 }
        var total: Int64 = 0
        for case let url as URL in enumerator {
            let values = try? url.resourceValues(forKeys: [.fileSizeKey, .isRegularFileKey])
            guard values?.isRegularFile == true else { continue }
            total += Int64(values?.fileSize ?? 0)
        }
        return total
    }

    private static func format(_ bytes: Int64) -> String {
        if bytes < 1024 { return "\(bytes) B" }
        let mb = Double(bytes) / 1_048_576
        if mb >= 0.1 { return String(format: "%.1f MB", mb) }
        return String(format: "%.0f KB", Double(bytes) / 1024)
    }
}

enum StudyReminder {
    private static let id = "wordbuddy.daily.reminder"

    static func sync(enabled: Bool, ask: Bool = false) {
        let center = UNUserNotificationCenter.current()
        if !enabled {
            center.removePendingNotificationRequests(withIdentifiers: [id])
            return
        }
        center.getNotificationSettings { settings in
            switch settings.authorizationStatus {
            case .authorized, .provisional, .ephemeral:
                schedule(center)
            case .notDetermined where ask:
                center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
                    if granted { schedule(center) }
                }
            default:
                break
            }
        }
    }

    private static func schedule(_ center: UNUserNotificationCenter) {
        let content = UNMutableNotificationContent()
        content.title = "词搭子"
        content.body = "提醒你坚持背单词"
        var date = DateComponents()
        date.hour = 20
        date.minute = 0
        let request = UNNotificationRequest(
            identifier: id,
            content: content,
            trigger: UNCalendarNotificationTrigger(dateMatching: date, repeats: true)
        )
        center.add(request)
    }
}

enum BiometricAuth {
    static var available: Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
    }

    /// Face ID on current iPhones. Touch ID remains on iPhone SE.
    private static var usesTouchID: Bool {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        return context.biometryType == .touchID
    }

    static var title: String { usesTouchID ? "指纹解锁" : "面容解锁" }
    static var methodName: String { usesTouchID ? "指纹" : "面容" }
    static var iconName: String { usesTouchID ? "touchid" : "faceid" }
    static var subtitle: String {
        "下次打开应用时验证\(methodName)；密码/验证码登录后不会再要求"
    }

    static var unavailableMessage: String {
        let context = LAContext()
        var error: NSError?
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            return "无法启动\(methodName)验证"
        }
        return "这台设备没有可用的\(methodName)"
    }

    static func authenticate(reason: String) async -> Bool {
        await authenticateAttempt(reason: reason) == .success
    }

    static func authenticateAttempt(reason: String) async -> BiometricAttempt {
        let context = LAContext()
        context.localizedFallbackTitle = ""
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else {
            return .failed
        }
        do {
            let ok = try await context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason)
            return ok ? .success : .failed
        } catch let error as LAError {
            switch error.code {
            case .userCancel, .appCancel, .systemCancel:
                return .cancelled
            case .biometryLockout:
                return .lockout
            default:
                return .failed
            }
        } catch {
            return .failed
        }
    }
}

enum BiometricAttempt {
    case success
    case failed
    case cancelled
    case lockout
}

struct BiometricLockCover: View {
    @EnvironmentObject private var model: AppModel
    @State private var failures = 0
    @State private var busy = false
    @State private var askPassword = false
    @State private var password = ""
    @State private var passwordError: String?
    @State private var unlocking = false

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: BiometricAuth.iconName)
                .font(.system(size: 48))
                .foregroundStyle(Theme.cyanSoft)
            Text("验证\(BiometricAuth.methodName)后继续使用")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Theme.onSurface)
            Button("验证") {
                Task { await attempt() }
            }
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(Theme.onPrimary)
                .padding(.horizontal, 28)
                .padding(.vertical, 10)
                .background(Theme.cyan, in: Capsule())
                .disabled(busy || unlocking)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .stellarScreenBackground()
        .onAppear {
            Task { await attempt() }
        }
        .overlay {
            if askPassword {
                passwordUnlock
            }
        }
    }

    private var passwordUnlock: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 14) {
                Text("密码解锁")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Theme.cyanSoft)
                Text(passwordError ?? "\(BiometricAuth.methodName)已错误 3 次，请输入登录密码")
                    .font(.system(size: 14))
                    .foregroundStyle(passwordError == nil ? Theme.onSurfaceVariant : Theme.pink)
                if let account = accountLabel {
                    Text("当前账号")
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.onSurfaceVariant)
                    Text(account)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Theme.cyanSoft)
                }
                SecureField("登录密码", text: $password)
                    .textContentType(.password)
                    .padding(12)
                    .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12))
                HStack {
                    Button("取消") {
                        askPassword = false
                        password = ""
                        passwordError = nil
                        failures = 0
                    }
                    .foregroundStyle(Theme.onSurfaceVariant)
                    Spacer()
                    Button(unlocking ? "验证中…" : "解锁") {
                        Task { await submitPassword() }
                    }
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Theme.onPrimary)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 8)
                    .background(Theme.cyan, in: Capsule())
                    .disabled(unlocking)
                }
            }
            .padding(20)
            .frame(maxWidth: 320)
            .background(Theme.surfaceHigh, in: RoundedRectangle(cornerRadius: 18))
            .padding(.horizontal, 28)
        }
    }

    private var accountLabel: String? {
        guard let session = model.session else { return nil }
        let phone = session.phone.trimmingCharacters(in: .whitespacesAndNewlines)
        let name = session.displayNickname
        if !phone.isEmpty, name != "词搭子" {
            return "\(name)\n\(phone)"
        }
        if !phone.isEmpty { return phone }
        return name
    }

    private func attempt() async {
        guard !busy, !askPassword, !model.biometricUnlocked else { return }
        busy = true
        defer { busy = false }
        while !askPassword && !model.biometricUnlocked {
            switch await BiometricAuth.authenticateAttempt(reason: "验证\(BiometricAuth.methodName)后继续使用词搭子") {
            case .success:
                model.biometricUnlocked = true
                failures = 0
                return
            case .cancelled:
                return
            case .failed:
                failures += 1
                if failures >= 3 {
                    password = ""
                    passwordError = nil
                    askPassword = true
                    return
                }
            case .lockout:
                failures = 3
                password = ""
                passwordError = "\(BiometricAuth.methodName)已暂时锁定，请输入登录密码"
                askPassword = true
                return
            }
        }
    }

    private func submitPassword() async {
        let phone = model.session?.phone.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard phone.count == 11 else {
            passwordError = "请退出后重新登录，再用密码解锁"
            return
        }
        guard password.count >= 6 else {
            passwordError = "密码至少 6 位"
            return
        }
        unlocking = true
        defer { unlocking = false }
        do {
            let result = try await model.loginWithPassword(phone: phone, password: password)
            guard let session = result.session else {
                passwordError = "密码不正确"
                return
            }
            await model.enter(session)
        } catch {
            passwordError = error.localizedDescription
        }
    }
}
