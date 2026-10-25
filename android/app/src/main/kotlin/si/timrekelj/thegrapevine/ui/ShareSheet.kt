package si.timrekelj.thegrapevine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.encodeBody

@Serializable
private data class SubmitBody(val group_id: String, val url: String, val comment: String?)

@Serializable
private data class SubmitResult(val status: String, val story_id: String? = null, val story_title: String? = null)

/**
 * Mirrors `ShareView.swift`. Also opened over other apps from the system share
 * sheet ([si.timrekelj.thegrapevine.ShareActivity]) with the shared link filled in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(app: AppState, onDismiss: () -> Unit, initialUrl: String = "", onOpenApp: (() -> Unit)? = null) {
    val api = LocalApi.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(initialUrl) }
    var comment by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var filed by remember { mutableStateOf(false) }
    var filedStoryId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Press.raised,
        shape = SheetCorner,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Share a link", style = display(26))
            val notice = when {
                !app.checked -> null
                app.session == null -> "Sign in to The Grapevine to share links with your paper."
                app.group == null -> "Join or start a paper in The Grapevine to share links."
                else -> null
            }
            if (notice != null) {
                Text(notice, style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
                onOpenApp?.let { InkButton("Open The Grapevine", onClick = it) }
                return@Column
            }
            if (busy || filed) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilingMark(busy, filed)
                    Text(if (filed) "+1 source" else "Reading the page", style = display(30))
                }
            }
            if (!filed) {
                PaperField(url, { url = it }, "https://…", label = "Link", keyboardType = KeyboardType.Uri)
                PaperField(comment, { comment = it }, "Why it caught your eye", label = "Add a line (optional)", singleLine = false)
                app.group?.let { Text("Everyone in ${it.name} sees this with your name.", style = MetaStyle.copy(color = Press.ink3)) }
            }
            Insert(result) { message ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(message, style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
                    if (failed) ReportAction(message, "Share", before = onDismiss)
                }
            }
            if (!filed) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (result == null) GhostButton("Cancel", enabled = !busy, modifier = Modifier.weight(1f), onClick = onDismiss)
            InkButton("File it", enabled = !busy && url.isNotBlank() && app.group != null, modifier = Modifier.weight(1f), busy = busy, busyLabel = "Filing…") {
                val group = app.group ?: return@InkButton
                scope.launch {
                    busy = true
                    result = null
                    failed = false
                    result = try {
                        val submitted: SubmitResult = api.postObject(
                            "functions/v1/submit-source",
                            encodeBody(SubmitBody(group.id, url.trim(), comment.trim().ifEmpty { null })),
                        )
                        // A 429 (too many links in ten minutes) arrives as ApiError with the server's message.
                        filed = submitted.status in listOf("joined", "new_story")
                        filedStoryId = submitted.story_id
                        val title = submitted.story_title?.takeIf { it.isNotBlank() } ?: "the story"
                        when (submitted.status) {
                            "duplicate" -> "Already in the paper. Your link was not added again."
                            "joined" -> "Filed under $title."
                            "new_story" -> "New story: $title."
                            else -> "Posted."
                        }
                    } catch (e: Exception) {
                        failed = true
                        e.message
                    }
                    busy = false
                }
            }
            }
            if (filed && filedStoryId != null) InkButton("Open the story") { app.pendingStoryId = filedStoryId; onDismiss() }
            Insert(result) { GhostButton("Done", onClick = onDismiss) }
        }
    }
}
