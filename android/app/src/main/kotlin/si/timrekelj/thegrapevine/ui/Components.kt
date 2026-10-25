package si.timrekelj.thegrapevine.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import si.timrekelj.thegrapevine.Api
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

val LocalMemberNames = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

val LocalApi = staticCompositionLocalOf<Api> { error("No Api provided") }

/** Thumbnails and clip cards. */
val Corner = RoundedCornerShape(14.dp)

/** Fields and chips. */
val FieldCorner = RoundedCornerShape(10.dp)

/** The lead card and the catch-up card. */
val CardCorner = RoundedCornerShape(28.dp)

/** Bottom sheets: the top corners of [CardCorner]. */
val SheetCorner = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

// ---------- people ----------

/** A member's disc: their initial on their own hue (see [MemberDisc]), named from [LocalMemberNames]. */
@Composable
fun Avatar(seed: String, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    MemberDisc(seed, LocalMemberNames.current[seed].orEmpty(), size, modifier)
}

/** Overlapping discs with a ring of the ground between them. */
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

/** The vine-green pill that means "new to you", like "3 new" on the lead photo. */
@Composable
fun NewBadge(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Press.red, CircleShape).padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(Press.onRed, CircleShape))
        Text(text, style = MetaStyle.copy(color = Press.onRed, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun MetaRow(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MetaStyle, modifier = modifier)
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Press.rule) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** A section title in the display face, like "The conversation". */
@Composable
fun SectionHead(title: String, modifier: Modifier = Modifier) {
    Text(title, style = SectionStyle, modifier = modifier.fillMaxWidth())
}

/**
 * A raised object on the ground (a clip card, the lead card): the raised fill with a soft pillow
 * shadow in light mode. Dark mode lifts by the lighter fill alone.
 */
fun Modifier.card(shape: Shape = Corner): Modifier = composed {
    val palette = LocalPalette.current
    val lifted = if (palette.isDark) this else shadow(10.dp, shape, ambientColor = palette.ink.copy(alpha = 0.04f), spotColor = palette.ink.copy(alpha = 0.16f))
    lifted.clip(shape).background(palette.raised)
}

// ---------- buttons & fields ----------

/**
 * The main action: an ink capsule. It sinks a little when pressed and taps the haptic engine.
 * While [busy] the label stays in place but unseen, so the capsule keeps its size, and three dots
 * pulse over it; [busyLabel] ("Sending…") is what TalkBack reads meanwhile.
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
            .heightIn(min = height).clip(CircleShape)
            .background(if (live) fill else fill.copy(alpha = 0.4f))
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Busy(busy, onFill) {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = onFill, maxLines = 1)
        }
    }
}

/** The secondary action: a tonal capsule. Busy the same way as [InkButton], in ink. */
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
            .heightIn(min = 48.dp).clip(CircleShape).background(Press.tint)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Busy(busy, Press.ink) {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (live) Press.ink else Press.ink3, textAlign = TextAlign.Center)
        }
    }
}

/** The label crossfades to [InkDots] while busy; the label still sets the size underneath. */
@Composable
private fun Busy(busy: Boolean, ink: Color, label: @Composable () -> Unit) {
    val shown by animateFloatAsState(if (busy) 0f else 1f, tween(180), label = "busy")
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.graphicsLayer { alpha = shown }.then(if (busy) Modifier.clearAndSetSemantics {} else Modifier)) { label() }
        if (shown < 1f) InkDots(Modifier.graphicsLayer { alpha = 1f - shown }, color = ink, contentDescription = null)
    }
}

/** An underlined text action, like "What changed". It fades a little when pressed. */
@Composable
fun TextAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClickLabel: String? = null, onClick: () -> Unit) {
    Text(
        text,
        style = MetaStyle.copy(
            color = if (enabled) Press.ink else Press.ink3,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
        ),
        modifier = modifier.pressable(enabled = enabled, give = Give.Type, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
    )
}

/**
 * A Material filled text field on the raised-2 fill. With a [label] the label floats inside the
 * field ("Link", "Add a line (optional)"); [pill] makes the composer's capsule without an indicator.
 */
@Composable
fun PaperField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    label: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    pill: Boolean = false,
) {
    TextField(
        value = value,
        enabled = enabled,
        onValueChange = onValueChange,
        label = label?.let { { Text(it) } },
        placeholder = { Text(placeholder, style = BodyStyle.copy(color = Press.ink3)) },
        singleLine = singleLine,
        isError = isError,
        textStyle = BodyStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = if (pill) CircleShape else RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Press.raised2, unfocusedContainerColor = Press.raised2,
            disabledContainerColor = Press.raised2, errorContainerColor = Press.raised2,
            focusedIndicatorColor = if (pill) Color.Transparent else Press.ink,
            unfocusedIndicatorColor = if (pill) Color.Transparent else Press.ink3,
            disabledIndicatorColor = if (pill) Color.Transparent else Press.rule,
            errorIndicatorColor = Press.danger,
            focusedLabelColor = Press.ink2, unfocusedLabelColor = Press.ink3, errorLabelColor = Press.danger,
            cursorColor = Press.ink, errorCursorColor = Press.danger,
            focusedTextColor = Press.ink, unfocusedTextColor = Press.ink, errorTextColor = Press.ink,
            disabledTextColor = Press.ink3,
        ),
        modifier = modifier,
    )
}

