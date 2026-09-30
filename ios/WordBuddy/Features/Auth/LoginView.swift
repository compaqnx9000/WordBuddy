import SwiftUI
import UIKit

private enum LoginMode: String, CaseIterable, Identifiable {
    case sms
    case password

    var id: String { rawValue }

    var title: String {
        switch self {
        case .sms: "验证码登录"
        case .password: "密码登录"
        }
    }
}

struct LoginView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss

    @State private var mode: LoginMode = .sms
    @State private var phone = ""
    @State private var code = ""
    @State private var password = ""
    @State private var passwordConfirm = ""
    @State private var inviteCode = ""
    @State private var needPassword = false
    @State private var bindToken: String?
    @State private var bindProvider = "微信"
    @State private var sending = false
    @State private var loggingIn = false
    @State private var countdown = 0
    @State private var errorMessage: String?

    private var bindingPhone: Bool { bindToken != nil }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
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
                .frame(height: 48)

                brand
                    .padding(.top, 4)

                if !needPassword && !bindingPhone {
                    Text("登录后可同步收藏与生词本")
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.cyanSoft)
                        .padding(.top, 22)
                        .padding(.bottom, 14)
                } else {
                    Spacer().frame(height: 28)
                }

                formCard
                    .glassPanel(neon: true)

                if !needPassword && !bindingPhone {
                    Text("其他登录方式")
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.onSurfaceVariant.opacity(0.75))
                        .padding(.top, 28)
                    HStack(spacing: 28) {
                        socialButton("WeChat", label: "微信登录", tint: Color(hex: 0x07C160)) {
                            Task { await loginWithWechat() }
                        }
                        socialButton("Alipay", label: "支付宝登录", tint: Color(hex: 0x1677FF)) {
                            Task { await loginWithAlipay() }
                        }
                    }
                    .padding(.top, 14)
                    .disabled(loggingIn)
                }

                Text(footer)
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.onSurfaceVariant.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 18)
                    .padding(.bottom, 28)
            }
            .padding(.horizontal, 28)
        }
        .scrollDismissesKeyboard(.interactively)
        .stellarScreenBackground()
    }

    private var brand: some View {
        VStack(spacing: 6) {
            ZStack {
                Circle()
                    .fill(Theme.cyan.opacity(0.16))
                    .frame(width: 132, height: 132)
                Image("WordBuddyLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 104, height: 104)
                    .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                    .shadow(color: Theme.cyan.opacity(0.4), radius: 18, y: 6)
            }
            Text("词搭子")
                .font(.system(size: 28, weight: .bold))
                .tracking(1.2)
                .foregroundStyle(Theme.cyanSoft)
                .padding(.top, 10)
            Text("和搭子一起记单词")
                .font(.system(size: 14))
                .foregroundStyle(Theme.onSurfaceVariant)
        }
    }

    private var formCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(cardTitle)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Theme.onSurface)
            Text(subtitle)
                .font(.system(size: 12))
                .foregroundStyle(Theme.onSurfaceVariant)
                .padding(.top, 6)
                .fixedSize(horizontal: false, vertical: true)

            if !needPassword && !bindingPhone {
                modeTabs
                    .padding(.top, 16)
            }

            loginField("手机号", text: $phone, icon: "iphone", keyboard: .phonePad)
                .padding(.top, 16)
                .onChange(of: phone) { _, value in
                    phone = String(value.filter(\.isNumber).prefix(11))
                    if !needPassword { errorMessage = nil }
                }

            if bindingPhone || needPassword || mode == .sms {
                HStack(spacing: 10) {
                    loginField("验证码", text: $code, icon: "message", keyboard: .numberPad)
                        .onChange(of: code) { _, value in
                            code = String(value.filter(\.isNumber).prefix(6))
                        }
                    Button(sendTitle) { Task { await sendCode() } }
                        .disabled(!canSend)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(canSend ? Theme.onPrimary : Theme.onSurfaceVariant)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 16)
                        .background(canSend ? Theme.cyan : Theme.surfaceHigh, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .padding(.top, 12)
            }

            if bindingPhone {
                loginField("设置密码（选填）", text: $password, icon: "lock", secure: true)
                    .padding(.top, 12)
                loginField("再输入一次密码（若已填）", text: $passwordConfirm, icon: "lock", secure: true)
                    .padding(.top, 12)
            } else if needPassword {
                loginField("新密码（至少 6 位）", text: $password, icon: "lock", secure: true)
                    .padding(.top, 12)
                loginField("再输入一次密码", text: $passwordConfirm, icon: "lock", secure: true)
                    .padding(.top, 12)
                loginField("邀请码（选填，好友搭子号）", text: $inviteCode, icon: "person.badge.plus", keyboard: .asciiCapable)
                    .padding(.top, 12)
            } else if mode == .password {
                loginField("密码（至少 6 位）", text: $password, icon: "lock", secure: true)
                    .padding(.top, 12)
            }

            if let errorMessage {
                Text(errorMessage)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.pink)
                    .padding(.top, 12)
            }

            Button {
                Task { await submit() }
            } label: {
                if loggingIn {
                    ProgressView().tint(Theme.onPrimary)
                } else {
                    Text(submitTitle)
                        .font(.system(size: 16, weight: .bold))
                        .tracking(1)
                }
            }
            .buttonStyle(LoginButtonStyle(enabled: !loggingIn, fill: bindingPhone || needPassword ? Theme.cyan : Theme.cyanSoft))
            .disabled(loggingIn)
            .padding(.top, 20)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 20)
    }

    private var modeTabs: some View {
        HStack(spacing: 4) {
            ForEach(LoginMode.allCases) { item in
                Button {
                    mode = item
                    errorMessage = nil
                } label: {
                    Text(item.title)
                        .font(.system(size: 13, weight: item == mode ? .bold : .medium))
                        .foregroundStyle(item == mode ? Theme.cyanSoft : Theme.onSurfaceVariant)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(
                            item == mode ? Theme.cyan.opacity(0.28) : Color.clear,
                            in: RoundedRectangle(cornerRadius: 11, style: .continuous)
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(Theme.surfaceHigh, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Theme.outline.opacity(0.35), lineWidth: 1)
        )
    }

    private var cardTitle: String {
        if bindingPhone { return "绑定手机号" }
        if needPassword { return "新用户，请设置登录密码" }
        return "手机号登录"
    }

    private var subtitle: String {
        if bindingPhone {
            return "\(bindProvider)登录成功。请绑定手机号，以便找回账号与提现。"
        }
        if needPassword { return "验证通过后设置密码，之后可用手机号+密码登录" }
        switch mode {
        case .sms: return "验证码登录，新手机号验证后需设置密码"
        case .password: return "使用已设置的密码登录"
        }
    }

    private var footer: String {
        if bindingPhone { return "验证码将发送到您的手机，请注意查收。" }
        if needPassword { return "设置密码时可填写好友邀请码（选填），注册成功双方可获积分。" }
        switch mode {
        case .sms: return "验证码将发送到您的手机，请注意查收。"
        case .password: return "若尚未设置密码，请先用验证码登录。"
        }
    }

    private var submitTitle: String {
        if bindingPhone { return "绑定并进入" }
        if needPassword { return "设置密码并进入" }
        return "登录"
    }

    private var canSend: Bool {
        countdown <= 0 && !sending && phone.count == 11
    }

    private var sendTitle: String {
        if sending { return "发送中" }
        if countdown > 0 { return "\(countdown)s" }
        return "获取验证码"
    }

    private func goBack() {
        if bindingPhone {
            bindToken = nil
            password = ""
            passwordConfirm = ""
            code = ""
            errorMessage = nil
            return
        }
        if needPassword {
            needPassword = false
            password = ""
            passwordConfirm = ""
            errorMessage = nil
            return
        }
        dismiss()
    }

    private func socialButton(_ image: String, label: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(image)
                .resizable()
                .scaledToFit()
                .frame(width: 34, height: 34)
                .frame(width: 52, height: 52)
                .background(Color.white.opacity(0.92), in: Circle())
                .overlay(Circle().stroke(tint.opacity(0.35), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private func loginField(
        _ placeholder: String,
        text: Binding<String>,
        icon: String,
        keyboard: UIKeyboardType = .default,
        secure: Bool = false
    ) -> some View {
        LoginField(placeholder: placeholder, text: text, icon: icon, keyboard: keyboard, secure: secure)
    }

    private func sendCode() async {
        guard phone.count == 11 else {
            errorMessage = "请输入11位手机号"
            return
        }
        sending = true
        errorMessage = nil
        defer { sending = false }
        do {
            let debug = try await model.sendCode(phone: phone)
            if let debug, !debug.isEmpty {
                code = String(debug.filter(\.isNumber).prefix(6))
            }
            countdown = 60
            while countdown > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                countdown -= 1
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func submit() async {
        if bindingPhone {
            await bindPhone()
            return
        }
        guard phone.count == 11 else {
            errorMessage = "请输入11位手机号"
            return
        }
        if needPassword {
            if password.count < 6 {
                errorMessage = "密码至少 6 位"
                return
            }
            if password != passwordConfirm {
                errorMessage = "两次密码不一致"
                return
            }
            if code.count != 6 {
                errorMessage = "请输入6位验证码"
                return
            }
        } else if mode == .sms {
            if code.count != 6 {
                errorMessage = "请输入6位验证码"
                return
            }
        } else if password.count < 6 {
            errorMessage = "密码至少 6 位"
            return
        }

        loggingIn = true
        errorMessage = nil
        defer { loggingIn = false }
        do {
            let result: AuthResult
            if needPassword {
                result = try await model.register(
                    phone: phone,
                    code: code,
                    password: password,
                    inviteCode: inviteCode
                )
            } else if mode == .password {
                result = try await model.loginWithPassword(phone: phone, password: password)
            } else {
                result = try await model.login(phone: phone, code: code)
            }
            if result.isNewUser && result.session == nil {
                needPassword = true
                password = ""
                passwordConfirm = ""
                return
            }
            try await finish(result)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func loginWithWechat() async {
        await socialLogin(provider: "微信") {
            let code = try await SocialAuth.shared.signInWechat()
            return try await model.api.loginWithWechat(code: code)
        }
    }

    private func loginWithAlipay() async {
        await socialLogin(provider: "支付宝") {
            let info = try await model.api.fetchAlipayLoginAuthInfo()
            let authCode = try await SocialAuth.shared.signInAlipay(authInfo: info)
            return try await model.api.loginWithAlipay(authCode: authCode)
        }
    }

    private func socialLogin(provider: String, run: () async throws -> AuthResult) async {
        guard !loggingIn else { return }
        loggingIn = true
        errorMessage = nil
        defer { loggingIn = false }
        do {
            let result = try await run()
            if result.needsPhoneBind || (result.session?.phone.isEmpty ?? true) {
                guard let token = result.session?.token, !token.isEmpty else {
                    errorMessage = "登录失败"
                    return
                }
                bindProvider = provider
                bindToken = token
                phone = ""
                code = ""
                password = ""
                passwordConfirm = ""
                return
            }
            try await finish(result)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func bindPhone() async {
        guard let token = bindToken else { return }
        guard phone.count == 11 else {
            errorMessage = "请输入11位手机号"
            return
        }
        guard code.count == 6 else {
            errorMessage = "请输入6位验证码"
            return
        }
        if !password.isEmpty || !passwordConfirm.isEmpty {
            if password.count < 6 {
                errorMessage = "密码至少 6 位"
                return
            }
            if password != passwordConfirm {
                errorMessage = "两次密码不一致"
                return
            }
        }
        loggingIn = true
        errorMessage = nil
        defer { loggingIn = false }
        do {
            let result = try await model.api.bindPhone(
                token: token,
                phone: phone,
                code: code,
                password: password.isEmpty ? nil : password
            )
            bindToken = nil
            try await finish(result)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func finish(_ result: AuthResult) async throws {
        guard let session = result.session else {
            throw SocialAuthError.message("登录失败")
        }
        await model.enter(session)
        dismiss()
    }
}

private struct LoginField: View {
    var placeholder: String
    @Binding var text: String
    var icon: String
    var keyboard: UIKeyboardType
    var secure: Bool

    @State private var revealed = false

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundStyle(Theme.onSurfaceVariant)
                .frame(width: 20)
            ZStack(alignment: .leading) {
                if text.isEmpty {
                    Text(placeholder)
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.onSurfaceVariant.opacity(0.55))
                }
                Group {
                    if secure && !revealed {
                        SecureField("", text: $text)
                    } else {
                        TextField("", text: $text)
                            .keyboardType(keyboard)
                    }
                }
                .font(.system(size: 15))
                .foregroundStyle(Theme.onSurface)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            }
            if secure {
                Button {
                    revealed.toggle()
                } label: {
                    Image(systemName: revealed ? "eye.slash" : "eye")
                        .font(.system(size: 16))
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 14)
        .background(Theme.surfaceHigh, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Theme.outline.opacity(0.45), lineWidth: 1)
        )
    }
}

private struct LoginButtonStyle: ButtonStyle {
    var enabled: Bool
    var fill: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(Theme.onPrimary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(enabled ? fill : Theme.outline, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}
