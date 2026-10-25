package si.timrekelj.thegrapevine.ui

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.PixelCopy
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import si.timrekelj.thegrapevine.ApiError
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.Feedback
import si.timrekelj.thegrapevine.FeedbackDraft
import si.timrekelj.thegrapevine.FeedbackImage
import si.timrekelj.thegrapevine.FeedbackKind
import si.timrekelj.thegrapevine.ShakeDetector
import kotlin.coroutines.resume

/**
 * Opens the report sheet for an error shown on [screen]. Provided at the root while someone is
 * signed in; null elsewhere (the share activity), where errors have no "Report" action.
 */
val LocalReporter = staticCompositionLocalOf<((error: String, screen: String) -> Unit)?> { null }

/** "Report" beside an error: files it as a bug with the message attached. [before] runs first, e.g. to close a sheet. */
@Composable
fun ReportAction(error: String, screen: String, modifier: Modifier = Modifier, before: () -> Unit = {}) {
    val report = LocalReporter.current ?: return
    TextAction("Report", modifier.minimumInteractiveComponentSize(), onClickLabel = "Report this problem") {
        before()
        report(error, screen)
    }
}

/**
 * The single report sheet, and shake-to-report, for the whole app. A shake prints the window
 * first, so the screenshot shows what was on screen rather than the sheet.
 */
@Composable
fun FeedbackHost(app: AppState) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var capturing by remember { mutableStateOf(false) }
    val signedIn = app.checked && app.session != null
    ShakeListener(enabled = signedIn && app.feedback == null && !capturing) {
        val window = activity?.window ?: return@ShakeListener
        capturing = true
        scope.launch {
            val shot = screenshot(window)?.let { withContext(Dispatchers.Default) { runCatching { Feedback.image(it) }.getOrNull() } }
            capturing = false
            app.report(FeedbackKind.Bug, "Shake", screenshot = shot)
        }
    }
    app.feedback?.let { FeedbackSheet(app, it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackSheet(app: AppState, draft: FeedbackDraft) {
    val context = LocalContext.current
    val links = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var pickError by remember { mutableStateOf<String?>(null) }

    fun add(uris: List<Uri>) {
        val room = Feedback.MAX_IMAGES - draft.images.size
        if (uris.isEmpty() || room <= 0) return
        scope.launch {
            adding = true
            pickError = null
            try {
                draft.images += withContext(Dispatchers.IO) { uris.take(room).map { readImage(context, it) } }
                if (uris.size > room) pickError = "Attach up to ${Feedback.MAX_IMAGES} images; the rest were left out."
            } catch (e: Exception) {
                pickError = if (e is ApiError) e.message else "Could not read the selected image."
            }
            adding = false
        }
    }

    // The multiple picker needs room for two or more; the last free place uses the single one.
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(Feedback.MAX_IMAGES)) { add(it) }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { add(listOf(it)) } }
    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    ModalBottomSheet(
        onDismissRequest = app::closeReport,
        // A report on its way can't be swiped away half-sent.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !draft.busy }),
        containerColor = Press.raised,
        shape = SheetCorner,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Report a problem or idea", style = display(26))
            Insert(draft.result) { result ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        result.issue_number?.let { "Thanks, filed as #$it." } ?: "Thanks, we have it.",
                        style = SectionStyle,
                    )
                    result.issue_url?.let { url -> TextAction("View it on GitHub", onClickLabel = "Open the issue") { links.openWebLink(url) } }
                    InkButton("Done", onClick = app::closeReport)
                }
            }
            Insert(if (draft.result == null) draft else null) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("What kind?", style = LabelStyle)
                    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeedbackKind.entries.forEach { kind ->
                            val selected = draft.kind == kind
                            Box(
                                Modifier.pressable(enabled = !draft.busy, give = Give.Chip, role = Role.RadioButton) { draft.kind = kind }
                                    .semantics { this.selected = selected }
                                    .heightIn(min = 44.dp)
                                    .background(animateColorAsState(if (selected) Press.ink else Press.raised2, label = "kind").value, CircleShape)
                                    .border(1.dp, if (selected) Press.ink else Press.rule, CircleShape)
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(kind.label, style = MetaStyle.copy(color = if (selected) Press.paper else Press.ink, fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                    PaperField(
                        draft.description,
                        { draft.description = it },
                        draft.kind.placeholder,
                        label = "What happened?",
                        modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp),
                        singleLine = false,
                        enabled = !draft.busy,
                        isError = draft.description.length > Feedback.MAX_DESCRIPTION,
                    )
                    if (draft.description.length > Feedback.MAX_DESCRIPTION - 200) {
                        Text("${draft.description.length} / ${Feedback.MAX_DESCRIPTION} characters", style = MetaStyle)
                    }
                    draft.error?.let { error ->
                        Column(
                            Modifier.fillMaxWidth().clip(Corner).background(Press.raised2).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("Error shown, sent with the report", style = LabelStyle)
                            Text(error, style = MetaStyle.copy(color = Press.ink2))
                        }
                    }
                    Text("Screenshots (up to ${Feedback.MAX_IMAGES})", style = LabelStyle)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        draft.images.forEachIndexed { index, image ->
                            Thumbnail(image, index + 1, enabled = !draft.busy) { draft.images.remove(image) }
                        }
                        if (draft.images.size < Feedback.MAX_IMAGES) {
                            Box(
                                Modifier.size(THUMB).pressable(enabled = !draft.busy && !adding, give = Give.Chip, role = Role.Button) {
                                    if (Feedback.MAX_IMAGES - draft.images.size > 1) pickMany.launch(imagesOnly) else pickOne.launch(imagesOnly)
                                }.clip(Corner).background(Press.raised2),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (adding) InkDots(dotSize = 5.dp, contentDescription = "Adding images")
                                else Icon(Icons.Filled.Add, contentDescription = "Add images", tint = Press.ink)
                            }
                        }
                    }
                    Insert(pickError) { Text(it, style = MetaStyle.copy(color = Press.danger)) }
                    Text(
                        "Reports are posted publicly on GitHub so we can track them. Leave out anything private.",
                        style = MetaStyle.copy(color = Press.ink3),
                    )
                    Insert(draft.failure) { Text(it, style = MetaStyle.copy(color = Press.danger)) }
                    InkButton("Send report", enabled = draft.canSend && !adding, busy = draft.busy, busyLabel = "Sending…", onClick = app::sendReport)
                }
            }
        }
    }
}

