import AlipaySDK
import AuthenticationServices
import CryptoKit
import Security
import UIKit
import WechatOpenSDK

struct WeChatPayOutcome: Equatable {
    var errCode: Int32
    var errStr: String

    var success: Bool { errCode == WXSuccess.rawValue }
    var cancelled: Bool { errCode == WXErrCodeUserCancel.rawValue }
}

struct AppleSignIn: Equatable {
    var identityToken: String
    var nonce: String
    var fullName: String
}

struct AlipayPayOutcome: Equatable {
    var resultStatus: String
    var memo: String

    var success: Bool { resultStatus == "9000" }
    var cancelled: Bool { resultStatus == "6001" }
    var pending: Bool { resultStatus == "8000" || resultStatus == "6004" }
}

enum SocialAuthError: LocalizedError {
    case message(String)

    var errorDescription: String? {
        switch self {
        case .message(let text): text
        }
    }
}

/// WeChat SendAuth and Alipay auth_V2. AppId matches the Android build (`wxc7735828bd1fb638`).
final class SocialAuth: NSObject, WXApiDelegate, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    static let shared = SocialAuth()

    static let wechatAppId = "wxc7735828bd1fb638"
    static let universalLink = "https://wordbuddy.cc/wordbuddy/"
    static let alipayScheme = "wordbuddy"

    private var pendingWechat: CheckedContinuation<String, Error>?
    private var pendingAlipay: CheckedContinuation<String, Error>?
    private var pendingApple: CheckedContinuation<AppleSignIn, Error>?
    private var appleNonce = ""
    private var appleController: ASAuthorizationController?
    private var pendingTransfer: CheckedContinuation<String, Error>?
    private var pendingWechatPay: CheckedContinuation<WeChatPayOutcome, Error>?
    private var pendingAlipayPay: CheckedContinuation<AlipayPayOutcome, Error>?
    private var alipayPayActive = false

    private override init() {
        super.init()
    }

    func register() {
        WXApi.registerApp(Self.wechatAppId, universalLink: Self.universalLink)
    }

    @MainActor
    func signInWechat() async throws -> String {
        guard let controller = Self.topController() else {
            throw SocialAuthError.message("无法打开微信")
        }
        return try await withCheckedThrowingContinuation { continuation in
            if let pendingWechat {
                self.pendingWechat = nil
                pendingWechat.resume(throwing: SocialAuthError.message("已取消微信授权"))
            }
            pendingWechat = continuation
            let request = SendAuthReq()
            request.scope = "snsapi_userinfo"
            request.state = "wordbuddy_login"
            WXApi.sendAuthReq(request, viewController: controller, delegate: self) { [weak self] success in
                guard let self, !success else { return }
                let pending = self.pendingWechat
                self.pendingWechat = nil
                pending?.resume(throwing: SocialAuthError.message("无法打开微信"))
            }
        }
    }

    @MainActor
    func signInApple() async throws -> AppleSignIn {
        let nonce = Self.randomNonce()
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(nonce)
        return try await withCheckedThrowingContinuation { continuation in
            if let pendingApple {
                self.pendingApple = nil
                pendingApple.resume(throwing: SocialAuthError.message("已取消 Apple 登录"))
            }
            pendingApple = continuation
            appleNonce = nonce
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            appleController = controller
            controller.performRequests()
        }
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        appleController = nil
        let pending = pendingApple
        pendingApple = nil
        guard let pending else { return }
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = credential.identityToken,
              let token = String(data: tokenData, encoding: .utf8),
              !token.isEmpty
        else {
            pending.resume(throwing: SocialAuthError.message("Apple 登录失败"))
            return
        }
        let name = credential.fullName?.formatted().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        pending.resume(returning: AppleSignIn(identityToken: token, nonce: appleNonce, fullName: name))
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        appleController = nil
        let pending = pendingApple
        pendingApple = nil
        guard let pending else { return }
        let nsError = error as NSError
        if nsError.domain == ASAuthorizationError.errorDomain,
           nsError.code == ASAuthorizationError.canceled.rawValue {
            pending.resume(throwing: SocialAuthError.message("已取消 Apple 登录"))
            return
        }
        pending.resume(throwing: SocialAuthError.message("Apple 登录失败"))
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let window = scenes.flatMap(\.windows).first(where: \.isKeyWindow) ?? scenes.first?.windows.first
        return window ?? ASPresentationAnchor()
    }

    @MainActor
    func signInAlipay(authInfo: String) async throws -> String {
        try await withCheckedThrowingContinuation { continuation in
            if let pendingAlipay {
                self.pendingAlipay = nil
                pendingAlipay.resume(throwing: SocialAuthError.message("已取消支付宝授权"))
            }
            pendingAlipay = continuation
            AlipaySDK.defaultService().auth_V2(withInfo: authInfo, fromScheme: Self.alipayScheme) { [weak self] result in
                self?.deliverAlipay(result)
            }
        }
    }

    /// Opens the WeChat merchant-transfer confirm page. Returns success, cancel, or fail.
    @MainActor
    func openMerchantTransfer(mchId: String, appId: String, packageInfo: String) async throws -> String {
        guard WXApi.isWXAppInstalled() else {
            throw SocialAuthError.message("请先安装微信")
        }
        return try await withCheckedThrowingContinuation { continuation in
            if let pendingTransfer {
                self.pendingTransfer = nil
                pendingTransfer.resume(throwing: SocialAuthError.message("已取消确认收款"))
            }
            pendingTransfer = continuation
            let request = WXOpenBusinessViewReq()
            request.businessType = "requestMerchantTransfer"
            request.query = "mchId=\(Self.formEncode(mchId))&appId=\(Self.formEncode(appId))&package=\(Self.formEncode(packageInfo))"
            WXApi.send(request) { [weak self] success in
                guard !success, let pending = self?.takeTransfer() else { return }
                pending.resume(throwing: SocialAuthError.message("无法打开微信确认收款"))
            }
        }
    }

    /// Opens WeChat Pay. The outcome distinguishes success, cancel, and failure.
    @MainActor
    func payWithWechat(
        partnerId: String,
        prepayId: String,
        packageValue: String,
        nonceStr: String,
        timeStamp: String,
        sign: String
    ) async throws -> WeChatPayOutcome {
        guard WXApi.isWXAppInstalled() else {
            throw SocialAuthError.message("请先安装微信")
        }
        return try await withCheckedThrowingContinuation { continuation in
            if let pending = takeWechatPay() {
                pending.resume(throwing: SocialAuthError.message("已取消支付"))
            }
            pendingWechatPay = continuation
            let request = PayReq()
            request.partnerId = partnerId
            request.prepayId = prepayId
            request.nonceStr = nonceStr
            request.timeStamp = UInt32(timeStamp) ?? 0
            request.setValue(packageValue.isEmpty ? "Sign=WXPay" : packageValue, forKey: "package")
            request.sign = sign
            WXApi.send(request) { [weak self] success in
                guard !success, let pending = self?.takeWechatPay() else { return }
                pending.resume(throwing: SocialAuthError.message("无法打开微信支付"))
            }
        }
    }

    /// Opens Alipay. App-to-app return is delivered through `handleOpen`.
    @MainActor
    func payWithAlipay(orderInfo: String) async throws -> AlipayPayOutcome {
        guard let scheme = URL(string: "alipays://"), UIApplication.shared.canOpenURL(scheme) else {
            throw SocialAuthError.message("请先安装支付宝")
        }
        return try await withCheckedThrowingContinuation { continuation in
            if let pending = takeAlipayPay() {
                pending.resume(throwing: SocialAuthError.message("已取消支付"))
            }
            pendingAlipayPay = continuation
            alipayPayActive = true
            AlipaySDK.defaultService().payOrder(orderInfo, fromScheme: Self.alipayScheme) { [weak self] result in
                self?.deliverAlipayPay(result)
            }
        }
    }

    @discardableResult
    func handleOpen(url: URL) -> Bool {
        let text = url.absoluteString
        if url.host == "safepay" || text.contains("safepay") {
            if alipayPayActive {
                AlipaySDK.defaultService().processOrder(withPaymentResult: url, standbyCallback: { [weak self] result in
                    self?.deliverAlipayPay(result)
                })
            } else {
                AlipaySDK.defaultService().processAuth_V2Result(url, standbyCallback: { [weak self] result in
                    self?.deliverAlipay(result)
                })
            }
            return true
        }
        return WXApi.handleOpen(url, delegate: self)
    }

    func handleUniversalLink(_ activity: NSUserActivity) -> Bool {
        WXApi.handleOpenUniversalLink(activity, delegate: self)
    }

    func onResp(_ resp: BaseResp) {
        if let pay = resp as? PayResp {
            deliverWechatPay(pay)
            return
        }
        if let transfer = resp as? WXOpenBusinessViewResp {
            deliverTransfer(transfer)
            return
        }
        guard let auth = resp as? SendAuthResp else { return }
        let pending = pendingWechat
        pendingWechat = nil
        if auth.errCode == WXSuccess.rawValue, let code = auth.code, !code.isEmpty {
            pending?.resume(returning: code)
            return
        }
        if auth.errCode == WXErrCodeUserCancel.rawValue {
            pending?.resume(throwing: SocialAuthError.message("已取消微信授权"))
            return
        }
        let message = auth.errStr.trimmingCharacters(in: .whitespacesAndNewlines)
        pending?.resume(throwing: SocialAuthError.message(message.isEmpty ? "微信登录失败" : message))
    }

    private func deliverTransfer(_ resp: WXOpenBusinessViewResp) {
        guard let pending = takeTransfer() else { return }
        if resp.errCode == WXErrCodeUserCancel.rawValue {
            pending.resume(returning: "cancel")
            return
        }
        guard resp.businessType == "requestMerchantTransfer" else {
            pending.resume(returning: "fail")
            return
        }
        let raw = resp.extMsg ?? ""
        guard let data = raw.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let result = json["result"] as? String,
              !result.isEmpty else {
            pending.resume(returning: resp.errCode == WXSuccess.rawValue ? "success" : "fail")
            return
        }
        pending.resume(returning: result)
    }

    private func deliverWechatPay(_ resp: PayResp) {
        guard let pending = takeWechatPay() else { return }
        let message = resp.errStr.trimmingCharacters(in: .whitespacesAndNewlines)
        pending.resume(returning: WeChatPayOutcome(errCode: resp.errCode, errStr: message))
    }

    private func deliverAlipayPay(_ result: [AnyHashable: Any]?) {
        guard let pending = takeAlipayPay() else { return }
        alipayPayActive = false
        pending.resume(returning: AlipayPayOutcome(
            resultStatus: Self.string(result?["resultStatus"]),
            memo: Self.string(result?["memo"])
        ))
    }

    private func takeWechatPay() -> CheckedContinuation<WeChatPayOutcome, Error>? {
        objc_sync_enter(self)
        defer { objc_sync_exit(self) }
        let pending = pendingWechatPay
        pendingWechatPay = nil
        return pending
    }

    private func takeAlipayPay() -> CheckedContinuation<AlipayPayOutcome, Error>? {
        objc_sync_enter(self)
        defer { objc_sync_exit(self) }
        let pending = pendingAlipayPay
        pendingAlipayPay = nil
        return pending
    }

    private func takeTransfer() -> CheckedContinuation<String, Error>? {
        objc_sync_enter(self)
        defer { objc_sync_exit(self) }
        let pending = pendingTransfer
        pendingTransfer = nil
        return pending
    }

    private static func randomNonce(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var bytes = [UInt8](repeating: 0, count: length)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        if status != errSecSuccess {
            return UUID().uuidString.replacingOccurrences(of: "-", with: "")
        }
        return String(bytes.map { charset[Int($0) % charset.count] })
    }

    private static func sha256(_ value: String) -> String {
        SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    private static func formEncode(_ value: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-_.")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    private func deliverAlipay(_ result: [AnyHashable: Any]?) {
        let pending = pendingAlipay
        pendingAlipay = nil
        guard let pending else { return }
        let status = Self.string(result?["resultStatus"])
        if status == "6001" {
            pending.resume(throwing: SocialAuthError.message("已取消支付宝授权"))
            return
        }
        let raw = Self.string(result?["result"])
        let memo = Self.string(result?["memo"])
        let code = Self.authCode(from: raw)
        if let code, !code.isEmpty {
            pending.resume(returning: code)
            return
        }
        pending.resume(throwing: SocialAuthError.message(memo.isEmpty ? "支付宝授权失败" : memo))
    }

    private static func authCode(from result: String) -> String? {
        for part in result.split(separator: "&") {
            let pieces = part.split(separator: "=", maxSplits: 1)
            guard pieces.count == 2, pieces[0] == "auth_code" else { continue }
            let value = String(pieces[1])
            let decoded = value.removingPercentEncoding ?? value
            if !decoded.isEmpty { return decoded }
        }
        return nil
    }

    private static func string(_ value: Any?) -> String {
        if let text = value as? String { return text }
        if let text = value as? NSString { return text as String }
        return ""
    }

    @MainActor
    private static func topController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let window = scenes.flatMap(\.windows).first(where: \.isKeyWindow) ?? scenes.first?.windows.first
        var controller = window?.rootViewController
        while let presented = controller?.presentedViewController {
            controller = presented
        }
        return controller
    }
}
