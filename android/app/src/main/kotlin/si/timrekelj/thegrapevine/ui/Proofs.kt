package si.timrekelj.thegrapevine.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Galley proofs: before a page comes back from the press, its layout is pulled in faint ink — bars
 * where the type will set, halftone screens where the plates will go — while an inking roller
 * passes down the sheet, darkening what it touches. The real page then fades in over it.
 */

/** The roller's place on its trip down the sheet, 0 (above) to 1 (below); null when standing still. */
private class Roller(val phase: State<Float>?, val sheet: Float) {
    /** How much fresh ink the roller is laying at window height [y], 0..1. */
    fun inkAt(y: Float): Float {
        val phase = phase ?: return 0f
        val band = sheet * 0.35f
        val at = -band + phase.value * (sheet + band * 2f)
        val t = (1f - abs(y - at) / band).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

private val LocalRoller = staticCompositionLocalOf<Roller?> { null }

/** A proof sheet: everything inside is a placeholder, read out once as "Loading". */
@Composable
fun GalleyProof(modifier: Modifier = Modifier, label: String = "Loading", content: @Composable ColumnScope.() -> Unit) {
    val reduced = rememberReducedMotion()
    val phase = if (reduced) {
        null
    } else {
        rememberInfiniteTransition(label = "roller").animateFloat(
            0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "roller phase",
        )
    }
    val sheet = LocalWindowInfo.current.containerSize.height.toFloat().coerceAtLeast(1f)
    val roller = remember(phase, sheet) { Roller(phase, sheet) }
    CompositionLocalProvider(LocalRoller provides roller) {
        Column(
            modifier.clearAndSetSemantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
            content = content,
        )
    }
}

/** Faint proof ink, darkened as the roller passes. */
private fun Modifier.proofInk(shape: androidx.compose.ui.graphics.Shape? = null): Modifier = composed {
    val roller = LocalRoller.current
    val ink = Press.ink
    val y = remember { mutableFloatStateOf(0f) }
    onGloballyPositioned { y.floatValue = it.positionInWindow().y + it.size.height / 2f }
        .drawBehind {
            val color = ink.copy(alpha = 0.07f + 0.08f * (roller?.inkAt(y.floatValue) ?: 0f))
            if (shape == null) drawRect(color) else drawCircle(color)
        }
}

/** A line of type not yet set. */
@Composable
fun ProofBar(fraction: Float, height: Dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(fraction).height(height).proofInk())
}

/** A block of a fixed width, like a label or a drop cap. */
@Composable
fun ProofBlock(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(modifier.width(width).height(height).proofInk())
}

/** An avatar's disc. */
@Composable
fun ProofDisc(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).proofInk(CircleShape))
}

/** A rule pulled in proof ink. */
@Composable
fun ProofRule(modifier: Modifier = Modifier, thickness: Dp = 1.dp) {
    Box(modifier.fillMaxWidth().height(thickness).proofInk())
}

/** A paragraph: full lines and a short last one. */
@Composable
fun ProofLines(lines: Int, modifier: Modifier = Modifier, lineHeight: Dp = 11.dp, gap: Dp = 9.dp, last: Float = 0.62f) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(lines) { index -> ProofBar(if (index == lines - 1) last else 1f, lineHeight) }
    }
}

/** A plate waiting for its photo: a fine halftone screen whose dots swell as the roller inks them. */
@Composable
fun ProofPlate(modifier: Modifier = Modifier) {
    val roller = LocalRoller.current
    val ink = Press.ink
    val y = remember { mutableFloatStateOf(0f) }
    Canvas(
        modifier.background(Press.tint).border(1.dp, Press.rule)
            .onGloballyPositioned { y.floatValue = it.positionInWindow().y },
    ) {
        val pitch = 6.dp.toPx()
        val rowStep = pitch * 0.866f
        val color = ink.copy(alpha = 0.16f)
        var row = 0
        var cy = rowStep / 2f
        while (cy < size.height) {
            val k = roller?.inkAt(y.floatValue + cy) ?: 0f
            val radius = pitch * (0.14f + 0.2f * k)
            var cx = if (row % 2 == 1) pitch else pitch / 2f
            while (cx < size.width) {
                drawCircle(color, radius, Offset(cx, cy))
                cx += pitch
            }
            cy += rowStep
            row++
        }
    }
}

// ---------- the pages, in proof ----------

private val Gutter = 20.dp

