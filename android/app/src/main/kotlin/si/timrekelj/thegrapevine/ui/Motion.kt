package si.timrekelj.thegrapevine.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the app moves. Nothing spins: work in progress is three pulsing dots or a placeholder of the
 * layout (Skeletons.kt), a pressed surface sinks a little, and late items unfold into the page
 * rather than dropping onto it.
 */

/** True when the reader has turned animations off (Developer options or Accessibility → Remove animations). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

// ---------- busy dots ----------

/** Three ink dots that swell one after another: the app's only "busy" mark. With reduced motion they only fade. */
@Composable
fun InkDots(
    modifier: Modifier = Modifier,
    color: Color = Press.ink,
    dotSize: Dp = 6.dp,
    contentDescription: String? = "Loading",
) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "ink dots")
    Row(
        modifier.clearAndSetSemantics {
            if (contentDescription != null) {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }
        },
        horizontalArrangement = Arrangement.spacedBy(dotSize * 0.75f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val start = index * DOT_STAGGER
            val ink = transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = DOT_CYCLE
                        0f at 0
                        0f at start using FastOutSlowInEasing
                        1f at start + 320 using FastOutSlowInEasing
                        0f at start + 680
                        0f at DOT_CYCLE
                    },
                ),
                label = "dot $index",
            )
            Box(
                Modifier.size(dotSize)
                    .graphicsLayer {
                        val t = ink.value
                        val scale = if (reduced) 1f else 0.3f + 0.7f * t
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.25f + 0.75f * t
                    }
                    .background(color, CircleShape),
            )
        }
    }
}

private const val DOT_CYCLE = 1100
private const val DOT_STAGGER = 150

/** Dots over a quiet line, for a whole screen that is still loading. */
@Composable
fun LoadingMark(modifier: Modifier = Modifier, label: String = "Opening the paper") {
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InkDots(dotSize = 8.dp, contentDescription = null)
        Text(label, style = LabelStyle.copy(color = Press.ink3), modifier = Modifier.padding(top = 14.dp))
    }
}

// ---------- pressing ----------

/** How far a pressed thing gives. */
enum class Give(val scale: Float, val fade: Float, val drop: Dp) {
    /** A button: pushed in and down a little. */
    Button(0.97f, 1f, 1.dp),

    /** A story on the page: barely moves, fades a little. */
    Story(0.985f, 0.82f, 0.dp),

    /** A word: fades more than it moves. */
    Type(0.96f, 0.55f, 0.dp),

    /** A chip or a small key: pressed in, no fade. */
    Chip(0.9f, 1f, 0.dp),
}

/**
 * Sinks when pressed, instead of Material's ripple; [interactionSource] is the press to follow.
 * Use with Material buttons by handing both the same source.
 */
fun Modifier.sink(interactionSource: InteractionSource, give: Give = Give.Button): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val depth by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = if (pressed) Spring.DampingRatioNoBouncy else 0.55f, stiffness = if (pressed) 1400f else Spring.StiffnessMediumLow),
        label = "sink",
    )
    graphicsLayer {
        val scale = 1f - (1f - give.scale) * depth
        scaleX = scale
        scaleY = scale
        alpha = 1f - (1f - give.fade) * depth.coerceIn(0f, 1f)
        translationY = give.drop.toPx() * depth
    }
}

/**
 * A clickable that sinks rather than ripples, with an optional tap of the haptic engine for
 * primary actions. Place it before backgrounds and clips so the whole surface moves.
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    give: Give = Give.Button,
    haptic: Boolean = false,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    sink(source, give).clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
    ) {
        if (haptic) haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        onClick()
    }
}

// ---------- late items ----------

/**
 * A late item set into the page: it unfolds and fades in, and folds away when removed. The last
 * value stays on the page while it leaves.
 */
@Composable
fun <T : Any> Insert(value: T?, content: @Composable (T) -> Unit) {
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    AnimatedVisibility(
        visible = value != null,
        enter = fadeIn(tween(260, delayMillis = 60)) + expandVertically(tween(260, easing = LinearOutSlowInEasing), expandFrom = Alignment.Top),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(220)),
    ) {
        last?.let { content(it) }
    }
}
