package com.hotgis.wordbuddy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp
import com.hotgis.wordbuddy.data.AccentStyle
import com.hotgis.wordbuddy.data.AppTheme

data class HwColorPalette(
    val background: Color,
    val surfaceGray: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val maskBlue: Color,
    val maskStripe: Color,
    val accentBlue: Color,
    val iconGray: Color,
    val nearWord: Color,
    val listAccent: Color,
    val synonym: Color,
    val antonym: Color,
    val deleteRed: Color,
    val playFill: Color,
    val onPlayFill: Color,
    val clearButton: Color,
    val starFilled: Color,
    val userNoteHighlight: Color,
)

val LightHwColors = HwColorPalette(
    background = Color(0xFFFFFFFF),
    surfaceGray = Color(0xFFF4F4F4),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF8A8A8A),
    textTertiary = Color(0xFFB0B0B0),
    divider = Color(0xFFEEEEEE),
    maskBlue = Color(0xFFC9E6FF),
    maskStripe = Color(0xFFB6DCFF),
    accentBlue = Color(0xFF2B8CFF),
    iconGray = Color(0xFF9A9A9A),
    nearWord = Color(0xFFD97706),
    listAccent = Color(0xFFFF9800),
    synonym = Color(0xFF0F9F8A),
    antonym = Color(0xFFE11D48),
    deleteRed = Color(0xFFFF3B30),
    playFill = Color(0xFF111111),
    onPlayFill = Color.White,
    clearButton = Color(0xFFD0D0D0),
    starFilled = Color(0xFFE6B325),
    userNoteHighlight = Color(0xFFFFF3CD),
)

val DarkHwColors = HwColorPalette(
    background = Color(0xFF121212),
    surfaceGray = Color(0xFF2C2C2C),
    textPrimary = Color(0xFFF2F2F2),
    textSecondary = Color(0xFFAAAAAA),
    textTertiary = Color(0xFF777777),
    divider = Color(0xFF3A3A3A),
    maskBlue = Color(0xFF1A3555),
    maskStripe = Color(0xFF244768),
    accentBlue = Color(0xFF5EB0FF),
    iconGray = Color(0xFF888888),
    nearWord = Color(0xFFF59E0B),
    listAccent = Color(0xFFFF9800),
    synonym = Color(0xFF2DD4BF),
    antonym = Color(0xFFFB7185),
    deleteRed = Color(0xFFFF453A),
    playFill = Color(0xFFF2F2F2),
    onPlayFill = Color(0xFF111111),
    clearButton = Color(0xFF555555),
    starFilled = Color(0xFFE6B325),
    userNoteHighlight = Color(0xFF3D3420),
)

val ForestStarHwColors = HwColorPalette(
    background = Color(0xFF042818),
    surfaceGray = Color(0xFF0F5235),
    textPrimary = Color(0xFFF4FFF7),
    textSecondary = Color(0xFFA8DDB8),
    textTertiary = Color(0xFF6BAF82),
    divider = Color(0xFF2E8556),
    maskBlue = Color(0xFF1E6844),
    maskStripe = Color(0xFF2A7A52),
    accentBlue = Color(0xFF9AD84A),
    iconGray = Color(0xFF7CB892),
    nearWord = Color(0xFFFFE066),
    listAccent = Color(0xFFB8F060),
    synonym = Color(0xFF9AD84A),
    antonym = Color(0xFFFF9EB5),
    deleteRed = Color(0xFFFF6B6B),
    playFill = Color(0xFFD8FF9A),
    onPlayFill = Color(0xFF1B4D2E),
    clearButton = Color(0xFF2E8556),
    starFilled = Color(0xFFFFE066),
    userNoteHighlight = Color(0xFF1A4D30),
)

val LocalHwColors = staticCompositionLocalOf { LightHwColors }

/** @deprecated Use [hwColors] inside composables. Kept for gradual migration. */
object HwColors {
    val Background @Composable get() = hwColors().background
    val SurfaceGray @Composable get() = hwColors().surfaceGray
    val TextPrimary @Composable get() = hwColors().textPrimary
    val TextSecondary @Composable get() = hwColors().textSecondary
    val TextTertiary @Composable get() = hwColors().textTertiary
    val Divider @Composable get() = hwColors().divider
    val MaskBlue @Composable get() = hwColors().maskBlue
    val MaskStripe @Composable get() = hwColors().maskStripe
    val AccentBlue @Composable get() = hwColors().accentBlue
    val IconGray @Composable get() = hwColors().iconGray
    val NearWord @Composable get() = hwColors().nearWord
    val ListAccent @Composable get() = hwColors().listAccent
    val Synonym @Composable get() = hwColors().synonym
    val Antonym @Composable get() = hwColors().antonym
    val DeleteRed @Composable get() = hwColors().deleteRed
    val PlayBlack @Composable get() = hwColors().playFill
    val UserNoteHighlight @Composable get() = hwColors().userNoteHighlight
}

@Composable
fun hwColors(): HwColorPalette = LocalHwColors.current

private val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
)

@Composable
fun HotWordsTheme(
    appTheme: AppTheme = AppTheme.Light,
    accentStyle: AccentStyle = AccentStyle.CyberNeon,
    content: @Composable () -> Unit,
) {
    val palette = when {
        accentStyle == AccentStyle.ForestStar -> ForestStarHwColors
        appTheme == AppTheme.Light -> LightHwColors
        else -> DarkHwColors
    }
    val materialScheme = when {
        accentStyle == AccentStyle.ForestStar -> darkColorScheme(
            primary = palette.accentBlue,
            onPrimary = Color(0xFF1B4D2E),
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.background,
            onSurface = palette.textPrimary,
            outline = palette.divider,
        )
        appTheme == AppTheme.Light -> lightColorScheme(
            primary = palette.accentBlue,
            onPrimary = Color.White,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.background,
            onSurface = palette.textPrimary,
            outline = palette.divider,
        )
        else -> darkColorScheme(
            primary = palette.accentBlue,
            onPrimary = Color.Black,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.background,
            onSurface = palette.textPrimary,
            outline = palette.divider,
        )
    }
    CompositionLocalProvider(LocalHwColors provides palette) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = Typography,
            content = content,
        )
    }
}
