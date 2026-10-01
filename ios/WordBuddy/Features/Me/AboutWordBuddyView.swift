import SwiftUI

enum AboutPage: Hashable {
    case features
    case complaint
    case agreement
    case privacySummary
    case privacy
}

struct AboutWordBuddyView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var showUpdate = false

    private var versionName: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
    }

    private var versionCode: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                topBar
                ScrollView {
                    VStack(spacing: 0) {
                        brand
                            .padding(.top, 28)
                        menu
                            .padding(.top, 28)
                        legalFooter
                            .padding(.top, 36)
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 28)
                }
            }
            .stellarScreenBackground()
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: AboutPage.self) { page in
                LegalDocumentView(title: page.title, bodyText: page.bodyText)
            }
        }
        .alert("检测更新", isPresented: $showUpdate) {
            Button("知道了", role: .cancel) {}
        } message: {
            Text("当前版本 \(versionName)（\(versionCode)）\n\n已是最新版本。")
        }
    }

    private var topBar: some View {
        ZStack {
            Text("关于词搭子")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.cyanSoft)
            HStack {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.cyanSoft)
                        .frame(width: 40, height: 40)
                }
                .buttonStyle(.plain)
                Spacer()
            }
            .padding(.horizontal, 12)
        }
        .frame(height: 56)
        .background(Theme.hasWallpaper ? Theme.surface.opacity(0.55) : Theme.surface.opacity(0.92))
    }

    private var brand: some View {
        VStack(spacing: 0) {
            Image("WordBuddyLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 88, height: 88)
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                .shadow(color: Theme.cyan.opacity(0.35), radius: 16, y: 6)
            Text("词搭子")
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(Theme.cyanSoft)
                .padding(.top, 16)
            Text("Version \(versionName)")
                .font(.system(size: 14))
                .foregroundStyle(Theme.onSurfaceVariant)
                .padding(.top, 6)
        }
    }

    private var menu: some View {
        VStack(spacing: 0) {
            NavigationLink(value: AboutPage.features) {
                menuRow("功能介绍")
            }
            .buttonStyle(.plain)
            ThemeHairline(alpha: 0.45)
            NavigationLink(value: AboutPage.complaint) {
                menuRow("投诉")
            }
            .buttonStyle(.plain)
            ThemeHairline(alpha: 0.45)
            Button {
                showUpdate = true
            } label: {
                menuRow("检测更新")
            }
            .buttonStyle(.plain)
        }
        .glassPanel()
    }

    private func menuRow(_ title: String) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 16))
                .foregroundStyle(Theme.onSurface)
            Spacer()
            Image(systemName: "chevron.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.onSurfaceVariant.opacity(0.45))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .contentShape(Rectangle())
    }

    private var legalFooter: some View {
        VStack(spacing: 10) {
            NavigationLink(value: AboutPage.agreement) {
                Text("《软件许可及服务协议》")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Theme.cyanSoft)
            }
            NavigationLink(value: AboutPage.privacySummary) {
                Text("《隐私保护指引摘要》")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Theme.cyanSoft)
            }
            NavigationLink(value: AboutPage.privacy) {
                Text("《隐私保护指引》")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Theme.cyanSoft)
            }
            Text("运营者：北京博冠鸿图科技有限公司\n联系电话：18500090601\n客服邮箱：hi@wordbuddy.cc")
                .font(.system(size: 12))
                .foregroundStyle(Theme.onSurface.opacity(0.88))
                .multilineTextAlignment(.center)
                .lineSpacing(4)
                .padding(.top, 4)
            Text("北京博冠鸿图科技有限公司 版权所有\nCopyright © 2024-2026 北京博冠鸿图科技有限公司. All Rights Reserved.")
                .font(.system(size: 11))
                .foregroundStyle(Theme.onSurface.opacity(0.72))
                .multilineTextAlignment(.center)
                .lineSpacing(3)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .glassPanel()
    }
}

private extension AboutPage {
    var title: String {
        switch self {
        case .features: "功能介绍"
        case .complaint: "投诉"
        case .agreement: LegalDocuments.agreementTitle
        case .privacySummary: LegalDocuments.privacySummaryTitle
        case .privacy: LegalDocuments.privacyTitle
        }
    }

    var bodyText: String {
        switch self {
        case .features: LegalDocuments.features
        case .complaint: LegalDocuments.complaint
        case .agreement: LegalDocuments.agreement
        case .privacySummary: LegalDocuments.privacySummary
        case .privacy: LegalDocuments.privacy
        }
    }
}

struct LegalDocumentView: View {
    var title: String
    var bodyText: String

    var body: some View {
        ScrollView {
            Text(bodyText)
                .font(.system(size: 15))
                .foregroundStyle(Theme.onSurface)
                .lineSpacing(5)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
        }
        .stellarScreenBackground()
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .themeNavigationBar()
    }
}
