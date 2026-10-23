package si.timrekelj.thegrapevine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.Member
import si.timrekelj.thegrapevine.PaperState
import si.timrekelj.thegrapevine.StoryRow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Gutter = 20.dp

/** Mirrors the web's `FrontPage.tsx`: a broadsheet, one column wide. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrontPageScreen(app: AppState, paper: PaperState, onOpen: (String) -> Unit, onEarlier: (Instant) -> Unit) {
    var sharing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(app.group?.id) { paper.load(app) }

    val refreshing = paper.isLoading && paper.stories.isNotEmpty()
    val pull = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { paper.load(app) },
        modifier = Modifier.fillMaxSize().clipToBounds(),
        state = pull,
        indicator = { InkingIndicator(pull, refreshing, Modifier.align(Alignment.TopCenter)) },
    ) {
        val stories = paper.stories
        val seenAt = paper.seenAt
        val lead = stories.firstOrNull()
        val side = remember(stories) { stories.drop(1).take(3) }
        val inside = remember(stories) { stories.drop(4) }
        val letters = remember(stories) {
            stories.flatMap { story -> story.messages.map { it to story } }
                .sortedByDescending { it.first.created_at }.take(4)
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item { Masthead(app, stories, seenAt, onShare = { sharing = true }) }
            val error = paper.error
            when {
                error != null -> item {
                    Column(Modifier.padding(Gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(error, style = BodyStyle)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TextAction("Try again") { paper.load(app) }
                            ReportAction(error, "Front page")
                        }
                    }
                }
                // First load: the page in galley proof; the stories fade in over it as they arrive.
                paper.isLoading && stories.isEmpty() -> item(key = "proof") { FrontPageProof(Modifier.animateItem()) }
                lead != null -> {
                    item(key = lead.id) { LeadStory(lead, app.members, seenAt, Modifier.animateItem().opens { onOpen(lead.id) }) }
                    if (side.isNotEmpty()) item {
                        Box(Modifier.padding(horizontal = Gutter).padding(top = 28.dp).fillMaxWidth().height(3.dp).background(Press.ink))
                    }
                    items(side, key = { it.id }) { story ->
                        SideStory(story, seenAt, pictured = story == side.first(), Modifier.animateItem().opens { onOpen(story.id) })
                    }
                    if (inside.isNotEmpty()) {
                        item { SectionHead("Also in this edition", Modifier.padding(horizontal = Gutter).padding(top = 36.dp, bottom = 6.dp)) }
                        items(inside, key = { it.id }) { story -> InsideStory(story, seenAt, Modifier.animateItem().opens { onOpen(story.id) }) }
                    }
                    if (letters.isNotEmpty()) {
                        item { SectionHead("Letters from the table", Modifier.padding(horizontal = Gutter).padding(top = 36.dp, bottom = 4.dp)) }
                        items(letters, key = { "letter-${it.first.id}" }) { (message, story) ->
                            Letter(message.body, app.members.firstOrNull { it.user_id == message.author_id }?.profiles?.display_name ?: "A member", story.title, message.created_at, Modifier.animateItem().opens { onOpen(story.id) })
                        }
                    }
                    paper.earlier?.let { cursor -> item { EarlierEditions { onEarlier(cursor) } } }
                    item { Colophon(app.members.size) }
                }
                else -> item { EmptyEdition(onShare = { sharing = true }) }
            }
        }
    }

    if (sharing) {
        ShareSheet(app, onDismiss = {
            sharing = false
            paper.load(app)
        })
    }
}

/**
 * Pull to refresh, on paper: a slip comes down from the top and its three dots ink up as it is
 * pulled; once let go they print in turn until the new edition is in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InkingIndicator(state: PullToRefreshState, refreshing: Boolean, modifier: Modifier) {
    Box(
        modifier
            .graphicsLayer {
                val pulled = state.distanceFraction.coerceIn(0f, 1.4f)
                translationY = pulled * 64.dp.toPx() - size.height
                alpha = pulled.coerceIn(0f, 1f)
            }
            .size(width = 64.dp, height = 30.dp)
            .background(Press.paper, Corner)
            .border(1.dp, Press.ink, Corner),
        contentAlignment = Alignment.Center,
    ) {
        if (refreshing) {
            InkDots(dotSize = 5.dp, contentDescription = "Refreshing the front page")
        } else {
            val ink = Press.ink
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { index ->
                    Box(
                        Modifier.size(5.dp)
                            .graphicsLayer {
                                val t = (state.distanceFraction * 3f - index).coerceIn(0f, 1f)
                                alpha = 0.25f + 0.75f * t
                                scaleX = 0.4f + 0.6f * t
                                scaleY = 0.4f + 0.6f * t
                            }
                            .background(ink, CircleShape),
                    )
                }
            }
        }
    }
}

private fun roman(n: Int): String {
    var left = n
    return buildString {
        listOf(10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I").forEach { (value, glyph) ->
            while (left >= value) { append(glyph); left -= value }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Masthead(app: AppState, stories: List<StoryRow>, seenAt: Instant?, onShare: () -> Unit) {
    val fresh = stories.count { story -> story.sources.any { isNew(it.created_at, seenAt) } || story.messages.any { isNew(it.created_at, seenAt) } }
    val categories = stories.mapNotNull { it.category }.distinct()
    val founded = app.group?.created_at
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 14.dp)) {
        Text(
            app.group?.name ?: "The paper",
            style = nameplate(if ((app.group?.name?.length ?: 0) > 18) 40 else 50).copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
        DoubleRule()
        Text(
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", LocalLocale.current.platformLocale)),
            style = BodyStyle.copy(fontStyle = FontStyle.Italic, fontSize = 16.sp, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val dateline = LabelStyle.copy(fontSize = 10.sp, letterSpacing = 0.08.em, color = Press.ink2)
            Box(Modifier.weight(1f)) {
                if (founded != null) {
                    val edition = (Duration.between(founded, Instant.now()).toDays() + 1).toInt().coerceAtLeast(1)
                    Text("VOL. ${roman((edition - 1) / 365 + 1)} · NO. $edition", style = dateline, maxLines = 1)
                }
            }
            AvatarStack(app.members.map { it.user_id }.take(5), 20.dp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text(
                    buildAnnotatedString {
                        append("${stories.size} ${if (stories.size == 1) "STORY" else "STORIES"}")
                        if (fresh > 0) {
                            append(" · ")
                            withStyle(SpanStyle(color = Press.red)) { append("$fresh NEW") }
                        }
                    },
                    style = dateline,
                    maxLines = 1,
                )
            }
        }
        Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("FRONT PAGE", style = LabelStyle)
                categories.forEach { Text(it.uppercase(), style = LabelStyle.copy(color = Press.ink2, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)) }
            }
        }
        InkButton("+  Share a link", onClick = onShare)
        Spacer(Modifier.height(10.dp))
        Hairline(color = Press.ink)
    }
}

/** A story on the page gives a little under the finger. */
private fun Modifier.opens(onOpen: () -> Unit) = pressable(give = Give.Story, onClickLabel = "Open story", onClick = onOpen)

