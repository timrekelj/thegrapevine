package si.timrekelj.thegrapevine.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                Text("THE PEOPLE BEHIND THE PAPER", style = LabelStyle)
                Text("Friends", style = display(34))
                Text("${app.members.size} at the table · ${app.group?.name ?: "Your paper"}", style = MetaStyle)
            }
        }
        items(app.members, key = { it.user_id }) { member ->
            Row(
                Modifier.fillMaxWidth().background(Grape.raised).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Monogram(member.profiles, 40.dp)
                Text(member.profiles.display_name, style = BodyStyle, modifier = Modifier.weight(1f))
                if (member.role == "owner") Text("Owner", style = MetaStyle, modifier = Modifier.background(Grape.paper, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp))
            }
            HorizontalDivider(color = Grape.rule, thickness = 0.5.dp)
        }
        app.group?.let { group ->
            item {
                Column(
                    Modifier.padding(top = 24.dp).fillMaxWidth().background(Grape.raised, RoundedCornerShape(18.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Make room for a friend", style = display(23))
                    Text("Good papers start with good company. Share an invitation to yours.", style = BodyStyle.copy(color = Grape.ink2))
                    Text("INVITE CODE", style = LabelStyle)
                    SelectionContainer {
                        Text(group.invite_code, style = BodyStyle.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth().background(Grape.paper, RoundedCornerShape(10.dp)).padding(14.dp))
                    }
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
