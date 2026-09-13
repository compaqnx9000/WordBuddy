package com.hotgis.wordbuddy.ui.design

import android.app.Activity
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

/** Phone, book-style dual fold (1 hinge), or tri-fold (2 hinges). */
enum class FoldableFormFactor {
    Phone,
    DualFold,
    TriFold,
}

/** Current fold posture — works across Samsung / Xiaomi / Huawei / Honor via Jetpack Window. */
enum class FoldPosture {
    Compact,
    FlatUnfolded,
    BookHalf,
    Tabletop,
}

/** Hinge safe-zone in dp from the left edge of the window. */
data class HingeGutter(
    val startDp: Float,
    val widthDp: Float,
)

data class FoldableLayoutInfo(
    val formFactor: FoldableFormFactor,
    val posture: FoldPosture,
    val windowWidthDp: Float,
    val windowHeightDp: Float,
    /** Width used by [DesignScaleProvider] so each pane scales like a phone canvas. */
    val designReferenceWidthDp: Float,
    val hingeGutters: List<HingeGutter>,
    /** List + card side-by-side on inner/unfolded displays. */
    val supportsDualPaneListCard: Boolean,
    val isTabletop: Boolean,
) {
    val hingeGutterWidthDp: Float
        get() = hingeGutters.sumOf { it.widthDp.toDouble() }.toFloat().coerceAtLeast(0f)

    companion object {
        val Phone = FoldableLayoutInfo(
            formFactor = FoldableFormFactor.Phone,
            posture = FoldPosture.Compact,
            windowWidthDp = DesignSpec.WIDTH_DP,
            windowHeightDp = DesignSpec.HEIGHT_DP,
            designReferenceWidthDp = DesignSpec.WIDTH_DP,
            hingeGutters = emptyList(),
            supportsDualPaneListCard = false,
            isTabletop = false,
        )
    }
}

val LocalFoldableLayout = compositionLocalOf { FoldableLayoutInfo.Phone }

/**
 * Tracks fold state for Samsung / Xiaomi / Huawei / Honor / OPPO foldables (2-fold & 3-fold)
 * through the standard AndroidX WindowManager API — no OEM-specific SDK required.
 */
@Composable
fun FoldableLayoutProvider(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val density = LocalDensity.current.density
    val tracker = remember(context) { WindowInfoTracker.getOrCreate(context) }
    val windowLayoutInfo by if (activity != null) {
        tracker.windowLayoutInfo(activity).collectAsState(initial = WindowLayoutInfo(emptyList()))
    } else {
        remember { androidx.compose.runtime.mutableStateOf(WindowLayoutInfo(emptyList())) }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val info = remember(maxWidth, maxHeight, windowLayoutInfo, density) {
            computeFoldableLayoutInfo(
                widthDp = maxWidth.value,
                heightDp = maxHeight.value,
                density = density,
                displayFeatures = windowLayoutInfo.displayFeatures,
            )
        }
        CompositionLocalProvider(LocalFoldableLayout provides info) {
            content()
        }
    }
}

