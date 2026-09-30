import SwiftUI

@main
struct WordBuddyApp: App {
    @UIApplicationDelegateAdaptor(WordBuddyAppDelegate.self) private var appDelegate
    @StateObject private var model = AppModel()

    init() {
        Theme.applyTabBar()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .preferredColorScheme(model.accentStyle.isLight ? .light : .dark)
                .onAppear { Theme.applyTabBar() }
                .onChange(of: model.accentStyle) { _, _ in Theme.applyTabBar() }
                .onOpenURL { url in
                    SocialAuth.shared.handleOpen(url: url)
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    _ = SocialAuth.shared.handleUniversalLink(activity)
                }
        }
    }
}

final class WordBuddyAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        SocialAuth.shared.register()
        return true
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        SocialAuth.shared.handleOpen(url: url)
    }

    func application(
        _ application: UIApplication,
        continue userActivity: NSUserActivity,
        restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void
    ) -> Bool {
        SocialAuth.shared.handleUniversalLink(userActivity)
    }
}
