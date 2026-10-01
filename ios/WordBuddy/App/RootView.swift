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
    @State private var showColdSplash = true

    private var needsBiometricLock: Bool {
        model.session != nil
            && model.biometricLogin
            && !model.biometricUnlocked
            && !model.showLogin
            && BiometricAuth.available
    }

    private var showsMainBar: Bool {
        !(tab == .shorts && shortsChromeHidden)
    }

    var body: some View {
        VStack(spacing: 0) {
            TabView(selection: $tab) {
                LookupView()
                    .tabItem { Label("首页", systemImage: "house.fill") }
                    .toolbar(.hidden, for: .tabBar)
                    .tag(MainTab.home)
                NotebookView()
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
                MeView()
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
        .id(model.accentStyle)
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
            if needsBiometricLock {
                BiometricLockCover {
                    Task {
                        if await BiometricAuth.authenticate(reason: "验证后继续使用词搭子") {
                            model.biometricUnlocked = true
                        }
                    }
                }
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

private struct StellarTabBar: View {
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
                    .foregroundStyle(selected ? Theme.cyan : Theme.tabInactive)
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
                        .fill(Theme.cyan.opacity(0.20))
                        .frame(height: 1)
                }
                .ignoresSafeArea(edges: .bottom)
        }
    }

    private var panel: Color {
        if Theme.hasWallpaper {
            return Color(uiColor: UIColor(Theme.surfaceContainer).withAlphaComponent(0.72))
        }
        return Color(uiColor: UIColor(Theme.background).withAlphaComponent(0.80))
    }
}
