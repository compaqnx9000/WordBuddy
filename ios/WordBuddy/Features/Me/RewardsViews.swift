import SwiftUI

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
            Text("积分兑礼")
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
                Text("我的积分")
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
                gatedLink("购买积分", systemImage: "cart") { BuyPointsView() }
                Spacer()
                Button {
                    category = "points_only"
                    Task { await loadGifts() }
                } label: {
                    quickLabel("bag", "0元起兑")
                }
                .buttonStyle(.plain)
                Spacer()
                gatedLink("积分提现", systemImage: "gift") { WithdrawView() }
                Spacer()
                Button { dismiss() } label: {
                    quickLabel("calendar", "每日签到")
                }
                .buttonStyle(.plain)
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
                    model.showLogin = true
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

    private func gatedLink<Destination: View>(_ title: String, systemImage: String, destination: @escaping () -> Destination) -> some View {
        Group {
            if loggedIn {
                NavigationLink {
                    destination()
                } label: {
                    quickLabel(systemImage, title)
                }
                .buttonStyle(.plain)
            } else {
                Button {
                    model.showLogin = true
                } label: {
                    quickLabel(systemImage, title)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func quickLabel(_ systemImage: String, _ title: String) -> some View {
        VStack(spacing: MallMetrics.sdp(4)) {
            Image(systemName: systemImage)
                .font(.system(size: MallMetrics.sdp(18), weight: .regular))
                .foregroundStyle(Theme.gold)
                .frame(width: MallMetrics.sdp(40), height: MallMetrics.sdp(40))
                .background(Theme.surfaceHigh, in: Circle())
            Text(title)
                .font(.system(size: MallMetrics.sdp(11)))
                .foregroundStyle(Theme.onSurfaceVariant)
        }
        .padding(.horizontal, MallMetrics.sdp(6))
        .padding(.vertical, MallMetrics.sdp(4))
    }

    private func giftCard(_ gift: GiftItem) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Color(hexString: gift.coverColor)
                .frame(maxWidth: .infinity)
                .frame(height: MallMetrics.coverSide)
                .overlay {
                    Text(gift.coverEmoji)
                        .font(.system(size: MallMetrics.sdp(44)))
                }
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
            gifts = try await model.api.listGifts(category: category)
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
    @State private var showAddress = false

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
            Text("将花费 \(gift?.priceLabel ?? "")\n当前积分 \(model.checkIn.totalPoints)")
        }
        .overlay {
            if showAddress {
                addressDialog
            }
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

    private func content(_ gift: GiftItem) -> some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Color(hexString: gift.coverColor)
                        .frame(maxWidth: .infinity)
                        .frame(height: UIScreen.main.bounds.width / 1.1)
                        .overlay {
                            Text(gift.coverEmoji)
                                .font(.system(size: MallMetrics.sdp(72)))
                        }
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
                            Text("积分已抵 \(offset) 元")
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
                        Text("当前积分 \(model.checkIn.totalPoints) · \(gift.redeemedLabel)")
                            .font(.system(size: MallMetrics.sdp(13)))
                            .foregroundStyle(Theme.cyan)
                            .padding(.top, MallMetrics.sdp(12))
                        if gift.cashFen > 0 {
                            Text("含现金部分：积分先扣，现金需客服确认（暂未开通在线支付）")
                                .font(.system(size: MallMetrics.sdp(12)))
                                .foregroundStyle(Theme.gold)
                                .padding(.top, MallMetrics.sdp(6))
                        }
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

    private var addressDialog: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea()
                .onTapGesture { showAddress = false }
            VStack(alignment: .leading, spacing: MallMetrics.sdp(8)) {
                Text("收货地址")
                    .font(.system(size: MallMetrics.sdp(20), weight: .bold))
                    .foregroundStyle(Theme.cyanSoft)
                addressField("收件人", "请填写收件人姓名", text: $name)
                addressField("电话", "请填写联系电话", text: $phone)
                addressField("地址", "省市区 + 详细地址", text: $detail)
                HStack {
                    Spacer()
                    Button("取消") { showAddress = false }
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .padding(MallMetrics.sdp(10))
                    Button("下一步") {
                        showAddress = false
                        confirm = true
                    }
                    .font(.system(size: MallMetrics.sdp(14), weight: .bold))
                    .foregroundStyle(Theme.onPrimary)
                    .padding(.horizontal, MallMetrics.sdp(16))
                    .padding(.vertical, MallMetrics.sdp(10))
                    .background(Theme.cyan, in: Capsule())
                }
            }
            .padding(MallMetrics.sdp(20))
            .background(Theme.surfaceContainer, in: RoundedRectangle(cornerRadius: MallMetrics.sdp(24), style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: MallMetrics.sdp(24), style: .continuous)
                    .stroke(Theme.cyan.opacity(0.4), lineWidth: 1)
            )
            .padding(.horizontal, MallMetrics.sdp(24))
        }
    }

    private func addressField(_ label: String, _ placeholder: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: MallMetrics.sdp(6)) {
            Text(label)
                .font(.system(size: MallMetrics.sdp(13), weight: .medium))
                .foregroundStyle(Theme.onSurfaceVariant)
            TextField(placeholder, text: text)
                .font(.system(size: MallMetrics.sdp(14)))
                .foregroundStyle(Theme.onSurface)
                .padding(MallMetrics.sdp(12))
                .background(Theme.surfaceHigh, in: RoundedRectangle(cornerRadius: MallMetrics.sdp(12), style: .continuous))
        }
    }

    private func beginRedeem(_ gift: GiftItem) {
        guard model.session != nil else {
            model.showLogin = true
            return
        }
        if gift.needAddress {
            showAddress = true
        } else {
            confirm = true
        }
    }

    private func load() async {
        if name.isEmpty { name = model.session?.shippingName ?? "" }
        if phone.isEmpty { phone = model.session?.shippingPhone ?? "" }
        if detail.isEmpty { detail = model.session?.shippingDetail ?? "" }
        loading = true
        defer { loading = false }
        gift = try? await model.api.fetchGift(id: giftId)
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
            model.showLogin = true
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
    @State private var errorText: String?

    private let wechatGreen = Color(hex: 0x07C160)
    private let alipayBlue = Color(hex: 0x1677FF)

    private var aiCost: Int {
        max(1, catalog?.aiImagePointsCost ?? model.aiImagePointsCost)
    }

    private var wechatReady: Bool { catalog?.wechatReady ?? false }

    private var alipayReady: Bool {
        (catalog?.alipayReady ?? true) || (catalog?.sandbox ?? false)
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    balanceCard
                    paySection
                    if let errorText {
                        Text(errorText).foregroundStyle(Color(hex: 0xFF8A80))
                    }
                    if catalog == nil && errorText == nil {
                        ProgressView().tint(Theme.cyan).frame(maxWidth: .infinity).padding(.top, 24)
                    } else if catalog?.items.isEmpty == true {
                        Text("暂无积分包")
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
            Text("购买积分")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.onSurface)
            Spacer()
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
    }

    private var balanceCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("当前积分")
                .font(.system(size: 13))
                .foregroundStyle(Theme.onSurfaceVariant)
            Text("\(model.checkIn.totalPoints)")
                .font(.system(size: 32, weight: .bold))
                .foregroundStyle(Theme.cyan)
                .padding(.top, 2)
            HStack(spacing: 6) {
                Image(systemName: "sparkles")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.gold)
                Text("AI 生图每次消耗 \(aiCost) 积分（缓存图不扣）")
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            .padding(.top, 8)
            if catalog?.sandbox == true && channel == "alipay" {
                Text("支付宝当前为沙箱：点购买会直接到账，不调起真实支付。")
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

    private var paySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("支付方式")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.onSurface)
            HStack(spacing: 10) {
                payChip(
                    title: "微信",
                    image: "WeChat",
                    accent: wechatGreen,
                    selected: channel == "wechat",
                    enabled: wechatReady
                ) {
                    channel = "wechat"
                }
                payChip(
                    title: "支付宝",
                    image: "Alipay",
                    accent: alipayBlue,
                    selected: channel == "alipay",
                    enabled: alipayReady
                ) {
                    channel = "alipay"
                }
            }
            if !wechatReady {
                Text("微信支付待商户证书配置完成后可用")
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.75))
            }
        }
    }

    private func payChip(
        title: String,
        image: String,
        accent: Color,
        selected: Bool,
        enabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(image)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 22, height: 22)
                    .opacity(enabled ? 1 : 0.4)
                Text(title)
                    .font(.system(size: 14, weight: selected ? .semibold : .medium))
                    .foregroundStyle(enabled ? Theme.onSurface : Theme.onSurfaceVariant.opacity(0.55))
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(chipFill(selected: selected, enabled: enabled, accent: accent), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(chipStroke(selected: selected, enabled: enabled, accent: accent), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    private func chipFill(selected: Bool, enabled: Bool, accent: Color) -> Color {
        if !enabled { return Theme.surfaceHigh.opacity(0.5) }
        if selected { return accent.opacity(0.12) }
        return Theme.surface
    }

    private func chipStroke(selected: Bool, enabled: Bool, accent: Color) -> Color {
        if !enabled { return Theme.outline.opacity(0.25) }
        if selected { return accent }
        return Theme.outline.opacity(0.35)
    }

    private func packageRow(_ package: PointPackage) -> some View {
        let busy = buyingId == package.id
        let enabled = buyingId == nil
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
                Text(packageLine(package))
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
                        ProgressView()
                            .controlSize(.small)
                            .tint(Theme.onSurface)
                    } else {
                        Text("购买")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Theme.onSurface)
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

    private func packageLine(_ package: PointPackage) -> String {
        if package.subtitle.isEmpty {
            return "\(package.points) 积分"
        }
        return "\(package.points) 积分 · \(package.subtitle)"
    }

    private func load() async {
        do {
            let fetched = try await model.api.fetchPointCatalog()
            catalog = fetched
            model.aiImagePointsCost = max(1, fetched.aiImagePointsCost)
            let alipayOn = fetched.alipayReady || fetched.sandbox
            channel = fetched.wechatReady ? "wechat" : (alipayOn ? "alipay" : "alipay")
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func buy(_ package: PointPackage) async {
        guard model.session != nil else {
            model.showLogin = true
            return
        }
        if channel == "wechat" && !wechatReady {
            model.banner = "微信支付尚未开通"
            return
        }
        if channel == "alipay" && !alipayReady {
            model.banner = "支付宝支付尚未开通"
            return
        }
        buyingId = package.id
        defer { buyingId = nil }
        await model.purchasePoints(packageId: package.id, points: package.points, channel: channel)
    }
}

struct WithdrawView: View {
    @EnvironmentObject private var model: AppModel
    @State private var config: WithdrawConfig?
    @State private var history: [WithdrawalItem] = []
    @State private var channel = "alipay"
    @State private var account = ""
    @State private var realName = ""
    @State private var busy = false
    @State private var errorText: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("可用积分")
                        .font(.caption)
                        .foregroundStyle(Theme.onSurfaceVariant)
                    Text("\(model.checkIn.totalPoints)")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundStyle(Theme.cyan)
                    if let config {
                        Text("单笔 ¥\(config.amountYuan)，消耗 \(config.pointsCost) 积分")
                            .font(.footnote)
                            .foregroundStyle(Theme.onSurfaceVariant)
                        if config.sandbox {
                            Text("当前是沙箱环境，打款为模拟到账。")
                                .font(.footnote)
                                .foregroundStyle(Theme.gold)
                        }
                        if !config.note.isEmpty {
                            Text(config.note)
                                .font(.footnote)
                                .foregroundStyle(Theme.gold)
                        }
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .glassPanel()
                if let errorText {
                    Text(errorText).foregroundStyle(Color(hex: 0xFF8A80))
                }
                Text("提现方式")
                    .font(.headline)
                    .foregroundStyle(Theme.onSurface)
                ForEach(config?.channels ?? []) { item in
                    Button {
                        channel = item.id
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.name)
                                    .foregroundStyle(Theme.onSurface)
                                if !item.accountHint.isEmpty {
                                    Text(item.accountHint)
                                        .font(.caption)
                                        .foregroundStyle(Theme.onSurfaceVariant)
                                }
                            }
                            Spacer()
                            Image(systemName: channel == item.id ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(channel == item.id ? Theme.cyan : Theme.onSurfaceVariant)
                        }
                        .padding(14)
                        .glassPanel()
                    }
                    .buttonStyle(.plain)
                }
                TextField(selectedChannel?.accountLabel ?? "收款账号", text: $account)
                    .textInputAutocapitalization(.never)
                    .foregroundStyle(Theme.onSurface)
                    .padding(12)
                    .background(Theme.surfaceHigh, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                if channel == "alipay" {
                    TextField("真实姓名（支付宝登录号需要）", text: $realName)
                        .foregroundStyle(Theme.onSurface)
                        .padding(12)
                        .background(Theme.surfaceHigh, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                Text("微信和支付宝 App 绑定即将支持。现在直接填写收款账号。")
                    .font(.caption)
                    .foregroundStyle(Theme.onSurfaceVariant)
                Button(busy ? "提交中…" : "确认提现") {
                    Task { await submit() }
                }
                .buttonStyle(PrimaryButtonStyle(enabled: !busy))
                .disabled(busy)
                if !history.isEmpty {
                    Text("提现记录")
                        .font(.headline)
                        .foregroundStyle(Theme.onSurface)
                    ForEach(history) { item in
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(item.channelLabel) ¥\(item.amountYuan)")
                                .foregroundStyle(Theme.onSurface)
                            Text("\(item.statusLabel) · \(item.account) · \(item.pointsSpent) 积分")
                                .font(.caption)
                                .foregroundStyle(Theme.onSurfaceVariant)
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .glassPanel()
                    }
                }
            }
            .padding(16)
        }
        .stellarScreenBackground()
        .navigationTitle("积分提现")
        .navigationBarTitleDisplayMode(.inline)
        .themeNavigationBar()
        .task { await load() }
    }

    private var selectedChannel: WithdrawChannel? {
        config?.channels.first { $0.id == channel }
    }

    private func load() async {
        guard let token = model.session?.token else {
            model.showLogin = true
            return
        }
        do {
            let fetched = try await model.api.fetchWithdrawConfig(token: token)
            config = fetched
            if fetched.channels.allSatisfy({ $0.id != channel }) {
                channel = fetched.channels.first?.id ?? "alipay"
            }
            history = try await model.api.listWithdrawals(token: token)
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func submit() async {
        guard let token = model.session?.token else {
            model.showLogin = true
            return
        }
        let trimmed = account.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 3 else {
            model.banner = "请填写收款账号"
            return
        }
        busy = true
        defer { busy = false }
        do {
            let result = try await model.api.createWithdrawal(
                token: token,
                channel: channel,
                account: trimmed,
                realName: realName.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            model.applyPoints(result.totalPoints)
            model.banner = result.message
            history = (try? await model.api.listWithdrawals(token: token)) ?? history
        } catch {
            model.banner = error.localizedDescription
        }
    }
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
                        Text("邀请 1 人你得 \(info.inviterReward) 积分，对方得 \(info.inviteeReward) 积分")
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
        return "我在用「词搭子」背单词，邀请你一起来！\n邀请码：\(id)\n打开链接注册：\(WordBuddyAPI.primaryBase)/i/\(id)"
    }

    private func load() async {
        guard let token = model.session?.token else {
            model.showLogin = true
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
            model.showLogin = true
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
