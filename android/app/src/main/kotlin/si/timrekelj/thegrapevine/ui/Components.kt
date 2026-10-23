package si.timrekelj.thegrapevine.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.scale
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.UriHandler
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import si.timrekelj.thegrapevine.Api
import si.timrekelj.thegrapevine.Config
import si.timrekelj.thegrapevine.Plate
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
    val image by produceState(Pictures.cached(url)?.asImageBitmap(), url) {
        if (value != null) return@produceState
        Pictures.load(url) { api.fetchBytes(url).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
            ?.let { value = it.asImageBitmap() }
    }
    Box(modifier.size(size).clip(CircleShape).background(Press.tint)) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
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
        NewDot()
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

/**
 * An ink block; the main action. It sinks like a stamp when pressed and taps the haptic engine.
 * While [busy] the label stays set but unseen, so the block keeps its size, and ink dots in paper
 * colour print over it; [busyLabel] ("Sending…") is what TalkBack reads meanwhile.
 */
@Composable
fun InkButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
    height: Dp = 50.dp,
    busy: Boolean = false,
    busyLabel: String? = null,
    fill: Color = Press.ink,
    onFill: Color = Press.paper,
    onClick: () -> Unit,
) {
    val live = enabled || busy
    Box(
        modifier
            .pressable(enabled = enabled && !busy, haptic = true, role = Role.Button, onClick = onClick)
            .semantics { if (busy) contentDescription = busyLabel ?: text }
            .heightIn(min = height).clip(Corner)
            .background(if (live) fill else fill.copy(alpha = 0.4f))
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Busy(busy, onFill) {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = onFill, maxLines = 1)
        }
    }
}

/** A ruled outline; the secondary action. Busy the same way as [InkButton], in ink. */
@Composable
fun GhostButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
    busy: Boolean = false,
    busyLabel: String? = null,
    onClick: () -> Unit,
) {
    val live = enabled || busy
    Box(
        modifier
            .pressable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .semantics { if (busy) contentDescription = busyLabel ?: text }
            .heightIn(min = 46.dp).clip(Corner).border(1.dp, if (live) Press.rule else Color.Transparent, Corner)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Busy(busy, Press.ink) {
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (live) Press.ink else Press.ink3, textAlign = TextAlign.Center)
        }
    }
}

/** The label crossfades to ink dots while busy; the label still sets the size underneath. */
@Composable
private fun Busy(busy: Boolean, ink: Color, label: @Composable () -> Unit) {
    val shown by animateFloatAsState(if (busy) 0f else 1f, tween(180), label = "busy")
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.graphicsLayer { alpha = shown }.then(if (busy) Modifier.clearAndSetSemantics {} else Modifier)) { label() }
        if (shown < 1f) InkDots(Modifier.graphicsLayer { alpha = 1f - shown }, color = ink, contentDescription = null)
    }
}

/** An underlined text action, like the web's `.story-text-button`. The ink lifts when pressed. */
@Composable
fun TextAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        style = MetaStyle.copy(
            color = if (enabled) Press.ink else Press.ink3,
            fontWeight = FontWeight.SemiBold,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
        ),
        modifier = modifier.pressable(enabled = enabled, give = Give.Type, role = Role.Button, onClick = onClick)
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
 * picture — a type plate with the publisher's name. `screen = false` prints the photo clean.
 */
