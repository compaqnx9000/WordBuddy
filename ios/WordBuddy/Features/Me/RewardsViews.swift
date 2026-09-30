import SwiftUI

struct PointsMallView: View {
    @EnvironmentObject private var model: AppModel
    @State private var categories: [GiftCategory] = [GiftCategory(id: "recommend", name: "推荐")]
    @State private var category = "recommend"
    @State private var gifts: [GiftItem] = []
    @State private var loading = true
    @State private var errorText: String?

    private let columns = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                header
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(categories) { item in
                            Button(item.name) {
                                category = item.id
                                Task { await loadGifts() }
                            }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(category == item.id ? Theme.onPrimary : Theme.cyanSoft)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(category == item.id ? Theme.cyan : Theme.surfaceHigh, in: Capsule())
                            .buttonStyle(.plain)
                        }
                    }
                }
                if loading {
                    ProgressView().tint(Theme.cyan).frame(maxWidth: .infinity).padding(.top, 24)
                } else if let errorText {
                    Text(errorText).foregroundStyle(Color(hex: 0xFF8A80))
                } else if gifts.isEmpty {
                    Text("暂无礼品").foregroundStyle(Theme.onSurfaceVariant)
                } else {
                    LazyVGrid(columns: columns, spacing: 10) {
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
            .padding(16)
        }
        .stellarScreenBackground()
        .navigationTitle("积分兑礼")
        .navigationBarTitleDisplayMode(.inline)
        .themeNavigationBar()
        .task { await load() }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("当前积分")
                .font(.caption)
                .foregroundStyle(Theme.onSurfaceVariant)
            Text("\(model.checkIn.totalPoints)")
                .font(.system(size: 32, weight: .bold))
                .foregroundStyle(Theme.gold)
            HStack(spacing: 12) {
                NavigationLink("购买积分") { BuyPointsView() }
                NavigationLink("提现") { WithdrawView() }
                NavigationLink("兑换记录") { GiftOrdersView() }
            }
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(Theme.cyan)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private func giftCard(_ gift: GiftItem) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(gift.coverEmoji)
                .font(.system(size: 36))
                .frame(maxWidth: .infinity, minHeight: 88)
                .background(Color(hexString: gift.coverColor), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            Text(gift.title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.onSurface)
                .lineLimit(2)
            Text(gift.priceLabel)
                .font(.caption.weight(.bold))
                .foregroundStyle(Theme.gold)
            Text(gift.redeemedLabel)
                .font(.caption2)
                .foregroundStyle(Theme.onSurfaceVariant)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
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
    @EnvironmentObject private var model: AppModel
    var giftId: Int64
    @State private var gift: GiftItem?
    @State private var loading = true
    @State private var busy = false
    @State private var name = ""
    @State private var phone = ""
    @State private var detail = ""
    @State private var confirm = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if loading {
                ProgressView().tint(Theme.cyan)
            } else if let gift {
                content(gift)
            } else {
                Text("礼品不存在").foregroundStyle(Theme.onSurfaceVariant)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .stellarScreenBackground()
        .navigationTitle("礼品详情")
        .navigationBarTitleDisplayMode(.inline)
        .themeNavigationBar()
        .task { await load() }
        .alert("确认兑换", isPresented: $confirm) {
            Button("确认") { Task { await redeem() } }
            Button("取消", role: .cancel) {}
        } message: {
            Text("将花费 \(gift?.priceLabel ?? "")\n当前积分 \(model.checkIn.totalPoints)")
        }
    }

    private func content(_ gift: GiftItem) -> some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(gift.coverEmoji)
                        .font(.system(size: 72))
                        .frame(maxWidth: .infinity, minHeight: 180)
                        .background(Color(hexString: gift.coverColor))
                    Text(gift.priceLabel)
                        .font(.title2.weight(.bold))
                        .foregroundStyle(Theme.gold)
                        .padding(.horizontal, 16)
                    Text(gift.title)
                        .font(.title3.weight(.bold))
                        .foregroundStyle(Theme.onSurface)
                        .padding(.horizontal, 16)
                    if !gift.subtitle.isEmpty {
                        Text(gift.subtitle)
                            .foregroundStyle(Theme.onSurfaceVariant)
                            .padding(.horizontal, 16)
                    }
                    Text(gift.description.isEmpty ? "兑换后由词搭子处理发放。" : gift.description)
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .padding(.horizontal, 16)
                    Text("当前积分 \(model.checkIn.totalPoints) · \(gift.redeemedLabel)")
                        .font(.footnote)
                        .foregroundStyle(Theme.cyanSoft)
                        .padding(.horizontal, 16)
                    if gift.cashFen > 0 {
                        Text("含现金部分：积分先扣，在线支付现金即将支持。")
                            .font(.footnote)
                            .foregroundStyle(Theme.gold)
                            .padding(.horizontal, 16)
                    }
                    if gift.needAddress {
                        addressFields
                    }
                }
                .padding(.bottom, 16)
            }
            Button(busy ? "兑换中…" : "立即兑换") {
                guard model.session != nil else {
                    model.showLogin = true
                    return
                }
                if gift.needAddress && (name.isEmpty || phone.isEmpty || detail.isEmpty) {
                    model.banner = "请填写收货人、手机号和地址"
                    return
                }
                confirm = true
            }
            .buttonStyle(PrimaryButtonStyle(enabled: !busy))
            .disabled(busy)
            .padding(16)
        }
    }

    private var addressFields: some View {
        VStack(spacing: 8) {
            field("收货人", text: $name)
            field("手机号", text: $phone)
            field("详细地址", text: $detail)
        }
        .padding(.horizontal, 16)
    }

    private func field(_ title: String, text: Binding<String>) -> some View {
        TextField(title, text: text)
            .foregroundStyle(Theme.onSurface)
            .padding(12)
            .background(Theme.surfaceHigh, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func load() async {
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
    @EnvironmentObject private var model: AppModel
    @State private var catalog: PointCatalog?
    @State private var buyingId: String?
    @State private var errorText: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("当前积分")
                        .font(.caption)
                        .foregroundStyle(Theme.onSurfaceVariant)
                    Text("\(model.checkIn.totalPoints)")
                        .font(.system(size: 34, weight: .bold))
                        .foregroundStyle(Theme.gold)
                    Text("微信和支付宝 iOS 支付即将支持。现在购买走模拟支付，积分会直接到账。")
                        .font(.footnote)
                        .foregroundStyle(Theme.onSurfaceVariant)
                    Text("AI 配图每次约 \(catalog?.aiImagePointsCost ?? model.aiImagePointsCost) 积分")
                        .font(.footnote)
                        .foregroundStyle(Theme.cyanSoft)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .glassPanel()
                if let errorText {
                    Text(errorText).foregroundStyle(Color(hex: 0xFF8A80))
                }
                if catalog == nil && errorText == nil {
                    ProgressView().tint(Theme.cyan).frame(maxWidth: .infinity)
                }
                ForEach(catalog?.items ?? []) { package in
                    packageRow(package)
                }
            }
            .padding(16)
        }
        .stellarScreenBackground()
        .navigationTitle("购买积分")
        .navigationBarTitleDisplayMode(.inline)
        .themeNavigationBar()
        .task { await load() }
    }

    private func packageRow(_ package: PointPackage) -> some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(package.title)
                        .font(.headline)
                        .foregroundStyle(Theme.onSurface)
                    if let badge = package.badge {
                        Text(badge)
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(Theme.onPrimary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Theme.gold, in: Capsule())
                    }
                }
                if !package.subtitle.isEmpty {
                    Text(package.subtitle)
                        .font(.footnote)
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
                Text("¥\(package.amountYuan) · \(package.points) 积分")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.cyanSoft)
            }
            Spacer()
            Button(buyingId == package.id ? "支付中" : "购买") {
                Task { await buy(package) }
            }
            .font(.subheadline.weight(.bold))
            .foregroundStyle(Theme.onPrimary)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(Theme.cyan, in: Capsule())
            .disabled(buyingId != nil)
        }
        .padding(14)
        .glassPanel()
    }

    private func load() async {
        do {
            let fetched = try await model.api.fetchPointCatalog()
            catalog = fetched
            model.aiImagePointsCost = max(1, fetched.aiImagePointsCost)
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func buy(_ package: PointPackage) async {
        guard model.session != nil else {
            model.showLogin = true
            return
        }
        buyingId = package.id
        defer { buyingId = nil }
        await model.purchasePoints(packageId: package.id, points: package.points)
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
