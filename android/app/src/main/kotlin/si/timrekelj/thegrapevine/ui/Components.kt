package si.timrekelj.thegrapevine.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import si.timrekelj.thegrapevine.Api
import si.timrekelj.thegrapevine.Config
import si.timrekelj.thegrapevine.Plate
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

val LocalApi = staticCompositionLocalOf<Api> { error("No Api provided") }

/** Square-ish corners for fields and buttons, like the web's 3px radius. */
val Corner = RoundedCornerShape(3.dp)

// ---------- avatars ----------

/** A member's Disco disc from the web's `/avatar/<id>.png`, generated from their id. */
@Composable
fun Avatar(seed: String, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val api = LocalApi.current
    val url = "${Config.webURL}/avatar/$seed.png"
    val image by produceState(AvatarCache.get(url)?.asImageBitmap(), url) {
        if (value != null) return@produceState
        val decoded = runCatching {
            withContext(Dispatchers.IO) { api.fetchBytes(url).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
        }.getOrNull()
        if (decoded != null) {
            AvatarCache.put(url, decoded)
            value = decoded.asImageBitmap()
        }
    }
    Box(modifier.size(size).clip(CircleShape).background(Press.tint)) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}

private object AvatarCache : LruCache<String, Bitmap>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

/** Overlapping avatars with a ring of paper between them. */
@Composable
fun AvatarStack(seeds: List<String>, size: Dp = 26.dp) {
    val ring = Press.paper
    Row {
        seeds.forEachIndexed { index, seed ->
            Box(
                Modifier.zIndex(index.toFloat()).offset(x = -(index * 6).dp).size(size + 4.dp)
                    .background(ring, CircleShape).padding(2.dp),
            ) { Avatar(seed, size) }
        }
    }
}

// ---------- marks ----------

/** A block of red, set square: the only stamp on the page means "new". */
@Composable
fun Stamp(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = LabelStyle.copy(color = Press.onRed, fontSize = 10.sp, letterSpacing = 0.06.sp),
        modifier = modifier.background(Press.red).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** "New to you": a red dot before the count. */
@Composable
fun NewMark(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(Press.red, CircleShape))
        Text(text, style = MetaStyle.copy(color = Press.ink, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun MetaRow(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MetaStyle, modifier = modifier)
}

/** Thick-and-thin rule under a nameplate. */
@Composable
fun DoubleRule(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Press.ink))
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Press.ink))
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Press.rule) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** A section head: a heavy rule, then an italic serif title. */
@Composable
fun SectionHead(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Press.ink))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = SectionStyle)
            Spacer(Modifier.size(14.dp))
            Hairline(Modifier.weight(1f))
        }
    }
}

/** The dashed edge of a clipping cut from the page. */
fun Modifier.clipping(color: Color, fill: Color): Modifier = drawBehind {
    drawRect(fill)
    val inset = 0.5.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius.Zero,
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

// ---------- buttons & fields ----------

/** An ink block; the main action. */
@Composable
fun InkButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier.fillMaxWidth(), height: Dp = 50.dp, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = height).clip(Corner)
            .background(if (enabled) Press.ink else Press.ink.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Press.paper, maxLines = 1)
    }
}

/** A ruled outline; the secondary action. */
@Composable
fun GhostButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 46.dp).clip(Corner).border(1.dp, if (enabled) Press.rule else Color.Transparent, Corner)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (enabled) Press.ink else Press.ink3, textAlign = TextAlign.Center)
    }
}

/** An underlined text action, like the web's `.story-text-button`. */
@Composable
fun TextAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        style = MetaStyle.copy(
            color = if (enabled) Press.ink else Press.ink3,
            fontWeight = FontWeight.SemiBold,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
        ),
        modifier = modifier.clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
    )
}

@Composable
fun PaperField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        enabled = enabled,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = BodyStyle.copy(color = Press.ink3)) },
        singleLine = singleLine,
        isError = isError,
        textStyle = BodyStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = Corner,
        colors = fieldColors(),
        modifier = modifier,
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Press.raised,
    unfocusedContainerColor = Press.raised,
    disabledContainerColor = Press.raised,
    errorContainerColor = Press.raised,
    focusedBorderColor = Press.ink,
    unfocusedBorderColor = Press.rule,
    errorBorderColor = Press.danger,
    cursorColor = Press.ink,
    focusedTextColor = Press.ink,
    unfocusedTextColor = Press.ink,
    errorTextColor = Press.ink,
)

