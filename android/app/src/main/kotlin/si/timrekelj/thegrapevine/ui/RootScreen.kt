package si.timrekelj.thegrapevine.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.OneSignalManager
import si.timrekelj.thegrapevine.PaperState
import si.timrekelj.thegrapevine.R

/** Mirrors `ContentView` in GrapevineApp.swift. */
@Composable
fun RootScreen(app: AppState) {
    Box(Modifier.fillMaxSize().background(Press.paper)) {
        when {
            !app.checked -> CircularProgressIndicator(color = Press.ink, strokeWidth = 2.dp, modifier = Modifier.align(Alignment.Center))
            app.session == null -> LoginScreen(app)
            app.group == null -> OnboardingScreen(app)
            else -> MainTabs(app)
        }
    }
    val signedIn = app.checked && app.session != null
    LaunchedEffect(signedIn) {
        if (signedIn) OneSignalManager.requestPushPermission()
    }
}

@Composable
private fun MainTabs(app: AppState) {
    val api = LocalApi.current
    val paper: PaperState = viewModel { PaperState(api) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openStory by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(app.pendingStoryId) {
        app.pendingStoryId?.let {
            openStory = it
            app.pendingStoryId = null
        }
    }

    val storyId = openStory
    if (storyId != null) {
        BackHandler { openStory = null }
        StoryScreen(storyId = storyId, app = app, onBack = {
            openStory = null
            paper.load(app)
        })
        return
    }

    Scaffold(
        containerColor = Press.paper,
        // The tabs draw under the status bar's own paper strip; the bar pads itself.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column {
                Hairline(color = Press.ink)
                NavigationBar(containerColor = Press.paper, tonalElevation = 0.dp) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Press.ink,
                        selectedTextColor = Press.ink,
                        unselectedIconColor = Press.ink3,
                        unselectedTextColor = Press.ink3,
                        indicatorColor = Press.tint,
                    )
                    val label = @Composable { text: String -> Text(text.uppercase(), style = LabelStyle.copy(fontSize = 10.sp, color = androidx.compose.ui.graphics.Color.Unspecified)) }
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { label("Paper") },
                        colors = colors,
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(painterResource(R.drawable.ic_friends), contentDescription = null) },
                        label = { label("Friends") },
                        colors = colors,
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
                        label = { label("Profile") },
                        colors = colors,
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).statusBarsPadding().clipToBounds()) {
            when (tab) {
                0 -> FrontPageScreen(app, paper, onOpen = { openStory = it })
                1 -> FriendsScreen(app)
                else -> ProfileScreen(app, onSignOut = { paper.clear() })
            }
        }
    }
}