private fun isNew(createdAt: Instant, seenAt: Instant?) = seenAt == null || seenAt.isBefore(createdAt)

private fun StoryRow.imageUrl() = sources.firstOrNull { it.image_url != null }?.image_url

private fun StoryRow.publisher() = sources.firstOrNull()?.publisher ?: "The paper"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryMeta(story: StoryRow, seenAt: Instant?, submitters: List<String> = emptyList(), publishers: Boolean = false) {
    val newSources = story.sources.count { isNew(it.created_at, seenAt) }
    val newMessages = story.messages.count { isNew(it.created_at, seenAt) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        val names = story.sources.map { it.publisher }.distinct()
        MetaRow(plural(story.sources.size, "source") + if (publishers && names.isNotEmpty()) ": ${names.joinToString(", ")}" else "")
        MetaRow(plural(story.messages.size, "message"))
        if (newSources > 0) NewMark(plural(newSources, "new source"))
        if (newMessages > 0) NewMark(plural(newMessages, "new message"))
        if (submitters.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { submitters.forEach { Avatar(it, 20.dp) } }
    }
}

@Composable
private fun LeadStory(story: StoryRow, members: List<Member>, seenAt: Instant?, modifier: Modifier) {
    val newSources = story.sources.count { isNew(it.created_at, seenAt) }
    val submitters = members.filter { member -> story.sources.any { it.submitted_by == member.user_id } }
    Column(modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text((story.category ?: "News").uppercase(), style = LabelStyle)
            if (newSources > 0) Stamp("+${plural(newSources, "source")}", Modifier.rotate(-2f))
        }
        Text(story.title.ifEmpty { "A link from the table" }, style = display(38), modifier = Modifier.padding(top = 10.dp, bottom = 16.dp))
        PlateView(story.imageUrl(), story.publisher(), Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        if (story.sources.isNotEmpty()) {
            MetaRow(
                "Pictured via ${story.publisher()}" + (submitters.firstOrNull()?.let { ", shared by ${it.profiles.display_name}" } ?: ""),
                Modifier.padding(top = 7.dp),
            )
        }
        Hairline(Modifier.padding(top = 8.dp))
        DropCapSummary(story.summary.ifEmpty { "The summary is still being prepared. Open the story to read the shared link." }, Modifier.padding(top = 16.dp, bottom = 14.dp))
        StoryMeta(story, seenAt, submitters.map { it.user_id }, publishers = true)
    }
}

/** The opening paragraph with a drop cap three lines deep. */
@Composable
fun DropCapSummary(text: String, modifier: Modifier = Modifier) {
    val first = text.take(1)
    val rest = text.drop(1)
    Row(modifier) {
        Text(first, style = display(62).copy(lineHeight = 58.sp), modifier = Modifier.padding(end = 6.dp))
        Text(rest, style = BodyStyle.copy(fontSize = 18.sp, lineHeight = 27.sp), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SideStory(story: StoryRow, seenAt: Instant?, pictured: Boolean, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (pictured) PlateView(story.imageUrl(), story.publisher(), Modifier.fillMaxWidth().aspectRatio(3f / 2f).padding(bottom = 4.dp))
        Text((story.category ?: "News").uppercase(), style = LabelStyle)
        Text(story.title.ifEmpty { "A link from the table" }, style = display(if (pictured) 28 else 22))
        if (pictured && story.summary.isNotEmpty()) {
            Text(story.summary, style = BodyStyle.copy(fontSize = 15.sp, lineHeight = 22.sp, color = Press.ink2), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        StoryMeta(story, seenAt)
        Hairline(Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun InsideStory(story: StoryRow, seenAt: Instant?, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = Gutter)) {
        Row(Modifier.padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text((story.category ?: "News").uppercase(), style = LabelStyle)
                Text(story.title.ifEmpty { "A link from the table" }, style = display(21))
                StoryMeta(story, seenAt)
            }
            PlateView(story.imageUrl(), story.publisher(), Modifier.size(100.dp))
        }
        Hairline()
    }
}

@Composable
private fun Letter(body: String?, author: String, story: String, at: Instant, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("“", style = display(54).copy(color = Press.ink3, lineHeight = 30.sp), modifier = Modifier.height(26.dp))
        Text(body ?: "Shared something with the table.", style = BodyStyle.copy(fontStyle = FontStyle.Italic, fontSize = 21.sp, lineHeight = 28.sp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Press.ink, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)) { append(author) }
                append(" on $story · ${clock(at)}")
            },
            style = MetaStyle,
        )
        Hairline(Modifier.padding(top = 10.dp))
    }
}

/** The way into the archive, when older stories didn't make this edition. */
@Composable
private fun EarlierEditions(onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 36.dp)) {
        Hairline(color = Press.ink)
        Row(
            Modifier.fillMaxWidth().pressable(give = Give.Type, onClickLabel = "Open earlier editions", onClick = onClick).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("EARLIER EDITIONS", style = LabelStyle, modifier = Modifier.weight(1f))
            Text("→", style = LabelStyle)
        }
        Hairline(color = Press.ink)
    }
}

@Composable
private fun Colophon(readers: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 40.dp, bottom = 32.dp)) {
        Hairline(color = Press.ink)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("The Grapevine", style = nameplate(22), modifier = Modifier.weight(1f))
            MetaRow("Printed ${clock(Instant.now())} · ${plural(readers, "reader")}", )
        }
    }
}

@Composable
private fun EmptyEdition(onShare: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("STOP THE PRESSES", style = LabelStyle)
        Text("This edition is waiting for its first story.", style = display(34).copy(textAlign = TextAlign.Center))
        Text(
            "Share a link and it becomes the lead. Links about the same event are filed together, and every story gets its own conversation.",
            style = BodyStyle.copy(color = Press.ink2, textAlign = TextAlign.Center),
        )
        InkButton("+  Share a link", modifier = Modifier.width(200.dp), onClick = onShare)
    }
}