// ---------- plates ----------

/**
 * A photograph printed in colour through a halftone screen (see [Plate]), or — without a usable
 * picture — a type plate with the publisher's name. `screen = false` prints the photo clean,
 * for attachments people shared themselves.
 */
@Composable
fun PlateView(url: String?, label: String, modifier: Modifier = Modifier, screen: Boolean = true) {
    val api = LocalApi.current
    val palette = LocalPalette.current
    BoxWithConstraints(modifier.clip(RoundedCornerShape(0.dp)).background(palette.tint), contentAlignment = Alignment.Center) {
        val columns = Plate.columns(maxWidth.value)
        val key = "$url|${if (screen) columns else 0}|${palette.isDark}"
        val image by produceState(url?.let { PlateCache.get(key)?.asImageBitmap() }, key) {
            if (url == null || value != null) return@produceState
            val printed = runCatching {
                withContext(Dispatchers.Default) { print(api.fetchBytes(url), if (screen) columns else 0, palette) }
            }.getOrNull()
            if (printed != null) {
                PlateCache.put(key, printed)
                value = printed.asImageBitmap()
            }
        }
        val bitmap: ImageBitmap? = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            TypePlate(label, Modifier.matchParentSize())
        }
    }
}

/** A source with no picture: the publisher's name on a light screen. */
@Composable
fun TypePlate(label: String, modifier: Modifier = Modifier) {
    val dots = Press.rule
    BoxWithConstraints(modifier.background(Press.raised).border(1.dp, Press.rule), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val step = 4.dp.toPx()
            val radius = 0.9.dp.toPx()
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) {
                    drawCircle(dots, radius, Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        val fontSize = (maxWidth.value * 0.12f).coerceIn(11f, 30f)
        Text(
            label,
            style = BodyStyle.copy(fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, fontSize = fontSize.sp, lineHeight = (fontSize * 1.08f).sp, textAlign = TextAlign.Center),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp).background(Press.raised).padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

private object PlateCache : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

private const val MAX_PLATE_WIDTH = 1080

/** Decodes the photo and, with `columns > 0`, prints it through a dot screen that many dots wide. */
private fun print(bytes: ByteArray, columns: Int, palette: Palette): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= MAX_PLATE_WIDTH) sample *= 2
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    if (columns <= 0) return source

    // The colour plate: a little extra saturation (and light, on black paper) for the ink lost between dots.
    val colour = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val matrix = ColorMatrix().apply {
        setSaturation(Plate.SATURATION)
        if (palette.isDark) postConcat(ColorMatrix().apply { setScale(1.12f, 1.12f, 1.12f, 1f) })
    }
    Canvas(colour).drawBitmap(source, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })

    // Luminance per cell, on a grid whose alternate rows are offset by half a dot.
    val pitch = source.width.toFloat() / columns
    val rowStep = pitch * 0.866f
    val rows = (source.height / rowStep).roundToInt().coerceAtLeast(1)
    val grid = Bitmap.createScaledBitmap(source, columns, rows, true)
    source.recycle()

    val printed = Bitmap.createBitmap(colour.width, colour.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(printed)
    canvas.drawColor(palette.paper.toArgb())
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(colour, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    for (row in 0 until rows) {
        val y = (row + 0.5f) * rowStep
        val shift = if (row % 2 == 1) 0.5f else 0f
        for (column in 0..columns) {
            val x = (column + 0.25f + shift) * pitch
            val pixel = grid.getPixel(column.coerceAtMost(columns - 1), row)
            val r = Plate.radius(Plate.luminance((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF), palette.isDark) * pitch
            if (r > 0.15f) canvas.drawCircle(x, y, r, ink)
        }
    }
    grid.recycle()
    colour.recycle()
    return printed
}

// ---------- words ----------

fun plural(count: Int, word: String) = "$count $word${if (count == 1) "" else "s"}"

fun timeAgo(instant: Instant, now: Instant = Instant.now()): String {
    val seconds = Duration.between(instant, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3600}h ago"
        seconds < 86_400 * 7 -> "${seconds / 86_400}d ago"
        else -> "${seconds / (86_400 * 7)}w ago"
    }
}

fun clock(instant: Instant): String =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(instant.atZone(ZoneId.systemDefault()))

fun dayLabel(instant: Instant): String =
    DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
        .format(instant.atZone(ZoneId.systemDefault()))
        .uppercase()
