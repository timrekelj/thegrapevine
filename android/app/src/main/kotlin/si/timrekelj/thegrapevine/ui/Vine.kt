package si.timrekelj.thegrapevine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.timrekelj.thegrapevine.Source
import java.time.Instant

/**
 * Each member owns one hue, in the order they joined the paper (the founder raspberry, then marigold,
 * terra, sky, grape, teal, cocoa). AppState records the order when the members load; anyone not in it
 * (a deleted account) falls back to a hash that matches web and iOS.
 */
object MemberHues {
    @Volatile var order: Map<String, Int> = emptyMap()
    fun record(ids: List<String>) { order = ids.withIndex().associate { (i, id) -> id.lowercase() to i } }
}

fun memberColorIndex(seed: String): Int {
    MemberHues.order[seed.lowercase()]?.let { return it % 7 }
    var hash = 0L
    seed.lowercase().forEach { hash = (hash * 31 + it.code) and 0xffffffffL }
    return (hash % 7).toInt()
}
val memberColors = listOf(0xFFD23D7E,0xFFE3A512,0xFFE2552B,0xFF2F7BEA,0xFF8E4FD8,0xFF12A29A,0xFF9C6B3C).map { Color(it) }
val memberLightColors = listOf(0xFFE66A9C,0xFFF2BE3E,0xFFF07A55,0xFF5C9BF5,0xFFAB7AE6,0xFF3FC1B9,0xFFB98858).map { Color(it) }

/** A member's disc: their initial in ink on a lit gradient of their hue, light at the top-left. */
@Composable
fun MemberDisc(seed: String, name: String, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val index = memberColorIndex(seed)
    Box(
        modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(memberLightColors[index], memberColors[index]))),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), fontWeight = FontWeight.SemiBold, fontSize = (size.value * .4f).sp, color = Color(0xFF15221B))
    }
}

@Composable
fun SourceBunch(sources: List<Source>, seenAt: Instant? = null, size: Dp = 44.dp) {
    val vine = Press.red
    val ground = Press.paper
    Canvas(Modifier.size(size, size * 1.23f)) {
        val factor = this.size.width / 44f
        scale(factor, factor, pivot = Offset.Zero) {
            val stem = Path().apply { moveTo(22f,12f); cubicTo(21f,7f,24f,3f,29f,2f) }
            drawPath(stem, vine, style = Stroke(2.5f))
            val leaf = Path().apply { moveTo(22f,10f); quadraticTo(10f,0f,7f,11f); quadraticTo(14f,16f,22f,10f); close() }
            drawPath(leaf, vine)
            val positions = listOf(Offset(13f,19f),Offset(31f,19f),Offset(22f,29f),Offset(13f,37f),Offset(31f,37f),Offset(22f,46f),Offset(22f,16f))
            sources.sortedByDescending { it.created_at }.take(7).forEachIndexed { index, source ->
                val at = positions[index]
                val hue = memberColorIndex(source.submitted_by ?: source.id)
                drawCircle(Brush.radialGradient(listOf(memberLightColors[hue], memberColors[hue]), center = at - Offset(3f,3f), radius = 13f), 8f, at)
                drawCircle(ground, 8f, at, style = Stroke(2f))
                if (seenAt == null || source.created_at > seenAt) drawCircle(vine, 10f, at, style = Stroke(2f))
            }
        }
    }
}

@Composable
fun FilingMark(busy: Boolean, confirmed: Boolean) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "reading")
    val amount by transition.animateFloat(
        initialValue = 70f, targetValue = 300f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200), androidx.compose.animation.core.RepeatMode.Reverse), label = "progress",
    )
    val vine = Press.red
    val ink = Press.ink
    val reduced = rememberReducedMotion()
    Canvas(Modifier.size(64.dp)) {
        val radius = 18.dp.toPx()
        drawCircle(ink, radius)
        val ring = 23.dp.toPx()
        drawArc(vine, -90f, if (confirmed || reduced || !busy) 360f else amount, false, topLeft = center - Offset(ring, ring), size = androidx.compose.ui.geometry.Size(ring * 2, ring * 2), style = Stroke(3.dp.toPx()))
        val leaf = Path().apply { moveTo(center.x, center.y-radius); relativeQuadraticTo(-20.dp.toPx(),-16.dp.toPx(),-20.dp.toPx(),0f); relativeQuadraticTo(12.dp.toPx(),8.dp.toPx(),20.dp.toPx(),0f) }
        drawPath(leaf, vine)
    }
}

/** One source as a single grape in its filer's hue, ringed vine green when it is new to you. */
@Composable
fun Grape(seed: String, new: Boolean, size: Dp = 40.dp) {
    val hue = memberColorIndex(seed)
    val vine = Press.red
    Canvas(Modifier.size(size)) {
        val ring = 3.dp.toPx()
        val radius = this.size.minDimension / 2f - if (new) ring * 2f else 0f
        drawCircle(
            Brush.linearGradient(listOf(memberLightColors[hue], memberColors[hue]), start = center - Offset(radius, radius), end = center + Offset(radius, radius)),
            radius,
        )
        if (new) drawCircle(vine, this.size.minDimension / 2f - ring / 2f, style = Stroke(ring))
    }
}
