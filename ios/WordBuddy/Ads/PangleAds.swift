#if true
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

/// What the SDK reported for one reward ad. The reward notice can land after the ad has closed,
/// so the caller keeps this object and re-reads `verified` while it asks the server.
@MainActor
final class RewardWatch {
    var shownAt = Date()
    /// The server-side check passed. Only this pays without asking the server again.
    var verified = false
    /// The ad network reported a reward, whether or not the server-side check passed.
    var adnSignal = false
    var playFinished = false
    var skipped = false
    var adn: String?
    var ecpm: String?
    var reason: String?

    var shownAgoSec: Int {
        max(0, Int(Date().timeIntervalSince(shownAt).rounded(.up)))
    }
}

@MainActor
final class PangleAds: NSObject {
    static let shared = PangleAds()

    private var started = false
    private var starting = false
    private var waiters: [(Bool) -> Void] = []
    private var consent: Task<Void, Never>?
    private let rewards = RewardPresenter()

    /// Signals of the reward ad that was shown last.
    var lastRewardWatch: RewardWatch? { rewards.lastWatch }

    /// Shows the tracking prompt once the window is on screen. Ads must wait for this.
    func ensureConsent() -> Task<Void, Never> {
        if let consent { return consent }
        let task = Task { @MainActor in
            await Self.askForTrackingIfNeeded()
        }
        consent = task
        return task
    }

