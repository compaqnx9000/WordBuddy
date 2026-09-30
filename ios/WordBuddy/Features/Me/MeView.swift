import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct MeView: View {
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
    @State private var path = NavigationPath()

    private let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(spacing: 14) {
                    heroCard
                    checkInCard
                    rewardsMenu
                    toolsMenu
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
        .alert("补签", isPresented: makeupPresented) {
            Button("确认补签") {
                if let makeupDate {
                    Task { await model.makeupCheckIn(date: makeupDate) }
                }
                makeupDate = nil
            }
            Button("取消", role: .cancel) { makeupDate = nil }
        } message: {
            Text("iOS 版不看广告，直接补签 \(makeupDate.map { ShanghaiDate.dayValue(of: $0) } ?? 0) 日。")
        }
        .sheet(isPresented: $showHelp) {
            infoSheet(
                title: "帮助与反馈",
                body: "查词、生词本、短视频、播客和积分功能可在底部五个 Tab 使用。\n\n反馈请联系客服微信或邮箱（与 Android 版相同渠道）。iOS 版暂不接入穿山甲广告。"
            )
        }
        .sheet(isPresented: $showAbout) {
            infoSheet(
                title: "关于词搭子",
                body: "词搭子 \(appVersion)\n英文查词、生词本、短视频、播客与积分兑礼。\n\niOS 版不接入穿山甲广告；微信/支付宝支付即将支持，积分充值可先走模拟支付。"
            )
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
            }
            .frame(height: 88)

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
                        model.showLogin = true
                    }
                }

                HStack(spacing: 8) {
                    Text(loggedIn ? model.session!.displayNickname : "未登录")
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

                Text(
                    loggedIn
                        ? "已收藏 \(model.wordTotal) 词 · 积分 \(model.checkIn.totalPoints)"
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
                            model.showLogin = true
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
        return "左右滑动查看本月，漏签可直接补签 · 累计 \(model.checkIn.totalPoints) 分"
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
            } else if slot.canMakeup {
                makeupDate = slot.date
            }
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

    private var rewardsMenu: some View {
        menuCard {
            menuRow("creditcard", Theme.pink, "充值积分", "测试价 ¥0.10") {
                guardRequireLogin { path.append(MeRoute.buyPoints) }
            }
            menuDivider()
            menuRow("play.circle", Theme.gold, "看视频领积分", "即将支持") {
                model.banner = "iOS 版暂不接入激励视频广告"
            }
            menuDivider()
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
                menuRowLabel("square.and.arrow.up", Theme.cyan, "导出词库", transferring ? "导出中…" : "不含图片和发音")
            }
            .buttonStyle(.plain)
            .disabled(transferring)
            menuDivider()
            Button {
                showImport = true
            } label: {
                menuRowLabel("square.and.arrow.down", Theme.pink, "导入词库", "合并导入")
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
            let canMakeup = !isClaimed && date < today
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
    @State private var items: [ShortClip] = []
    @State private var loading = true

    var body: some View {
        Group {
            if loading {
                ProgressView().tint(Theme.cyan)
            } else if items.isEmpty {
                Text("还没有收藏的短视频")
                    .foregroundStyle(Theme.onSurfaceVariant)
            } else {
                List {
                    ForEach(items) { clip in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(clip.title.isEmpty ? "英语短视频" : clip.title)
                                .foregroundStyle(Theme.onSurface)
                            Text(clip.author)
                                .font(.caption)
                                .foregroundStyle(Theme.onSurfaceVariant)
                        }
                        .listRowBackground(Theme.surface)
                        .swipeActions {
                            Button("取消收藏", role: .destructive) {
                                Task { await unfavorite(clip) }
                            }
                        }
                    }
                }
                .scrollContentBackground(.hidden)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .stellarScreenBackground()
        .navigationTitle("短视频收藏")
        .navigationBarTitleDisplayMode(.inline)
        .themeNavigationBar()
        .task { await load() }
    }

    private func load() async {
        guard let token = model.session?.token else {
            model.showLogin = true
            loading = false
            return
        }
        items = (try? await model.api.fetchShortFavorites(token: token)) ?? []
        loading = false
    }

    private func unfavorite(_ clip: ShortClip) async {
        guard let token = model.session?.token else { return }
        _ = try? await model.api.setShortFavorite(token: token, videoId: clip.id, favorited: false)
        items.removeAll { $0.id == clip.id }
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
    @State private var showPassword = false
    @State private var oldPassword = ""
    @State private var newPassword = ""

    var body: some View {
        Form {
            Section {
                Text("配置你的沉浸式学习体验。")
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.85))
            }
            Section {
                HStack(spacing: 10) {
                    Image(systemName: "paintpalette.fill")
                        .foregroundStyle(Theme.pink)
                    Text("外观主题")
                        .font(.headline)
                        .foregroundStyle(Theme.onSurface)
                }
                HStack(spacing: 0) {
                    Text("当前主题：")
                        .foregroundStyle(Theme.onSurfaceVariant)
                    Text(model.accentStyle.label)
                        .fontWeight(.bold)
                        .foregroundStyle(model.accentStyle.swatch)
                }
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: 4), spacing: 14) {
                    ForEach(AccentStyle.allCases) { style in
                        Button {
                            model.setAccentStyle(style)
                        } label: {
                            ThemeSwatchView(style: style, selected: style == model.accentStyle)
                                .frame(maxWidth: .infinity, minHeight: 44)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(style.label)
                    }
                }
                .padding(.vertical, 4)
            }
            Section("发音") {
                Picker("口音", selection: Binding(
                    get: { model.accent },
                    set: { model.setAccent($0) }
                )) {
                    ForEach(Accent.allCases) { accent in
                        Text(accent.label).tag(accent)
                    }
                }
                Toggle("翻页自动朗读", isOn: Binding(
                    get: { model.speakOnPageChange },
                    set: { model.setSpeakOnPageChange($0) }
                ))
            }
            Section("生词本") {
                Toggle("默认隐藏释义", isOn: Binding(
                    get: { model.hideDefinitions },
                    set: { model.setHideDefinitions($0) }
                ))
            }
            Section("AI 配图") {
                Picker("生图服务", selection: Binding(
                    get: { model.imageProvider },
                    set: { model.setImageProvider($0) }
                )) {
                    ForEach(ImageProvider.allCases) { provider in
                        Text(provider.label).tag(provider)
                    }
                }
                Text("每次生成约消耗 \(model.aiImagePointsCost) 积分。")
                    .font(.footnote)
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            Section("账号") {
                Button("修改密码") { showPassword = true }
                NavigationLink("积分提现") { WithdrawView() }
            }
            Section("关于") {
                LabeledContent("词搭子", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                Text("英文查词、生词本、短视频和播客。iOS 版不接入穿山甲广告。")
                    .font(.footnote)
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
        }
        .scrollContentBackground(.hidden)
        .listRowBackground(Theme.glass)
        .stellarScreenBackground()
        .navigationTitle("设置")
        .navigationBarTitleDisplayMode(.inline)
        .themeNavigationBar()
        .tint(Theme.cyan)
        .id(model.accentStyle)
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
