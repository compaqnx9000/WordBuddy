import SwiftUI
import UIKit

enum AccentStyle: String, CaseIterable, Identifiable {
    case cyberNeon
    case emerald
    case electric
    case solar
    case aurora
    case ember
    case frost
    case forestStar

    var id: String { rawValue }

    var label: String {
        switch self {
        case .cyberNeon: "赛博霓虹"
        case .emerald: "翡翠薄雾"
        case .electric: "电光潮汐"
        case .solar: "日光金辉"
        case .aurora: "极光粉"
        case .ember: "余烬珊瑚"
        case .frost: "霜蓝"
        case .forestStar: "星野翠绿"
        }
    }

    var swatch: Color {
        switch self {
        case .cyberNeon: Color(argb: 0xFF00F0FF)
        case .emerald: Color(argb: 0xFF2DD4BF)
        case .electric: Color(argb: 0xFF38BDF8)
        case .solar: Color(argb: 0xFFF5C542)
        case .aurora: Color(argb: 0xFFF472B6)
        case .ember: Color(argb: 0xFFFF6B6B)
        case .frost: Color(argb: 0xFF0E8A96)
        case .forestStar: Color(argb: 0xFF8CC63F)
        }
    }

    var isLight: Bool { self == .frost }
}

struct StellarPalette {
    var background: Color
    var surface: Color
    var surfaceHigh: Color
    var surfaceContainer: Color
    var onSurface: Color
    var onSurfaceVariant: Color
    var outline: Color
    var cyan: Color
    var cyanBright: Color
    var cyanSoft: Color
    var headword: Color
    var pink: Color
    var gold: Color
    var glass: Color
    var glassBorder: Color
    var neonBorder: Color
    var tabInactive: Color
    var onPrimary: Color
    var isLight: Bool
    /// Asset catalog image name when the theme uses a full-bleed wallpaper (星野翠绿).
    var wallpaperImageName: String? = nil

    var hasWallpaper: Bool { wallpaperImageName != nil }
}

enum StellarPalettes {
    static let cyberNeon = StellarPalette(
        background: Color(argb: 0xFF0D1515),
        surface: Color(argb: 0xFF151D1E),
        surfaceHigh: Color(argb: 0xFF2E3637),
        surfaceContainer: Color(argb: 0xFF192122),
        onSurface: Color(argb: 0xFFDCE4E5),
        onSurfaceVariant: Color(argb: 0xFFB9CACB),
        outline: Color(argb: 0xFF3B494B),
        cyan: Color(argb: 0xFF00DBE9),
        cyanBright: Color(argb: 0xFF00F0FF),
        cyanSoft: Color(argb: 0xFFDBFCFF),
        headword: Color(argb: 0xFF00E5FF),
        pink: Color(argb: 0xFFFFABF3),
        gold: Color(argb: 0xFFFED639),
        glass: Color(argb: 0x662E3637),
        glassBorder: Color(argb: 0x33FFFFFF),
        neonBorder: Color(argb: 0x8000F0FF),
        tabInactive: Color(argb: 0x99B9CACB),
        onPrimary: Color(argb: 0xFF00363A),
        isLight: false
    )

    static let emerald = StellarPalette(
        background: Color(argb: 0xFF071412),
        surface: Color(argb: 0xFF0E1C19),
        surfaceHigh: Color(argb: 0xFF244039),
        surfaceContainer: Color(argb: 0xFF132420),
        onSurface: Color(argb: 0xFFE1F5F0),
        onSurfaceVariant: Color(argb: 0xFFA9C9C0),
        outline: Color(argb: 0xFF35564E),
        cyan: Color(argb: 0xFF2DD4BF),
        cyanBright: Color(argb: 0xFF5EEAD4),
        cyanSoft: Color(argb: 0xFFCCFBF1),
        headword: Color(argb: 0xFF2DD4BF),
        pink: Color(argb: 0xFF86EFAC),
        gold: Color(argb: 0xFFFBBF24),
        glass: Color(argb: 0x66244039),
        glassBorder: Color(argb: 0x332DD4BF),
        neonBorder: Color(argb: 0x802DD4BF),
        tabInactive: Color(argb: 0x99A9C9C0),
        onPrimary: Color(argb: 0xFF042F2E),
        isLight: false
    )

