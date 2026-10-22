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
        containerColor = Grape.paper,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Grape.ink)
                    }
                },
                actions = {
                    // Story links open the app on phones that have it, and the website everywhere else.
                    IconButton(onClick = {
                        val link = "${Config.webURL}/story/$storyId"
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                        context.startActivity(Intent.createChooser(send, "Share story"))
                    }) { Icon(Icons.Filled.Share, contentDescription = "Share story", tint = Grape.ink) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Grape.paper),
            )
        },
        bottomBar = {
            Surface(color = Grape.raised) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    store.error?.let { Text(it, fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                    if (attachments.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MetaRow(plural(attachments.size, "attachment"), Modifier.padding(start = 8.dp))
                            TextButton(onClick = { attachments = emptyList() }, enabled = !locked) { Text("Remove", color = Grape.ink) }
                        }
                    }
                    replyTo?.let { parent ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                "Replying to ${store.profiles[parent.author_id]?.display_name ?: "…"}: ${parent.body ?: "attachment"}",
                                fontSize = 13.sp,
                                color = Grape.ink2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { replyTo = null }, enabled = !locked) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel reply", tint = Grape.ink2)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            },
                            enabled = !locked && !loadingAttachments,
                        ) { Icon(Icons.Filled.Add, contentDescription = "Attach images or video", tint = Grape.ink) }
                        PaperField(draft, { draft = it }, "Message", modifier = Modifier.weight(1f), singleLine = false)
                        IconButton(
                            onClick = {
                                scope.launch {
                                    sending = true
                                    if (store.postMessage(draft, replyTo?.id, attachments)) {
                                        draft = ""
                                        replyTo = null
                                        attachments = emptyList()
                                    }
                                    sending = false
                                }
                            },
                            enabled = !sending && !loadingAttachments && (draft.isNotBlank() || attachments.isNotEmpty()),
                        ) {
                            if (sending) CircularProgressIndicator(color = Grape.ink, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message", tint = Grape.ink)
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
                item {
                    PlateView(store.sources.firstOrNull { it.image_url != null }?.image_url, store.sources.firstOrNull()?.publisher ?: "The paper", 180.dp, Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp))
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text((story.category ?: "News").uppercase(), style = LabelStyle, modifier = Modifier.weight(1f))
                            MetaRow("Updated ${timeAgo(story.last_activity_at)}")
                        }
                        Text(story.title, style = display(30))
                        Text(story.summary, style = BodyStyle.copy(color = Grape.ink2, lineHeight = 25.sp))
                        MetaRow(
                            "Summary from $readable of ${store.sources.size} sources, newest first · What changed",
                            Modifier.clickable { showHistory = true },
                        )
                    }
                }
                item {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) { items(store.sources, key = { it.id }) { SourceClip(it) } }
                }
                item {
                    Row(
                        Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { reportTarget = "summary" }) { Text("Report summary", fontSize = 13.sp, color = Grape.ink, fontWeight = FontWeight.SemiBold) }
                        TextButton(onClick = { reportTarget = "category" }) { Text("Wrong category", fontSize = 13.sp, color = Grape.ink, fontWeight = FontWeight.SemiBold) }
                    }
                    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(0.5.dp).background(Grape.rule))
                    Text("Around the table", style = display(24), modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp))
                }

                val timeline = (store.sources.map { Entry.FromSource(it) } + store.messages.map { Entry.FromMessage(it) })
                    .sortedBy { it.createdAt }
                timeline.groupBy { it.createdAt.atZone(ZoneId.systemDefault()).toLocalDate() }.forEach { (_, entries) ->
                    item(key = "day-${entries.first().key}") {
                        Text(
                            dayLabel(entries.first().createdAt),
                            style = LabelStyle,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(entries, key = { it.key }) { entry ->
                        when (entry) {
                            is Entry.FromSource -> SourceEntry(entry.source, store, onOpen = { uriHandler.openUri(it) })
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
                        CircularProgressIndicator(color = Grape.ink)
                    }
                }
            } else {
                store.error?.let { error ->
                    item {
                        Column(Modifier.padding(20.dp)) {
                            Text(error, style = BodyStyle)
                            TextButton(onClick = { scope.launch { store.load(storyId) } }) { Text("Try again", color = Grape.ink) }
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
private fun SourceClip(source: Source) {
    Column(
        Modifier.width(150.dp).background(Grape.raised, RoundedCornerShape(12.dp)).border(0.5.dp, Grape.rule, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PlateView(source.image_url, source.publisher, 64.dp, Modifier.width(64.dp))
        MetaRow(source.publisher)
        Text(source.source_title.ifEmpty { source.domain }, style = display(13), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SourceEntry(source: Source, store: StoryState, onOpen: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            source.submitted_by?.let { store.profiles[it] }?.let { profile ->
                Monogram(profile, 28.dp)
                Text(profile.display_name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Grape.ink)
            }
            Spacer(Modifier.weight(1f))
            MetaRow(timeAgo(source.created_at))
        }
        source.submitter_comment?.takeIf { it.isNotEmpty() }?.let { Text(it, style = BodyStyle) }
        Column(
            Modifier.fillMaxWidth()
                .background(Grape.raised, RoundedCornerShape(12.dp))
                .border(0.5.dp, Grape.rule, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MetaRow(source.publisher + (source.author?.let { " · $it" } ?: ""))
            Text(source.source_title.ifEmpty { source.domain }, style = display(17))
            when (source.extraction_status) {
                "paywalled" -> MetaRow("Paywalled — headline only")
                "failed" -> MetaRow("Couldn't read this page")
                "deleted" -> MetaRow("Post deleted or unavailable")
            }
            if (source.url.startsWith("http://") || source.url.startsWith("https://")) {
                MetaRow("Open ↗", Modifier.clickable { onOpen(source.url) })
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
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            store.profiles[message.author_id]?.let { author ->
                Monogram(author, 28.dp)
                Text(if (author.id == userId) "You" else author.display_name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Grape.ink)
            }
            Spacer(Modifier.weight(1f))
            MetaRow(timeAgo(message.created_at))
        }
        message.reply_to?.let { parentId -> store.messages.firstOrNull { it.id == parentId } }?.let { parent ->
            Row(Modifier.padding(start = 8.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(Grape.rule))
                Column {
                    Text(store.profiles[parent.author_id]?.display_name ?: "…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Grape.ink)
                    Text(parent.body ?: "Attachment", fontSize = 13.sp, color = Grape.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        message.body?.takeIf { it.isNotEmpty() }?.let { Text(it, style = BodyStyle) }
        message.message_media.orEmpty().sortedBy { it.position }.forEach { MessageMedia(it) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(EMOJIS) { emoji ->
                    val count = message.reactions.orEmpty().count { it.emoji == emoji }
                    val mine = message.reactions.orEmpty().any { it.emoji == emoji && it.user_id == userId }
                    Text(
                        if (count > 0) "$emoji $count" else emoji,
                        fontSize = 13.sp,
                        color = if (mine) Grape.paper else Grape.ink,
                        modifier = Modifier
                            .background(if (mine) Grape.ink else Grape.paper, CircleShape)
                            .border(BorderStroke(0.5.dp, if (mine) Grape.ink else Grape.rule), CircleShape)
                            .clickable { onReact(emoji) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            TextButton(onClick = { onReply(message) }, enabled = replyEnabled) {
                Text("↩ Reply", fontSize = 13.sp, color = Grape.ink2, fontWeight = FontWeight.SemiBold)
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
        MetaRow("▶ Play video", Modifier.clickable(enabled = url != null) { url?.let(uriHandler::openUri) }.padding(vertical = 6.dp))
    } else {
        PlateView(url, media.alt_text ?: "Shared image", 220.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(store: StoryState, initialTarget: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(initialTarget) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Grape.paper, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Report a correction", style = display(24))
            Text("WHAT IS WRONG?", style = LabelStyle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("summary" to "Summary", "title" to "Title", "category" to "Category").forEach { (value, label) ->
                    FilterChip(
                        selected = target == value,
                        onClick = { target = value },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Grape.ink,
                            selectedLabelColor = Grape.paper,
                            labelColor = Grape.ink,
                        ),
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
            store.error?.let { Text(it, fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(history: List<StoryHistoryEntry>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Grape.paper) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            item { Text("What changed", style = display(24), modifier = Modifier.padding(bottom = 12.dp)) }
            if (history.isEmpty()) item { MetaRow("No earlier versions yet.") }
            items(history, key = { it.id }) { version ->
                Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaRow("${dayLabel(version.created_at)} · ${timeAgo(version.created_at)}")
                    Text(version.title, style = display(17))
                    Text(version.summary, fontSize = 15.sp, color = Grape.ink2)
                }
                HorizontalDivider(color = Grape.rule, thickness = 0.5.dp)
            }
        }
    }
}