/** The front page under its masthead: a lead with its plate, a pictured side story, and two inside rows. */
@Composable
fun FrontPageProof(modifier: Modifier = Modifier) {
    GalleyProof(modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 22.dp, bottom = 32.dp), label = "Loading the front page") {
        ProofBlock(64.dp, 11.dp)
        Column(Modifier.padding(top = 14.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProofBar(1f, 30.dp)
            ProofBar(0.9f, 30.dp)
            ProofBar(0.55f, 30.dp)
        }
        ProofPlate(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        ProofBar(0.5f, 10.dp, Modifier.padding(top = 9.dp))
        ProofRule(modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProofBlock(40.dp, 46.dp)
            ProofLines(3, lineHeight = 12.dp, gap = 9.dp, last = 1f, modifier = Modifier.weight(1f).padding(top = 3.dp))
        }
        ProofLines(2, lineHeight = 12.dp, modifier = Modifier.padding(top = 9.dp))

        ProofRule(Modifier.padding(top = 28.dp), thickness = 3.dp)
        ProofPlate(Modifier.padding(top = 20.dp).fillMaxWidth().aspectRatio(3f / 2f))
        ProofBlock(56.dp, 11.dp, Modifier.padding(top = 12.dp))
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ProofBar(1f, 22.dp)
            ProofBar(0.7f, 22.dp)
        }
        ProofLines(2, modifier = Modifier.padding(top = 12.dp))
        ProofRule(modifier = Modifier.padding(top = 20.dp))

        repeat(2) {
            Row(Modifier.padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProofBlock(48.dp, 10.dp)
                    ProofBar(1f, 18.dp)
                    ProofBar(0.6f, 18.dp)
                    ProofBar(0.45f, 10.dp)
                }
                ProofPlate(Modifier.size(100.dp))
            }
            ProofRule()
        }
    }
}

/** A story under its nameplate: headline, plate, the summary, clippings, and the start of the conversation. */
@Composable
fun StoryProof(modifier: Modifier = Modifier) {
    GalleyProof(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 24.dp), label = "Loading the story") {
        Column(Modifier.padding(horizontal = Gutter)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProofBlock(58.dp, 11.dp)
                ProofBlock(80.dp, 11.dp)
            }
            Column(Modifier.padding(top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProofBar(1f, 32.dp)
                ProofBar(0.85f, 32.dp)
                ProofBar(0.4f, 32.dp)
            }
            ProofRule()
            ProofBar(0.55f, 10.dp, Modifier.padding(vertical = 9.dp))
            ProofRule()
            ProofPlate(Modifier.padding(top = 20.dp).fillMaxWidth().aspectRatio(16f / 9f))
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProofBlock(40.dp, 46.dp)
                ProofLines(3, lineHeight = 12.dp, last = 1f, modifier = Modifier.weight(1f).padding(top = 3.dp))
            }
            ProofLines(3, lineHeight = 12.dp, modifier = Modifier.padding(top = 9.dp))
        }
        Row(Modifier.padding(horizontal = Gutter).padding(top = 30.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) {
                Row(Modifier.weight(1f).clipping(Press.rule, Press.raised).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProofPlate(Modifier.size(54.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProofBar(0.6f, 9.dp)
                        ProofBar(1f, 12.dp)
                        ProofBar(0.8f, 12.dp)
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = Gutter).padding(top = 36.dp)) {
            ProofRule(thickness = 3.dp)
            ProofBar(0.5f, 20.dp, Modifier.padding(top = 10.dp))
            repeat(2) { index ->
                Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProofDisc(36.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProofBar(0.35f, 11.dp)
                        ProofLines(if (index == 0) 2 else 1, lineHeight = 12.dp, last = 0.7f)
                    }
                }
            }
        }
    }
}

/** A day of earlier editions: the dateline, its ink rule, and a few rows. */
@Composable
fun ArchiveProof(modifier: Modifier = Modifier) {
    GalleyProof(modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 26.dp), label = "Loading earlier editions") {
        ProofBlock(150.dp, 11.dp)
        ProofRule(modifier = Modifier.padding(top = 8.dp))
        listOf(0.9f, 0.65f, 0.8f, 0.55f).forEach { headline ->
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProofBlock(52.dp, 10.dp)
                ProofBar(headline, 18.dp)
                ProofBar(0.4f, 10.dp)
            }
            ProofRule()
        }
        Spacer(Modifier.height(8.dp))
    }
}
