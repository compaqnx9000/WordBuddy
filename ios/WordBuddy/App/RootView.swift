import SwiftUI
import UIKit

enum MainTab: Hashable {
    case home
    case notebook
    case shorts
    case podcast
    case me
}

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var tab: MainTab = .home

    @State private var shortsChromeHidden = false
    @State private var mallCoversTabBar = false
    @State private var showColdSplash = true

    private var needsBiometricLock: Bool {
        model.session != nil
            && model.biometricLogin
            && !model.biometricUnlocked
            && !model.showLogin
            && BiometricAuth.available
    }

    private var showsMainBar: Bool {
        tab != .notebook
            && !(tab == .shorts && shortsChromeHidden)
            && !(tab == .me && mallCoversTabBar)
            && model.shortsWordLookup == nil
    }

    var body: some View {
        VStack(spacing: 0) {
            TabView(selection: $tab) {
                LookupView()
                    .tabItem { Label("首页", systemImage: "house.fill") }
                    .toolbar(.hidden, for: .tabBar)
                    .tag(MainTab.home)
                NotebookView(onBack: { tab = .home })
                    .tabItem { Label("生词本", systemImage: "book.fill") }
                    .toolbar(.hidden, for: .tabBar)
                    .tag(MainTab.notebook)
                ShortsView(isSelected: tab == .shorts, chromeHidden: $shortsChromeHidden)
                    .tabItem { Label("短视频", systemImage: "play.circle.fill") }
                    .toolbar(.hidden, for: .tabBar)
                    .tag(MainTab.shorts)
                // 上架前关闭播客：不展示第三方电台和播客。恢复时取消下面三行注释。
                // PodcastView()
                //     .tabItem { Label("播客", systemImage: "headphones") }
                //     .toolbar(.hidden, for: .tabBar)
                //     .tag(MainTab.podcast)
                MeView(mallCoversTabBar: $mallCoversTabBar)
                    .tabItem { Label("我", systemImage: "sparkles") }
                    .toolbar(.hidden, for: .tabBar)
                    .tag(MainTab.me)
            }
            .toolbar(.hidden, for: .tabBar)
            .tint(Theme.cyan)
            if showsMainBar {
                StellarTabBar(selection: $tab)
            }
        }
        .stellarScreenBackground()
        .environmentObject(model.podcast)
        .fullScreenCover(isPresented: $model.showLogin) {
            LoginView()
                .environmentObject(model)
                .onAppear { model.noteLoginCoverVisible(true) }
                .onDisappear { model.noteLoginCoverVisible(false) }
        }
        .onChange(of: model.pendingLookup) { _, word in
            if word != nil { tab = .home }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background {
                model.biometricUnlocked = false
                Task { await model.flushStudyScene() }
            }
            if phase != .active, !model.podcastPlayWhenScreenOff {
                model.podcast.pause()
            }
            updateActivityTracking(phase: phase)
        }
        .onChange(of: needsBiometricLock) { _, _ in
            updateActivityTracking(phase: scenePhase)
        }
        .onChange(of: model.session?.userId) { _, _ in
            updateActivityTracking(phase: scenePhase)
        }
        .overlay {
            if showColdSplash {
                ColdSplashCover { showColdSplash = false }
                    .ignoresSafeArea()
            }
        }
        .overlay {
            if let progress = model.catalogCopyProgress {
                CatalogCopyProgressCover(progress: progress)
                    .ignoresSafeArea()
            }
        }
        .overlay {
            if needsBiometricLock {
                BiometricLockCover()
            }
        }
        .onAppear(perform: applyScreenshotLaunchArguments)
        .task {
            await model.bootstrap()
            StudyReminder.sync(enabled: model.dailyReminder)
            updateActivityTracking(phase: scenePhase)
            applyScreenshotLaunchArguments()
        }
        .alert("提示", isPresented: bannerPresented) {
            Button("好", role: .cancel) { model.banner = nil }
        } message: {
            Text(model.banner ?? "")
        }
    }

    private func applyScreenshotLaunchArguments() {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-shot-login") {
            showColdSplash = false
            model.openLogin()
        }
        guard let flag = args.firstIndex(of: "-shot-tab"), flag + 1 < args.count else { return }
        showColdSplash = false
        let name = args[flag + 1]
        switch name {
        case "home":
            tab = .home
            if flag + 2 < args.count, !args[flag + 2].hasPrefix("-") {
                model.pendingLookup = args[flag + 2]
            }
        case "notebook":
            tab = .notebook
            if flag + 2 < args.count, !args[flag + 2].hasPrefix("-") {
                let name = args[flag + 2]
                if let match = model.notebooks.first(where: { $0.name.contains(name) }) {
                    model.activeNotebookId = match.id
                }
            }
        case "shorts":
            tab = .shorts
        case "podcast":
            tab = .podcast
        case "me":
            tab = .me
        default:
            break
        }
        #endif
    }

    private func updateActivityTracking(phase: ScenePhase) {
        let track = phase != .background && model.session != nil && !needsBiometricLock
        if track {
            model.startActivityTracking()
        } else {
            model.stopActivityTracking()
        }
    }

    private var bannerPresented: Binding<Bool> {
        Binding(
            get: { model.banner != nil },
            set: { if !$0 { model.banner = nil } }
        )
    }
}

private struct CatalogCopyProgressCover: View {
    var progress: CatalogCopyProgress