    static let electric = StellarPalette(
        background: Color(argb: 0xFF070B16),
        surface: Color(argb: 0xFF0E1524),
        surfaceHigh: Color(argb: 0xFF243044),
        surfaceContainer: Color(argb: 0xFF141C2E),
        onSurface: Color(argb: 0xFFE2EAF8),
        onSurfaceVariant: Color(argb: 0xFFA8B8D4),
        outline: Color(argb: 0xFF33415C),
        cyan: Color(argb: 0xFF38BDF8),
        cyanBright: Color(argb: 0xFF7DD3FC),
        cyanSoft: Color(argb: 0xFFE0F2FE),
        headword: Color(argb: 0xFF38BDF8),
        pink: Color(argb: 0xFFA5B4FC),
        gold: Color(argb: 0xFFFDE047),
        glass: Color(argb: 0x66243044),
        glassBorder: Color(argb: 0x3338BDF8),
        neonBorder: Color(argb: 0x8038BDF8),
        tabInactive: Color(argb: 0x99A8B8D4),
        onPrimary: Color(argb: 0xFF0C4A6E),
        isLight: false
    )

    static let solar = StellarPalette(
        background: Color(argb: 0xFF120E08),
        surface: Color(argb: 0xFF1A140C),
        surfaceHigh: Color(argb: 0xFF3A2E1C),
        surfaceContainer: Color(argb: 0xFF221A10),
        onSurface: Color(argb: 0xFFFFF4E0),
        onSurfaceVariant: Color(argb: 0xFFD2BC96),
        outline: Color(argb: 0xFF56462C),
        cyan: Color(argb: 0xFFF5C542),
        cyanBright: Color(argb: 0xFFFDE68A),
        cyanSoft: Color(argb: 0xFFFFF3C4),
        headword: Color(argb: 0xFFFFB020),
        pink: Color(argb: 0xFFFF9F6B),
        gold: Color(argb: 0xFFFFE08A),
        glass: Color(argb: 0x663A2E1C),
        glassBorder: Color(argb: 0x33F5C542),
        neonBorder: Color(argb: 0x80F5C542),
        tabInactive: Color(argb: 0x99D2BC96),
        onPrimary: Color(argb: 0xFF3B2A08),
        isLight: false
    )

    static let aurora = StellarPalette(
        background: Color(argb: 0xFF120B14),
        surface: Color(argb: 0xFF1A101C),
        surfaceHigh: Color(argb: 0xFF3A2A3E),
        surfaceContainer: Color(argb: 0xFF221528),
        onSurface: Color(argb: 0xFFF8EAF6),
        onSurfaceVariant: Color(argb: 0xFFCDB6CB),
        outline: Color(argb: 0xFF534056),
        cyan: Color(argb: 0xFFF472B6),
        cyanBright: Color(argb: 0xFFF9A8D4),
        cyanSoft: Color(argb: 0xFFFCE7F3),
        headword: Color(argb: 0xFF67E8F9),
        pink: Color(argb: 0xFF67E8F9),
        gold: Color(argb: 0xFFFDE68A),
        glass: Color(argb: 0x663A2A3E),
        glassBorder: Color(argb: 0x33F472B6),
        neonBorder: Color(argb: 0x80F472B6),
        tabInactive: Color(argb: 0x99CDB6CB),
        onPrimary: Color(argb: 0xFF4A0E2E),
        isLight: false
    )

    static let ember = StellarPalette(
        background: Color(argb: 0xFF140A0A),
        surface: Color(argb: 0xFF1C1010),
        surfaceHigh: Color(argb: 0xFF3D2626),
        surfaceContainer: Color(argb: 0xFF251515),
        onSurface: Color(argb: 0xFFFFEDEC),
        onSurfaceVariant: Color(argb: 0xFFD4B0AE),
        outline: Color(argb: 0xFF5A3838),
        cyan: Color(argb: 0xFFFF6B6B),
        cyanBright: Color(argb: 0xFFFF8E8E),
        cyanSoft: Color(argb: 0xFFFFE4E6),
        headword: Color(argb: 0xFFFF8A3D),
        pink: Color(argb: 0xFFFFB4A2),
        gold: Color(argb: 0xFFFBBF24),
        glass: Color(argb: 0x663D2626),
        glassBorder: Color(argb: 0x33FF6B6B),
        neonBorder: Color(argb: 0x80FF6B6B),
        tabInactive: Color(argb: 0x99D4B0AE),
        onPrimary: Color(argb: 0xFF4C0519),
        isLight: false
    )

