package si.timrekelj.thegrapevine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import si.timrekelj.thegrapevine.ArchiveState
import si.timrekelj.thegrapevine.ArchivedStory
import java.time.ZoneId

private val Gutter = 20.dp

/** Earlier editions: every story older than the front page, by the day it last moved. */
@Composable
fun ArchiveScreen(archive: ArchiveState, list: LazyListState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    LaunchedEffect(archive) { archive.open() }

    val stories = archive.stories
    val days = remember(stories) {
        stories.groupBy { it.last_activity_at.atZone(ZoneId.systemDefault()).toLocalDate() }.values.toList()
    }

    Column(Modifier.fillMaxSize().background(Press.paper)) {
        Column(Modifier.statusBarsPadding().padding(horizontal = Gutter).padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("← Front page", style = MetaStyle.copy(color = Press.ink), modifier = Modifier.weight(1f).pressable(give = Give.Type, onClick = onBack).padding(vertical = 8.dp))
                Text("The Grapevine", style = nameplate(24))
                Spacer(Modifier.weight(1f))
            }
            DoubleRule(Modifier.padding(top = 4.dp))
        }

        LazyColumn(Modifier.fillMaxSize(), state = list) {
            item { SectionHead("Earlier editions", Modifier.padding(horizontal = Gutter).padding(top = 22.dp)) }
            days.forEach { day ->
                item(key = "day-${day.first().id}") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 26.dp)) {
                        Text(dayLabel(day.first().last_activity_at), style = LabelStyle)
                        Hairline(Modifier.padding(top = 8.dp), color = Press.ink)
                    }
                }
                items(day, key = { it.id }) { story ->
                    ArchivedRow(story, Modifier.animateItem().pressable(give = Give.Story, onClickLabel = "Open story") { onOpen(story.id) })
                }
            }
            val error = archive.error
            when {
                error != null -> item {
                    Column(Modifier.padding(Gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(error, style = BodyStyle)
                        TextAction("Try again") { archive.loadPage() }
                    }
                }
                // The first page comes up in galley proof; later pages ink the button's dots.
                archive.isLoading && stories.isEmpty() -> item(key = "proof") { ArchiveProof(Modifier.animateItem()) }
                archive.loaded && stories.isEmpty() -> item {
                    Text("Nothing older yet.", style = BodyStyle.copy(color = Press.ink2), modifier = Modifier.padding(Gutter))
                }
                archive.hasMore || archive.isLoading -> item(key = "earlier") {
                    GhostButton(
                        "Load earlier",
                        modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = Gutter).padding(top = 24.dp),
                        busy = archive.isLoading,
                        busyLabel = "Loading earlier stories…",
                    ) { archive.loadPage() }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(32.dp)) }
        }
    }
}

@Composable
private fun ArchivedRow(story: ArchivedStory, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = Gutter)) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text((story.category ?: "News").uppercase(), style = LabelStyle)
            Text(story.title.ifEmpty { "A link from the table" }, style = display(20))
            MetaRow("${plural(story.sourceCount, "source")} · ${plural(story.messageCount, "message")}")
        }
        Hairline()
    }
}