    func start(_ done: @escaping (Bool) -> Void) {
        if started {
            done(true)
            return
        }
        waiters.append(done)
        guard !starting else { return }
        starting = true
        Task { @MainActor in
            await self.ensureConsent().value
            self.startSDK()
        }
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

    /// The system drops the alert if it is requested before the app is active and a key window exists.
    /// On iPad that silent drop left review with no tracking prompt, while the ad SDK had already started.
    private static func askForTrackingIfNeeded() async {
        guard ATTrackingManager.trackingAuthorizationStatus == .notDetermined else { return }
        for attempt in 0..<4 {
            await waitForVisibleWindow()
            if attempt > 0 {
                try? await Task.sleep(nanoseconds: 600_000_000)
            }
            guard ATTrackingManager.trackingAuthorizationStatus == .notDetermined else { return }
            let status = await requestTracking()
            if status != .notDetermined { return }
        }
    }

    private static func waitForVisibleWindow() async {
        for _ in 0..<40 {
            let active = UIApplication.shared.applicationState == .active
            let visible = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .contains { scene in
                    scene.activationState == .foregroundActive && scene.windows.contains(where: \.isKeyWindow)
                }
            if active && visible {
                try? await Task.sleep(nanoseconds: 500_000_000)
                return
            }
            try? await Task.sleep(nanoseconds: 100_000_000)
        }
    }

    private static func requestTracking() async -> ATTrackingManager.AuthorizationStatus {
        await withCheckedContinuation { continuation in
            ATTrackingManager.requestTrackingAuthorization { status in
                continuation.resume(returning: status)
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
        title.text = "你的词搭子"
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
    /// How long a closed ad stays alive. Releasing it at close dropped reward checks that were still on the way.
    private static let lingerSeconds: TimeInterval = 20
    /// After close, wait this long for a reward notice before reporting the ad as unconfirmed.
    private static let closeGraceSeconds: TimeInterval = 1.5

    private var ad: BUNativeExpressRewardedVideoAd?
    private var host: UIViewController?
    private var completion: ((RewardShowResult) -> Void)?
    private var watch: RewardWatch?
    private var lingering: [(ad: BUNativeExpressRewardedVideoAd, watch: RewardWatch)] = []
    private var settled = false
    private var shown = false
    private var presented = false
    private var timeout: DispatchWorkItem?

    private(set) var lastWatch: RewardWatch?

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
        let accountId = userId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard let numericId = Int(accountId), numericId > 0 else {
            completion(.failed("请先登录"))
            return
        }
        guard let host = UIApplication.shared.topViewController else {
            completion(.failed("广告暂未填充"))
            return
        }
        self.host = host
        self.completion = completion
        let watch = RewardWatch()
        self.watch = watch
        lastWatch = watch
        settled = false
        shown = false
        presented = false
        let model = BURewardedVideoModel()
        model.userId = String(numericId)
        model.rewardName = "搭币"
        model.rewardAmount = 200
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

    /// The ad on screen, or one that closed a moment ago and is still being kept alive.
    private func watch(for rewardedVideoAd: BUNativeExpressRewardedVideoAd) -> RewardWatch? {
        if rewardedVideoAd === ad { return watch }
        return lingering.first { $0.ad === rewardedVideoAd }?.watch
    }

    private func noteNetwork(_ rewardedVideoAd: BUNativeExpressRewardedVideoAd, into watch: RewardWatch) {
        if let info = rewardedVideoAd.mediation?.getShowEcpmInfo() {
            watch.adn = info.adnName
            watch.ecpm = info.ecpm
        }
    }

    func nativeExpressRewardedVideoAdDidVisible(_ rewardedVideoAd: BUNativeExpressRewardedVideoAd) {
        shown = true
        timeout?.cancel()
        guard let watch = watch(for: rewardedVideoAd) else { return }
        watch.shownAt = Date()
        noteNetwork(rewardedVideoAd, into: watch)
    }

    func nativeExpressRewardedVideoAdDidClickSkip(_ rewardedVideoAd: BUNativeExpressRewardedVideoAd) {
        watch(for: rewardedVideoAd)?.skipped = true
    }

    func nativeExpressRewardedVideoAdDidPlayFinish(_ rewardedVideoAd: BUNativeExpressRewardedVideoAd, didFailWithError error: Error?) {
        if error == nil {
            watch(for: rewardedVideoAd)?.playFinished = true
        }
    }

    func nativeExpressRewardedVideoAdDidClose(_ rewardedVideoAd: BUNativeExpressRewardedVideoAd) {
        guard rewardedVideoAd === ad, let watch else { return }
        // 用户看到「已获得奖励」后马上点跳过时，奖励校验还在路上。
        // 广告对象先留着，校验结果回来还能收到，服务器也还能收到回调。
        lingering.append((rewardedVideoAd, watch))
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.lingerSeconds) { [weak self] in
            self?.lingering.removeAll { $0.ad === rewardedVideoAd }
        }
        ad = nil
        host = nil
        self.watch = nil
        guard !settled else { return }
        if watch.verified {
            finish(.rewarded)
            return
        }
        Task { @MainActor [weak self] in
            let deadline = Date().addingTimeInterval(Self.closeGraceSeconds)
            while !watch.verified, Date() < deadline {
                try? await Task.sleep(nanoseconds: 150_000_000)
            }
            self?.finish(watch.verified ? .rewarded : .skipped)
        }
    }

    func nativeExpressRewardedVideoAdServerRewardDidSucceed(_ rewardedVideoAd: BUNativeExpressRewardedVideoAd, verify: Bool) {
        guard let watch = watch(for: rewardedVideoAd) else { return }
        watch.adnSignal = true
        let detail = rewardedVideoAd.rewardedVideoModel.mediation
        if let name = detail.adnName, !name.isEmpty { watch.adn = name }
        if let ecpm = detail.ecpm, !ecpm.isEmpty { watch.ecpm = ecpm }
        if verify {
            watch.verified = true
            if rewardedVideoAd === ad {
                finish(.rewarded)
            }
        } else {
            watch.reason = detail.reason ?? "verify=false"
        }
    }

    func nativeExpressRewardedVideoAdServerRewardDidFail(_ rewardedVideoAd: BUNativeExpressRewardedVideoAd, error: Error?) {
        guard let watch = watch(for: rewardedVideoAd) else { return }
        watch.adnSignal = true
        watch.reason = error?.localizedDescription ?? "reward check failed"
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
#else
import SwiftUI
import UIKit

enum RewardShowResult {
    case rewarded
    case skipped
    case failed(String)
}

@MainActor
final class RewardWatch {
    var verified = false
    var adnSignal = false
    var playFinished = false
    var skipped = false
    var adn: String?
    var ecpm: String?
    var reason: String?
    var shownAgoSec: Int { 0 }
}

@MainActor
final class PangleAds: NSObject {
    static let shared = PangleAds()

    var lastRewardWatch: RewardWatch? { nil }

    func showReward(userId: String?) async -> RewardShowResult {
        .failed("广告暂未开放")
    }
}

struct ColdSplashCover: UIViewControllerRepresentable {
    var onFinish: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        DispatchQueue.main.async { onFinish() }
        return UIViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@MainActor
final class DrawAdPool: ObservableObject {
    static let shared = DrawAdPool()

    @Published private(set) var revision = 0

    var hasReady: Bool { false }

    func preload() {}

    func takeReady() -> String? { nil }
}

struct DrawAdPage: View {
    let adKey: String
    let active: Bool

    var body: some View { Color.clear }
}
#endif
