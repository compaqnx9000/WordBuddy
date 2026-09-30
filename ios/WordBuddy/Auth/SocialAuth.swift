import AlipaySDK
import UIKit
import WechatOpenSDK

enum SocialAuthError: LocalizedError {
    case message(String)

    var errorDescription: String? {
        switch self {
        case .message(let text): text
        }
    }
}

/// WeChat SendAuth and Alipay auth_V2. AppId matches the Android build (`wxc7735828bd1fb638`).
final class SocialAuth: NSObject, WXApiDelegate {
    static let shared = SocialAuth()

    static let wechatAppId = "wxc7735828bd1fb638"
    static let universalLink = "https://wordbuddy.cc/wordbuddy/"
    static let alipayScheme = "wordbuddy"

    private var pendingWechat: CheckedContinuation<String, Error>?
    private var pendingAlipay: CheckedContinuation<String, Error>?

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

    @discardableResult
    func handleOpen(url: URL) -> Bool {
        let text = url.absoluteString
        if url.host == "safepay" || text.contains("safepay") {
            AlipaySDK.defaultService().processAuth_V2Result(url, standbyCallback: { [weak self] result in
                self?.deliverAlipay(result)
            })
            return true
        }
        return WXApi.handleOpen(url, delegate: self)
    }

    func handleUniversalLink(_ activity: NSUserActivity) -> Bool {
        WXApi.handleOpenUniversalLink(activity, delegate: self)
    }

    func onResp(_ resp: BaseResp) {
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
