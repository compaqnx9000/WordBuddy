import SwiftUI

/// Loads the admin-uploaded gift photo. Falls back to the emoji on the cover color.
private struct GiftCoverImage: View {
    var urlString: String?
    var emoji: String
    var colorHex: String
    var emojiSize: CGFloat

    @EnvironmentObject private var model: AppModel

    var body: some View {
        ZStack {
            Color(hexString: colorHex)
            if let urlString, let url = model.api.absoluteURL(for: urlString) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    default:
                        Text(emoji).font(.system(size: emojiSize))
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                Text(emoji).font(.system(size: emojiSize))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
    }
}

/// Same canvas as Android `sdp`: 1 design point at a 440pt-wide window.
private enum MallMetrics {
    static var scale: CGFloat {
        let width = UIScreen.main.bounds.width
        return min(max(width / 440, 0.72), 1.6)
    }

    static func sdp(_ value: CGFloat) -> CGFloat {
        value * scale
    }

    /// Two-column card width: screen minus the 12pt page inset and the 10pt gap.
    static var coverSide: CGFloat {
        let page = sdp(12) * 2 + sdp(10)
        return (UIScreen.main.bounds.width - page) / 2
    }
}

struct PointsMallView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var model: AppModel
    @State private var categories: [GiftCategory] = [GiftCategory(id: "recommend", name: "推荐")]
    @State private var category = "recommend"
    @State private var gifts: [GiftItem] = []
    @State private var loading = true
    @State private var errorText: String?

    private var columns: [GridItem] {
        [
            GridItem(.flexible(), spacing: MallMetrics.sdp(10)),
            GridItem(.flexible(), spacing: MallMetrics.sdp(10)),
        ]
    }

    private var loggedIn: Bool { model.session != nil }

    private var userName: String {
        guard loggedIn else { return "未登录" }
        return model.session?.displayNickname ?? "词搭子"
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ScrollView {
                VStack(alignment: .leading, spacing: MallMetrics.sdp(10)) {
                    header
                    categoryTabs
                    if loading {
                        ProgressView().tint(Theme.cyan).frame(maxWidth: .infinity).padding(.top, MallMetrics.sdp(24))
                    } else if let errorText {
                        Text(errorText)
                            .font(.system(size: MallMetrics.sdp(14)))
                            .foregroundStyle(Theme.pink)
                    } else if gifts.isEmpty {
                        Text("暂无礼品")
                            .font(.system(size: MallMetrics.sdp(14)))
                            .foregroundStyle(Theme.onSurfaceVariant)
                            .padding(.top, MallMetrics.sdp(8))
                    } else {
                        LazyVGrid(columns: columns, spacing: MallMetrics.sdp(10)) {
                            ForEach(gifts) { gift in
                                NavigationLink {
                                    GiftDetailView(giftId: gift.id)
                                } label: {
                                    giftCard(gift)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(.horizontal, MallMetrics.sdp(12))
                .padding(.vertical, MallMetrics.sdp(10))
            }
        }
        .stellarScreenBackground()
        .toolbar(.hidden, for: .navigationBar)
        .task { await load() }
    }

    private var topBar: some View {
        HStack(spacing: 0) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: MallMetrics.sdp(17), weight: .semibold))
                    .foregroundStyle(Theme.onSurface)
                    .frame(width: MallMetrics.sdp(48), height: MallMetrics.sdp(48))
            }
            .buttonStyle(.plain)
            Text("搭币兑礼")
                .font(.system(size: MallMetrics.sdp(18), weight: .bold))
                .foregroundStyle(Theme.cyanSoft)
            Spacer()
        }
        .padding(.horizontal, MallMetrics.sdp(4))
        .frame(height: MallMetrics.sdp(56))
        .background(panel.ignoresSafeArea(edges: .top))
    }

