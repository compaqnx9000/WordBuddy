import SwiftUI

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
                PodcastView()
                    .tabItem { Label("播客", systemImage: "headphones") }
                    .toolbar(.hidden, for: .tabBar)
                    .tag(MainTab.podcast)
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
        }
        .onChange(of: model.pendingLookup) { _, word in
            if word != nil { tab = .home }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background {
                model.biometricUnlocked = false
            }
            if phase != .active, !model.podcastPlayWhenScreenOff {
                model.podcast.pause()
            }
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
        .task {
            await model.bootstrap()
            StudyReminder.sync(enabled: model.dailyReminder)
        }
        .alert("提示", isPresented: bannerPresented) {
            Button("好", role: .cancel) { model.banner = nil }
        } message: {
            Text(model.banner ?? "")
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
        Item(tab: .podcast, title: "播客", selected: "headphones", idle: "headphones"),
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
