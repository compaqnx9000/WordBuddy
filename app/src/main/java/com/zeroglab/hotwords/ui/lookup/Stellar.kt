package com.zeroglab.hotwords.ui.lookup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeroglab.hotwords.data.AccentStyle
import com.zeroglab.hotwords.data.Definition
import com.zeroglab.hotwords.R
import com.zeroglab.hotwords.ui.design.sdp
import com.zeroglab.hotwords.ui.design.ssp

/**
 * Full Stellar surface + accent set. Property names keep the historical
 * Cyan / Pink / Gold aliases so existing call sites stay readable.
 */
data class StellarPalette(
    val Background: Color,
    val Surface: Color,
    val SurfaceHigh: Color,
    val SurfaceContainer: Color,
    val OnSurface: Color,
    val OnSurfaceVariant: Color,
    val Outline: Color,
    /** Primary accent (buttons, active tabs, links). */
    val Cyan: Color,
    /** Brighter primary for pressed / glow states. */
    val CyanBright: Color,
    /** Soft primary — headlines & FAB fills on dark themes. */
    val CyanSoft: Color,
    /** Secondary accent (noun POS, highlights). */
    val Pink: Color,
    /** Tertiary accent (verb POS, stars). */
    val Gold: Color,
    val Glass: Color,
    val GlassBorder: Color,
    val NeonBorder: Color,
    val TabInactive: Color,
    /** Text sitting on CyanSoft / primary filled buttons. */
    val OnPrimary: Color,
    val isLight: Boolean,
    /** When set, full-screen wallpaper is drawn instead of flat [Background]. */
    val backgroundImageRes: Int? = null,
)

object StellarPalettes {
    val CyberNeon = StellarPalette(
        Background = Color(0xFF0D1515),
        Surface = Color(0xFF151D1E),
        SurfaceHigh = Color(0xFF2E3637),
        SurfaceContainer = Color(0xFF192122),
        OnSurface = Color(0xFFDCE4E5),
        OnSurfaceVariant = Color(0xFFB9CACB),
        Outline = Color(0xFF3B494B),
        Cyan = Color(0xFF00DBE9),
        CyanBright = Color(0xFF00F0FF),
        CyanSoft = Color(0xFFDBFCFF),
        Pink = Color(0xFFFFABF3),
        Gold = Color(0xFFFED639),
        Glass = Color(0x662E3637),
        GlassBorder = Color(0x33FFFFFF),
        NeonBorder = Color(0x8000F0FF),
        TabInactive = Color(0x99B9CACB),
        OnPrimary = Color(0xFF00363A),
        isLight = false,
    )

    val Emerald = StellarPalette(
        Background = Color(0xFF071412),
        Surface = Color(0xFF0E1C19),
        SurfaceHigh = Color(0xFF244039),
        SurfaceContainer = Color(0xFF132420),
        OnSurface = Color(0xFFE1F5F0),
        OnSurfaceVariant = Color(0xFFA9C9C0),
        Outline = Color(0xFF35564E),
        Cyan = Color(0xFF2DD4BF),
        CyanBright = Color(0xFF5EEAD4),
        CyanSoft = Color(0xFFCCFBF1),
        Pink = Color(0xFF86EFAC),
        Gold = Color(0xFFFBBF24),
        Glass = Color(0x66244039),
        GlassBorder = Color(0x332DD4BF),
        NeonBorder = Color(0x802DD4BF),
        TabInactive = Color(0x99A9C9C0),
        OnPrimary = Color(0xFF042F2E),
        isLight = false,
    )

    val Electric = StellarPalette(
        Background = Color(0xFF070B16),
        Surface = Color(0xFF0E1524),
        SurfaceHigh = Color(0xFF243044),
        SurfaceContainer = Color(0xFF141C2E),
        OnSurface = Color(0xFFE2EAF8),
        OnSurfaceVariant = Color(0xFFA8B8D4),
        Outline = Color(0xFF33415C),
        Cyan = Color(0xFF38BDF8),
        CyanBright = Color(0xFF7DD3FC),
        CyanSoft = Color(0xFFE0F2FE),
        Pink = Color(0xFFA5B4FC),
        Gold = Color(0xFFFDE047),
        Glass = Color(0x66243044),
        GlassBorder = Color(0x3338BDF8),
        NeonBorder = Color(0x8038BDF8),
        TabInactive = Color(0x99A8B8D4),
        OnPrimary = Color(0xFF0C4A6E),
        isLight = false,
    )

