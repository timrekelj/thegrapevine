package si.timrekelj.thegrapevine.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
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
import si.timrekelj.thegrapevine.unreadSince

private sealed class Entry(val key: String, val createdAt: Instant) {
    class FromSource(val source: Source) : Entry("s${source.id}", source.created_at)
    class FromMessage(val message: Message) : Entry("m${message.id}", message.created_at)
}

/** The reaction picker, in order; existing reactions sort by it too. */
private val EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "👏", "🚀", "👀", "🎉")

/** Material's outlined "add reaction" glyph, drawn here to avoid the extended icon set. */
private val AddReactionIcon: ImageVector by lazy {
    ImageVector.Builder("AddReaction", 24.dp, 24.dp, 24f, 24f).addPath(
        PathParser().parsePathString(
            "M7,9.5C7,8.67 7.67,8 8.5,8S10,8.67 10,9.5S9.33,11 8.5,11S7,10.33 7,9.5zM12,17.5c2.33,0 4.31,-1.46 5.11,-3.5H6.89" +
                "C7.69,16.04 9.67,17.5 12,17.5zM15.5,11c0.83,0 1.5,-0.67 1.5,-1.5S16.33,8 15.5,8S14,8.67 14,9.5S14.67,11 15.5,11z" +
                "M22,1h-2v2h-2v2h2v2h2V5h2V3h-2V1zM20,12c0,4.42 -3.58,8 -8,8s-8,-3.58 -8,-8s3.58,-8 8,-8c1.46,0 2.82,0.4 4,1.08V2.84" +
                "C14.77,2.3 13.42,2 11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12c0,-1.05 -0.17,-2.05 -0.47,-3" +
                "h-2.13C19.78,9.93 20,10.94 20,12z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

/** Mirrors `StoryView.swift`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen(storyId: String, app: AppState, onBack: () -> Unit, backLabel: String = "Back to the front page") {
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
    var showMore by remember { mutableStateOf(false) }
    val userId = app.session?.userId.orEmpty()

    // shortcut: poll while this story is visible; use Realtime subscriptions for larger groups.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isSending by rememberUpdatedState(sending)
    // One visit per opening (not per rotation): its first read marks where "new since" starts.
    var visitStarted by rememberSaveable(storyId) { mutableStateOf(false) }
    LaunchedEffect(storyId) {
        if (!visitStarted) {
            visitStarted = true
            store.beginVisit(storyId)
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                store.load(storyId)
                while (true) {
                    delay(10_000)
                    if (!isSending && store.pendingMessageId == null) store.load(storyId)
                    // Messages seen live shouldn't show as new next time.
                    store.markRead(storyId, onlyIfChanged = true)
                }
            } finally {
                // Leaving the story, or the app going to the background.
                store.markRead(storyId)
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

    var catchUpExpanded by rememberSaveable(storyId) { mutableStateOf(true) }
    val summaryPreferences = remember(context) { context.getSharedPreferences("summary-versions", android.content.Context.MODE_PRIVATE) }
    val summaryKey = "${app.session?.userId}.$storyId"
    LaunchedEffect(store.story?.summary) {
        store.story?.summary?.let { catchUpExpanded = summaryPreferences.getString(summaryKey, null) != it }
    }
    val locked = sending || store.pendingMessageId != null

    // Sources and messages interleaved by time, grouped by day; rebuilt only when either list changes.
    val timeline = remember(store.sources, store.messages) {
        (store.sources.map { Entry.FromSource(it) } + store.messages.map { Entry.FromMessage(it) })
            .sortedBy { it.createdAt }
            .groupBy { it.createdAt.atZone(ZoneId.systemDefault()).toLocalDate() }
            .values.toList()
    }
    val unread = remember(store.messages, store.lastRead, userId) { unreadSince(store.messages, store.lastRead, userId) }

    Scaffold(
        containerColor = Press.paper,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel, tint = Press.ink) }
                },
                actions = {
                    // Story links open the app on phones that have it, and the website everywhere else.
                    IconButton(onClick = {
                        val link = "${Config.webURL}/story/$storyId"
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                        context.startActivity(Intent.createChooser(send, "Share story"))
                    }) { Icon(Icons.Outlined.Share, contentDescription = "Share story", tint = Press.ink) }
                    Box {
                        IconButton(onClick = { showMore = true }, enabled = store.story != null) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Press.ink)
                        }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }, shape = Corner, containerColor = Press.raised) {
                            DropdownMenuItem(text = { Text("Report the title or category", style = BodyStyle.copy(fontSize = 15.sp)) }, onClick = {
                                showMore = false
                                reportTarget = "category"
                            })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Press.paper, scrolledContainerColor = Press.paper),
            )
        },
        bottomBar = {
            Column(Modifier.background(Press.paper).navigationBarsPadding().imePadding()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Insert(store.error) { error ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(error, style = MetaStyle.copy(color = Press.danger), modifier = Modifier.weight(1f))
                            ReportAction(error, "Story")
                        }
                    }
                    Insert(attachments.size.takeIf { it > 0 }) { count ->
                        ComposerNote(plural(count, "attachment"), "Remove", enabled = !locked) { attachments = emptyList() }
                    }
                    Insert(replyTo) { parent ->
                        ComposerNote(
                            "Replying to ${store.profiles[parent.author_id]?.display_name ?: "…"}: ${parent.body ?: "attachment"}",
                            "Cancel",
                            enabled = !locked,
                        ) { replyTo = null }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .pressable(enabled = !locked && !loadingAttachments, give = Give.Chip, onClickLabel = "Attach images or video") {
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                }
                                .size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Reading the picked files off the disk: dots where the plus was.
                            if (loadingAttachments) {
                                InkDots(dotSize = 4.dp, contentDescription = "Reading attachments")
                            } else {
                                Icon(Icons.Filled.Add, contentDescription = "Attach images or video", tint = Press.ink)
                            }
                        }
                        PaperField(draft, { draft = it }, "Tell the others", modifier = Modifier.weight(1f), singleLine = false, pill = true)
                        val canSend = !sending && !loadingAttachments && (draft.isNotBlank() || attachments.isNotEmpty())
                        SendButton(enabled = canSend, busy = sending) {
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
                item(key = "head") {
                    Column(Modifier.animateItem().padding(horizontal = 20.dp).padding(top = 8.dp)) {
                        Text(
                            "${story.category ?: "Unfiled"} · updated ${clock(store.history.firstOrNull()?.created_at ?: story.last_activity_at)}",
                            style = LabelStyle.copy(color = Press.ink3),
                        )
                        Text(story.title.ifEmpty { "Reading this story" }, style = display(34), modifier = Modifier.padding(top = 10.dp, bottom = 20.dp))
                        // The paper's own writing sits on a dark card so it never reads as a friend's message.
                        val catchText = Color(0xFFEEF3EA)
                        val catchMeta = catchText.copy(alpha = 0.72f)
                        Column(Modifier.fillMaxWidth().background(Press.catchUp, CardCorner).padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth().pressable(give = Give.Type, onClickLabel = if (catchUpExpanded) "Collapse catch-up" else "Expand catch-up") {
                                catchUpExpanded = !catchUpExpanded
                                summaryPreferences.edit().apply { if (!catchUpExpanded) putString(summaryKey, story.summary) else remove(summaryKey) }.apply()
                            }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("The catch-up", style = NameStyle.copy(color = catchText), modifier = Modifier.weight(1f))
                                Icon(
                                    if (catchUpExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = catchMeta,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }
                            Text(
                                story.summary.ifEmpty { "The summary could not be written." },
                                style = BodyStyle.copy(color = catchText),
                                maxLines = if (catchUpExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            if (catchUpExpanded) {
                                Box(Modifier.padding(end = 8.dp).fillMaxWidth().height(1.dp).background(catchText.copy(alpha = 0.14f)))
                                Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("$readable of ${plural(store.sources.size, "source")}", style = MetaStyle.copy(fontSize = 14.sp, color = catchMeta), modifier = Modifier.weight(1f))
                                    CatchUpAction("What changed") { showHistory = true }
                                    CatchUpAction("Report") { reportTarget = "summary" }
                                }
                            }
                        }
                    }
                }
                if (store.sources.isNotEmpty()) item(key = "sources") {
                    Column(Modifier.animateItem().padding(top = 24.dp)) {
                        Text("Sources · ${store.sources.size}", style = LabelStyle, modifier = Modifier.padding(horizontal = 20.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 10.dp),
                        ) { items(store.sources, key = { it.id }) { SourceGrape(it, store.lastRead) } }
                    }
                }
                item(key = "conversation") { SectionHead("The conversation", Modifier.animateItem().padding(horizontal = 20.dp).padding(top = 28.dp)) }

                if (timeline.isEmpty()) item(key = "empty") { Text("Start the conversation.", style = BodyStyle.copy(color = Press.ink2), modifier = Modifier.animateItem().padding(20.dp)) }
                timeline.forEach { entries ->
                    item(key = "day-${entries.first().key}") { Box(Modifier.animateItem()) { DayDivider(dayLabel(entries.first().createdAt)) } }
                    // New messages and sources are set in: they fade in while the rest of the column makes room.
                    items(entries, key = { it.key }) { entry ->
                        Box(Modifier.animateItem()) {
                            when (entry) {
                                is Entry.FromSource -> SourceEntry(entry.source, store, userId, onOpen = { uriHandler.openWebLink(it) })
                                is Entry.FromMessage -> Column {
                                    val lastRead = store.lastRead
                                    if (unread != null && lastRead != null && entry.message.id == unread.firstMessageId) {
                                        NewSinceDivider("${unread.count} new since ${clock(lastRead)}")
                                    }
                                    MessageEntry(
                                        message = entry.message,
                                        store = store,
                                        userId = userId,
                                        replyEnabled = store.pendingMessageId == null,
                                        onReply = { replyTo = it },
                                        onReact = { emoji -> scope.launch { store.toggleReaction(entry.message.id, emoji) } },
                                    )
                                }
                            }
                        }
                    }
                }
                store.notice?.let { notice -> item(key = "notice") { MetaRow(notice, Modifier.animateItem().padding(horizontal = 20.dp, vertical = 12.dp)) } }
                item(key = "foot") { Spacer(Modifier.height(24.dp)) }
            } else if (store.error == null) {
                // Before the story first comes back: a placeholder of its layout, which the story fades in over.
                item(key = "skeleton") { StorySkeleton(Modifier.animateItem()) }
            } else {
                store.error?.let { error ->
                    item {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("This story could not be loaded.", style = display(28))
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
    Row(Modifier.fillMaxWidth().background(Press.raised2, FieldCorner).padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MetaStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        TextAction(action, enabled = enabled, onClick = onAction)
    }
}

/** The send key: an ink circle with an up arrow; three dots while the message goes out. */
@Composable
private fun SendButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val live = enabled || busy
    Box(
        Modifier
            .pressable(enabled = enabled && !busy, haptic = true, role = Role.Button, onClickLabel = "Send", onClick = onClick)
            .semantics { contentDescription = if (busy) "Sending…" else "Send" }
            .size(48.dp).background(if (live) Press.ink else Press.ink.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) InkDots(color = Press.paper, dotSize = 5.dp, contentDescription = null)
        else Icon(ArrowUpGlyph, contentDescription = null, tint = Press.paper)
    }
}

/** An underlined action on the dark catch-up card. */
@Composable
private fun CatchUpAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MetaStyle.copy(fontSize = 14.sp, color = Color(0xFFEEF3EA), fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
        modifier = Modifier.pressable(give = Give.Type, role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
    )
}

@Composable
private fun DayDivider(label: String) {
    Text(
        label,
        style = LabelStyle.copy(color = Press.ink3, textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 26.dp, bottom = 4.dp),
    )
}

/** Where this visit's unread messages start: vine green, the colour of "new to you". */
@Composable
private fun NewSinceDivider(label: String) {
    Row(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Press.red))
        Text(
            label,
            style = LabelStyle.copy(color = Press.red),
            modifier = Modifier.padding(horizontal = 10.dp).background(Press.vineSoft, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(Press.red))
    }
}

/** A source as its grape in the filer's hue, with the publisher under it; opens the original. */
@Composable
private fun SourceGrape(source: Source, seenAt: Instant? = null) {
    val uri = LocalUriHandler.current
    val new = seenAt == null || source.created_at > seenAt
    val paywalled = source.extraction_status == "paywalled"
    Column(
        Modifier.widthIn(min = 72.dp, max = 132.dp)
            .pressable(give = Give.Chip, onClickLabel = "Open ${source.publisher}") { uri.openWebLink(source.url) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Grape(source.submitted_by ?: source.id, new, 44.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (paywalled) Icon(Icons.Outlined.Lock, contentDescription = "Headline only", tint = Press.ink3, modifier = Modifier.size(14.dp))
            Text(
                if (new) "${source.publisher} · new" else source.publisher,
                style = MetaStyle.copy(color = if (new) Press.red else Press.ink2, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
            EntryHead(if (author?.id == userId) "You" else author?.display_name ?: "A member", "filed ${source.publisher}", source.created_at)
            source.submitter_comment?.takeIf { it.isNotEmpty() }?.let { Text(it, style = BodyStyle) }
            val opens = source.url.startsWith("http://") || source.url.startsWith("https://")
            Row(
                Modifier.padding(top = 4.dp).fillMaxWidth()
                    .pressable(enabled = opens, give = Give.Story, onClickLabel = "Open ${source.publisher}") { onOpen(source.url) }
                    .card(Corner).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (source.image_url != null) PhotoView(source.image_url, source.publisher, Modifier.size(64.dp), shape = RoundedCornerShape(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(source.publisher + (source.author?.let { " · $it" } ?: ""), style = MetaStyle.copy(color = Press.ink3, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(source.source_title.ifEmpty { source.domain }, style = NameStyle.copy(fontSize = 16.sp, lineHeight = 21.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    when (source.extraction_status) {
                        "paywalled" -> "Paywalled, headline only. The summary does not use this source."
                        "failed" -> "Couldn't read this page"
                        "deleted" -> "Post deleted or unavailable"
                        else -> null
                    }?.let { note -> MetaRow(note, Modifier.padding(top = 4.dp)) }
                }
                if (opens) Icon(OpenInNewGlyph, contentDescription = null, tint = Press.ink3, modifier = Modifier.size(20.dp))
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
    var picking by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val reactions = message.reactions.orEmpty()
    // Only reactions someone used, in picker order; unknown emoji keep their first-seen order after.
    val used = reactions.map { it.emoji }.distinct()
        .sortedBy { EMOJIS.indexOf(it).takeIf { index -> index >= 0 } ?: EMOJIS.size }
    val react = { emoji: String ->
        val adding = reactions.none { it.emoji == emoji && it.user_id == userId }
        haptics.performHapticFeedback(if (adding) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        onReact(emoji)
    }
    Row(
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    picking = true
                })
            }
            .semantics { onLongClick("Add reaction") { picking = true; true } }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(message.author_id, 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EntryHead(if (message.author_id == userId) "You" else author?.display_name ?: "A member", null, message.created_at)
            message.reply_to?.let { parentId -> store.messages.firstOrNull { it.id == parentId } }?.let { parent ->
                Row(
                    Modifier.height(IntrinsicSize.Min).clip(FieldCorner).background(Press.raised2).padding(end = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(Press.ink3))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Press.ink)) {
                                append(store.profiles[parent.author_id]?.display_name ?: "…")
                            }
                            append("  ")
                            append(parent.body ?: "Attachment")
                        },
                        style = BodyStyle.copy(fontSize = 15.sp, lineHeight = 20.sp, color = Press.ink2),
                        modifier = Modifier.padding(vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            message.body?.takeIf { it.isNotEmpty() }?.let { Text(it, style = BodyStyle) }
            message.message_media.orEmpty().sortedBy { it.position }.forEach { MessageMedia(it) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    items(used, key = { it }) { emoji ->
                        ReactionChip(
                            emoji = emoji,
                            count = reactions.count { it.emoji == emoji },
                            mine = reactions.any { it.emoji == emoji && it.user_id == userId },
                            modifier = Modifier.animateItem(),
                            onClick = { react(emoji) },
                        )
                    }
                    item(key = "add") {
                        Box(Modifier.animateItem()) {
                            Icon(
                                AddReactionIcon,
                                contentDescription = "Add reaction",
                                tint = Press.ink3,
                                modifier = Modifier
                                    .pressable(give = Give.Chip, onClickLabel = "Add reaction") { picking = true }
                                    .clip(CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                    .size(18.dp),
                            )
                            DropdownMenu(
                                expanded = picking,
                                onDismissRequest = { picking = false },
                                shape = Corner,
                                containerColor = Press.raised,
                            ) {
                                ReactionPicker(mine = reactions.filter { it.user_id == userId }.map { it.emoji }.toSet()) { emoji ->
                                    picking = false
                                    react(emoji)
                                }
                            }
                        }
                    }
                }
                TextAction("Reply", enabled = replyEnabled) { onReply(message) }
            }
        }
    }
}

/**
 * A reaction's count, in a pill. Pressing gives; a change pops it once, and the count rolls up or
 * down.
 */
@Composable
private fun ReactionChip(emoji: String, count: Int, mine: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val reduced = rememberReducedMotion()
    val pop = remember { Animatable(1f) }
    val settled = remember { booleanArrayOf(false) }
    LaunchedEffect(mine, count) {
        // Not on first showing; only when it changes under the reader.
        if (!settled[0]) { settled[0] = true; return@LaunchedEffect }
        if (reduced) return@LaunchedEffect
        pop.animateTo(1.18f, tween(90, easing = LinearOutSlowInEasing))
        pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMedium))
    }
    val fill by animateColorAsState(if (mine) Press.ink else Press.raised2, tween(180), label = "reaction fill")
    val type by animateColorAsState(if (mine) Press.paper else Press.ink, tween(180), label = "reaction type")
    val edge by animateColorAsState(if (mine) Press.ink else Press.rule, tween(180), label = "reaction edge")
    Row(
        modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .pressable(give = Give.Chip, onClickLabel = if (mine) "Remove $emoji" else "React $emoji", onClick = onClick)
            .background(fill, CircleShape)
            .border(BorderStroke(1.dp, edge), CircleShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MetaStyle.copy(fontSize = 13.sp, color = type)
        Text("$emoji ", style = style)
        AnimatedContent(
            count,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically { if (up) it else -it } + fadeIn(tween(160)))
                    .togetherWith(slideOutVertically { if (up) -it else it } + fadeOut(tween(120)))
                    .using(SizeTransform(clip = true))
            },
            label = "reaction count",
        ) { Text("$it", style = style) }
    }
}

/** The full set of reactions, in a menu. */
@Composable
private fun ReactionPicker(mine: Set<String>, onPick: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EMOJIS.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { emoji ->
                    val selected = emoji in mine
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (selected) Press.tint else Color.Transparent)
                            .pressable(give = Give.Chip, onClickLabel = if (selected) "Remove $emoji" else "React $emoji") { onPick(emoji) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(emoji, fontSize = 22.sp)
                    }
                }
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
        Row(
            Modifier.pressable(enabled = url != null, give = Give.Story, role = Role.Button) { url?.let(uriHandler::openWebLink) }
                .card(Corner).padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Press.ink)
            Text("Play video", style = NameStyle)
        }
    } else {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).semantics { contentDescription = media.alt_text ?: "Shared image" }) {
            PhotoView(url, media.alt_text ?: "Shared image", Modifier.matchParentSize())
            if (media.kind == "gif") {
                Text(
                    "GIF",
                    style = MetaStyle.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, color = Color.White),
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(store: StoryState, initialTarget: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(initialTarget) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Press.raised, shape = SheetCorner, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Report a correction", style = display(26))
            Text("What is wrong?", style = LabelStyle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("summary" to "Summary", "title" to "Title", "category" to "Category").forEach { (value, label) ->
                    val selected = target == value
                    Text(
                        label,
                        style = MetaStyle.copy(color = if (selected) Press.paper else Press.ink, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.pressable(give = Give.Chip, role = Role.RadioButton) { target = value }
                            .heightIn(min = 40.dp)
                            .background(animateColorAsState(if (selected) Press.ink else Press.raised2, label = "target").value, FieldCorner)
                            .border(1.dp, if (selected) Press.ink else Press.rule, FieldCorner)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            PaperField(note, { note = it }, "What should it say?", label = "Note (optional)", singleLine = false)
            InkButton("Report", busy = busy, busyLabel = "Reporting…") {
                scope.launch {
                    busy = true
                    val success = store.report(target, note)
                    busy = false
                    if (success) onDismiss()
                }
            }
            Insert(store.error) { Text(it, style = MetaStyle.copy(color = Press.danger)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(history: List<StoryHistoryEntry>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Press.raised, shape = SheetCorner) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            item {
                Text("What changed", style = display(26), modifier = Modifier.padding(bottom = 4.dp))
            }
            if (history.isEmpty()) item { Text("No previous summaries yet.", style = BodyStyle, modifier = Modifier.padding(vertical = 12.dp)) }
            items(history, key = { it.id }) { version ->
                Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaRow("${dayLabel(version.created_at)} · ${clock(version.created_at)}")
                    Text(version.title, style = display(19))
                    Text(version.summary, style = BodyStyle.copy(fontSize = 15.sp, lineHeight = 22.sp, color = Press.ink2))
                }
                Hairline()
            }
        }
    }
}
