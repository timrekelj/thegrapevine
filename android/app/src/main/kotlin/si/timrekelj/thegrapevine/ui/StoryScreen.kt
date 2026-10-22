package si.timrekelj.thegrapevine.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.timrekelj.thegrapevine.ApiError
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.Attachment
import si.timrekelj.thegrapevine.Config
import si.timrekelj.thegrapevine.Media
import si.timrekelj.thegrapevine.Message
import si.timrekelj.thegrapevine.Source
import si.timrekelj.thegrapevine.StoryHistoryEntry
import si.timrekelj.thegrapevine.StoryState
import java.time.Instant
import java.time.ZoneId

private sealed class Entry(val key: String, val createdAt: Instant) {
    class FromSource(val source: Source) : Entry("s${source.id}", source.created_at)
    class FromMessage(val message: Message) : Entry("m${message.id}", message.created_at)
}

private val EMOJIS = listOf("🚀", "👏", "😂", "❤️", "🔥", "👀")

/** Mirrors `StoryView.swift`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen(storyId: String, app: AppState, onBack: () -> Unit) {
    val api = LocalApi.current
    val store: StoryState = viewModel(key = "story-$storyId") { StoryState(api) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var draft by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    var sending by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var loadingAttachments by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    val userId = app.session?.userId.orEmpty()

    // shortcut: poll while this story is visible; use Realtime subscriptions for larger groups.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isSending by rememberUpdatedState(sending)
    LaunchedEffect(storyId) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            store.load(storyId)
            while (true) {
                delay(10_000)
                if (!isSending && store.pendingMessageId == null) store.load(storyId)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(Attachment.MAX_IMAGES)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            loadingAttachments = true
            try {
                val loaded = withContext(Dispatchers.IO) { uris.map { readAttachment(context, it) } }
                Attachment.validate(loaded)
                attachments = loaded
                store.error = null
            } catch (e: Exception) {
                store.error = e.message
            }
            loadingAttachments = false
        }
    }

    val locked = sending || store.pendingMessageId != null

    Scaffold(
        containerColor = Press.paper,
        topBar = {
            Column(Modifier.background(Press.paper).statusBarsPadding().padding(horizontal = 20.dp).padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("← Front page", style = MetaStyle.copy(color = Press.ink), modifier = Modifier.weight(1f).clickable(onClick = onBack).padding(vertical = 8.dp))
                    Text("The Grapevine", style = nameplate(24))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        // Story links open the app on phones that have it, and the website everywhere else.
                        TextAction("Share") {
                            val link = "${Config.webURL}/story/$storyId"
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                            context.startActivity(Intent.createChooser(send, "Share story"))
                        }
                    }
                }
                DoubleRule(Modifier.padding(top = 4.dp))
            }
        },
        bottomBar = {
            Column(Modifier.background(Press.paper).navigationBarsPadding().imePadding()) {
                Hairline(color = Press.ink)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    store.error?.let { Text(it, style = MetaStyle.copy(color = Press.danger)) }
                    if (attachments.isNotEmpty()) {
                        ComposerNote(plural(attachments.size, "attachment"), "Remove", enabled = !locked) { attachments = emptyList() }
                    }
                    replyTo?.let { parent ->
                        ComposerNote(
                            "Replying to ${store.profiles[parent.author_id]?.display_name ?: "…"}: ${parent.body ?: "attachment"}",
                            "Cancel",
                            enabled = !locked,
                        ) { replyTo = null }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(48.dp).border(1.dp, Press.rule, Corner)
                                .clickable(enabled = !locked && !loadingAttachments) {
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Add, contentDescription = "Attach images or video", tint = Press.ink) }
                        PaperField(draft, { draft = it }, "Write to the table…", modifier = Modifier.weight(1f), singleLine = false)
                        val canSend = !sending && !loadingAttachments && (draft.isNotBlank() || attachments.isNotEmpty())
                        InkButton(if (sending) "Sending…" else "Send", enabled = canSend, modifier = Modifier, height = 48.dp) {
                            scope.launch {
                                sending = true
                                if (store.postMessage(draft, replyTo?.id, attachments)) {
                                    draft = ""
                                    replyTo = null
                                    attachments = emptyList()
                                }
                                sending = false
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        val story = store.story
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (story != null) {
                val readable = store.sources.count { it.extraction_status == "ok" }
                val lead = store.sources.firstOrNull { it.image_url != null }
                item {
                    Column(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text((story.category ?: "Unfiled").uppercase(), style = LabelStyle)
                            Text("Updated ${clock(store.history.firstOrNull()?.created_at ?: story.last_activity_at)}", style = MetaStyle.copy(color = Press.ink3))
                        }
                        Text(story.title.ifEmpty { "Reading this story" }, style = display(40), modifier = Modifier.padding(top = 12.dp, bottom = 16.dp))
                        Hairline(color = Press.ink)
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                buildAnnotatedString {
                                    append("Filed from ")
                                    withStyle(SpanStyle(color = Press.ink, fontWeight = FontWeight.SemiBold)) { append("$readable") }
                                    append(" of ")
                                    withStyle(SpanStyle(color = Press.ink, fontWeight = FontWeight.SemiBold)) { append("${store.sources.size}") }
                                    append(if (store.sources.size == 1) " source · " else " sources · ")
                                },
                                style = MetaStyle,
                            )
                            TextAction("What changed") { showHistory = true }
                        }
                        Hairline()
                        PlateView(lead?.image_url, store.sources.firstOrNull()?.publisher ?: "The paper", Modifier.padding(top = 20.dp).fillMaxWidth().aspectRatio(16f / 9f))
                        lead?.let { MetaRow("Pictured via ${it.publisher}", Modifier.padding(top = 7.dp).alpha(0.8f)) }
                        if (story.summary.isNotEmpty()) {
                            DropCapSummary(story.summary, Modifier.padding(top = 18.dp, bottom = 12.dp))
                        } else {
                            Text("The summary could not be written.", style = BodyStyle, modifier = Modifier.padding(top = 18.dp, bottom = 12.dp))
                            GhostButton("Retry summary") { reportTarget = "summary" }
                        }
                        Hairline()
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Something off?", style = MetaStyle.copy(color = Press.ink3))
                            TextAction("Report the summary") { reportTarget = "summary" }
                            TextAction("Wrong category") { reportTarget = "category" }
                        }
                    }
                }
                if (store.sources.isNotEmpty()) item {
                    Column(Modifier.padding(top = 20.dp)) {
                        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
                            Text("CLIPPINGS", style = LabelStyle, modifier = Modifier.weight(1f))
                            MetaRow("${store.sources.size}")
                        }
                        Hairline(Modifier.padding(horizontal = 20.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 12.dp),
                        ) { items(store.sources, key = { it.id }) { SourceClip(it) } }
                    }
                }
                item { SectionHead("The conversation", Modifier.padding(horizontal = 20.dp).padding(top = 36.dp)) }

                val timeline = (store.sources.map { Entry.FromSource(it) } + store.messages.map { Entry.FromMessage(it) })
                    .sortedBy { it.createdAt }
                if (timeline.isEmpty()) item { Text("Start the conversation.", style = BodyStyle.copy(color = Press.ink2), modifier = Modifier.padding(20.dp)) }
                timeline.groupBy { it.createdAt.atZone(ZoneId.systemDefault()).toLocalDate() }.forEach { (_, entries) ->
                    item(key = "day-${entries.first().key}") { DayDivider(dayLabel(entries.first().createdAt)) }
                    items(entries, key = { it.key }) { entry ->
                        when (entry) {
                            is Entry.FromSource -> SourceEntry(entry.source, store, userId, onOpen = { uriHandler.openUri(it) })
                            is Entry.FromMessage -> MessageEntry(
                                message = entry.message,
                                store = store,
                                userId = userId,
                                replyEnabled = store.pendingMessageId == null,
                                onReply = { replyTo = it },
                                onReact = { emoji -> scope.launch { store.toggleReaction(entry.message, emoji) } },
                            )
                        }
                    }
                }
                store.notice?.let { notice -> item { MetaRow(notice, Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) } }
                item { Spacer(Modifier.height(24.dp)) }
            } else if (store.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Press.ink, strokeWidth = 2.dp)
                    }
                }
            } else {
                store.error?.let { error ->
                    item {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("STOP THE PRESSES", style = LabelStyle)
                            Text("This story could not be loaded.", style = display(30))
                            Text(error, style = BodyStyle.copy(color = Press.ink2))
                            InkButton("Try again", modifier = Modifier) { scope.launch { store.load(storyId) } }
                        }
                    }
                }
            }
        }
    }

    reportTarget?.let { target ->
        ReportSheet(store, initialTarget = target, onDismiss = { reportTarget = null })
    }
    if (showHistory) HistorySheet(store.history, onDismiss = { showHistory = false })
}

private fun readAttachment(context: Context, uri: Uri): Attachment {
    val resolver = context.contentResolver
    val type = resolver.getType(uri) ?: "application/octet-stream"
    val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }
    if (size != null && size > Attachment.MAX_BYTES) throw ApiError.Network("Attachments must be no larger than 20 MB.")
    val data = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw ApiError.Network("Could not read the selected photo or video.")
    return Attachment(
        data = data,
        kind = if (type.startsWith("video/")) "video" else "image",
        contentType = type,
        fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type) ?: "bin",
    )
}

@Composable
private fun ComposerNote(text: String, action: String, enabled: Boolean, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Press.tint).padding(horizontal = 10.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MetaStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        TextAction(action, enabled = enabled, onClick = onAction)
    }
}

@Composable
private fun DayDivider(label: String) {
    Row(Modifier.padding(horizontal = 20.dp).padding(top = 26.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Hairline(Modifier.weight(1f))
        Text(label, style = LabelStyle.copy(color = Press.ink3), modifier = Modifier.padding(horizontal = 12.dp))
        Hairline(Modifier.weight(1f))
    }
}

@Composable
private fun SourceClip(source: Source) {
    Row(
        Modifier.width(250.dp).clipping(Press.ink3, Press.raised).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlateView(source.image_url, source.publisher, Modifier.size(60.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(source.publisher, style = MetaStyle.copy(color = Press.ink, fontWeight = FontWeight.SemiBold), maxLines = 1)
            Text(source.source_title.ifEmpty { source.domain }, style = display(15), maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (source.extraction_status == "paywalled") MetaRow("Headline only")
        }
    }
}

@Composable
private fun EntryHead(name: String, action: String?, at: java.time.Instant) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, style = NameStyle)
        action?.let { Text(it, style = MetaStyle.copy(color = Press.ink3)) }
        Text(clock(at), style = MetaStyle.copy(color = Press.ink3))
    }
}

@Composable
private fun SourceEntry(source: Source, store: StoryState, userId: String, onOpen: (String) -> Unit) {
    val author = source.submitted_by?.let { store.profiles[it] }
    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(source.submitted_by ?: source.id, 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EntryHead(if (author?.id == userId) "You" else author?.display_name ?: "A member", "filed a source", source.created_at)
            source.submitter_comment?.takeIf { it.isNotEmpty() }?.let { Text(it, style = BodyStyle) }
            Row(
                Modifier.fillMaxWidth().clipping(Press.ink3, Press.raised).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlateView(source.image_url, source.publisher, Modifier.size(72.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text((source.publisher + (source.author?.let { " · $it" } ?: "")).uppercase(), style = LabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(source.source_title.ifEmpty { source.domain }, style = display(18))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetaRow(clock(source.created_at), Modifier.weight(1f))
                        if (source.url.startsWith("http://") || source.url.startsWith("https://")) {
                            TextAction("Open ↗") { onOpen(source.url) }
                        }
                    }
                    when (source.extraction_status) {
                        "paywalled" -> "Paywalled — headline only. The summary does not use this source."
                        "failed" -> "Couldn't read this page"
                        "deleted" -> "Post deleted or unavailable"
                        else -> null
                    }?.let { note ->
                        Hairline()
                        MetaRow(note)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageEntry(
    message: Message,
    store: StoryState,
    userId: String,
    replyEnabled: Boolean,
    onReply: (Message) -> Unit,
    onReact: (String) -> Unit,
) {
    val author = store.profiles[message.author_id]
    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(message.author_id, 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EntryHead(if (message.author_id == userId) "You" else author?.display_name ?: "A member", null, message.created_at)
            message.reply_to?.let { parentId -> store.messages.firstOrNull { it.id == parentId } }?.let { parent ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(Press.ink))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontStyle = FontStyle.Normal, fontWeight = FontWeight.SemiBold, color = Press.ink)) {
                                append(store.profiles[parent.author_id]?.display_name ?: "…")
                            }
                            append("  ")
                            append(parent.body ?: "Attachment")
                        },
                        style = BodyStyle.copy(fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp, color = Press.ink2),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            message.body?.takeIf { it.isNotEmpty() }?.let { Text(it, style = BodyStyle) }
            message.message_media.orEmpty().sortedBy { it.position }.forEach { MessageMedia(it) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(EMOJIS) { emoji ->
                        val count = message.reactions.orEmpty().count { it.emoji == emoji }
                        val mine = message.reactions.orEmpty().any { it.emoji == emoji && it.user_id == userId }
                        Text(
                            if (count > 0) "$emoji $count" else emoji,
                            style = MetaStyle.copy(fontSize = 13.sp, color = if (mine) Press.paper else Press.ink),
                            modifier = Modifier
                                .alpha(if (count > 0 || mine) 1f else 0.45f)
                                .background(if (mine) Press.ink else Press.paper, CircleShape)
                                .border(BorderStroke(1.dp, if (mine) Press.ink else if (count > 0) Press.rule else androidx.compose.ui.graphics.Color.Transparent), CircleShape)
                                .clickable { onReact(emoji) }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        )
                    }
                }
                TextAction("↩ Reply", enabled = replyEnabled) { onReply(message) }
            }
        }
    }
}

@Composable
private fun MessageMedia(media: Media) {
    val api = LocalApi.current
    val uriHandler = LocalUriHandler.current
    val url by produceState<String?>(null, media.url) { value = runCatching { api.mediaURL(media.url) }.getOrNull() }
    if (media.kind == "video") {
        Text(
            "▶  Play video",
            style = MetaStyle.copy(color = Press.ink),
            modifier = Modifier.clipping(Press.ink3, Press.raised).clickable(enabled = url != null) { url?.let(uriHandler::openUri) }.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    } else {
        PlateView(url, media.alt_text ?: "Shared image", Modifier.fillMaxWidth().aspectRatio(4f / 3f), screen = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(store: StoryState, initialTarget: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(initialTarget) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Press.paper, shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Report a correction", style = display(28))
            DoubleRule()
            Text("WHAT IS WRONG?", style = LabelStyle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("summary" to "Summary", "title" to "Title", "category" to "Category").forEach { (value, label) ->
                    val selected = target == value
                    Text(
                        label,
                        style = MetaStyle.copy(color = if (selected) Press.paper else Press.ink, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.background(if (selected) Press.ink else Press.paper, Corner)
                            .border(1.dp, if (selected) Press.ink else Press.rule, Corner)
                            .clickable { target = value }.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
            Text("NOTE (OPTIONAL)", style = LabelStyle)
            PaperField(note, { note = it }, "…", singleLine = false)
            InkButton(if (busy) "Reporting…" else "Report", enabled = !busy) {
                scope.launch {
                    busy = true
                    val success = store.report(target, note)
                    busy = false
                    if (success) onDismiss()
                }
            }
            store.error?.let { Text(it, style = MetaStyle.copy(color = Press.danger)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(history: List<StoryHistoryEntry>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Press.paper, shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            item {
                Text("What changed", style = display(28))
                DoubleRule(Modifier.padding(top = 12.dp, bottom = 4.dp))
            }
            if (history.isEmpty()) item { Text("No previous summaries yet.", style = BodyStyle, modifier = Modifier.padding(vertical = 12.dp)) }
            items(history, key = { it.id }) { version ->
                Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaRow("${dayLabel(version.created_at)} · ${clock(version.created_at)}")
                    Text(version.title, style = display(21))
                    Text(version.summary, style = BodyStyle.copy(fontSize = 15.sp, lineHeight = 22.sp, color = Press.ink2))
                }
                Hairline()
            }
        }
    }
}
