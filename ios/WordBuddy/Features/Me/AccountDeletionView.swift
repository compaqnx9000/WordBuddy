import SwiftUI

struct AccountDeletionView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var status: AccountDeletionStatus?
    @State private var agreed = false
    @State private var force = false
    @State private var reason = "不常使用"
    @State private var code = ""
    @State private var busy = false

    private let reasons = ["不常使用", "担心隐私安全", "缺少需要的功能", "准备换账号", "其他"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                notice
                if let status, status.pending {
                    pending(status)
                } else {
                    conditions
                    form
                }
            }
            .padding(20)
        }
        .stellarScreenBackground()
        .navigationTitle("注销账号")
        .navigationBarTitleDisplayMode(.inline)
        .themeNavigationBar()
        .task { await reload() }
    }

    private var notice: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("注销须知")
                .font(.headline)
                .foregroundStyle(Theme.cyanSoft)
            Text("注销完成后，即使用同一手机号重新注册，也无法找回本账号里的生词、搭币和资料。")
                .foregroundStyle(Theme.onSurface)
            Text("普通注销有 \(status?.cooldownDays ?? 7) 天冷静期，期间可以撤销。强行注销在验证后立即生效。")
                .foregroundStyle(Theme.onSurfaceVariant)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private var conditions: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("注销条件")
                .font(.headline)
                .foregroundStyle(Theme.onSurface)
            if let status, !status.conditions.isEmpty {
                ForEach(status.conditions) { item in
                    HStack(alignment: .top, spacing: 8) {
                        Image(systemName: item.ok ? "checkmark.circle.fill" : "exclamationmark.circle")
                            .foregroundStyle(item.ok ? Theme.cyan : Theme.gold)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title).foregroundStyle(Theme.onSurface)
                            if !item.detail.isEmpty {
                                Text(item.detail)
                                    .font(.footnote)
                                    .foregroundStyle(Theme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                Text("正在检查账号状态…")
                    .foregroundStyle(Theme.onSurfaceVariant)
            }
            if let points = status?.remainingPoints, points > 0 {
                Text("剩余搭币 \(points)，注销后不再保留。")
                    .font(.footnote)
                    .foregroundStyle(Theme.gold)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private var form: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("注销原因")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.onSurface)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(reasons, id: \.self) { item in
                        Button(item) { reason = item }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(reason == item ? Theme.onPrimary : Theme.cyanSoft)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(reason == item ? Theme.cyan : Theme.surfaceHigh, in: Capsule())
                            .buttonStyle(.plain)
                    }
                }
            }
            Toggle("我已阅读并同意《账号注销须知》", isOn: $agreed)
                .tint(Theme.cyan)
                .foregroundStyle(Theme.onSurface)
            if status?.allPassed == false {
                Toggle("条件未满足，仍要强行注销", isOn: $force)
                    .tint(Theme.gold)
                    .foregroundStyle(Theme.onSurface)
            }
            HStack {
                TextField("短信验证码", text: $code)
                    .keyboardType(.numberPad)
                    .foregroundStyle(Theme.onSurface)
                Button(busy ? "发送中" : "获取验证码") {
                    Task { await sendCode() }
                }
                .disabled(busy || !agreed)
                .foregroundStyle(Theme.cyan)
            }
            Button(busy ? "提交中" : "确认注销") {
                Task { await submit() }
            }
            .buttonStyle(PrimaryButtonStyle(enabled: agreed && code.count >= 4 && !busy))
            .disabled(!agreed || code.count < 4 || busy)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private func pending(_ status: AccountDeletionStatus) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("注销申请已提交")
                .font(.headline)
                .foregroundStyle(Theme.gold)
            Text("将于 \(status.dueAtLabel.isEmpty ? "冷静期结束" : status.dueAtLabel) 自动完成。期间仍可使用账号，撤销后申请取消。")
                .foregroundStyle(Theme.onSurface)
            Button("撤销注销") {
                Task { await cancel() }
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(busy)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private func reload() async {
        status = await model.loadDeletion()
    }

    private func sendCode() async {
        busy = true
        defer { busy = false }
        if let debug = await model.sendDeletionCode(force: force) {
            code = debug
        }
    }

    private func submit() async {
        busy = true
        defer { busy = false }
        let useForce = status?.allPassed == false && force
        if let next = await model.submitDeletion(code: code, reason: reason, force: useForce) {
            status = next
            if next.deleted || next.immediate || model.session == nil {
                dismiss()
            }
        }
    }

    private func cancel() async {
        busy = true
        defer { busy = false }
        status = await model.cancelDeletion()
    }
}
