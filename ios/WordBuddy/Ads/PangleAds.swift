import AppTrackingTransparency
import SwiftUI
import UIKit

enum PangleSlot {
    static let appID = "5891856"
    static let splash = "104619559"
    static let draw = "104620080"
    static let reward = "104620521"
}

enum RewardShowResult {
    case rewarded
    case skipped
    case failed(String)
}

@MainActor
final class PangleAds: NSObject {
    static let shared = PangleAds()

    private var started = false
    private var starting = false
    private var waiters: [(Bool) -> Void] = []
    private let rewards = RewardPresenter()

    func start(_ done: @escaping (Bool) -> Void) {
        if started {
            done(true)
            return
        }
        waiters.append(done)
        guard !starting else { return }
        starting = true
        requestTrackingThenStart()
    }

    func showReward(userId: String?) async -> RewardShowResult {
        let ready = await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            start { ok in
                continuation.resume(returning: ok)
            }
        }
        guard ready else { return .failed("广告暂未填充") }
        return await rewards.show(userId: userId)
    }

    private func requestTrackingThenStart() {
        guard ATTrackingManager.trackingAuthorizationStatus == .notDetermined else {
            startSDK()
            return
        }
        ATTrackingManager.requestTrackingAuthorization { _ in
            Task { @MainActor in
                self.startSDK()
            }
        }
    }

    private func startSDK() {
        let configuration = BUAdSDKConfiguration.configuration()
        configuration.appID = PangleSlot.appID
        configuration.useMediation = true
        configuration.debugLog = 0
        BUAdSDKManager.start(asyncCompletionHandler: { success, error in
            Task { @MainActor in
                if let error {
                    NSLog("Pangle start failed: %@", error.localizedDescription)
                }
                self.started = success
                self.starting = false
                let waiters = self.waiters
                self.waiters.removeAll()
                waiters.forEach { $0(success) }
            }
        })
    }
}

struct ColdSplashCover: UIViewControllerRepresentable {
    var onFinish: () -> Void

    func makeUIViewController(context: Context) -> ColdSplashController {
        let controller = ColdSplashController()
        controller.onFinish = onFinish
        return controller
    }

    func updateUIViewController(_ uiViewController: ColdSplashController, context: Context) {}
}

final class ColdSplashController: UIViewController, BUSplashAdDelegate {
    var onFinish: (() -> Void)?
    private var splashAd: BUSplashAd?
    private var finished = false
    private var didShow = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.05, green: 0.07, blue: 0.10, alpha: 1)
        let title = UILabel()
        title.text = "词搭子"
        title.textColor = .white
        title.font = .systemFont(ofSize: 34, weight: .semibold)
        title.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(title)
        NSLayoutConstraint.activate([
            title.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            title.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard splashAd == nil, !finished else { return }
        PangleAds.shared.start { [weak self] ok in
            guard let self else { return }
            if !ok {
                self.finish()
                return
            }
            self.loadSplash()
        }
    }

    private func loadSplash() {
        let ad = BUSplashAd(slotID: PangleSlot.splash, adSize: UIScreen.main.bounds.size)
        ad.delegate = self
        ad.tolerateTimeout = 3
        splashAd = ad
        ad.loadData()
        DispatchQueue.main.asyncAfter(deadline: .now() + 6) { [weak self] in
            guard let self, !self.didShow else { return }
            self.finish()
        }
    }

    private func finish() {
        guard !finished else { return }
        finished = true
        splashAd?.delegate = nil
        splashAd = nil
        onFinish?()
        onFinish = nil
    }

    func splashAdLoadSuccess(_ splashAd: BUSplashAd) {
        splashAd.showSplashView(inRootViewController: self)
    }

    func splashAdLoadFail(_ splashAd: BUSplashAd, error: BUAdError?) {
        finish()
    }

    func splashAdRenderSuccess(_ splashAd: BUSplashAd) {}

    func splashAdRenderFail(_ splashAd: BUSplashAd, error: BUAdError?) {
        finish()
    }

    func splashAdWillShow(_ splashAd: BUSplashAd) {
        didShow = true
    }

    func splashAdDidShow(_ splashAd: BUSplashAd) {
        didShow = true
    }

    func splashAdDidClick(_ splashAd: BUSplashAd) {}

    func splashAdDidClose(_ splashAd: BUSplashAd, closeType: BUSplashAdCloseType) {
        finish()
    }

    func splashAdViewControllerDidClose(_ splashAd: BUSplashAd) {
        finish()
    }

    func splashDidCloseOtherController(_ splashAd: BUSplashAd, interactionType: BUInteractionType) {}

    func splashVideoAdDidPlayFinish(_ splashAd: BUSplashAd, didFailWithError error: Error?) {}
}

