package si.timrekelj.thegrapevine.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import si.timrekelj.thegrapevine.Api
import si.timrekelj.thegrapevine.Plate
import si.timrekelj.thegrapevine.Profile
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val LocalApi = staticCompositionLocalOf<Api> { error("No Api provided") }

@Composable
fun Monogram(profile: Profile, size: Dp = 32.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Grape.ink),
        contentAlignment = Alignment.Center,
    ) {
        Text(profile.monogram, style = display((size.value * 0.47f).toInt()).copy(color = Grape.paper))
    }
}

@Composable
fun Stamp(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Grape.onPink,
        modifier = Modifier
            .background(Grape.pink, RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun MetaRow(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MetaStyle, modifier = modifier)
}

@Composable
fun InkButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Grape.ink,
            contentColor = Grape.paper,
            disabledContainerColor = Grape.ink.copy(alpha = 0.35f),
            disabledContentColor = Grape.paper,
        ),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
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
) {
    OutlinedTextField(
        value = value,
        enabled = enabled,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Grape.ink2) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Grape.raised,
            unfocusedContainerColor = Grape.raised,
            focusedBorderColor = Grape.ink,
            unfocusedBorderColor = Grape.rule,
            cursorColor = Grape.ink,
            focusedTextColor = Grape.ink,
            unfocusedTextColor = Grape.ink,
        ),
        modifier = modifier,
    )
}

/** The halftone plate: a source image printed in one ink, or a flat plate with the publisher's name. */
@Composable
fun PlateView(url: String?, label: String, height: Dp, modifier: Modifier = Modifier.fillMaxWidth()) {
    val api = LocalApi.current
    val image by produceState(url?.let { PlateCache.get(it)?.asImageBitmap() }, url) {
        if (url == null || value != null) return@produceState
        val printed = runCatching { withContext(Dispatchers.Default) { printInOneInk(api.fetchBytes(url)) } }.getOrNull()
        if (printed != null) {
            PlateCache.put(url, printed)
            value = printed.asImageBitmap()
        }
    }
    Box(
        modifier.height(height).clip(RoundedCornerShape(10.dp)).background(Grape.ink.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap: ImageBitmap? = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Text(label, style = display(13), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp))
        }
    }
}

private object PlateCache : LruCache<String, Bitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

private const val MAX_PLATE_WIDTH = 1080

private fun printInOneInk(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= MAX_PLATE_WIDTH) sample *= 2
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val printed = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(Plate.channels)) }
    Canvas(printed).drawBitmap(source, 0f, 0f, paint)
    source.recycle()
    return printed
}

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

fun dayLabel(instant: Instant): String =
    DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
        .format(instant.atZone(ZoneId.systemDefault()))
        .uppercase()
