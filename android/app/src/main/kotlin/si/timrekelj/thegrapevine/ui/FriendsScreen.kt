package si.timrekelj.thegrapevine.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.Config

@Composable
fun FriendsScreen(app: AppState) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp)) {
        item {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("People", style = display(30))
                Text("${app.members.size} in ${app.group?.name ?: "your paper"}", style = MetaStyle.copy(fontSize = 15.sp, color = Press.ink3), maxLines = 1)
            }
        }
        items(app.members, key = { it.user_id }) { member ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MemberDisc(member.user_id, member.profiles.display_name, 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(member.profiles.display_name, style = NameStyle.copy(fontSize = 17.sp))
                    if (member.role == "owner") Text("Started the paper", style = MetaStyle.copy(color = Press.ink3))
                }
            }
            Hairline()
        }
        app.group?.let { group ->
            item {
                Column(
                    Modifier.padding(top = 28.dp).fillMaxWidth().card(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SectionHead("Invite someone")
                    Row(
                        Modifier.fillMaxWidth().background(Press.raised2, FieldCorner).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Invite code", style = MetaStyle.copy(color = Press.ink3), modifier = Modifier.weight(1f))
                        SelectionContainer {
                            Text(group.invite_code, style = NameStyle.copy(fontSize = 17.sp, fontFeatureSettings = "tnum"))
                        }
                    }
                    Text("Anyone with the code can join and read everything in the paper.", style = MetaStyle.copy(fontSize = 14.sp, color = Press.ink2))
                    InkButton("Share invitation") {
                        val link = "${Config.webURL}/invite?code=${group.invite_code}"
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                        context.startActivity(Intent.createChooser(send, "Share invitation"))
                    }
                }
            }
        }
    }
}