    val Solar = StellarPalette(
        Background = Color(0xFF120E08),
        Surface = Color(0xFF1A140C),
        SurfaceHigh = Color(0xFF3A2E1C),
        SurfaceContainer = Color(0xFF221A10),
        OnSurface = Color(0xFFFFF4E0),
        OnSurfaceVariant = Color(0xFFD2BC96),
        Outline = Color(0xFF56462C),
        Cyan = Color(0xFFF5C542),
        CyanBright = Color(0xFFFDE68A),
        CyanSoft = Color(0xFFFFF3C4),
        Pink = Color(0xFFFF9F6B),
        Gold = Color(0xFFFFE08A),
        Glass = Color(0x663A2E1C),
        GlassBorder = Color(0x33F5C542),
        NeonBorder = Color(0x80F5C542),
        TabInactive = Color(0x99D2BC96),
        OnPrimary = Color(0xFF3B2A08),
        isLight = false,
    )

    val Aurora = StellarPalette(
        Background = Color(0xFF120B14),
        Surface = Color(0xFF1A101C),
        SurfaceHigh = Color(0xFF3A2A3E),
        SurfaceContainer = Color(0xFF221528),
        OnSurface = Color(0xFFF8EAF6),
        OnSurfaceVariant = Color(0xFFCDB6CB),
        Outline = Color(0xFF534056),
        Cyan = Color(0xFFF472B6),
        CyanBright = Color(0xFFF9A8D4),
        CyanSoft = Color(0xFFFCE7F3),
        Pink = Color(0xFF67E8F9),
        Gold = Color(0xFFFDE68A),
        Glass = Color(0x663A2A3E),
        GlassBorder = Color(0x33F472B6),
        NeonBorder = Color(0x80F472B6),
        TabInactive = Color(0x99CDB6CB),
        OnPrimary = Color(0xFF4A0E2E),
        isLight = false,
    )

    val Ember = StellarPalette(
        Background = Color(0xFF140A0A),
        Surface = Color(0xFF1C1010),
        SurfaceHigh = Color(0xFF3D2626),
        SurfaceContainer = Color(0xFF251515),
        OnSurface = Color(0xFFFFEDEC),
        OnSurfaceVariant = Color(0xFFD4B0AE),
        Outline = Color(0xFF5A3838),
        Cyan = Color(0xFFFF6B6B),
        CyanBright = Color(0xFFFF8E8E),
        CyanSoft = Color(0xFFFFE4E6),
        Pink = Color(0xFFFFB4A2),
        Gold = Color(0xFFFBBF24),
        Glass = Color(0x663D2626),
        GlassBorder = Color(0x33FF6B6B),
        NeonBorder = Color(0x80FF6B6B),
        TabInactive = Color(0x99D4B0AE),
        OnPrimary = Color(0xFF4C0519),
        isLight = false,
    )

    val Frost = StellarPalette(
        Background = Color(0xFFEEF3F4),
        Surface = Color(0xFFFFFFFF),
        SurfaceHigh = Color(0xFFDCE5E7),
        SurfaceContainer = Color(0xFFF7FAFA),
        OnSurface = Color(0xFF152022),
        OnSurfaceVariant = Color(0xFF4F6366),
        Outline = Color(0xFFB7C6C8),
        Cyan = Color(0xFF0E8A96),
        CyanBright = Color(0xFF14B8C6),
        CyanSoft = Color(0xFF0F5F68),
        Pink = Color(0xFFC026A0),
        Gold = Color(0xFFB45309),
        Glass = Color(0xE6FFFFFF),
        GlassBorder = Color(0x26000000),
        NeonBorder = Color(0x660E8A96),
        TabInactive = Color(0x994F6366),
        OnPrimary = Color(0xFFFFFFFF),
        isLight = true,
    )

    /** User-provided emerald star-trail wallpaper as the app background. */
    val ForestStar = StellarPalette(
        Background = Color(0xFF042818),
        Surface = Color(0x990A4A2E),
        SurfaceHigh = Color(0xB31E6844),
        SurfaceContainer = Color(0xA60F5235),
        OnSurface = Color(0xFFF4FFF7),
        OnSurfaceVariant = Color(0xFFD4F5DC),
        Outline = Color(0x809AD84A),
        Cyan = Color(0xFF9AD84A),
        CyanBright = Color(0xFFB8F060),
        CyanSoft = Color(0xFFD8FF9A),
        Pink = Color(0xFFFF9EB5),
        Gold = Color(0xFFFFE066),
        Glass = Color(0x731E6844),
        GlassBorder = Color(0x559AD84A),
        NeonBorder = Color(0x809AD84A),
        TabInactive = Color(0xCCD4F5DC),
        OnPrimary = Color(0xFF1B4D2E),
        isLight = false,
        backgroundImageRes = R.drawable.bg_forest_star,
    )

