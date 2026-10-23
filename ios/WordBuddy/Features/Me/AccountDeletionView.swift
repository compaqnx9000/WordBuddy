import SwiftUI

struct AccountDeletionView: View {
    var coversTabBar: Binding<Bool> = .constant(false)

    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss

    @State private var status: AccountDeletionStatus?
    @State private var step: Step = .notice
    @State private var agreed = false
    @State private var forceAgree = false
    @State private var forceMode = false
    @State private var showProtocol = false
    @State private var selectedReasons: Set<String> = []
    @State private var code = ""
    @State private var countdown = 0
    @State private var sending = false
    @State private var submitting = false
    @State private var loading = false
    @State private var loaded = false
    @State private var localError: String?
    @State private var infoMessage: String?

    private let reasons = ["不常使用", "担心隐私安全", "缺少需要的功能", "准备换账号", "其他"]

    private var canContinueCheck: Bool {
        status?.allPassed == true || forceAgree
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            if status == nil && loading && !loaded {
                Spacer()
                ProgressView()
                    .tint(Theme.cyan)
                Spacer()
            } else if showProtocol {
                ScrollView {
                    Text(LegalDocuments.deletionNotice)
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .lineSpacing(6)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 16)
                }
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        switch step {
                        case .notice:
                            noticeCard
                            agreeRow
                        case .check:
                            checkCard
                            forceRow
                        case .verify:
                            verifyCard
                        case .pending:
                            pendingCard
                        }
                        if let localError, !localError.isEmpty {
                            Text(localError)
                                .font(.system(size: 13))
                                .foregroundStyle(Theme.pink)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        primaryButton
                        if step == .verify, let infoMessage, !infoMessage.isEmpty, localError == nil {
                            Text(infoMessage)
                                .font(.system(size: 13))
                                .foregroundStyle(Theme.onSurfaceVariant)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        if step == .verify {
                            Text(forceMode
                                ? "强行注销会立即删除账号，搭币、生词本等全部清空，不可恢复。"
                                : "跳过原因并注销也可以。提交后有 \(status?.cooldownDays ?? 7) 天冷静期，期间可随时撤销。")
                                .font(.system(size: 12))
                                .foregroundStyle(forceMode ? Theme.pink : Theme.onSurfaceVariant.opacity(0.8))
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 28)
                }
            }
        }
        .stellarScreenBackground()
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { coversTabBar.wrappedValue = true }
        .onDisappear { coversTabBar.wrappedValue = false }
        .task { await reload() }
        .onChange(of: status?.pending) { _, pending in
            if pending == true { step = .pending }
        }
    }

    private var topBar: some View {
        ZStack {
            Text(showProtocol ? LegalDocuments.deletionTitle : "注销账号")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Theme.onSurface)
                .lineLimit(1)
            HStack {
                Button(action: goBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.cyanSoft)
                        .frame(width: 40, height: 40)
                }
                .buttonStyle(.plain)
                Spacer()
            }
        }
        .padding(.horizontal, 12)
        .frame(height: 56)
        .background(Theme.hasWallpaper ? Theme.surface.opacity(0.55) : Theme.surface.opacity(0.92))
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Theme.cyan.opacity(0.20))
                .frame(height: 1)
        }
    }

    private var noticeCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("注销须知")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.onSurface)
            Text("注销是不可恢复的操作。冷静期届满或强行注销完成后，即使使用相同手机号重新注册，也无法找回本账号中的内容。")
                .font(.system(size: 14))
                .foregroundStyle(Theme.pink)
                .lineSpacing(4)
                .padding(.top, 8)
            VStack(alignment: .leading, spacing: 6) {
                noticeLine("无法继续登录本账号，搭子号与资料将被删除")
                noticeLine("生词本、收藏、学习记录与助记无法找回")
                noticeLine("剩余搭币视为自愿放弃；也可勾选强行注销立即删除")
                noticeLine("普通注销有 7 天冷静期；强行注销验证后立即生效")
                noticeLine("相关日志依法可能保留不少于 6 个月")
            }
            .padding(.top, 14)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private var agreeRow: some View {
        HStack(alignment: .center, spacing: 10) {
            DeletionCheckBox(checked: agreed) { agreed.toggle() }
            Text("我已阅读并同意")
                .font(.system(size: 14))
                .foregroundStyle(Theme.onSurface)
                .onTapGesture { agreed.toggle() }
            Button {
                showProtocol = true
            } label: {
                Text("《\(LegalDocuments.deletionTitle)》")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.cyanSoft)
            }
            .buttonStyle(.plain)
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
    }

    private var checkCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("注销检测")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.onSurface)
            Text(status?.allPassed == true
                ? "当前满足注销条件。也可勾选强行注销，验证后立即删除。"
                : "还有未完成事项。若你确认全部放弃，可勾选强行注销，验证后立即删除账号。")
                .font(.system(size: 14))
                .foregroundStyle(status?.allPassed == true ? Theme.onSurfaceVariant : Theme.pink)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let status {
                ForEach(status.conditions) { item in
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: item.ok ? "checkmark.circle" : "exclamationmark.circle")
                            .font(.system(size: 18))
                            .foregroundStyle(item.ok ? Theme.cyan : Theme.pink)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(Theme.onSurface)
                            if !item.detail.isEmpty {
                                Text(item.detail)
                                    .font(.system(size: 13))
                                    .foregroundStyle(Theme.onSurfaceVariant)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                }
                if status.remainingPoints > 0 {
                    Text("当前剩余 \(status.remainingPoints) 搭币，注销后视为放弃。")
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                Text("正在检查账号状态…")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private var forceRow: some View {
        HStack(alignment: .top, spacing: 10) {
            DeletionCheckBox(checked: forceAgree) { forceAgree.toggle() }
                .padding(.top, 1)
            Text("我同意强行注销：放弃搭币、未完成兑换/提现及全部账号权益，验证后立即删除，不可恢复。")
                .font(.system(size: 13))
                .foregroundStyle(Theme.pink)
                .lineSpacing(3)
                .frame(maxWidth: .infinity, alignment: .leading)
                .onTapGesture { forceAgree.toggle() }
        }
        .padding(.vertical, 4)
    }

    private var verifyCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(forceMode ? "强行注销 · 身份验证" : "身份验证")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.onSurface)
            Text("验证码将发送至 \(status?.phoneMasked.isEmpty == false ? status!.phoneMasked : "绑定手机号")，以确认是你本人操作。")
                .font(.system(size: 14))
                .foregroundStyle(Theme.onSurfaceVariant)
                .frame(maxWidth: .infinity, alignment: .leading)
            HStack(spacing: 10) {
                ZStack(alignment: .leading) {
                    if code.isEmpty {
                        Text("6 位验证码")
                            .font(.system(size: 16))
                            .foregroundStyle(Theme.onSurfaceVariant.opacity(0.6))
                    }
                    TextField("", text: $code)
                        .keyboardType(.numberPad)
                        .font(.system(size: 16))
                        .foregroundStyle(Theme.onSurface)
                        .onChange(of: code) { _, value in
                            code = String(value.filter(\.isNumber).prefix(6))
                        }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.surfaceHigh, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                Button(sendTitle) { Task { await sendCode() } }
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(countdown > 0 || sending ? Theme.onSurfaceVariant : Theme.cyanSoft)
                    .disabled(countdown > 0 || sending)
                    .buttonStyle(.plain)
            }
            Text("注销原因（可多选，可跳过）")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Theme.onSurface)
            DeletionFlowLayout(spacing: 8) {
                ForEach(reasons, id: \.self) { reason in
                    let selected = selectedReasons.contains(reason)
                    Button {
                        if selected {
                            selectedReasons.remove(reason)
                        } else {
                            selectedReasons.insert(reason)
                        }
                    } label: {
                        Text(reason)
                            .font(.system(size: 13))
                            .foregroundStyle(selected ? Theme.onPrimary : Theme.onSurfaceVariant)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(selected ? Theme.cyanSoft : Theme.surfaceHigh, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private var pendingCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("账号正在注销中")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.onSurface)
            Text("将于 \(status?.dueAtLabel.isEmpty == false ? status!.dueAtLabel : "冷静期结束") 自动完成注销。期间可继续使用；若还想留下，请点击下方撤销。")
                .font(.system(size: 14))
                .foregroundStyle(Theme.onSurfaceVariant)
                .lineSpacing(4)
            Text("冷静期内不要只退出登录就以为取消了申请。到期未撤销将永久删除账号数据。")
                .font(.system(size: 13))
                .foregroundStyle(Theme.pink)
                .lineSpacing(4)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private var primaryButton: some View {
        let enabled = buttonEnabled
        let destructive = step == .verify || step == .pending || (step == .check && forceAgree)
        return Button(action: handlePrimary) {
            Text(buttonTitle)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(enabled ? Theme.onPrimary : Theme.onSurfaceVariant)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(
                    enabled ? (destructive ? Theme.pink : Theme.cyan) : Theme.surfaceHigh,
                    in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                )
                .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    private var buttonTitle: String {
        switch step {
        case .notice:
            return "下一步"
        case .check:
            if forceAgree { return "下一步（强行注销）" }
            if status?.allPassed == true { return "下一步" }
            return "请先勾选强行注销"
        case .verify:
            if submitting && forceMode { return "正在注销…" }
            if submitting { return "提交中…" }
            if forceMode { return "确认并立即注销" }
            return "确认注销"
        case .pending:
            return loading || submitting ? "处理中…" : "撤销注销"
        }
    }

    private var buttonEnabled: Bool {
        switch step {
        case .notice:
            return agreed && !loading
        case .check:
            return canContinueCheck && !loading
        case .verify:
            return code.count == 6 && !submitting && !sending
        case .pending:
            return !loading && !submitting
        }
    }

    private var sendTitle: String {
        if sending { return "发送中" }
        if countdown > 0 { return "\(countdown)s" }
        return "获取验证码"
    }

    private func noticeLine(_ text: String) -> some View {
        Text("· \(text)")
            .font(.system(size: 14))
            .foregroundStyle(Theme.onSurfaceVariant)
            .lineSpacing(4)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func goBack() {
        if showProtocol {
            showProtocol = false
            return
        }
        localError = nil
        infoMessage = nil
        switch step {
        case .notice, .pending:
            dismiss()
        case .verify:
            step = .check
        case .check:
            step = .notice
        }
    }

    private func handlePrimary() {
        localError = nil
        infoMessage = nil
        switch step {
        case .notice:
            step = .check
            if status == nil { Task { await reload() } }
        case .check:
            forceMode = forceAgree
            code = ""
            step = .verify
        case .verify:
            Task { await submit() }
        case .pending:
            Task { await cancel() }
        }
    }

    private func reload() async {
        loading = true
        defer {
            loading = false
            loaded = true
        }
        do {
            let next = try await model.loadDeletion()
            status = next
            if next.pending { step = .pending }
        } catch {
            localError = error.localizedDescription
        }
    }

    private func sendCode() async {
        sending = true
        localError = nil
        infoMessage = nil
        defer { sending = false }
        do {
            let debug = try await model.sendDeletionCode(force: forceMode)
            countdown = 60
            infoMessage = if let debug, !debug.isEmpty {
                "验证码 \(debug)"
            } else {
                "验证码已发送"
            }
            Task { await tickCountdown() }
        } catch {
            localError = error.localizedDescription
        }
    }

    private func tickCountdown() async {
        while countdown > 0, !Task.isCancelled {
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            if countdown > 0 { countdown -= 1 }
        }
    }

    private func submit() async {
        submitting = true
        defer { submitting = false }
        let reason = reasons.filter { selectedReasons.contains($0) }.joined(separator: "、")
        do {
            let next = try await model.submitDeletion(
                code: code,
                reason: reason.isEmpty ? nil : reason,
                force: forceMode
            )
            status = next
            if next.deleted || next.immediate || model.session == nil {
                dismiss()
            } else {
                step = .pending
            }
        } catch {
            localError = error.localizedDescription
        }
    }

    private func cancel() async {
        submitting = true
        defer { submitting = false }
        do {
            status = try await model.cancelDeletion()
            dismiss()
        } catch {
            localError = error.localizedDescription
        }
    }

    private enum Step {
        case notice, check, verify, pending
    }
}

private struct DeletionCheckBox: View {
    var checked: Bool
    var onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            RoundedRectangle(cornerRadius: 5, style: .continuous)
                .fill(checked ? Theme.cyan : Color.clear)
                .overlay {
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .stroke(checked ? Theme.cyan : Theme.onSurfaceVariant, lineWidth: 1.5)
                    if checked {
                        Text("✓")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Theme.onPrimary)
                    }
                }
                .frame(width: 20, height: 20)
        }
        .buttonStyle(.plain)
    }
}

private struct DeletionFlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        let rows = rows(width: width, subviews: subviews)
        let height = rows.reduce(CGFloat(0)) { $0 + $1.height } + spacing * CGFloat(max(0, rows.count - 1))
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rows = rows(width: bounds.width, subviews: subviews)
        var y = bounds.minY
        var index = 0
        for row in rows {
            var x = bounds.minX
            for size in row.sizes {
                subviews[index].place(
                    at: CGPoint(x: x, y: y),
                    proposal: ProposedViewSize(width: size.width, height: size.height)
                )
                x += size.width + spacing
                index += 1
            }
            y += row.height + spacing
        }
    }

    private func rows(width: CGFloat, subviews: Subviews) -> [(height: CGFloat, sizes: [CGSize])] {
        var result: [(height: CGFloat, sizes: [CGSize])] = []
        var current: [CGSize] = []
        var rowWidth: CGFloat = 0
        var rowHeight: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            let next = rowWidth == 0 ? size.width : rowWidth + spacing + size.width
            if rowWidth > 0, next > width {
                result.append((rowHeight, current))
                current = [size]
                rowWidth = size.width
                rowHeight = size.height
            } else {
                current.append(size)
                rowWidth = next
                rowHeight = max(rowHeight, size.height)
            }
        }
        if !current.isEmpty {
            result.append((rowHeight, current))
        }
        return result
    }
}