// ---------- photographs ----------

/**
 * A source's own preview photo, shown clean and cropped to fill the frame, or (without a usable
 * picture) a plain tile with the publisher's name. [shape] is the frame: 14 for thumbnails and
 * clips, none when a card already clips it.
 */
@Composable
fun PhotoView(url: String?, label: String, modifier: Modifier = Modifier, shape: Shape = Corner) {
    val api = LocalApi.current
    BoxWithConstraints(modifier.clip(shape).background(Press.tint), contentAlignment = Alignment.Center) {
        // Decoded at the pixel size it is shown, so a thumbnail costs a thumbnail's memory.
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        val key = "$url|${width}x$height"
        val image by produceState(url?.let { Pictures.cached(key)?.asImageBitmap() }, key) {
            if (url == null || value != null || width <= 0) return@produceState
            Pictures.load(key) { Pictures.bytes(url) { api.fetchBytes(url) }?.let { decodeToBox(it, width, height) } }
                ?.let { value = it.asImageBitmap() }
        }
        val bitmap: ImageBitmap? = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            PublisherTile(label, Modifier.matchParentSize(), shape)
        }
    }
}

/** A source with no picture: the publisher's name in the display face on a raised-2 tile. */
@Composable
fun PublisherTile(label: String, modifier: Modifier = Modifier, shape: Shape = Corner) {
    BoxWithConstraints(modifier.clip(shape).background(Press.raised2), contentAlignment = Alignment.Center) {
        val fontSize = (maxWidth.value * 0.16f).coerceIn(11f, 18f)
        Text(
            label,
            style = display(18).copy(color = Press.ink2, fontSize = fontSize.sp, lineHeight = (fontSize * 1.12f).sp, textAlign = TextAlign.Center),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/**
 * Decoded pictures shared by every photo on screen. Each picture is fetched and decoded once at a
 * time however many views ask for it; the work runs off the main thread.
 */
private object Pictures {
    private val decoded = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Recent downloads, so one photo shown at two sizes is fetched once. */
    private val downloads = object : LruCache<String, ByteArray>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    // Only touched on the main thread (see share).
    private val inFlight = HashMap<String, Deferred<Any?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun cached(key: String): Bitmap? = decoded.get(key)

    suspend fun load(key: String, make: suspend () -> Bitmap?): Bitmap? =
        decoded.get(key) ?: share(key) { make()?.also { decoded.put(key, it) } } as Bitmap?

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
 * pixels. A `height` of 0 keeps the photo's own proportions.
 */
private fun decodeToBox(bytes: ByteArray, width: Int, height: Int): Bitmap? {
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
    val boxed = createBitmap(width, boxHeight)
    Canvas(boxed).drawBitmap(
        decoded,
        Rect(left, top, left + cropWidth, top + cropHeight),
        Rect(0, 0, width, boxHeight),
        Paint(Paint.FILTER_BITMAP_FLAG),
    )
    decoded.recycle()
    return boxed
}

// ---------- glyphs ----------

/** Material Symbols outlines that the core icon set lacks, drawn from their 24-unit paths. */
private fun glyph(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()

/** "link", on the Share a link button. */
val LinkGlyph: ImageVector by lazy {
    glyph(
        "Link",
        "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1z" +
            "M8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z",
    )
}

/** "open_in_new", on a clip that opens its source. */
val OpenInNewGlyph: ImageVector by lazy {
    glyph(
        "OpenInNew",
        "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z",
    )
}

/** "arrow_upward", on the composer's send button. */
val ArrowUpGlyph: ImageVector by lazy {
    glyph("ArrowUpward", "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8 -8,8z")
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