    fun forStyle(style: AccentStyle): StellarPalette = when (style) {
        AccentStyle.CyberNeon -> CyberNeon
        AccentStyle.Emerald -> Emerald
        AccentStyle.Electric -> Electric
        AccentStyle.Solar -> Solar
        AccentStyle.Aurora -> Aurora
        AccentStyle.Ember -> Ember
        AccentStyle.Frost -> Frost
        AccentStyle.ForestStar -> ForestStar
    }
}

val LocalStellar = staticCompositionLocalOf { StellarPalettes.CyberNeon }

@Composable
fun StellarTheme(
    style: AccentStyle,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalStellar provides StellarPalettes.forStyle(style)) {
        content()
    }
}

/**
 * Theme-aware color accessors. Must be read from a Composable (or
 * [ReadOnlyComposable]) under [StellarTheme].
 */
object Stellar {
    val Background: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.Background
    val Surface: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.Surface
    val SurfaceHigh: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.SurfaceHigh
    val SurfaceContainer: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.SurfaceContainer
    val OnSurface: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.OnSurface
    val OnSurfaceVariant: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.OnSurfaceVariant
    val Outline: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.Outline
    val Cyan: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.Cyan
    val CyanBright: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.CyanBright
    val CyanSoft: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.CyanSoft
    val Pink: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.Pink
    val Gold: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.Gold
    val Glass: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.Glass
    val GlassBorder: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.GlassBorder
    val NeonBorder: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.NeonBorder
    val TabInactive: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.TabInactive
    val OnPrimary: Color
        @Composable @ReadOnlyComposable get() = LocalStellar.current.OnPrimary
}

@Composable
fun Modifier.stellarGlass(neon: Boolean = false): Modifier {
    val shape = RoundedCornerShape(16.sdp())
    return this
        .clip(shape)
        .background(Stellar.Glass, shape)
        .border(
            width = 1.dp,
            color = if (neon) Stellar.NeonBorder else Stellar.GlassBorder,
            shape = shape,
        )
}

fun stellarPosColor(pos: String, palette: StellarPalette): Color {
    val key = pos.lowercase().trim().trimEnd('.')
    return when {
        key.startsWith("n") && !key.startsWith("num") -> palette.Pink
        key.startsWith("adj") || key == "a" -> palette.Cyan
        key.startsWith("v") -> palette.Gold
        key.startsWith("adv") -> palette.CyanSoft
        else -> palette.CyanSoft
    }
}

@Composable
@ReadOnlyComposable
fun stellarPosColor(pos: String): Color = stellarPosColor(pos, LocalStellar.current)

@Composable
fun StellarDefinitionRow(
    def: Definition,
    modifier: Modifier = Modifier,
    userNote: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    onMeaningLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val posText = when {
        def.pos.isBlank() -> ""
        def.pos.endsWith(".") || def.pos.startsWith("【") -> def.pos
        else -> "${def.pos}."
    }
    val posColor = if (userNote) Stellar.Pink else stellarPosColor(def.pos)
    val meaningColor = if (userNote) Stellar.Pink.copy(alpha = 0.92f) else Stellar.OnSurface
    val lineH = 26.ssp()
    val bodySize = 16.ssp()
    val firstLineHeight = with(LocalDensity.current) { lineH.toDp() }
    val sharedStyle = TextStyle(
        fontSize = bodySize,
        lineHeight = lineH,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )

    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (userNote) {
                    Modifier
                        .clip(RoundedCornerShape(10.sdp()))
                        .background(Stellar.Pink.copy(alpha = 0.10f))
                        .padding(horizontal = 10.sdp(), vertical = 8.sdp())
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .width(52.sdp())
                .height(firstLineHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (posText.isNotBlank()) {
                Text(
                    text = posText,
                    color = posColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    style = sharedStyle.copy(
                        shadow = Shadow(
                            color = posColor.copy(alpha = 0.45f),
                            blurRadius = 8f,
                        ),
                    ),
                )
            }
        }
        Text(
            text = def.meaning,
            color = meaningColor,
            style = sharedStyle,
            maxLines = maxLines,
            overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
            onTextLayout = { onMeaningLayout?.invoke(it) },
            modifier = Modifier.weight(1f),
        )
    }
}