    var body: some View {
        ZStack {
            Color.black.opacity(0.55)
            VStack(spacing: 16) {
                Text(progress.title)
                    .font(.headline.weight(.bold))
                    .foregroundStyle(Theme.cyanSoft)
                    .multilineTextAlignment(.center)
                if progress.total > 0 {
                    ProgressView(value: Double(progress.copied), total: Double(progress.total))
                        .tint(Theme.cyan)
                    Text("\(progress.copied) / \(progress.total)")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.onSurface)
                } else {
                    ProgressView()
                        .tint(Theme.cyan)
                    Text("准备中")
                        .font(.subheadline)
                        .foregroundStyle(Theme.onSurfaceVariant)
                }
            }
            .padding(24)
            .frame(maxWidth: 280)
            .background(Theme.surfaceContainer, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .stroke(Theme.cyan.opacity(0.45), lineWidth: 1)
            }
        }
    }
}

private struct StellarTabBar: View {
    @EnvironmentObject private var model: AppModel
    @Binding var selection: MainTab

    private struct Item: Identifiable {
        let tab: MainTab
        let title: String
        let selected: String
        let idle: String
        var id: MainTab { tab }
    }

    private let items: [Item] = [
        Item(tab: .home, title: "首页", selected: "house.fill", idle: "house"),
        Item(tab: .notebook, title: "生词本", selected: "book.fill", idle: "book"),
        Item(tab: .shorts, title: "短视频", selected: "play.circle.fill", idle: "play.circle"),
        // 上架前关闭播客入口。恢复时取消下一行注释。
        // Item(tab: .podcast, title: "播客", selected: "headphones", idle: "headphones"),
        Item(tab: .me, title: "我", selected: "sparkles", idle: "sparkle"),
    ]

    var body: some View {
        let palette = StellarPalettes.palette(for: model.accentStyle)
        HStack(spacing: 0) {
            ForEach(items) { item in
                let selected = selection == item.tab
                Button {
                    selection = item.tab
                } label: {
                    VStack(spacing: 3) {
                        Image(systemName: selected ? item.selected : item.idle)
                            .font(.system(size: 20, weight: .regular))
                            .frame(height: 22)
                        Text(item.title)
                            .font(.system(size: 12, weight: selected ? .bold : .medium))
                    }
                    .foregroundStyle(selected ? palette.cyan : palette.tabInactive)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 4)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, 8)
        .padding(.bottom, 8)
        .background {
            panel
                .overlay(alignment: .top) {
                    Rectangle()
                        .fill(palette.cyan.opacity(0.20))
                        .frame(height: 1)
                }
                .ignoresSafeArea(edges: .bottom)
        }
    }

    private var panel: Color {
        let palette = StellarPalettes.palette(for: model.accentStyle)
        if palette.hasWallpaper {
            return Color(uiColor: UIColor(palette.surfaceContainer).withAlphaComponent(0.72))
        }
        return Color(uiColor: UIColor(palette.background).withAlphaComponent(0.80))
    }
}

/// Android-style toast. Its own window sits above a rewarded-video controller,
/// and it does not take taps.
enum AppToast {
    private static var window: ToastWindow?
    private static var hideWork: DispatchWorkItem?

    static func show(_ text: String) {
        DispatchQueue.main.async {
            present(text)
        }
    }

    private static func present(_ text: String) {
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive })
            ?? UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first
        else { return }

        hideWork?.cancel()
        let host = ensureWindow(in: scene)
        host.isHidden = false
        let root = host.rootViewController?.view
        root?.subviews.forEach { $0.removeFromSuperview() }

        let label = UILabel()
        label.text = text
        label.textColor = .white
        label.font = .systemFont(ofSize: 15, weight: .medium)
        label.numberOfLines = 0
        label.textAlignment = .center

        let bubble = UIView()
        bubble.backgroundColor = UIColor.black.withAlphaComponent(0.82)
        bubble.layer.cornerRadius = 8
        bubble.clipsToBounds = true
        bubble.isUserInteractionEnabled = false
        bubble.translatesAutoresizingMaskIntoConstraints = false
        label.translatesAutoresizingMaskIntoConstraints = false
        bubble.addSubview(label)
        root?.addSubview(bubble)

        guard let root else { return }
        let guide = root.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            label.topAnchor.constraint(equalTo: bubble.topAnchor, constant: 10),
            label.bottomAnchor.constraint(equalTo: bubble.bottomAnchor, constant: -10),
            label.leadingAnchor.constraint(equalTo: bubble.leadingAnchor, constant: 16),
            label.trailingAnchor.constraint(equalTo: bubble.trailingAnchor, constant: -16),
            bubble.centerXAnchor.constraint(equalTo: guide.centerXAnchor),
            bubble.leadingAnchor.constraint(greaterThanOrEqualTo: guide.leadingAnchor, constant: 32),
            bubble.trailingAnchor.constraint(lessThanOrEqualTo: guide.trailingAnchor, constant: -32),
            bubble.bottomAnchor.constraint(equalTo: guide.bottomAnchor, constant: -72),
        ])

        let work = DispatchWorkItem {
            host.isHidden = true
            root.subviews.forEach { $0.removeFromSuperview() }
        }
        hideWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 1, execute: work)
    }

    private static func ensureWindow(in scene: UIWindowScene) -> ToastWindow {
        if let window, window.windowScene === scene { return window }
        let created = ToastWindow(windowScene: scene)
        created.windowLevel = .alert + 1
        created.backgroundColor = .clear
        let controller = UIViewController()
        controller.view.backgroundColor = .clear
        created.rootViewController = controller
        created.isHidden = true
        window = created
        return created
    }
}

private final class ToastWindow: UIWindow {
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? { nil }
}