@MainActor
final class RewardPresenter: NSObject, BUNativeExpressRewardedVideoAdDelegate {
    private var ad: BUNativeExpressRewardedVideoAd?
    private var host: UIViewController?
    private var completion: ((RewardShowResult) -> Void)?
    private var rewarded = false
    private var settled = false
    private var shown = false
    private var presented = false
    private var timeout: DispatchWorkItem?

    func show(userId: String?) async -> RewardShowResult {
        await withCheckedContinuation { continuation in
            show(userId: userId) { result in
                continuation.resume(returning: result)
            }
        }
    }

    private func show(userId: String?, completion: @escaping (RewardShowResult) -> Void) {
        guard self.completion == nil else {
            completion(.failed("广告暂未填充"))
            return
        }
        guard let host = UIApplication.shared.topViewController else {
            completion(.failed("广告暂未填充"))
            return
        }
        self.host = host
        self.completion = completion
        rewarded = false
        settled = false
        shown = false
        presented = false
        let model = BURewardedVideoModel()
        model.userId = userId ?? "0"
        model.rewardName = "积分"
        model.rewardAmount = 1
        let ad = BUNativeExpressRewardedVideoAd(slotID: PangleSlot.reward, rewardedVideoModel: model)
        ad.delegate = self
        self.ad = ad
        ad.loadData()
        let work = DispatchWorkItem { [weak self] in
            guard let self, !self.shown else { return }
            self.finish(.failed("广告暂未填充"))
        }
        timeout = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 4, execute: work)
    }

    private func presentIfReady(_ ad: BUNativeExpressRewardedVideoAd) {
        guard !presented, let host else { return }
        presented = true
        let shown = ad.show(fromRootViewController: host)
        if !shown {
            finish(.failed("广告暂未填充"))
        }
    }

    private func finish(_ result: RewardShowResult) {
        guard !settled else { return }
        settled = true
        timeout?.cancel()
        timeout = nil
        let completion = self.completion
        self.completion = nil
        completion?(result)
    }

    func nativeExpressRewardedVideoAdDidLoad(_ rewardedVideoAd: BUNativeExpressRewardedVideoAd) {
        presentIfReady(rewardedVideoAd)
    }

    func nativeExpressRewardedVideoAd(_ rewardedVideoAd: BUNativeExpressRewardedVideoAd, didFailWithError error: Error?) {
        finish(.failed("广告暂未填充"))
    }

    func nativeExpressRewardedVideoAdDidDownLoadVideo(_ rewardedVideoAd: BUNativeExpressRewardedVideoAd) {
        presentIfReady(rewardedVideoAd)
    }

    func nativeExpressRewardedVideoAdDidVisible(_ rewardedVideoAd: BUNativeExpressRewardedVideoAd) {
        shown = true
        timeout?.cancel()
    }

    func nativeExpressRewardedVideoAdDidClose(_ rewardedVideoAd: BUNativeExpressRewardedVideoAd) {
        if !settled {
            finish(rewarded ? .rewarded : .skipped)
        }
        ad = nil
        host = nil
    }

    func nativeExpressRewardedVideoAdServerRewardDidSucceed(_ rewardedVideoAd: BUNativeExpressRewardedVideoAd, verify: Bool) {
        if verify {
            rewarded = true
            finish(.rewarded)
        }
    }
}

@MainActor
final class DrawAdPool: NSObject, ObservableObject, BUNativeAdsManagerDelegate {
    static let shared = DrawAdPool()