private val THUMB = 76.dp

@Composable
private fun Thumbnail(image: FeedbackImage, number: Int, enabled: Boolean, onRemove: () -> Unit) {
    val bitmap = remember(image.id) { image.thumbnail.asImageBitmap() }
    Box(Modifier.size(THUMB)) {
        Image(
            bitmap,
            contentDescription = "Image $number",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(THUMB).clip(Corner),
        )
        // The mark is small; the place to press it is a full 44dp in the corner.
        Box(
            Modifier.align(Alignment.TopEnd).size(44.dp)
                .pressable(enabled = enabled, give = Give.Chip, role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(Modifier.padding(4.dp).size(22.dp).background(Press.ink, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = "Remove image $number", tint = Press.paper, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Listens to the accelerometer only while the app is in front and [enabled]. */
@Composable
private fun ShakeListener(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onShake)
    if (!enabled) return
    LifecycleResumeEffect(context) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val detector = ShakeDetector()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (detector.onSample(event.values[0], event.values[1], event.values[2], event.timestamp / 1_000_000)) latest()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (accelerometer != null) sensors.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose { sensors?.unregisterListener(listener) }
    }
}

/** What the window shows right now, or null if it can't be copied. */
private suspend fun screenshot(window: Window): Bitmap? {
    val view = window.decorView
    if (view.width <= 0 || view.height <= 0) return null
    val bitmap = createBitmap(view.width, view.height)
    return suspendCancellableCoroutine { continuation ->
        runCatching {
            PixelCopy.request(window, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) continuation.resume(bitmap)
                else {
                    bitmap.recycle()
                    continuation.resume(null)
                }
            }, Handler(Looper.getMainLooper()))
        }.onFailure {
            bitmap.recycle()
            continuation.resume(null)
        }
    }
}

/** A picked image, read with a ceiling so a huge file can't exhaust memory, then set as a JPEG. */
private fun readImage(context: Context, uri: Uri): FeedbackImage {
    val resolver = context.contentResolver
    val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }
    if (size != null && size > MAX_PICKED_BYTES) throw ApiError.Network("Images must be no larger than 40 MB.")
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw ApiError.Network("Could not read the selected image.")
    return Feedback.image(bytes)
}

private const val MAX_PICKED_BYTES = 40L * 1024 * 1024
