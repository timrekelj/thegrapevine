package si.timrekelj.thegrapevine.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Loading placeholders: before a screen's data comes back, its layout is drawn as plain shapes in
 * the tint colour, rounded like the real thing (28 for the lead card, 14 for thumbnails, capsules
 * for lines of text). They wait a moment before showing, so a quick load never flashes them, then
 * breathe gently until the real screen fades in over them. With reduced motion they hold still.
 */

/** How long a load may take before the placeholder shows at all. */
private const val SHOW_AFTER_MS = 250

/** The shared pulse, 0..1; null when holding still. Read in the draw phase only. */
private val LocalPulse = staticCompositionLocalOf<State<Float>?> { null }

/** A placeholder screen: everything inside is read out once as [label]. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, label: String = "Loading", content: @Composable ColumnScope.() -> Unit) {
    val reduced = rememberReducedMotion()
    val pulse = if (reduced) {
        null
    } else {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "skeleton pulse",
        )
    }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduced) {
            kotlinx.coroutines.delay(SHOW_AFTER_MS.toLong())
            shown.snapTo(1f)
        } else {
            shown.animateTo(1f, tween(220, delayMillis = SHOW_AFTER_MS))
        }
    }
    CompositionLocalProvider(LocalPulse provides pulse) {
        Column(
            modifier
                .graphicsLayer { alpha = shown.value }
                .clearAndSetSemantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                },
            content = content,
        )
    }
}

/** The tint fill in [shape], its alpha breathing with the shared pulse. */
private fun Modifier.bone(shape: Shape): Modifier = composed {
    val pulse = LocalPulse.current
    val tint = Press.ink
    drawBehind {
        val color = tint.copy(alpha = 0.07f + 0.04f * (pulse?.value ?: 0f))
        if (shape == RectangleShape) drawRect(color)
        else drawOutline(shape.createOutline(size, layoutDirection, this), color)
    }
}

/** A line of text: a capsule [fraction] of the width. */
@Composable
fun SkeletonLine(fraction: Float, height: Dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(fraction).height(height).bone(CircleShape))
}

/** A fixed-size shape: a label, a disc, a thumbnail. */
@Composable
fun SkeletonBlock(width: Dp, height: Dp, modifier: Modifier = Modifier, shape: Shape = CircleShape) {
    Box(modifier.width(width).height(height).bone(shape))
}

/** A paragraph: full lines and a short last one. */
@Composable
fun SkeletonLines(lines: Int, modifier: Modifier = Modifier, lineHeight: Dp = 12.dp, gap: Dp = 9.dp, last: Float = 0.62f) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(lines) { index -> SkeletonLine(if (index == lines - 1) last else 1f, lineHeight) }
    }
}

// ---------- the screens, as placeholders ----------

private val Gutter = 20.dp

/** The front page under its dateline: the lead card and three rows. Sits inside the page's own margins. */
@Composable
fun FrontPageSkeleton(modifier: Modifier = Modifier) {
    Skeleton(modifier.fillMaxWidth().padding(bottom = 32.dp), label = "Loading the front page") {
        Column(Modifier.fillMaxWidth().card(CardCorner)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.6f).bone(RectangleShape))
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonLine(0.35f, 13.dp)
                Spacer(Modifier.height(2.dp))
                SkeletonLine(0.95f, 30.dp)
                SkeletonLine(0.8f, 30.dp)
                SkeletonLine(0.5f, 30.dp)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SkeletonBlock(44.dp, 44.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonLine(0.7f, 13.dp)
                        SkeletonLine(0.4f, 13.dp)
                    }
                }
            }
        }
        repeat(3) { index ->
            Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(32.dp, 32.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonLine(1f, 17.dp)
                    SkeletonLine(if (index == 1) 0.5f else 0.75f, 17.dp)
                    SkeletonLine(0.6f, 11.dp)
                }
                SkeletonBlock(64.dp, 64.dp, shape = Corner)
            }
            Hairline()
        }
    }
}

/** A story under its app bar: the title, the catch-up card, the sources and the start of the conversation. */
@Composable
fun StorySkeleton(modifier: Modifier = Modifier) {
    Skeleton(modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 22.dp, bottom = 24.dp), label = "Loading the story") {
        SkeletonLine(0.35f, 13.dp)
        Column(Modifier.padding(top = 14.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLine(1f, 32.dp)
            SkeletonLine(0.85f, 32.dp)
            SkeletonLine(0.4f, 32.dp)
        }
        Box(Modifier.fillMaxWidth().height(180.dp).bone(CardCorner))
        SkeletonLine(0.25f, 13.dp, Modifier.padding(top = 24.dp))
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(4) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(40.dp, 40.dp)
                    SkeletonBlock(64.dp, 11.dp)
                }
            }
        }
        repeat(2) { index ->
            Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(36.dp, 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonLine(0.35f, 12.dp)
                    SkeletonLines(if (index == 0) 2 else 1, lineHeight = 13.dp, last = 0.7f)
                }
            }
        }
    }
}

/** A day of earlier editions: the day's label and a few rows. */
@Composable
fun ArchiveSkeleton(modifier: Modifier = Modifier) {
    Skeleton(modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 26.dp), label = "Loading earlier editions") {
        SkeletonBlock(150.dp, 13.dp)
        listOf(0.9f, 0.65f, 0.8f, 0.55f).forEach { headline ->
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonLine(headline, 18.dp)
                SkeletonLine(0.45f, 11.dp)
            }
            Hairline()
        }
        Spacer(Modifier.height(8.dp))
    }
}
