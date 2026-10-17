package si.timrekelj.thegrapevine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.Member
import si.timrekelj.thegrapevine.PaperState
import si.timrekelj.thegrapevine.StoryRow
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Mirrors `FrontPageView.swift`: the lead story, then the ranked rest. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrontPageScreen(app: AppState, paper: PaperState, onOpen: (String) -> Unit) {
    var sharing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(app.group?.id) { paper.load(app) }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = paper.isLoading && paper.stories.isNotEmpty(),
            onRefresh = { paper.load(app) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                item { Masthead(app.group?.name ?: "The paper") }
                val error = paper.error
                val stories = paper.stories
                when {
                    error != null -> item {
                        Column(Modifier.padding(20.dp)) {
                            Text(error, style = BodyStyle)
                            TextButton(onClick = { paper.load(app) }) { Text("Try again", color = Grape.ink) }
                        }
                    }
                    paper.isLoading && stories.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Grape.ink)
                        }
                    }
                    stories.isNotEmpty() -> {
                        item(key = stories.first().id) {
                            LeadStory(stories.first(), app.members, paper.seenAt, Modifier.clickable { onOpen(stories.first().id) })
                        }
                        items(stories.drop(1), key = { it.id }) { story ->
                            StoryLine(story, paper.seenAt, Modifier.clickable { onOpen(story.id) })
                        }
                        item { Spacer(Modifier.height(96.dp)) }
                    }
                    else -> item {
                        Column(
                            Modifier.fillMaxWidth().padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Share the first link to start the paper.", style = display(21), textAlign = TextAlign.Center)
                            InkButton("Share a link", modifier = Modifier) { sharing = true }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { sharing = true },
            containerColor = Grape.ink,
            contentColor = Grape.paper,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Share a link") }
    }

    if (sharing) {
        ShareSheet(app, onDismiss = {
            sharing = false
            paper.load(app)
        })
    }
}

@Composable
private fun Masthead(name: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault()).format(LocalDate.now()).uppercase(),
            style = LabelStyle,
        )
        Text(name, style = display(28))
    }
    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.5.dp).background(Grape.ink))
}

private fun isNew(createdAt: Instant, seenAt: Instant?) = seenAt == null || seenAt.isBefore(createdAt)

private fun StoryRow.imageUrl() = sources.firstOrNull { it.image_url != null }?.image_url

@Composable
private fun NewDot(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(Grape.pink, CircleShape))
        MetaRow("$count new")
    }
}

@Composable
private fun LeadStory(story: StoryRow, members: List<Member>, seenAt: Instant?, modifier: Modifier) {
    val newSources = story.sources.count { isNew(it.created_at, seenAt) }
    val newMessages = story.messages.count { isNew(it.created_at, seenAt) }
    val submitters = members.filter { member -> story.sources.any { it.submitted_by == member.user_id } }.map { it.profiles }

    Column(modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlateView(story.imageUrl(), story.sources.firstOrNull()?.publisher ?: "The paper", 200.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text((story.category ?: "News").uppercase(), style = LabelStyle)
            if (newSources > 0) Stamp("+${plural(newSources, "source")}")
        }
        Text(story.title.ifEmpty { "A link from the table" }, style = display(34))
        if (story.summary.isNotEmpty()) Text(story.summary, style = BodyStyle.copy(color = Grape.ink2))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetaRow(plural(story.sources.size, "source"))
            MetaRow(plural(story.messages.size, "message"))
            if (newMessages > 0) NewDot(newMessages)
            Spacer(Modifier.weight(1f))
            submitters.take(4).forEach { Monogram(it, 24.dp) }
        }
    }
}

@Composable
private fun StoryLine(story: StoryRow, seenAt: Instant?, modifier: Modifier) {
    val newCount = story.sources.count { isNew(it.created_at, seenAt) } + story.messages.count { isNew(it.created_at, seenAt) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text((story.category ?: "News").uppercase(), style = LabelStyle)
                Text(story.title.ifEmpty { "A link from the table" }, style = display(21))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetaRow(plural(story.sources.size, "source"))
                    MetaRow(plural(story.messages.size, "message"))
                    if (newCount > 0) NewDot(newCount)
                }
            }
            PlateView(story.imageUrl(), story.sources.firstOrNull()?.publisher ?: "?", 72.dp, Modifier.width(72.dp))
        }
        HorizontalDivider(color = Grape.rule, thickness = 0.5.dp)
    }
}
