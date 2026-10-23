package si.timrekelj.thegrapevine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.createBitmap
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A problem, improvement or feature request sent to `POST functions/v1/submit-feedback`.
 * The function files it as a public GitHub issue: an AI writes the title and summary, and the
 * reporter's words are kept verbatim.
 */
enum class FeedbackKind(val value: String, val label: String, val placeholder: String) {
    Bug("bug", "Bug", "What went wrong, and what did you expect to happen?"),
    Enhancement("enhancement", "Improvement", "What could work better, and how?"),
    Feature("feature", "New feature", "What would you like The Grapevine to do?"),
}

@Serializable
data class FeedbackContext(
    val platform: String = "android",
    val app_version: String,
    val os: String,
    val device: String,
    val screen: String? = null,
    val error: String? = null,
)

@Serializable
data class FeedbackBody(
    val kind: String,
    val description: String,
    val images: List<String>,
    val context: FeedbackContext,
)

@Serializable
data class FeedbackResult(val id: String, val issue_number: Int? = null, val issue_url: String? = null)

/** A picked photo or screenshot, already a JPEG; [uploaded] is its storage path once sent. */
class FeedbackImage(val jpeg: ByteArray, val thumbnail: Bitmap) {
    val id: String = UUID.randomUUID().toString()
    var uploaded: String? = null
}

/**
 * What the report sheet is editing. It lives in [AppState] so the draft, its pictures and a send in
 * progress survive rotation, and stays put after a failed send.
 */
class FeedbackDraft(kind: FeedbackKind, val screen: String?, val error: String?) {
    var kind by mutableStateOf(kind)
    var description by mutableStateOf("")
    val images = mutableStateListOf<FeedbackImage>()
    var busy by mutableStateOf(false)
    var failure by mutableStateOf<String?>(null)
    var result by mutableStateOf<FeedbackResult?>(null)

    val canSend: Boolean
        get() = !busy && result == null && description.trim().length in 1..Feedback.MAX_DESCRIPTION
}

object Feedback {
    const val MAX_IMAGES = 5
    const val MAX_DESCRIPTION = 4000
    const val MAX_SIDE = 2048
    private const val MAX_BYTES = 10 * 1024 * 1024
    private const val QUALITY = 80
    private const val THUMBNAIL_SIDE = 240

    /** Uploads the pictures not yet sent to the `feedback` bucket, then files the report. */
    suspend fun send(api: Api, userId: String, draft: FeedbackDraft): FeedbackResult {
        val owner = userId.lowercase()
        val paths = draft.images.map { image ->
            image.uploaded ?: "$owner/${UUID.randomUUID().toString().lowercase()}.jpg".also { path ->
                api.upload(Attachment(image.jpeg, "image", "image/jpeg", "jpg"), path, bucket = "feedback")
                image.uploaded = path
            }
        }
        return api.postObject(
            "functions/v1/submit-feedback",
            encodeBody(body(draft.kind, draft.description, paths, context(draft.screen, draft.error))),
        )
    }

    fun body(kind: FeedbackKind, description: String, images: List<String>, context: FeedbackContext) =
        FeedbackBody(kind = kind.value, description = description.trim(), images = images, context = context)

    fun context(screen: String?, error: String?) = FeedbackContext(
        app_version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        os = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        device = device(Build.MANUFACTURER, Build.MODEL),
        screen = screen?.takeIf { it.isNotBlank() },
        error = error?.takeIf { it.isNotBlank() },
    )

    /** "Google Pixel 9", not "Google Google Pixel 9" when the model already names the maker. */
    fun device(manufacturer: String, model: String): String {
        val maker = manufacturer.replaceFirstChar { it.uppercase() }
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$maker $model".trim()
    }

    /** The size to print a [width]×[height] picture at so its longest side is at most [maxSide]. */
    fun scaledSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= maxSide) return width to height
        val scale = maxSide.toDouble() / longest
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    /** A picked file: decoded at about the size it is sent at, turned upright, and set as a JPEG. */
    fun image(bytes: ByteArray): FeedbackImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ApiError.Network("Could not read the selected image.")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw ApiError.Network("Could not read the selected image.")
        return image(upright(decoded, orientation(bytes)))
    }

    /** A screenshot or decoded picture, scaled down and set as a JPEG on white (no transparency in JPEG). */
    fun image(bitmap: Bitmap): FeedbackImage {
        val (width, height) = scaledSize(bitmap.width, bitmap.height)
        val flat = createBitmap(width, height)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, null, android.graphics.Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        bitmap.recycle()
        val jpeg = ByteArrayOutputStream().use { out ->
            flat.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
        if (jpeg.size > MAX_BYTES) throw ApiError.Network("Images must be no larger than 10 MB.")
        val (thumbWidth, thumbHeight) = scaledSize(width, height, THUMBNAIL_SIDE)
        val thumbnail = Bitmap.createScaledBitmap(flat, thumbWidth, thumbHeight, true)
        if (thumbnail !== flat) flat.recycle()
        return FeedbackImage(jpeg, thumbnail)
    }

    private fun orientation(bytes: ByteArray): Int = runCatching {
        ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply { postRotate(270f); postScale(-1f, 1f) }
            else -> return bitmap
        }
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (turned !== bitmap) bitmap.recycle()
        return turned
    }
}

/**
 * Tells a shake from a phone being carried, after Square's seismic: over the last half second at
 * least three quarters of the readings must be well above gravity, across at least a quarter
 * second of readings. After a shake it rests for [cooldownMillis].
 */
class ShakeDetector(
    private val threshold: Float = 15f,
    private val windowMillis: Long = 500,
    private val cooldownMillis: Long = 2000,
) {
    private val samples = ArrayDeque<Pair<Long, Boolean>>()
    private var restUntil = Long.MIN_VALUE

    /** One accelerometer reading in m/s²; true when it completes a shake. */
    fun onSample(x: Float, y: Float, z: Float, timeMillis: Long): Boolean {
        if (timeMillis < restUntil) return false
        samples.addLast(timeMillis to (sqrt(x * x + y * y + z * z) > threshold))
        while (samples.isNotEmpty() && samples.first().first < timeMillis - windowMillis) samples.removeFirst()
        val span = timeMillis - samples.first().first
        val accelerating = samples.count { it.second }
        if (samples.size >= 4 && span >= windowMillis / 2 && accelerating * 4 >= samples.size * 3) {
            samples.clear()
            restUntil = timeMillis + cooldownMillis
            return true
        }
        return false
    }
}