    private var panel: Color {
        if Theme.hasWallpaper {
            return Theme.surfaceContainer.opacity(0.72)
        }
        return Theme.background.opacity(0.80)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(userName)
                .font(.system(size: MallMetrics.sdp(16), weight: .semibold))
                .foregroundStyle(Theme.onSurface)
            HStack(alignment: .center, spacing: MallMetrics.sdp(8)) {
                Text("我的搭币")
                    .font(.system(size: MallMetrics.sdp(13)))
                    .foregroundStyle(Theme.onSurfaceVariant)
                Text("\(model.checkIn.totalPoints)")
                    .font(.system(size: MallMetrics.sdp(28), weight: .bold))
                    .foregroundStyle(Theme.cyan)
                Spacer(minLength: MallMetrics.sdp(8))
                ordersLink
            }
            .padding(.top, MallMetrics.sdp(10))
            HStack {
                mallStat(value: loggedIn ? "\(model.checkIn.streakDays)天" : "—", caption: "连续签到")
                Spacer()
                mallStat(
                    value: loggedIn ? (model.checkIn.checkedInToday ? "已签到" : "未签到") : "—",
                    caption: "今日"
                )
            }
            .padding(.top, MallMetrics.sdp(14))
        }
        .padding(MallMetrics.sdp(16))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(headerFill, in: RoundedRectangle(cornerRadius: MallMetrics.sdp(18), style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: MallMetrics.sdp(18), style: .continuous)
                .stroke(Theme.cyan.opacity(0.25), lineWidth: 1)
        )
    }

    private var headerFill: LinearGradient {
        LinearGradient(
            colors: [
                Theme.cyan.opacity(0.35),
                Theme.pink.opacity(0.18),
                Theme.surfaceContainer,
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    private var ordersLink: some View {
        Group {
            if loggedIn {
                NavigationLink {
                    GiftOrdersView()
                } label: {
                    Text("我的订单 >")
                        .font(.system(size: MallMetrics.sdp(13)))
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
                .buttonStyle(.plain)
            } else {
                Button {
                    model.openLogin()
                } label: {
                    Text("我的订单 >")
                        .font(.system(size: MallMetrics.sdp(13)))
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var categoryTabs: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: MallMetrics.sdp(14)) {
                ForEach(categories) { item in
                    let active = item.id == category
                    Button {
                        category = item.id
                        Task { await loadGifts() }
                    } label: {
                        VStack(spacing: MallMetrics.sdp(4)) {
                            Text(item.name)
                                .font(.system(size: MallMetrics.sdp(14), weight: active ? .bold : .regular))
                                .foregroundStyle(active ? Theme.onSurface : Theme.onSurfaceVariant)
                            Capsule()
                                .fill(active ? Theme.cyan : Color.clear)
                                .frame(width: MallMetrics.sdp(18), height: MallMetrics.sdp(3))
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func mallStat(value: String, caption: String) -> some View {
        VStack(spacing: MallMetrics.sdp(4)) {
            Text(value)
                .font(.system(size: MallMetrics.sdp(16), weight: .bold))
                .foregroundStyle(Theme.cyanSoft)
                .frame(height: MallMetrics.sdp(40))
            Text(caption)
                .font(.system(size: MallMetrics.sdp(11)))
                .foregroundStyle(Theme.onSurfaceVariant)
        }
        .padding(.horizontal, MallMetrics.sdp(6))
        .padding(.vertical, MallMetrics.sdp(4))
    }

    private func giftCard(_ gift: GiftItem) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            GiftCoverImage(
                urlString: gift.coverImage ?? gift.bannerImages.first,
                emoji: gift.coverEmoji,
                colorHex: gift.coverColor,
                emojiSize: MallMetrics.sdp(44)
            )
            .frame(maxWidth: .infinity)
            .frame(height: MallMetrics.coverSide)
            VStack(alignment: .leading, spacing: MallMetrics.sdp(4)) {
                Text(gift.title)
                    .font(.system(size: MallMetrics.sdp(13), weight: .medium))
                    .foregroundStyle(Theme.onSurface)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, minHeight: MallMetrics.sdp(36), maxHeight: MallMetrics.sdp(36), alignment: .topLeading)
                Text(gift.priceLabel)
                    .font(.system(size: MallMetrics.sdp(13), weight: .bold))
                    .foregroundStyle(Theme.pink)
                Text(gift.redeemedLabel)
                    .font(.system(size: MallMetrics.sdp(11)))
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.75))
            }
            .padding(MallMetrics.sdp(10))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.glass)
        .clipShape(RoundedRectangle(cornerRadius: MallMetrics.sdp(14), style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: MallMetrics.sdp(14), style: .continuous)
                .stroke(Theme.glassBorder, lineWidth: 1)
        )
    }

    private func load() async {
        if let fetched = try? await model.api.listGiftCategories(), !fetched.isEmpty {
            categories = fetched
        }
        await loadGifts()
    }

    private func loadGifts() async {
        loading = true
        errorText = nil
        defer { loading = false }
        do {
            // 上架前不展示含现金的礼品。在线支付未开通，审核员会看到未完成的支付说明。
            gifts = try await model.api.listGifts(category: category, token: model.session?.token).filter { $0.cashFen == 0 }
        } catch {
            errorText = error.localizedDescription
        }
    }
}

struct GiftDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var model: AppModel
    var giftId: Int64
    @State private var gift: GiftItem?
    @State private var loading = true
    @State private var busy = false
    @State private var name = ""
    @State private var phone = ""
    @State private var detail = ""
    @State private var confirm = false
    @State private var missingAddress = false
    @State private var openProfile = false

    var body: some View {
        VStack(spacing: 0) {
            topBar
            if loading {
                ProgressView().tint(Theme.cyan).frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let gift {
                content(gift)
            } else {
                Text("礼品不存在")
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    .padding(MallMetrics.sdp(24))
            }
        }
        .stellarScreenBackground()
        .toolbar(.hidden, for: .navigationBar)
        .task { await load() }
        .alert("确认兑换", isPresented: $confirm) {
            Button("确认") { Task { await redeem() } }
            Button("取消", role: .cancel) {}
        } message: {
            Text("将花费 \(gift?.priceLabel ?? "")\n当前搭币 \(model.checkIn.totalPoints)")
        }
        .alert("请先填写收货地址", isPresented: $missingAddress) {
            Button("去填写") { openProfile = true }
            Button("取消", role: .cancel) {}
        } message: {
            Text("兑换需要收货地址。请先到个人资料中填写收件人、电话和地址，再回来兑换。")
        }
        .navigationDestination(isPresented: $openProfile) {
            ProfileEditView()
                .toolbar(.visible, for: .navigationBar)
        }
    }

    private var topBar: some View {
        HStack(spacing: 0) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: MallMetrics.sdp(17), weight: .semibold))
                    .foregroundStyle(Theme.onSurface)
                    .frame(width: MallMetrics.sdp(48), height: MallMetrics.sdp(48))
            }
            .buttonStyle(.plain)
            Text("礼品详情")
                .font(.system(size: MallMetrics.sdp(18), weight: .bold))
                .foregroundStyle(Theme.cyanSoft)
            Spacer()
        }
        .padding(.horizontal, MallMetrics.sdp(4))
        .frame(height: MallMetrics.sdp(56))
        .background(detailPanel.ignoresSafeArea(edges: .top))
    }

    private var detailPanel: Color {
        if Theme.hasWallpaper {
            return Theme.surfaceContainer.opacity(0.72)
        }
        return Theme.background.opacity(0.80)
    }

    private func giftBanner(_ gift: GiftItem) -> some View {
        let height = UIScreen.main.bounds.width / 1.1
        let banners = gift.bannerImages
        return Group {
            if banners.isEmpty {
                GiftCoverImage(
                    urlString: nil,
                    emoji: gift.coverEmoji,
                    colorHex: gift.coverColor,
                    emojiSize: MallMetrics.sdp(72)
                )
            } else if banners.count == 1 {
                GiftCoverImage(
                    urlString: banners[0],
                    emoji: gift.coverEmoji,
                    colorHex: gift.coverColor,
                    emojiSize: MallMetrics.sdp(72)
                )
            } else {
                TabView {
                    ForEach(Array(banners.enumerated()), id: \.offset) { _, url in
                        GiftCoverImage(
                            urlString: url,
                            emoji: gift.coverEmoji,
                            colorHex: gift.coverColor,
                            emojiSize: MallMetrics.sdp(72)
                        )
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .automatic))
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .clipped()
    }

    private func content(_ gift: GiftItem) -> some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    giftBanner(gift)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(gift.priceLabel)
                            .font(.system(size: MallMetrics.sdp(22), weight: .bold))
                            .foregroundStyle(.white)
                        if let original = gift.originalPriceYuan, !original.isEmpty {
                            Text("优惠前 ¥\(original)")
                                .font(.system(size: MallMetrics.sdp(12)))
                                .foregroundStyle(.white.opacity(0.85))
                        }
                        if let offset = gift.pointsOffsetYuan, !offset.isEmpty {
                            Text("搭币已抵 \(offset) 元")
                                .font(.system(size: MallMetrics.sdp(12)))
                                .foregroundStyle(Color(hex: 0xFFE08A))
                                .padding(.top, MallMetrics.sdp(4))
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, MallMetrics.sdp(16))
                    .padding(.vertical, MallMetrics.sdp(14))
                    .background(
                        LinearGradient(
                            colors: [Color(hex: 0xE85D4C), Color(hex: 0x3D7EA6)],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    VStack(alignment: .leading, spacing: 0) {
                        Text(gift.title)
                            .font(.system(size: MallMetrics.sdp(18), weight: .bold))
                            .foregroundStyle(Theme.onSurface)
                        if !gift.subtitle.isEmpty {
                            Text(gift.subtitle)
                                .font(.system(size: MallMetrics.sdp(13)))
                                .foregroundStyle(Theme.onSurfaceVariant)
                                .padding(.top, MallMetrics.sdp(4))
                        }
                        Text(gift.description.isEmpty ? "兑换后由词搭子客服处理发货或发放虚拟权益。" : gift.description)
                            .font(.system(size: MallMetrics.sdp(14)))
                            .foregroundStyle(Theme.onSurfaceVariant)
                            .lineSpacing(MallMetrics.sdp(6))
                            .padding(.top, MallMetrics.sdp(10))
                        Text("当前搭币 \(model.checkIn.totalPoints) · \(gift.redeemedLabel)")
                            .font(.system(size: MallMetrics.sdp(13)))
                            .foregroundStyle(Theme.cyan)
                            .padding(.top, MallMetrics.sdp(12))
                        // 上架前不展示现金补差说明。恢复在线支付后再打开。
                        // if gift.cashFen > 0 {
                        //     Text("含现金部分：搭币先扣，现金需客服确认（暂未开通在线支付）")
                        //         .font(.system(size: MallMetrics.sdp(12)))
                        //         .foregroundStyle(Theme.gold)
                        //         .padding(.top, MallMetrics.sdp(6))
                        // }
                    }
                    .padding(MallMetrics.sdp(16))
                }
            }
            HStack(spacing: MallMetrics.sdp(12)) {
                Text(gift.priceLabel)
                    .font(.system(size: MallMetrics.sdp(14), weight: .bold))
                    .foregroundStyle(Theme.pink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button(busy ? "兑换中…" : "立即兑换") {
                    beginRedeem(gift)
                }
                .font(.system(size: MallMetrics.sdp(14), weight: .bold))
                .foregroundStyle(Theme.onPrimary)
                .padding(.horizontal, MallMetrics.sdp(18))
                .padding(.vertical, MallMetrics.sdp(12))
                .background(busy ? Theme.surfaceHigh : Theme.pink, in: Capsule())
                .disabled(busy)
            }
            .padding(MallMetrics.sdp(12))
            .background(Theme.surfaceContainer.ignoresSafeArea(edges: .bottom))
        }
    }

    private func beginRedeem(_ gift: GiftItem) {
        guard let session = model.session else {
            model.openLogin()
            return
        }
        if gift.cashFen > 0 {
            model.banner = "该礼品暂未开放兑换"
            return
        }
        if gift.needAddress {
            let savedName = session.shippingName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let savedPhone = session.shippingPhone?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let savedDetail = session.shippingDetail?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !savedName.isEmpty, !savedPhone.isEmpty, !savedDetail.isEmpty else {
                missingAddress = true
                return
            }
            name = savedName
            phone = savedPhone
            detail = savedDetail
        }
        confirm = true
    }

    private func load() async {
        loading = true
        defer { loading = false }
        gift = try? await model.api.fetchGift(id: giftId, token: model.session?.token)
    }

    private func redeem() async {
        guard let token = model.session?.token, let gift else { return }
        busy = true
        defer { busy = false }
        do {
            let result = try await model.api.redeemGift(
                token: token,
                giftId: gift.id,
                name: name,
                phone: phone,
                detail: detail
            )
            model.applyPoints(result.totalPoints)
            model.banner = result.message
            dismiss()
        } catch {
            model.banner = error.localizedDescription
        }
    }
}

struct GiftOrdersView: View {
    @EnvironmentObject private var model: AppModel
    @State private var orders: [GiftOrder] = []
    @State private var loading = true

    var body: some View {
        Group {
            if loading {
                ProgressView().tint(Theme.cyan)
            } else if orders.isEmpty {
                Text("还没有兑换记录").foregroundStyle(Theme.onSurfaceVariant)
            } else {
                List(orders) { order in
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(order.coverEmoji) \(order.giftTitle)")
                            .foregroundStyle(Theme.onSurface)
                        Text("\(order.priceLabel) · \(order.statusLabel)")
                            .font(.footnote)
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }
                    .listRowBackground(Theme.surface)
                }
                .scrollContentBackground(.hidden)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .stellarScreenBackground()
        .navigationTitle("兑换记录")
        .navigationBarTitleDisplayMode(.inline)
        .themeNavigationBar()
        .task { await load() }
    }

    private func load() async {
        guard let token = model.session?.token else {
            model.openLogin()
            loading = false
            return
        }
        orders = (try? await model.api.listGiftOrders(token: token)) ?? []
        loading = false
    }
}

struct BuyPointsView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var model: AppModel
    @State private var catalog: PointCatalog?
    @State private var channel = "alipay"
    @State private var buyingId: String?
    @State private var resumingId: Int64?
    @State private var errorText: String?
    @State private var balance = 0
    @State private var history: [PointOrder] = []
    @State private var historyLoading = false
    @State private var showHistory = false
    @State private var now = Date()
    @State private var reloadedExpired = false

    private var wechatReady: Bool { catalog?.wechatReady ?? false }
    private var alipayReady: Bool { (catalog?.alipayReady ?? true) || (catalog?.sandbox ?? false) }
    private var payBusy: Bool { buyingId != nil || resumingId != nil }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    balanceCard
                    Text("支付方式")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Theme.onSurface)
                    payRow(id: "alipay", title: "支付宝", ready: alipayReady)
                    payRow(id: "wechat", title: "微信", ready: wechatReady)
                    if let errorText {
                        Text(errorText).foregroundStyle(Theme.pink)
                    }
                    if catalog == nil && errorText == nil {
                        ProgressView().tint(Theme.cyan).frame(maxWidth: .infinity).padding(.top, 24)
                    } else if catalog?.items.isEmpty == true {
                        Text("暂无配图档位")
                            .font(.subheadline)
                            .foregroundStyle(Theme.onSurfaceVariant)
                    } else {
                        ForEach(catalog?.items ?? []) { package in
                            packageRow(package)
                        }
                    }
                }
                .padding(16)
            }
        }
        .stellarScreenBackground()
        .toolbar(.hidden, for: .navigationBar)
        .task { await load() }
        .onAppear { balance = model.checkIn.imageCredits }
        .onChange(of: model.checkIn.imageCredits) { _, credits in
            balance = credits
        }
        .task(id: showHistory) {
            guard showHistory else { return }
            while !Task.isCancelled && showHistory {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                now = Date()
            }
        }
        .onChange(of: expiredPendingIds) { _, ids in
            guard showHistory, !ids.isEmpty, !reloadedExpired else { return }
            reloadedExpired = true
            Task { await loadHistory() }
        }
        .overlay { if showHistory { historyDialog } }
    }

    private var topBar: some View {
        HStack(spacing: 4) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.left")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Theme.onSurface)
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.plain)
            Text("购买配图")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.onSurface)
            Spacer()
            if model.session != nil {
                Button("购买记录") {
                    showHistory = true
                    reloadedExpired = false
                    Task { await loadHistory() }
                }
                .font(.subheadline.weight(.medium))
                .foregroundStyle(Theme.cyanSoft)
                .padding(.horizontal, 8)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
    }

    private var balanceCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("剩余配图")
                .font(.system(size: 13))
                .foregroundStyle(Theme.onSurfaceVariant)
            Text("\(balance) 张")
                .font(.system(size: 32, weight: .bold))
                .foregroundStyle(Theme.cyan)
                .padding(.top, 2)
            Text("只能用来给单词生成助记配图，不能提现、兑礼或转给别人。生成成功扣 1 张，看过的缓存图不扣。")
                .font(.system(size: 12))
                .foregroundStyle(Theme.onSurfaceVariant)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 8)
            if catalog?.sandbox == true {
                Text("支付宝当前为沙箱：选支付宝购买会直接到账，不调起真实支付。")
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.gold)
                    .padding(.top, 6)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(
                colors: [Theme.cyan.opacity(0.28), Theme.pink.opacity(0.14)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            in: RoundedRectangle(cornerRadius: 16, style: .continuous)
        )
    }

    private func payRow(id: String, title: String, ready: Bool) -> some View {
        let selected = channel == id
        return Button {
            channel = id
        } label: {
            HStack(spacing: 12) {
                WithdrawChannelBadge(channelId: id, size: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(Theme.onSurface)
                    if !ready {
                        Text("尚未开通")
                            .font(.system(size: 12))
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }
                }
                Spacer()
                if selected {
                    Text("已选")
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.cyan)
                }
            }
            .padding(14)
            .background(
                (selected ? Theme.cyan.opacity(0.12) : Theme.surfaceContainer),
                in: RoundedRectangle(cornerRadius: 14, style: .continuous)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(selected ? Theme.cyan : Theme.outline.opacity(0.35), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func packageRow(_ package: PointPackage) -> some View {
        let busy = buyingId == package.id
        let enabled = !payBusy
        let credits = package.credits > 0 ? package.credits : package.points
        return HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(package.title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.onSurface)
                    if let badge = package.badge, !badge.isEmpty {
                        Text(badge)
                            .font(.system(size: 11))
                            .foregroundStyle(Theme.gold)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Theme.gold.opacity(0.15), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                    }
                }
                Text(package.subtitle.isEmpty ? "\(credits) 张助记配图" : package.subtitle)
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.onSurfaceVariant)
                Text("¥\(package.amountYuan)")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(Theme.pink)
                    .padding(.top, 2)
            }
            Spacer(minLength: 8)
            Button {
                Task { await buy(package) }
            } label: {
                Group {
                    if busy {
                        ProgressView().controlSize(.small).tint(Theme.onPrimary)
                    } else {
                        Text("购买")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Theme.onPrimary)
                    }
                }
                .frame(minWidth: 28, minHeight: 18)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Theme.cyan.opacity(enabled ? 1 : 0.4), in: Capsule())
            }
            .buttonStyle(.plain)
            .disabled(!enabled)
        }
        .padding(14)
        .glassPanel()
    }

    private var visibleHistory: [PointOrder] {
        history.filter { $0.status != "pending" || payLeft($0, at: now) > 0 }
    }

    private var expiredPendingIds: [Int64] {
        history.filter { $0.status == "pending" && payLeft($0, at: now) <= 0 }.map(\.id)
    }

    private var historyDialog: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture { showHistory = false }
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("购买记录")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(Theme.cyanSoft)
                    Spacer()
                    Button { showHistory = false } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }
                    .buttonStyle(.plain)
                }
                if historyLoading && visibleHistory.isEmpty {
                    ProgressView().tint(Theme.cyan).frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if visibleHistory.isEmpty {
                    Text("暂无购买记录")
                        .font(.subheadline)
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView {
                        VStack(spacing: 10) {
                            ForEach(visibleHistory) { item in
                                historyCard(item)
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Theme.surfaceContainer.opacity(0.98), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.35), lineWidth: 1)
            )
            .padding(.horizontal, 18)
            .padding(.vertical, 48)
        }
    }

    private func historyCard(_ item: PointOrder) -> some View {
        let payChannel = item.payChannel ?? "alipay"
        let channelName = payChannel == "wechat" ? "微信" : "支付宝"
        let pendingLeft = item.status == "pending" ? payLeft(item, at: now) : 0
        let busy = resumingId == item.id
        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                WithdrawChannelBadge(channelId: payChannel, size: 28)
                Text(channelName)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.onSurface)
                Spacer()
                Text(pendingLeft > 0 ? "待支付 \(formatPayLeft(pendingLeft))" : (item.statusLabel.isEmpty ? item.status : item.statusLabel))
                    .font(.system(size: 13))
                    .foregroundStyle(orderStatusColor(item.status))
            }
            historyMeta("购买内容", "\(item.points) 张配图")
            historyMeta("支付金额", "¥\(displayAmount(item))")
            historyMeta("支付方式", channelName)
            historyMeta("购买时间", formatWithdrawTime(item.createdAt))
            historyMeta("到账时间", arrivalText(item, pendingLeft: pendingLeft))
            if pendingLeft > 0 {
                Button {
                    Task { await resume(item) }
                } label: {
                    Group {
                        if busy {
                            ProgressView().controlSize(.small).tint(Theme.onPrimary)
                        } else {
                            Text("继续支付")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(Theme.onPrimary)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(Theme.cyan.opacity(busy ? 0.4 : 1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(busy || payBusy)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surfaceHigh.opacity(0.9), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func historyMeta(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.system(size: 12)).foregroundStyle(Theme.onSurfaceVariant)
            Spacer(minLength: 12)
            Text(value).font(.system(size: 12)).foregroundStyle(Theme.onSurface).lineLimit(1)
        }
    }

    private func load() async {
        balance = model.checkIn.imageCredits
        do {
            catalog = try await model.api.fetchImageCreditCatalog()
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func loadHistory() async {
        guard let token = model.session?.token else {
            model.openLogin()
            return
        }
        historyLoading = true
        defer { historyLoading = false }
        do {
            history = try await model.api.listImageCreditOrders(token: token)
        } catch {
            model.banner = error.localizedDescription
        }
    }

    private func buy(_ package: PointPackage) async {
        guard let token = model.session?.token else {
            model.openLogin()
            return
        }
        if channel == "alipay" && !alipayReady {
            model.banner = "支付宝支付尚未开通"
            return
        }
        if channel == "wechat" && !wechatReady {
            model.banner = "微信支付尚未开通"
            return
        }
        guard !payBusy else { return }
        buyingId = package.id
        defer { buyingId = nil }
        do {
            let created = try await model.api.createPointOrder(token: token, packageId: package.id, channel: channel)
            guard created.orderId > 0 else {
                model.banner = "下单失败"
                return
            }
            guard let paid = await pay(token: token, created: created) else { return }
            let credits = package.credits > 0 ? package.credits : package.points
            await settle(token: token, created: created, credits: credits, paid: paid)
        } catch {
            model.banner = error.localizedDescription
        }
    }

    private func resume(_ order: PointOrder) async {
        guard let token = model.session?.token else {
            model.openLogin()
            return
        }
        guard !payBusy else { return }
        if payLeft(order, at: Date()) <= 0 {
            model.banner = "超过 10 分钟未支付，订单已取消"
            await loadHistory()
            return
        }
        resumingId = order.id
        defer { resumingId = nil }
        do {
            let created = try await model.api.continuePointOrder(token: token, orderId: order.id)
            guard let paid = await pay(token: token, created: created) else {
                await loadHistory()
                return
            }
            await settle(token: token, created: created, credits: order.points, paid: paid)
            if showHistory { await loadHistory() }
        } catch {
            model.banner = error.localizedDescription
            await loadHistory()
        }
    }

    private func pay(token: String, created: PointPurchaseResult) async -> PointOrderPayResult? {
        if created.channel == "wechat" {
            guard let wx = created.wechatPay, !wx.prepayId.isEmpty, !wx.sign.isEmpty else {
                model.banner = "微信下单失败"
                return nil
            }
            do {
                let result = try await SocialAuth.shared.payWithWechat(
                    partnerId: wx.partnerId,
                    prepayId: wx.prepayId,
                    packageValue: wx.packageValue,
                    nonceStr: wx.nonceStr,
                    timeStamp: wx.timeStamp,
                    sign: wx.sign
                )
                if result.success { return PointOrderPayResult(order: PointOrder(id: created.orderId, packageId: "", points: created.points, amountFen: created.amountFen, status: "", statusLabel: "", amountYuan: ""), balance: nil, imageCredits: nil) }
                model.banner = result.cancelled ? "已取消支付" : (result.errStr.isEmpty ? "支付未完成" : result.errStr)
                return nil
            } catch {
                model.banner = error.localizedDescription
                return nil
            }
        }
        if created.sandbox || (created.orderInfo ?? "").isEmpty {
            do {
                return try await model.api.simulatePointOrderPay(token: token, orderId: created.orderId)
            } catch {
                model.banner = error.localizedDescription
                return nil
            }
        }
        do {
            let result = try await SocialAuth.shared.payWithAlipay(orderInfo: created.orderInfo ?? "")
            if result.success || result.pending {
                return PointOrderPayResult(order: PointOrder(id: created.orderId, packageId: "", points: created.points, amountFen: created.amountFen, status: "", statusLabel: "", amountYuan: ""), balance: nil, imageCredits: nil)
            }
            model.banner = result.cancelled ? "已取消支付" : (result.memo.isEmpty ? "支付未完成(\(result.resultStatus))" : result.memo)
            return nil
        } catch {
            model.banner = error.localizedDescription
            return nil
        }
    }

    private func settle(token: String, created: PointPurchaseResult, credits: Int, paid: PointOrderPayResult) async {
        if let creditsBalance = paid.imageCredits {
            applyCredits(creditsBalance)
            model.banner = "已到账 \(credits) 张配图"
            return
        }
        var credited = false
        var latest = balance
        for attempt in 0..<12 {
            if attempt > 0 {
                try? await Task.sleep(nanoseconds: 500_000_000)
            }
            if let lookup = try? await model.api.getPointOrder(token: token, orderId: created.orderId),
               lookup.order.status == "paid" {
                latest = lookup.imageCredits ?? (balance + credits)
                applyCredits(latest)
                credited = true
                break
            }
        }
        model.banner = credited ? "已到账 \(credits) 张配图" : "支付结果确认中，请稍后查看剩余配图"
    }

    private func applyCredits(_ next: Int) {
        let value = max(0, next)
        balance = value
        model.checkIn.imageCredits = value
    }

    private func displayAmount(_ item: PointOrder) -> String {
        if !item.amountYuan.isEmpty { return item.amountYuan }
        if item.amountFen % 100 == 0 { return String(item.amountFen / 100) }
        return String(format: "%.2f", Double(item.amountFen) / 100)
    }

    private func arrivalText(_ item: PointOrder, pendingLeft: TimeInterval) -> String {
        switch item.status {
        case "paid", "refunded": formatWithdrawTime(item.paidAt)
        case "closed": "—"
        default: "剩余 \(formatPayLeft(pendingLeft))"
        }
    }

    private func orderStatusColor(_ status: String) -> Color {
        switch status {
        case "paid": Theme.cyan
        case "refunded": Theme.pink
        case "closed": Theme.onSurfaceVariant
        default: Theme.gold
        }
    }

    private func payLeft(_ order: PointOrder, at now: Date) -> TimeInterval {
        guard let expires = parseWithdrawDate(order.expiresAt) else { return 0 }
        return max(0, expires.timeIntervalSince(now))
    }

    private func formatPayLeft(_ seconds: TimeInterval) -> String {
        let total = max(0, Int(seconds))
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}

private let withdrawWechatGreen = Color(hex: 0x07C160)

struct WithdrawView: View {
    @EnvironmentObject private var model: AppModel
    @State private var config: WithdrawConfig?
    @State private var history: [WithdrawalItem] = []
    @State private var channel = "alipay"
    @State private var selectedFen = 20
    @State private var loading = true
    @State private var submitting = false
    @State private var watchingAd = false
    @State private var errorText: String?
    @State private var confirmOpen = false
    @State private var showHistory = false

    var body: some View {
        Group {
            if model.session?.token == nil {
                loggedOut
            } else if loading && config == nil {
                ProgressView()
                    .tint(Theme.cyan)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                content
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .stellarScreenBackground()
        .navigationTitle("搭币提现")
        .navigationBarTitleDisplayMode(.inline)
        .themeNavigationBar()
        .toolbar {
            if model.session?.token != nil {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("提现记录") { showHistory = true }
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(Theme.cyanSoft)
                }
            }
        }
        .task { await load() }
        .overlay { if confirmOpen { confirmDialog } }
        .overlay { if showHistory { historyDialog } }
    }

    private var loggedOut: some View {
        VStack(spacing: 12) {
            Text("登录后可提现")
                .font(.body)
                .foregroundStyle(Theme.onSurfaceVariant)
            Button("去登录") { model.openLogin() }
                .font(.body.weight(.semibold))
                .foregroundStyle(Theme.cyan)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                balanceCard
                Text("提现方式")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.onSurface)
                ForEach(payoutChannels) { item in
                    channelRow(item)
                }
                if !payoutReady {
                    accountWarning
                }
                Text("提现金额")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.onSurface)
                amountRow
                if let errorText {
                    Text(errorText)
                        .font(.footnote)
                        .foregroundStyle(Theme.pink)
                }
                confirmButton
            }
            .padding(16)
        }
    }

    private var balanceCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("可用搭币")
                .font(.system(size: 13))
                .foregroundStyle(Theme.onSurfaceVariant)
            Text("\(model.checkIn.totalPoints)")
                .font(.system(size: 28, weight: .bold))
                .foregroundStyle(Theme.cyan)
            if let config {
                Text(config.sandbox ? "沙箱模式，不会真实打款。每天只能提现一次。" : "每天只能提现一次。")
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.onSurfaceVariant)
                if !config.note.isEmpty {
                    Text(config.note)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.gold)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private func channelRow(_ item: WithdrawChannel) -> some View {
        let selected = item.id == channel
        let account = item.id == "wechat" ? model.session?.wechatAccount : model.session?.alipayAccount
        return Button {
            channel = item.id
        } label: {
            HStack(spacing: 12) {
                WithdrawChannelBadge(channelId: item.id, size: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.id == "wechat" ? "微信" : "支付宝")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(Theme.onSurface)
                    Text(maskPayoutAccount(account))
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .lineLimit(1)
                }
                Spacer()
                if selected {
                    Text("已选")
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.cyan)
                }
            }
            .padding(14)
            .background(
                (selected ? Theme.cyan.opacity(0.12) : Theme.surfaceContainer),
                in: RoundedRectangle(cornerRadius: 14, style: .continuous)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(selected ? Theme.cyan : Theme.outline.opacity(0.35), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private var accountWarning: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("收款账号")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Theme.onSurface)
            Text(selectedAccount.isEmpty
                 ? "尚未\(channel == "wechat" ? "绑定微信" : "绑定支付宝")收款账号"
                 : "尚未填写支付宝实名，打款会被拒绝")
                .font(.system(size: 13))
                .foregroundStyle(Theme.pink)
            NavigationLink(value: MeRoute.profile) {
                Text("去个人资料设置")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.cyan)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private var amountRow: some View {
        HStack(spacing: 10) {
            ForEach(config?.tiers ?? []) { tier in
                let selected = tier.amountFen == selectedFen
                let affordable = tier.pointsCost <= model.checkIn.totalPoints
                Button {
                    selectedFen = tier.amountFen
                } label: {
                    VStack(spacing: 4) {
                        Text(tier.label)
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(selected ? Theme.cyan : Theme.onSurface)
                        Text("\(tier.pointsCost) 搭币")
                            .font(.system(size: 12))
                            .foregroundStyle(affordable ? Theme.onSurfaceVariant : Theme.pink)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .padding(.horizontal, 6)
                    .background(
                        (selected ? Theme.cyan.opacity(0.12) : Theme.surfaceContainer),
                        in: RoundedRectangle(cornerRadius: 14, style: .continuous)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(selected ? Theme.cyan : Theme.outline.opacity(0.35), lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var confirmButton: some View {
        let tier = selectedTier
        let alreadyToday = withdrewToday(history)
        let enabled = !submitting && !watchingAd && config != nil && tier != nil && !alreadyToday
        return Button {
            guard let picked = tier else { return }
            validateAndWatch(picked, alreadyToday: alreadyToday)
        } label: {
            Group {
                if submitting || watchingAd {
                    ProgressView()
                        .tint(Theme.onSurface)
                        .controlSize(.small)
                } else {
                    Text(alreadyToday ? "今天已提现" : (tier.map { "确认提现 \($0.label)" } ?? "确认提现"))
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(enabled || watchingAd ? Theme.onPrimary : Theme.onSurfaceVariant)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                enabled ? Theme.cyan : Theme.surfaceHigh,
                in: RoundedRectangle(cornerRadius: 14, style: .continuous)
            )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    private var confirmDialog: some View {
        let tier = selectedTier
        let channelName = channel == "wechat" ? "微信" : "支付宝"
        let message: String = {
            guard let tier else { return "" }
            if channel == "wechat" {
                return "将消耗 \(tier.pointsCost) 搭币，向微信发放 \(tier.label)（¥\(tier.amountYuan)）。每天只能提现一次，微信和支付宝共用这一次。首次提现会打开微信确认收款并授权免确认；授权后再次提现将直接到账零钱。"
            }
            return "将消耗 \(tier.pointsCost) 搭币，向\(channelName)账号「\(maskPayoutAccount(selectedAccount))」发放 \(tier.label)（¥\(tier.amountYuan)）。每天只能提现一次，微信和支付宝共用这一次。"
        }()
        return ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture { if !submitting { confirmOpen = false } }
            VStack(alignment: .leading, spacing: 14) {
                Text("确认提现")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Theme.cyanSoft)
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
                HStack {
                    Spacer()
                    Button("取消") { if !submitting { confirmOpen = false } }
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .disabled(submitting)
                    Button(submitting ? "提交中…" : "确认") {
                        Task { await submit() }
                    }
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Theme.onPrimary)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 8)
                    .background(Theme.cyan, in: Capsule())
                    .disabled(submitting)
                }
            }
            .padding(20)
            .background(Theme.surfaceContainer.opacity(0.98), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.35), lineWidth: 1)
            )
            .padding(.horizontal, 28)
        }
    }

    private var historyDialog: some View {
        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture { showHistory = false }
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("提现记录")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(Theme.cyanSoft)
                    Spacer()
                    Button {
                        showHistory = false
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }
                    .buttonStyle(.plain)
                }
                if history.isEmpty {
                    Text("暂无提现记录")
                        .font(.subheadline)
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView {
                        VStack(spacing: 10) {
                            ForEach(history) { item in
                                historyCard(item)
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Theme.surfaceContainer.opacity(0.98), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.35), lineWidth: 1)
            )
            .padding(.horizontal, 18)
            .padding(.vertical, 48)
        }
    }

    private func historyCard(_ item: WithdrawalItem) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                WithdrawChannelBadge(channelId: item.channel, size: 28)
                Text(item.channel == "wechat" ? "微信" : "支付宝")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.onSurface)
                Spacer()
                Text(item.statusLabel.isEmpty ? item.status : item.statusLabel)
                    .font(.system(size: 13))
                    .foregroundStyle(historyStatusColor(item.status))
            }
            historyMeta("提现金额", "¥\(item.amountYuan)")
            historyMeta("提现方式", item.channel == "wechat" ? "微信" : "支付宝")
            historyMeta("提现时间", formatWithdrawTime(item.createdAt))
            historyMeta("到账时间", arrivalText(item))
            if item.wechatConfirm != nil {
                Button {
                    Task { await confirmWechat(item, fromHistory: true) }
                } label: {
                    Text("去微信确认/授权")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(withdrawWechatGreen, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surfaceHigh.opacity(0.9), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func historyMeta(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
                .font(.system(size: 12))
                .foregroundStyle(Theme.onSurfaceVariant)
            Spacer(minLength: 12)
            Text(value)
                .font(.system(size: 12))
                .foregroundStyle(Theme.onSurface)
                .lineLimit(1)
        }
    }

    private var payoutChannels: [WithdrawChannel] {
        let channels = config?.channels ?? []
        if !channels.isEmpty { return channels }
        return [
            WithdrawChannel(id: "alipay", name: "支付宝", accountLabel: "支付宝账号", accountHint: ""),
            WithdrawChannel(id: "wechat", name: "微信", accountLabel: "微信账号", accountHint: ""),
        ]
    }

    private var selectedAccount: String {
        let raw = channel == "wechat" ? model.session?.wechatAccount : model.session?.alipayAccount
        return raw?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    private var alipayName: String {
        model.session?.alipayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    private var payoutReady: Bool {
        if channel == "alipay" {
            return !selectedAccount.isEmpty && (isAlipayAuthIdentity(selectedAccount) || !alipayName.isEmpty)
        }
        return !selectedAccount.isEmpty
    }

    private var selectedTier: WithdrawTier? {
        guard let tiers = config?.tiers, !tiers.isEmpty else { return nil }
        return tiers.first { $0.amountFen == selectedFen } ?? tiers.first
    }

    private func load() async {
        guard let token = model.session?.token else {
            loading = false
            return
        }
        loading = true
        errorText = nil
        defer { loading = false }
        do {
            let fetched = try await model.api.fetchWithdrawConfig(token: token)
            config = fetched
            if fetched.channels.allSatisfy({ $0.id != channel }) {
                channel = "alipay"
            }
            if !fetched.tiers.contains(where: { $0.amountFen == selectedFen }) {
                selectedFen = fetched.tiers.first?.amountFen ?? selectedFen
            }
            history = try await model.api.listWithdrawals(token: token)
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func validateAndWatch(_ tier: WithdrawTier, alreadyToday: Bool) {
        if tier.pointsCost > model.checkIn.totalPoints {
            model.banner = "搭币不足"
            return
        }
        if alreadyToday {
            model.banner = "今天已经提现过一次，请明天再试"
            return
        }
        if selectedAccount.count < 3 {
            model.banner = channel == "wechat" ? "请先在个人资料中绑定微信" : "请先绑定支付宝账号"
            return
        }
        if channel == "wechat", config?.sandbox != true, config?.wechatReady != true {
            model.banner = "微信提现尚未开通（商户证书未配置）"
            return
        }
        if channel == "alipay",
           config?.sandbox != true,
           !isAlipayAuthIdentity(selectedAccount),
           alipayName.isEmpty {
            model.banner = "请先在个人资料中填写支付宝实名"
            return
        }
        if channel == "wechat",
           config?.sandbox != true,
           selectedAccount.count < 18 || !selectedAccount.hasPrefix("o") {
            model.banner = "请先在个人资料中绑定微信账号"
            return
        }
        selectedFen = tier.amountFen
        watchingAd = true
        Task {
            let result = await PangleAds.shared.showReward(userId: model.session.map { String($0.userId) })
            watchingAd = false
            switch result {
            case .rewarded:
                confirmOpen = true
            case .skipped:
                model.banner = "需看完广告才能提现"
            case .failed(let message):
                model.banner = message == "广告暂未填充" ? "广告暂未填充，请稍后再试" : "广告播放失败：\(message)"
            }
        }
    }

    private func submit() async {
        guard let token = model.session?.token, let tier = selectedTier, !submitting else { return }
        submitting = true
        defer { submitting = false }
        do {
            let result = try await model.api.createWithdrawal(
                token: token,
                channel: channel,
                account: selectedAccount,
                realName: channel == "alipay" ? alipayName : nil,
                amountFen: tier.amountFen
            )
            confirmOpen = false
            if let checkIn = result.checkIn {
                model.checkIn = checkIn
            } else {
                model.applyPoints(result.totalPoints)
            }
            if let confirm = result.item.wechatConfirm {
                await confirmWechat(result.item, fromHistory: false)
            } else {
                model.banner = withdrawNotice(result.message, item: result.item)
            }
            history = (try? await model.api.listWithdrawals(token: token)) ?? history
        } catch {
            model.banner = error.localizedDescription
        }
    }

    private func confirmWechat(_ item: WithdrawalItem, fromHistory: Bool) async {
        guard let confirm = item.wechatConfirm else { return }
        let tip: String
        do {
            let result = try await SocialAuth.shared.openMerchantTransfer(
                mchId: confirm.mchId,
                appId: confirm.appId,
                packageInfo: confirm.packageInfo
            )
            switch result {
            case "cancel":
                tip = fromHistory ? "已取消确认，可稍后再打开" : "已取消确认。可在提现记录里再次打开微信收款页"
            case "fail":
                tip = "无法打开微信确认收款"
            default:
                tip = fromHistory ? "请在微信里点确认收款" : "请在微信里确认收款并授权；之后提现将直接到账"
            }
        } catch {
            tip = error.localizedDescription
        }
        model.banner = tip
        if let token = model.session?.token {
            history = (try? await model.api.listWithdrawals(token: token)) ?? history
        }
    }

    private func historyStatusColor(_ status: String) -> Color {
        switch status {
        case "success": Theme.cyan
        case "failed": Theme.pink
        default: Theme.gold
        }
    }

    private func arrivalText(_ item: WithdrawalItem) -> String {
        switch item.status {
        case "success": formatWithdrawTime(item.paidAt ?? item.updatedAt)
        case "failed": "—"
        default: "处理中"
        }
    }
}

private struct WithdrawChannelBadge: View {
    var channelId: String
    var size: CGFloat

    var body: some View {
        let wechat = channelId.lowercased() == "wechat"
        Image(wechat ? "WeChat" : "Alipay")
            .resizable()
            .scaledToFill()
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: size * (wechat ? 0.28 : 0.22), style: .continuous))
    }
}

func withdrawNotice(_ message: String, item: WithdrawalItem) -> String {
    let yuan: String
    if !item.amountYuan.isEmpty {
        yuan = item.amountYuan
    } else if item.amountFen > 0 {
        yuan = String(format: "%.2f", Double(item.amountFen) / 100)
    } else {
        return message
    }
    guard let range = message.range(of: #"¥\d+(?:\.\d+)?"#, options: .regularExpression) else {
        return message
    }
    return message.replacingCharacters(in: range, with: "¥\(yuan)")
}

func isAlipayAuthIdentity(_ value: String?) -> Bool {
    let id = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    if id.count < 16 { return false }
    if id.range(of: "^2088\\d{12,16}$", options: .regularExpression) != nil { return true }
    if id.contains("@") || id.range(of: "^1\\d{10}$", options: .regularExpression) != nil { return false }
    return id.range(of: "^[A-Za-z0-9_-]{16,128}$", options: .regularExpression) != nil
}

private func maskPayoutAccount(_ raw: String?) -> String {
    let value = raw?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    if value.isEmpty { return "未设置" }
    if value.hasPrefix("o"), value.count >= 18 { return "已绑定微信" }
    if isAlipayAuthIdentity(value) { return "已绑定支付宝" }
    if value.count <= 4 { return value }
    if let at = value.firstIndex(of: "@") {
        let name = value[..<at]
        let domain = value[at...]
        return String(name.prefix(2)) + "***" + domain
    }
    let digits = value.filter(\.isNumber)
    if value.count >= 7, value.allSatisfy({ $0.isNumber || $0 == "+" }), digits.count >= 7 {
        return String(digits.prefix(3)) + "****" + String(digits.suffix(4))
    }
    return String(value.prefix(2)) + "***" + String(value.suffix(2))
}

private func withdrewToday(_ items: [WithdrawalItem]) -> Bool {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "Asia/Shanghai") ?? .current
    let today = Date()
    return items.contains { item in
        guard item.status == "pending" || item.status == "success" else { return false }
        guard let created = parseWithdrawDate(item.createdAt) else { return false }
        return calendar.isDate(created, inSameDayAs: today)
    }
}

private func formatWithdrawTime(_ iso: String?) -> String {
    guard let iso, !iso.isEmpty else { return "—" }
    guard let date = parseWithdrawDate(iso) else {
        return String(iso.prefix(16)).replacingOccurrences(of: "T", with: " ")
    }
    let display = DateFormatter()
    display.locale = Locale(identifier: "zh_CN")
    display.timeZone = .current
    display.dateFormat = "yyyy-MM-dd HH:mm"
    return display.string(from: date)
}

private func parseWithdrawDate(_ iso: String?) -> Date? {
    guard let iso, !iso.isEmpty else { return nil }
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    if let date = formatter.date(from: iso) { return date }
    formatter.formatOptions = [.withInternetDateTime]
    return formatter.date(from: iso)
}

struct InviteView: View {
    @EnvironmentObject private var model: AppModel
    @State private var info: InviteInfo?
    @State private var code = ""
    @State private var busy = false
    @State private var errorText: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("我的邀请码")
                        .font(.caption)
                        .foregroundStyle(Theme.onSurfaceVariant)
                    Text(model.session?.buddyId ?? "登录后显示")
                        .font(.system(size: 28, weight: .bold, design: .monospaced))
                        .foregroundStyle(Theme.cyanSoft)
                    if let info {
                        Text("邀请 1 人你得 \(info.inviterReward) 搭币，对方得 \(info.inviteeReward) 搭币")
                            .font(.footnote)
                            .foregroundStyle(Theme.onSurface)
                        Text("已邀请 \(info.invitedCount) 人")
                            .font(.footnote)
                            .foregroundStyle(Theme.gold)
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .glassPanel()
                if let buddyId = model.session?.buddyId, !buddyId.isEmpty {
                    ShareLink(item: shareText(buddyId)) {
                        Text("分享邀请")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(PrimaryButtonStyle())
                }
                if let errorText {
                    Text(errorText).foregroundStyle(Color(hex: 0xFF8A80))
                }
                if info?.canBindInvite == true {
                    Text("填写邀请人")
                        .font(.headline)
                        .foregroundStyle(Theme.onSurface)
                    TextField("对方的搭子号", text: $code)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .foregroundStyle(Theme.onSurface)
                        .padding(12)
                        .background(Theme.surfaceHigh, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    Button(busy ? "提交中…" : "绑定邀请码") {
                        Task { await bind() }
                    }
                    .buttonStyle(PrimaryButtonStyle(enabled: !busy))
                    .disabled(busy)
                } else if let bound = info?.invitedByBuddyId, !bound.isEmpty {
                    Text("已绑定邀请人 \(bound)")
                        .foregroundStyle(Theme.cyanSoft)
                }
                Text("新用户注册时也可以填写邀请码。")
                    .font(.caption)
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            .padding(16)
        }
        .stellarScreenBackground()
        .navigationTitle("邀请")
        .navigationBarTitleDisplayMode(.inline)
        .themeNavigationBar()
        .task { await load() }
    }

    private func shareText(_ buddyId: String) -> String {
        let id = buddyId.trimmingCharacters(in: .whitespacesAndNewlines)
        return "我在用「你的词搭子」背单词，邀请你一起来！\n邀请码：\(id)\n打开链接注册：\(WordBuddyAPI.primaryBase)/i/\(id)"
    }

    private func load() async {
        guard let token = model.session?.token else {
            model.openLogin()
            return
        }
        do {
            info = try await model.api.fetchInviteInfo(token: token)
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func bind() async {
        guard let token = model.session?.token else {
            model.openLogin()
            return
        }
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 4 else {
            model.banner = "请输入邀请码"
            return
        }
        busy = true
        defer { busy = false }
        do {
            let result = try await model.api.bindInviteCode(token: token, inviteCode: trimmed)
            model.applyPoints(result.totalPoints)
            model.banner = result.message
            code = ""
            info = try await model.api.fetchInviteInfo(token: token)
        } catch {
            model.banner = error.localizedDescription
        }
    }
}

struct PointsLedgerView: View {
    @EnvironmentObject private var model: AppModel
    @State private var pageData: PointsLedgerPage?
    @State private var items: [PointsLedgerEntry] = []
    @State private var loading = true
    @State private var loadingMore = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("当前搭币")
                    .font(.caption)
                    .foregroundStyle(Theme.onSurfaceVariant)
                Text("\(pageData?.balance ?? model.checkIn.totalPoints)")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundStyle(Theme.cyan)
                Text("收入和支出都会记在这里。")
                    .font(.caption)
                    .foregroundStyle(Theme.onSurfaceVariant)
                if loading && items.isEmpty {
                    ProgressView().tint(Theme.cyan).frame(maxWidth: .infinity).padding(.top, 24)
                } else if items.isEmpty {
                    Text("还没有搭币记录")
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .padding(.top, 12)
                } else {
                    ForEach(items) { item in
                        HStack(alignment: .center, spacing: 12) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.title)
                                    .font(.body.weight(.semibold))
                                    .foregroundStyle(Theme.onSurface)
                                if let detail = item.detail, !detail.isEmpty {
                                    Text(detail)
                                        .font(.caption)
                                        .foregroundStyle(Theme.onSurfaceVariant)
                                }
                                Text(Self.displayTime(item.createdAt))
                                    .font(.caption)
                                    .foregroundStyle(Theme.onSurfaceVariant)
                            }
                            Spacer(minLength: 8)
                            Text(item.delta >= 0 ? "+\(item.delta)" : "\(item.delta)")
                                .font(.title3.weight(.bold))
                                .foregroundStyle(item.delta >= 0 ? Theme.cyan : Theme.pink)
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .glassPanel()
                    }
                    if items.count < (pageData?.total ?? items.count) {
                        Button(loadingMore ? "加载中…" : "加载更多") {
                            Task { await load(page: (pageData?.page ?? 1) + 1) }
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(Theme.cyan)
                        .frame(maxWidth: .infinity)
                        .disabled(loadingMore)
                    }
                }
            }
            .padding(16)
        }
        .stellarScreenBackground()
        .navigationTitle("搭币明细")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load(page: 1) }
    }

    private func load(page: Int) async {
        guard let token = model.session?.token else {
            loading = false
            model.openLogin()
            return
        }
        if page == 1 { loading = true } else { loadingMore = true }
        defer {
            loading = false
            loadingMore = false
        }
        do {
            let result = try await model.api.fetchPointsLedger(token: token, page: page)
            pageData = result
            if page == 1 {
                items = result.items
            } else {
                let seen = Set(items.map(\.id))
                items.append(contentsOf: result.items.filter { !seen.contains($0.id) })
            }
        } catch {
            model.banner = error.localizedDescription
        }
    }

    private static func displayTime(_ iso: String?) -> String {
        guard let iso else { return "" }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        var date = formatter.date(from: iso)
        if date == nil {
            formatter.formatOptions = [.withInternetDateTime]
            date = formatter.date(from: iso)
        }
        guard let date else { return "" }
        let display = DateFormatter()
        display.locale = Locale(identifier: "zh_CN")
        display.timeZone = TimeZone(identifier: "Asia/Shanghai")
        display.dateFormat = "MM-dd HH:mm"
        return display.string(from: date)
    }
}