    static let frost = StellarPalette(
        background: Color(argb: 0xFFE6F1F3),
        surface: Color(argb: 0xFFFFFFFF),
        surfaceHigh: Color(argb: 0xFFD2E3E7),
        surfaceContainer: Color(argb: 0xFFF4FAFB),
        onSurface: Color(argb: 0xFF0C2429),
        onSurfaceVariant: Color(argb: 0xFF35565C),
        outline: Color(argb: 0xFF8FAAB0),
        cyan: Color(argb: 0xFF0B7A86),
        cyanBright: Color(argb: 0xFF129EAB),
        cyanSoft: Color(argb: 0xFF074F57),
        headword: Color(argb: 0xFF0B7A86),
        pink: Color(argb: 0xFFA61D88),
        gold: Color(argb: 0xFF9A4A0A),
        glass: Color(argb: 0xF2FFFFFF),
        glassBorder: Color(argb: 0x330B7A86),
        neonBorder: Color(argb: 0x660B7A86),
        tabInactive: Color(argb: 0x9935565C),
        onPrimary: Color(argb: 0xFFFFFFFF),
        isLight: true
    )

    static let forestStar = StellarPalette(
        background: Color(argb: 0xFF042818),
        surface: Color(argb: 0x990A4A2E),
        surfaceHigh: Color(argb: 0xB31E6844),
        surfaceContainer: Color(argb: 0xA60F5235),
        onSurface: Color(argb: 0xFFF4FFF7),
        onSurfaceVariant: Color(argb: 0xFFD4F5DC),
        outline: Color(argb: 0x809AD84A),
        cyan: Color(argb: 0xFF9AD84A),
        cyanBright: Color(argb: 0xFFB8F060),
        cyanSoft: Color(argb: 0xFFD8FF9A),
        headword: Color(argb: 0xFFB8F060),
        pink: Color(argb: 0xFFFF9EB5),
        gold: Color(argb: 0xFFFFE066),
        glass: Color(argb: 0x731E6844),
        glassBorder: Color(argb: 0x559AD84A),
        neonBorder: Color(argb: 0x809AD84A),
        tabInactive: Color(argb: 0xCCD4F5DC),
        onPrimary: Color(argb: 0xFF1B4D2E),
        isLight: false,
        wallpaperImageName: "BgForestStar"
    )

    static func palette(for style: AccentStyle) -> StellarPalette {
        switch style {
        case .cyberNeon: cyberNeon
        case .emerald: emerald
        case .electric: electric
        case .solar: solar
        case .aurora: aurora
        case .ember: ember
        case .frost: frost
        case .forestStar: forestStar
        }
    }
}

enum Theme {
    static var palette: StellarPalette { StellarPalettes.palette(for: SettingsStore.accentStyle) }
    static var background: Color { palette.background }
    static var surface: Color { palette.surface }
    static var surfaceHigh: Color { palette.surfaceHigh }
    static var surfaceContainer: Color { palette.surfaceContainer }
    static var onSurface: Color { palette.onSurface }
    static var onSurfaceVariant: Color { palette.onSurfaceVariant }
    static var outline: Color { palette.outline }
    static var cyan: Color { palette.cyan }
    static var cyanBright: Color { palette.cyanBright }
    static var cyanSoft: Color { palette.cyanSoft }
    static var headword: Color { palette.headword }
    static var pink: Color { palette.pink }
    static var gold: Color { palette.gold }
    static var glass: Color { palette.glass }
    static var glassBorder: Color { palette.glassBorder }
    static var neonBorder: Color { palette.neonBorder }
    static var tabInactive: Color { palette.tabInactive }
    static var onPrimary: Color { palette.onPrimary }
    static var isLight: Bool { palette.isLight }
    static var hasWallpaper: Bool { palette.hasWallpaper }
    static var wallpaperImageName: String? { palette.wallpaperImageName }