@Composable
fun PlateView(url: String?, label: String, modifier: Modifier = Modifier, screen: Boolean = true) {
    val api = LocalApi.current
    val palette = LocalPalette.current
    BoxWithConstraints(modifier.clip(RectangleShape).background(palette.tint), contentAlignment = Alignment.Center) {
        // Printed at the pixel size it is shown, so a thumbnail costs a thumbnail's memory.
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        val columns = if (screen) Plate.columns(maxWidth.value) else 0
        val key = "$url|${width}x$height|$columns|${palette.isDark}"
        val image by produceState(url?.let { Pictures.cached(key)?.asImageBitmap() }, key) {
            if (url == null || value != null || width <= 0) return@produceState
            Pictures.load(key) { Pictures.bytes(url) { api.fetchBytes(url) }?.let { print(it, width, height, columns, palette) } }
                ?.let { value = it.asImageBitmap() }
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
    val density = LocalDensity.current
    val screen = remember(dots, density) { with(density) { dotScreen(dots, 4.dp.toPx(), 0.9.dp.toPx()) } }
    BoxWithConstraints(modifier.background(Press.raised).border(1.dp, Press.rule), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().background(screen))
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

/** One dot of the type plate's light screen, tiled across the plate in a single draw. */
private fun dotScreen(color: Color, step: Float, radius: Float): ShaderBrush {
    val size = step.roundToInt().coerceAtLeast(2)
    val tile = ImageBitmap(size, size)
    androidx.compose.ui.graphics.Canvas(tile).drawCircle(
        Offset(size / 2f, size / 2f),
        radius,
        androidx.compose.ui.graphics.Paint().apply { this.color = color; isAntiAlias = true },
    )
    return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

/**
 * Decoded pictures shared by every avatar and plate on screen. Each picture is fetched and printed
 * once at a time however many views ask for it; the work runs off the main thread.
 */
private object Pictures {
    private val printed = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Recent downloads, so one photo shown at two sizes is fetched once. */
    private val downloads = object : LruCache<String, ByteArray>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    // Only touched on the main thread (see share).
    private val inFlight = HashMap<String, Deferred<Any?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun cached(key: String): Bitmap? = printed.get(key)

    suspend fun load(key: String, make: suspend () -> Bitmap?): Bitmap? =
        printed.get(key) ?: share(key) { make()?.also { printed.put(key, it) } } as Bitmap?

    suspend fun bytes(url: String, fetch: suspend () -> ByteArray): ByteArray? =
        downloads.get(url) ?: share("bytes|$url") { fetch().also { downloads.put(url, it) } } as ByteArray?

    private suspend fun share(key: String, work: suspend () -> Any?): Any? = withContext(Dispatchers.Main.immediate) {
        val job = inFlight.getOrPut(key) {
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    withContext(Dispatchers.Default) { runCatching { work() }.getOrNull() }
                } finally {
                    inFlight.remove(key)
                }
            }
        }
        job.await()
    }
}

/**
 * Decodes the photo, crops it to the box the way [ContentScale.Crop] would, and scales it to the box's
 * pixels. With `columns > 0` it is then printed through a dot screen that many dots wide.
 * A `height` of 0 keeps the photo's own proportions.
 */
private fun print(bytes: ByteArray, width: Int, height: Int, columns: Int, palette: Palette): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val boxHeight = if (height > 0) height else (width.toLong() * bounds.outHeight / bounds.outWidth).toInt().coerceAtLeast(1)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= boxHeight) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val scale = maxOf(width.toFloat() / decoded.width, boxHeight.toFloat() / decoded.height)
    val cropWidth = (width / scale).roundToInt().coerceIn(1, decoded.width)
    val cropHeight = (boxHeight / scale).roundToInt().coerceIn(1, decoded.height)
    val left = (decoded.width - cropWidth) / 2
    val top = (decoded.height - cropHeight) / 2
    val source = createBitmap(width, boxHeight)
    Canvas(source).drawBitmap(
        decoded,
        Rect(left, top, left + cropWidth, top + cropHeight),
        Rect(0, 0, width, boxHeight),
        Paint(Paint.FILTER_BITMAP_FLAG),
    )
    decoded.recycle()
    if (columns <= 0) return source

    // The colour plate: a little extra saturation (and light, on black paper) for the ink lost between dots.
    val colour = createBitmap(source.width, source.height)
    val matrix = ColorMatrix().apply {
        setSaturation(Plate.SATURATION)
        if (palette.isDark) postConcat(ColorMatrix().apply { setScale(1.12f, 1.12f, 1.12f, 1f) })
    }
    Canvas(colour).drawBitmap(source, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })

    // Luminance per cell, on a grid whose alternate rows are offset by half a dot.
    val pitch = source.width.toFloat() / columns
    val rowStep = pitch * 0.866f
    val rows = (source.height / rowStep).roundToInt().coerceAtLeast(1)
    val grid = source.scale(columns, rows)
    source.recycle()

    val printed = createBitmap(colour.width, colour.height)
    val canvas = Canvas(printed)
    canvas.drawColor(palette.paper.toArgb())
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(colour, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    for (row in 0 until rows) {
        val y = (row + 0.5f) * rowStep
        val shift = if (row % 2 == 1) 0.5f else 0f
        for (column in 0..columns) {
            val x = (column + 0.25f + shift) * pitch
            val pixel = grid[column.coerceAtMost(columns - 1), row]
            val r = Plate.radius(Plate.luminance((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF), palette.isDark) * pitch
            if (r > 0.15f) canvas.drawCircle(x, y, r, ink)
        }
    }
    grid.recycle()
    colour.recycle()
    return printed
}

// ---------- links ----------

/** Opens a link from a story in the browser: only http(s), and no browser is not a crash. */
fun UriHandler.openWebLink(url: String) {
    if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) return
    runCatching { openUri(url) }
}

/** An error under a field, with the info glyph. */
@Composable
fun ErrorLine(error: String?) {
    if (error == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = Press.danger, modifier = Modifier.size(18.dp))
        Text(error, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = Press.danger)
    }
}

// ---------- words ----------

fun plural(count: Int, word: String) = "$count $word${if (count == 1) "" else "s"}"

fun clock(instant: Instant): String =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(instant.atZone(ZoneId.systemDefault()))

fun dayLabel(instant: Instant): String =
    DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
        .format(instant.atZone(ZoneId.systemDefault()))
        .uppercase()
