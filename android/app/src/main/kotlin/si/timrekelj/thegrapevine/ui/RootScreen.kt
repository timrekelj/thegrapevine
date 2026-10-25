package si.timrekelj.thegrapevine.ui

import si.timrekelj.thegrapevine.FeedbackKind
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyListState
import si.timrekelj.thegrapevine.AppState
import si.timrekelj.thegrapevine.ArchiveState
import si.timrekelj.thegrapevine.InstantSerializer
import si.timrekelj.thegrapevine.OneSignalManager
import si.timrekelj.thegrapevine.PaperState
import si.timrekelj.thegrapevine.R

private enum class Stage { Checking, SignedOut, Onboarding, Paper }

/** Mirrors `ContentView` in GrapevineApp.swift. One stage cross-fades into the next. */
@Composable
fun RootScreen(app: AppState) {
    val stage = when {
        !app.checked -> Stage.Checking
        app.session == null -> Stage.SignedOut
        app.group == null -> Stage.Onboarding
        else -> Stage.Paper
    }
    val signedIn = app.checked && app.session != null
    // Errors offer "Report" only once there is someone to file it as.
    val reporter = remember(app) { { error: String, screen: String -> app.report(FeedbackKind.Bug, screen, error) } }
    CompositionLocalProvider(
        LocalMemberNames provides app.members.associate { it.user_id to it.profiles.display_name },
        LocalReporter provides reporter.takeIf { signedIn },
    ) {
        Crossfade(stage, Modifier.fillMaxSize().background(Press.paper), animationSpec = tween(320), label = "stage") { current ->
            Box(Modifier.fillMaxSize()) {
                when (current) {
                    Stage.Checking -> LoadingMark(modifier = Modifier.align(Alignment.Center))
                    Stage.SignedOut -> LoginScreen(app)
                    Stage.Onboarding -> OnboardingScreen(app)
                    Stage.Paper -> MainTabs(app)
                }
            }
        }
    }
    FeedbackHost(app)
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

    // Earlier editions open over the tabs from the front page; a story opens over either.
    var openArchive by rememberSaveable { mutableStateOf<String?>(null) }
    val archiveFrom = openArchive
    val group = app.group?.id
    // The archive keeps its place while a story from it is open.
    val archiveList = rememberSaveable(archiveFrom, saver = LazyListState.Saver) { LazyListState() }

    val storyId = openStory
    if (storyId != null) {
        val close = {
            openStory = null
            if (archiveFrom == null) paper.load(app)
        }
        BackHandler(onBack = close)
        StoryScreen(
            storyId = storyId,
            app = app,
            onBack = close,
            backLabel = if (archiveFrom != null) "Back to earlier editions" else "Back to the front page",
        )
        return
    }
    if (archiveFrom != null && group != null) {
        val archive: ArchiveState = viewModel(key = "archive-$group-$archiveFrom") {
            ArchiveState(api, group, InstantSerializer.parse(archiveFrom))
        }
        BackHandler { openArchive = null }
        ArchiveScreen(archive, archiveList, onBack = { openArchive = null }, onOpen = { openStory = it })
        return
    }

    if (tab != 0) {
        BackHandler { tab = 0 }
        Column(Modifier.fillMaxSize().background(Press.paper).statusBarsPadding()) {
            Box(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                IconButton(onClick = { tab = 0 }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the front page", tint = Press.ink)
                }
            }
            if (tab == 1) FriendsScreen(app) else ProfileScreen(app, onSignOut = { paper.clear() })
        }
        return
    }
    Box(Modifier.fillMaxSize().background(Press.paper).statusBarsPadding()) {
        FrontPageScreen(app, paper, onOpen = { openStory = it }, onEarlier = { openArchive = InstantSerializer.format(it) }, onPeople = { tab = 1 }, onAccount = { tab = 2 })
    }
}