    static func posColor(_ pos: String) -> Color {
        let key = pos.lowercased().trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "."))
        if key.hasPrefix("adv") { return cyanSoft }
        if key.hasPrefix("n"), !key.hasPrefix("num") { return pink }
        if key.hasPrefix("adj") || key == "a" { return cyan }
        if key.hasPrefix("v") { return gold }
        return cyanSoft
    }

    static func applyTabBar() {
        let palette = palette
        let appearance = UITabBarAppearance()
        if palette.hasWallpaper {
            appearance.configureWithTransparentBackground()
        } else {
            appearance.configureWithOpaqueBackground()
        }
        appearance.backgroundColor = UIColor(palette.surface)
        let inactive = UIColor(palette.tabInactive)
        let accent = UIColor(palette.cyan)
        let normal = appearance.stackedLayoutAppearance.normal
        normal.iconColor = inactive
        normal.titleTextAttributes = [.foregroundColor: inactive]
        let selected = appearance.stackedLayoutAppearance.selected
        selected.iconColor = accent
        selected.titleTextAttributes = [.foregroundColor: accent]
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
        DispatchQueue.main.async {
            for scene in UIApplication.shared.connectedScenes {
                guard let windowScene = scene as? UIWindowScene else { continue }
                for window in windowScene.windows {
                    apply(appearance, to: window)
                }
            }
        }
    }

    private static func apply(_ appearance: UITabBarAppearance, to view: UIView) {
        if let tabBar = view as? UITabBar {
            tabBar.standardAppearance = appearance
            tabBar.scrollEdgeAppearance = appearance
            tabBar.tintColor = UIColor(Theme.cyan)
            tabBar.unselectedItemTintColor = UIColor(Theme.tabInactive)
        }
        view.subviews.forEach { apply(appearance, to: $0) }
    }
}

extension Color {
    init(argb: UInt32) {
        self.init(
            .sRGB,
            red: Double((argb >> 16) & 0xFF) / 255,
            green: Double((argb >> 8) & 0xFF) / 255,
            blue: Double(argb & 0xFF) / 255,
            opacity: Double((argb >> 24) & 0xFF) / 255
        )
    }

    init(hex: UInt32) {
        self.init(argb: 0xFF000000 | hex)
    }

    init(hexString: String, fallback: Color = Color(hex: 0x1B6CA8)) {
        var raw = hexString.trimmingCharacters(in: .whitespacesAndNewlines)
        if raw.hasPrefix("#") { raw.removeFirst() }
        guard raw.count == 6, let value = UInt32(raw, radix: 16) else {
            self = fallback
            return
        }
        self.init(hex: value)
    }
}

struct GlassPanel: ViewModifier {
    var neon: Bool = false

    func body(content: Content) -> some View {
        content
            .background(Theme.glass, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(neon ? Theme.neonBorder : Theme.glassBorder, lineWidth: 1)
            )
    }
}

struct StellarScreenBackground: ViewModifier {
    @EnvironmentObject private var model: AppModel

    func body(content: Content) -> some View {
        content
            .background {
                ZStack {
                    Theme.background
                    if let name = Theme.wallpaperImageName {
                        Image(name)
                            .resizable()
                            .scaledToFill()
                            .ignoresSafeArea()
                    }
                }
                .ignoresSafeArea()
                // Force SwiftUI to re-evaluate Theme colors when style changes.
                .id(model.accentStyle)
            }
    }
}

extension View {
    func glassPanel(neon: Bool = false) -> some View {
        modifier(GlassPanel(neon: neon))
    }

    func stellarScreenBackground() -> some View {
        modifier(StellarScreenBackground())
    }

    func themeNavigationBar() -> some View {
        toolbarBackground(Theme.hasWallpaper ? Theme.surface.opacity(0.55) : Theme.surface.opacity(0.92), for: .navigationBar)
            .toolbarColorScheme(Theme.isLight ? .light : .dark, for: .navigationBar)
    }
}

/// Full-width hairline. Opacity replaces the color's alpha, matching Android `Color.copy(alpha = ...)`.
struct ThemeHairline: View {
    var color: Color = Theme.outline
    var alpha: Double = 0.35

    var body: some View {
        Rectangle()
            .fill(color.replacingOpacity(alpha))
            .frame(maxWidth: .infinity)
            .frame(height: 1)
    }
}

extension Color {
    func replacingOpacity(_ opacity: Double) -> Color {
        let ui = UIColor(self)
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        guard ui.getRed(&red, green: &green, blue: &blue, alpha: &alpha) else {
            return self.opacity(opacity)
        }
        return Color(.sRGB, red: Double(red), green: Double(green), blue: Double(blue), opacity: opacity)
    }
}

struct DefinitionLine: View {
    var definition: Definition

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            if !definition.pos.isEmpty {
                Text(definition.pos.hasSuffix(".") || definition.pos.hasPrefix("【") ? definition.pos : "\(definition.pos).")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.posColor(definition.pos))
                    .frame(width: 48, alignment: .leading)
            }
            Text(definition.meaning)
                .foregroundStyle(Theme.onSurface)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    var enabled: Bool = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(Theme.onPrimary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(enabled ? Theme.cyan : Theme.outline, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}