fun computeFoldableLayoutInfo(
    widthDp: Float,
    heightDp: Float,
    density: Float,
    displayFeatures: List<DisplayFeature>,
): FoldableLayoutInfo {
    val folds = displayFeatures.filterIsInstance<FoldingFeature>()
    val verticalHinges = folds.filter { it.orientation == FoldingFeature.Orientation.VERTICAL }
    val horizontalHinges = folds.filter { it.orientation == FoldingFeature.Orientation.HORIZONTAL }

    val formFactor = when {
        verticalHinges.size >= 2 -> FoldableFormFactor.TriFold
        verticalHinges.size == 1 -> FoldableFormFactor.DualFold
        // Some OEMs only report a wide inner display without a hinge feature when fully flat.
        widthDp >= 900f && heightDp >= 600f -> FoldableFormFactor.DualFold
        else -> FoldableFormFactor.Phone
    }

    val isTabletop = horizontalHinges.any { it.state == FoldingFeature.State.HALF_OPENED } ||
        (folds.any {
            it.state == FoldingFeature.State.HALF_OPENED &&
                it.orientation == FoldingFeature.Orientation.HORIZONTAL
        })

    val posture = when {
        isTabletop -> FoldPosture.Tabletop
        folds.any { it.state == FoldingFeature.State.HALF_OPENED } -> FoldPosture.BookHalf
        formFactor != FoldableFormFactor.Phone && widthDp >= 680f -> FoldPosture.FlatUnfolded
        widthDp >= 600f -> FoldPosture.FlatUnfolded
        else -> FoldPosture.Compact
    }

    val hingeGutters = verticalHinges.map { feature ->
        val bounds: Rect = feature.bounds
        HingeGutter(
            startDp = bounds.left / density,
            widthDp = (bounds.width() / density).coerceAtLeast(12f),
        )
    }.sortedBy { it.startDp }

    val usableWidth = (widthDp - hingeGutters.sumOf { it.widthDp.toDouble() }).toFloat()
        .coerceAtLeast(320f)

    val supportsDualPane = (posture == FoldPosture.FlatUnfolded || posture == FoldPosture.BookHalf) &&
        usableWidth >= 680f &&
        formFactor != FoldableFormFactor.Phone

    val designReferenceWidthDp = when {
        supportsDualPane && formFactor == FoldableFormFactor.TriFold ->
            usableWidth / 3f
        supportsDualPane ->
            usableWidth / 2f
        formFactor == FoldableFormFactor.TriFold ->
            usableWidth / 3f
        else ->
            widthDp.coerceAtMost(DesignSpec.WIDTH_DP * 1.35f)
    }

    return FoldableLayoutInfo(
        formFactor = formFactor,
        posture = posture,
        windowWidthDp = widthDp,
        windowHeightDp = heightDp,
        designReferenceWidthDp = designReferenceWidthDp,
        hingeGutters = hingeGutters,
        supportsDualPaneListCard = supportsDualPane,
        isTabletop = isTabletop,
    )
}

/** Reserve space at vertical hinge lines so content is not clipped (2-fold & 3-fold). */
@Composable
fun FoldableHingeGutter(
    gutter: HingeGutter,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier
            .width(gutter.widthDp.dp)
            .fillMaxHeight()
            .background(androidx.compose.ui.graphics.Color.Transparent),
    )
}

/**
 * Dual-/tri-fold list + card split. Inserts hinge gutters between panes automatically.
 */
@Composable
fun FoldableDualPaneRow(
    modifier: Modifier = Modifier,
    listWeight: Float = 0.42f,
    detailWeight: Float = 0.58f,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
) {
    val foldable = LocalFoldableLayout.current
    Row(modifier.fillMaxSize()) {
        when (foldable.formFactor) {
            FoldableFormFactor.TriFold -> {
                val gutters = foldable.hingeGutters
                Box(Modifier.weight(1f)) { listPane() }
                gutters.getOrNull(0)?.let { FoldableHingeGutter(it) }
                Box(
                    Modifier
                        .weight(2f)
                        .padding(start = gutters.getOrNull(1)?.widthDp?.dp ?: 0.dp),
                ) {
                    detailPane()
                }
            }
            else -> {
                Box(Modifier.weight(listWeight)) { listPane() }
                foldable.hingeGutters.forEach { FoldableHingeGutter(it) }
                Box(Modifier.weight(detailWeight)) { detailPane() }
            }
        }
    }
}

/** Tabletop posture: primary content on top, controls / secondary area below the hinge. */
@Composable
fun FoldableTabletopColumn(
    modifier: Modifier = Modifier,
    topFraction: Float = 0.58f,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    val foldable = LocalFoldableLayout.current
    if (!foldable.isTabletop) {
        top()
        return
    }
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(topFraction)) { top() }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(foldable.hingeGutters.firstOrNull()?.widthDp?.dp ?: 12.dp),
        )
        Box(Modifier.weight(1f - topFraction)) { bottom() }
    }
}

@Composable
fun Modifier.foldableCenteredContent(): Modifier {
    val foldable = LocalFoldableLayout.current
    val maxContent = when (foldable.formFactor) {
        FoldableFormFactor.TriFold -> foldable.designReferenceWidthDp.dp * 2f
        FoldableFormFactor.DualFold -> foldable.designReferenceWidthDp.dp * 1.5f
        FoldableFormFactor.Phone -> 520.dp
    }
    return this then Modifier.padding(horizontal = 0.dp).then(
        if (foldable.windowWidthDp > maxContent.value) {
            Modifier.padding(horizontal = ((foldable.windowWidthDp - maxContent.value) / 2f).coerceAtLeast(0f).dp)
        } else {
            Modifier
        },
    )
}