    @Published private(set) var revision = 0

    private var manager: BUNativeAdsManager?
    private var ready: BUNativeAd?
    private var loading = false
    private var active: [String: BUNativeAd] = [:]
    private var retry: DispatchWorkItem?

    var hasReady: Bool { ready != nil }

    func preload() {
        PangleAds.shared.start { [weak self] ok in
            guard ok, let self else { return }
            self.load()
        }
    }

    func takeReady() -> String? {
        guard let ad = ready else { return nil }
        ready = nil
        let key = UUID().uuidString
        active[key] = ad
        revision += 1
        load()
        return key
    }

    func ad(for key: String) -> BUNativeAd? {
        active[key]
    }

    func setPlaying(_ playing: Bool, key: String) {
        PangleMessage.send(playing ? "play" : "pause", to: active[key]?.mediation)
    }

    private func load() {
        guard ready == nil, !loading else { return }
        loading = true
        let slot = BUAdSlot()
        slot.id = PangleSlot.draw
        slot.adType = .drawVideo
        slot.position = .fullscreen
        slot.imgSize = BUSize(by: .drawFullScreen)
        let manager = BUNativeAdsManager(slot: slot)
        manager.adSize = UIScreen.main.bounds.size
        manager.delegate = self
        self.manager = manager
        manager.loadAdData(withCount: 1)
    }

    func nativeAdsManagerSuccess(toLoad adsManager: BUNativeAdsManager, nativeAds: [BUNativeAd]?) {
        loading = false
        guard let ad = nativeAds?.first else {
            scheduleRetry()
            return
        }
        ready = ad
        revision += 1
    }

    func nativeAdsManager(_ adsManager: BUNativeAdsManager, didFailWithError error: Error?) {
        loading = false
        scheduleRetry()
    }

    private func scheduleRetry() {
        retry?.cancel()
        let work = DispatchWorkItem { [weak self] in
            self?.load()
        }
        retry = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 20, execute: work)
    }
}

struct DrawAdPage: UIViewControllerRepresentable {
    let adKey: String
    let active: Bool

    func makeUIViewController(context: Context) -> DrawAdController {
        let controller = DrawAdController()
        controller.adKey = adKey
        return controller
    }

    func updateUIViewController(_ uiViewController: DrawAdController, context: Context) {
        uiViewController.setActive(active)
    }
}

final class DrawAdController: UIViewController {
    var adKey = ""
    private var attached = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        attachIfNeeded()
        view.subviews.first?.frame = view.bounds
    }

    func setActive(_ active: Bool) {
        guard attached else { return }
        DrawAdPool.shared.setPlaying(active, key: adKey)
    }

    private func attachIfNeeded() {
        guard !attached, let ad = DrawAdPool.shared.ad(for: adKey) else { return }
        ad.rootViewController = self
        guard let canvas = ad.mediation?.canvasView else { return }
        attached = true
        canvas.frame = view.bounds
        canvas.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(canvas)
        PangleMessage.send("render", to: ad.mediation)
        if view.window != nil {
            DrawAdPool.shared.setPlaying(true, key: adKey)
        }
    }
}

/// GroMore 的 mediation 对象不一定实现 play / pause / render，直接调用会触发 unrecognized selector 并闪退。
private enum PangleMessage {
    static func send(_ name: String, to object: AnyObject?) {
        guard let object else { return }
        let selector = NSSelectorFromString(name)
        guard object.responds(to: selector) else { return }
        object.perform(selector)
    }
}

extension UIApplication {
    var topViewController: UIViewController? {
        connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }?
            .rootViewController?
            .topMostPresented
    }
}

private extension UIViewController {
    var topMostPresented: UIViewController {
        if let presented = presentedViewController {
            return presented.topMostPresented
        }
        if let navigation = self as? UINavigationController, let visible = navigation.visibleViewController {
            return visible.topMostPresented
        }
        if let tab = self as? UITabBarController, let selected = tab.selectedViewController {
            return selected.topMostPresented
        }
        return self
    }
}
