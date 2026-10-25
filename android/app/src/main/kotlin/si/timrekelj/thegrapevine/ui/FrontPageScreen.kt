package si.timrekelj.thegrapevine.ui

import si.timrekelj.thegrapevine.Profile
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.Member
import si.timrekelj.thegrapevine.PaperState
import si.timrekelj.thegrapevine.StoryRow
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrontPageScreen(app: AppState, paper: PaperState, onOpen: (String) -> Unit, onEarlier: (Instant) -> Unit, onPeople: () -> Unit = {}, onAccount: () -> Unit = {}) {
    var sharing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(app.group?.id) { paper.load(app) }
    PaperScreenContent(
        groupName = app.group?.name ?: "The Grapevine", members = app.members, me = app.profile,
        stories = paper.stories, seenAt = paper.seenAt, loading = paper.isLoading, error = paper.error, earlier = paper.earlier,
        onOpen = onOpen, onEarlier = onEarlier, onPeople = onPeople, onAccount = onAccount,
        onShare = { sharing = true }, onRefresh = { paper.load(app) },
    )
    if (sharing) ShareSheet(app, onDismiss = { sharing = false; paper.load(app) })
}

/** Stateless rendering shared by the live paper and development visual checks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaperScreenContent(
    groupName: String, members: List<Member>, stories: List<StoryRow>, seenAt: Instant?, loading: Boolean, error: String?, earlier: Instant?,
    me: Profile? = null,
    onOpen: (String) -> Unit, onEarlier: (Instant) -> Unit, onPeople: () -> Unit, onAccount: () -> Unit, onShare: () -> Unit, onRefresh: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = Press.paper,
        topBar = { TopAppBar(
            title = { Text(groupName, style = display(24), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            actions = { IconButton(onClick = onAccount, modifier = Modifier.semantics { contentDescription = "Your account" }) {
                if (me != null) MemberDisc(me.id, me.display_name, 32.dp) else Icon(Icons.Default.AccountCircle, contentDescription = null)
            } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Press.paper),
            windowInsets = WindowInsets(0),
        ) },
        floatingActionButton = { ExtendedFloatingActionButton(
            onClick = { onShare() }, icon = { Icon(LinkGlyph, contentDescription = null) }, text = { Text("Share a link") },
            containerColor = Press.ink, contentColor = Press.paper, shape = RoundedCornerShape(28.dp),
        ) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        PullToRefreshBox(isRefreshing = loading && stories.isNotEmpty(), onRefresh = { onRefresh() }, modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
                            val before = earlier
                            val dateStyle = NameStyle.copy(fontSize = 17.sp)
                            if (before != null) {
                                Text("$date  ›", style = dateStyle, modifier = Modifier.pressable(give = Give.Type, onClickLabel = "Earlier editions") { onEarlier(before) }.padding(vertical = 2.dp))
                            } else {
                                Text(date, style = dateStyle)
                            }
                            val fresh = stories.sumOf { story -> story.sources.count { isNew(it.created_at, seenAt) } + story.messages.count { isNew(it.created_at, seenAt) } }
                            val since = seenAt?.let { " since ${clock(it)}" }.orEmpty()
                            Text(
                                if (fresh > 0) "$fresh new$since" else "Nothing new$since",
                                style = MetaStyle.copy(fontSize = 15.sp, color = if (fresh > 0) Press.red else Press.ink3, fontWeight = FontWeight.SemiBold),
                            )
                        }
                        Box(Modifier.pressable(give = Give.Type, onClickLabel = "People and invitations", onClick = onPeople).padding(vertical = 10.dp)) {
                            AvatarStack(members.map { it.user_id }.take(5), 26.dp)
                        }
                    }
                }
                when {
                    error != null -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(error, style = BodyStyle)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                TextAction("Try again") { onRefresh() }
                                ReportAction(error, "Front page")
                            }
                        }
                    }
                    loading && stories.isEmpty() -> item { FrontPageSkeleton() }
                    stories.isEmpty() -> item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            SourceBunch(emptyList(), size = 80.dp)
                            Text("Share the first link to start the paper.", style = display(34).copy(textAlign = TextAlign.Center))
                            InkButton("Share a link", onClick = { onShare() })
                        }
                    }
                    else -> {
                        items(stories, key = { it.id }) { story ->
                            val lead = story.id == stories.first().id
                            if (lead) LeadStory(story, members, seenAt, Modifier.animateItem().pressable(give = Give.Story, onClickLabel = "Open story") { onOpen(story.id) })
                            else CompactStory(story, seenAt, Modifier.animateItem().pressable(give = Give.Story, onClickLabel = "Open story") { onOpen(story.id) })
                        }
                        earlier?.let { before ->
                            item {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 8.dp).pressable(give = Give.Story, role = androidx.compose.ui.semantics.Role.Button) { onEarlier(before) }
                                        .heightIn(min = 56.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Earlier editions", style = NameStyle.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
                                    Text("›", style = NameStyle.copy(fontSize = 17.sp, color = Press.ink3))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isNew(at: Instant, seenAt: Instant?) = seenAt == null || at > seenAt
private fun StoryRow.imageUrl() = sources.firstOrNull { it.image_url != null }?.image_url

@Composable
internal fun LeadStory(story: StoryRow, members: List<Member>, seenAt: Instant?, modifier: Modifier) {
    val freshSources = story.sources.count { isNew(it.created_at, seenAt) }
    val fresh = freshSources + story.messages.count { isNew(it.created_at, seenAt) }
    Column(modifier.fillMaxWidth().padding(bottom = 8.dp).card(CardCorner)) {
        Box {
            PhotoView(story.imageUrl(), story.sources.firstOrNull()?.publisher ?: "The paper", Modifier.fillMaxWidth().aspectRatio(1.6f), shape = androidx.compose.ui.graphics.RectangleShape)
            if (fresh > 0) NewBadge("$fresh new", Modifier.padding(16.dp))
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${story.category ?: "News"} · ${clock(story.last_activity_at)}", style = LabelStyle.copy(color = Press.ink3))
            Text(story.title.ifEmpty { "A link from the table" }, style = display(34))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SourceBunch(story.sources, seenAt)
                Column {
                    Text(plural(story.sources.size, "source") + if (freshSources > 0) ", $freshSources new" else "", style = BodyStyle.copy(fontSize = 15.sp, color = Press.ink2))
                    Text(plural(story.messages.size, "message"), style = BodyStyle.copy(fontSize = 15.sp, color = Press.ink2))
                }
            }
            story.messages.maxByOrNull { it.created_at }?.let { latest ->
                Hairline()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val author = members.firstOrNull { it.user_id == latest.author_id }?.profiles?.display_name ?: "A member"
                    MemberDisc(latest.author_id, author, 28.dp)
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(author) }
                        append("  ")
                        append(latest.body ?: "Shared an attachment.")
                    }, style = BodyStyle.copy(fontSize = 15.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(clock(latest.created_at), style = MetaStyle)
                }
            }
        }
    }
}

@Composable
internal fun CompactStory(story: StoryRow, seenAt: Instant?, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SourceBunch(story.sources, seenAt, 32.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(story.title.ifEmpty { "A link from the table" }, style = display(19))
                val fresh = story.sources.count { isNew(it.created_at, seenAt) } + story.messages.count { isNew(it.created_at, seenAt) }
                val vine = Press.red
                Text(
                    buildAnnotatedString {
                        if (fresh > 0) {
                            withStyle(SpanStyle(color = vine, fontWeight = FontWeight.SemiBold)) { append("$fresh new") }
                            append(" · ")
                        }
                        append("${story.category ?: "News"} · ${plural(story.sources.size, "source")} · ${plural(story.messages.size, "message")} · ${clock(story.last_activity_at)}")
                    },
                    style = MetaStyle.copy(fontSize = 14.sp, color = Press.ink3),
                )
            }
            PhotoView(story.imageUrl(), story.sources.firstOrNull()?.publisher ?: "The paper", Modifier.size(64.dp))
        }
        Hairline()
    }
}
