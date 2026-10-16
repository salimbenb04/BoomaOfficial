package com.booma.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import com.booma.app.data.Liked
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import com.booma.app.data.SearchResults
import com.booma.app.data.Shelf
import com.booma.app.data.artistFromSubtitle
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.booma.app.CrashReport
import com.booma.app.data.AuthStore
import com.booma.app.data.Downloads
import com.booma.app.data.ListCache
import com.booma.app.data.Lists
import com.booma.app.data.Playlist
import com.booma.app.data.SettingsStore
import com.booma.app.data.Track
import com.booma.app.data.YtmRepository
import com.booma.app.player.PlayerHolder
import com.booma.app.player.bigArt
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

private enum class BoomaTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    SEARCH("Search", Icons.Filled.Search),
    LIBRARY("Library", Icons.Filled.LibraryMusic),
    ACCOUNT("You", Icons.Filled.Person),
}

private val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun BoomaApp(
    auth: AuthStore,
    repo: YtmRepository,
    settings: SettingsStore,
    background: Color,
    onBackgroundChange: (Color) -> Unit,
    gradient: Boolean,
    onGradientChange: (Boolean) -> Unit,
    snakeHead: Boolean,
    onSnakeHeadChange: (Boolean) -> Unit,
    dynamicTheme: Boolean,
    onDynamicThemeChange: (Boolean) -> Unit,
    openPlayerSignal: Int,
    playlistRequest: Playlist?,
    onPlaylistRequestHandled: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(BoomaTab.HOME) }
    var signedIn by remember { mutableStateOf(auth.isSignedIn()) }
    var nowPlayingOpen by remember { mutableStateOf(false) }
    var openOnQueue by remember { mutableStateOf(false) }
    var openPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var pickPlaylistFor by remember { mutableStateOf<Track?>(null) }
    // kept here so Home and Search stay where you scrolled after opening an album or playlist
    val homeList = rememberLazyListState()
    val searchList = rememberLazyListState()
    val play: (List<Track>, Int) -> Unit = { list, index -> PlayerHolder.playQueue(list, index) }
    val context = LocalContext.current
    val appScope = rememberCoroutineScope()
    val actions = rememberTrackActions(
        repo = repo,
        onPickPlaylist = { pickPlaylistFor = it },
        onLiked = { Lists.tracks.remove("liked") },
    )

    // the notification, dynamic island or widget asked for the player
    LaunchedEffect(openPlayerSignal) {
        if (openPlayerSignal > 0) {
            withTimeoutOrNull(4000) { snapshotFlow { PlayerHolder.currentId }.first { it != null } }
            if (PlayerHolder.currentId != null) {
                openOnQueue = false
                nowPlayingOpen = true
            }
        }
    }

    // the widget asked to open one of your playlists
    LaunchedEffect(playlistRequest) {
        playlistRequest?.let {
            nowPlayingOpen = false
            tab = BoomaTab.LIBRARY
            openPlaylist = it
            onPlaylistRequestHandled()
        }
    }

    BackHandler(enabled = nowPlayingOpen || openPlaylist != null) {
        when {
            nowPlayingOpen -> nowPlayingOpen = false
            else -> openPlaylist = null
        }
    }

    CompositionLocalProvider(LocalTrackActions provides actions) {
    Box(Modifier.fillMaxSize().boomaBackground()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    MiniPlayerSlot {
                        MiniPlayer(
                            onOpen = {
                                openOnQueue = false
                                nowPlayingOpen = true
                            },
                            onOpenQueue = {
                                openOnQueue = true
                                nowPlayingOpen = true
                            },
                        )
                    }
                    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                        BoomaTab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = {
                                    openPlaylist = null
                                    tab = t
                                },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.background,
                                    indicatorColor = OwlCore,
                                    selectedTextColor = OwlCore,
                                    unselectedIconColor = Muted,
                                    unselectedTextColor = Muted,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = tab to openPlaylist,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                modifier = Modifier.fillMaxSize().padding(padding),
                label = "tabs",
            ) { (current, pl) ->
                if (pl != null) {
                    // an album, playlist or mix opened from any tab
                    PlaylistScreen(repo, pl, play, onBack = { openPlaylist = null })
                } else when (current) {
                    BoomaTab.HOME -> HomeScreen(repo, signedIn, play, onOpen = { openPlaylist = it }, onSignIn = { tab = BoomaTab.ACCOUNT }, listState = homeList)
                    BoomaTab.SEARCH -> SearchScreen(repo, settings, play, onOpen = { openPlaylist = it }, listState = searchList)
                    BoomaTab.LIBRARY -> LibraryScreen(repo, signedIn, play, onOpenPlaylist = { openPlaylist = it }, onSignIn = { tab = BoomaTab.ACCOUNT })
                    BoomaTab.ACCOUNT -> when {
                        !signedIn -> LoginScreen(onSignedIn = { cookie ->
                            auth.save(cookie)
                            Lists.clear()
                            signedIn = true
                        })
                        else -> AccountScreen(
                            background = background,
                            onBackgroundChange = onBackgroundChange,
                            gradient = gradient,
                            onGradientChange = onGradientChange,
                            snakeHead = snakeHead,
                            onSnakeHeadChange = onSnakeHeadChange,
                            dynamicTheme = dynamicTheme,
                            onDynamicThemeChange = onDynamicThemeChange,
                            settings = settings,
                            onSignOut = {
                                auth.clear()
                                clearWebSession()
                                Lists.clear()
                                Liked.clear()
                                signedIn = false
                                toast(context, "Signed out")
                            },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = nowPlayingOpen,
            enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 400f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(260)),
        ) {
            NowPlayingScreen(repo = repo, startOnQueue = openOnQueue, onClose = { nowPlayingOpen = false })
        }
    }
    pickPlaylistFor?.let { track ->
        AddToPlaylistDialog(
            track = track,
            loadPlaylists = {
                Lists.playlists["library"] ?: repo.playlists().also { Lists.playlists["library"] = it }
            },
            onPick = { pl ->
                pickPlaylistFor = null
                appScope.launch {
                    try {
                        repo.addToPlaylist(pl.id, track.id)
                        Lists.tracks.remove("playlist:${pl.id}")
                        toast(context, "Added to ${pl.title}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        toast(context, "Couldn't add to ${pl.title}. You can only add to your own playlists.")
                    }
                }
            },
            onCreate = { name ->
                pickPlaylistFor = null
                appScope.launch {
                    try {
                        repo.createPlaylist(name, listOf(track.id))
                        Lists.playlists.remove("library")
                        PlaylistsChanged.version++
                        toast(context, "Made \"$name\" with ${track.title}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        toast(context, "Couldn't make the playlist. Check your connection.")
                    }
                }
            },
            onDismiss = { pickPlaylistFor = null },
        )
    }
    }
}

/** Goes up when you make, rename or delete a playlist, so the playlist list reloads. */
private object PlaylistsChanged {
    var version by mutableIntStateOf(0)
}

// ---------------------------------------------------------------- loading with a memory cache

private class Load<T>(initial: T?) {
    var data by mutableStateOf(initial)
    var loading by mutableStateOf(initial == null)
    var error by mutableStateOf<String?>(null)
}

@Composable
private fun <T> rememberLoad(key: String, cache: ListCache<T>, retry: Int, load: suspend () -> T): Load<T> {
    val state = remember(key) { Load(cache[key]) }
    LaunchedEffect(key, retry) {
        if (state.data == null) state.loading = true
        state.error = null
        try {
            val value = load()
            cache[key] = value
            state.data = value
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (state.data == null) state.error = e.message ?: e.toString()
        }
        state.loading = false
    }
    return state
}

// ---------------------------------------------------------------- shared pieces

/** Cover art in a rounded frame that fades in, or a quiet music note when there is none. */
@Composable
internal fun Artwork(url: String?, modifier: Modifier, corner: Dp = 10.dp, fade: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Color.White.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            val context = LocalContext.current
            val request = remember(url, fade) { ImageRequest.Builder(context).data(url).crossfade(fade).build() }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.35f))
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp))
}

/** Shows the mini player once something is loaded; only this slot reacts to that, not the whole app. */
@Composable
private fun MiniPlayerSlot(content: @Composable () -> Unit) {
    val visible = PlayerHolder.currentId != null
    Column {
        AnimatedVisibility(visible = visible) { content() }
    }
}

/** Floating player above the tabs. Tap to open; swipe sideways to skip. */
@Composable
private fun MiniPlayer(onOpen: () -> Unit, onOpenQueue: () -> Unit) {
    // progress is read only while drawing the line below, so updates never rebuild the mini player
    val progress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            val d = PlayerHolder.duration()
            progress.floatValue = if (d > 0) (PlayerHolder.position().toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    val lineBrush = Brush.horizontalGradient(listOf(OwlGlow, OwlCore))
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f),
        shadowElevation = 8.dp,
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth()
            .graphicsLayer { translationX = dragX * 0.5f }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragX < -threshold) PlayerHolder.next() else if (dragX > threshold) PlayerHolder.previous()
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                ) { change, amount ->
                    change.consume()
                    dragX += amount
                }
            }
            .clickable(onClick = onOpen),
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(PlayerHolder.artwork, Modifier.size(46.dp), corner = 10.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(PlayerHolder.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(PlayerHolder.artist, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Up next")
                }
                IconButton(onClick = { PlayerHolder.togglePlayPause() }) {
                    Icon(
                        if (PlayerHolder.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (PlayerHolder.playing) "Pause" else "Play",
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            if (PlayerHolder.buffering) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = OwlGlow, trackColor = Color.Transparent)
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .drawBehind { drawRect(lineBrush, size = Size(size.width * progress.floatValue, size.height)) },
                )
            }
        }
    }
}

/** Placeholder rows that pulse gently while a list loads. */
@Composable
private fun SkeletonRows(count: Int = 8) {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.05f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val shade = Color.White.copy(alpha = alpha)
    Column {
        repeat(count) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(shade))
                Spacer(Modifier.width(14.dp))
                Column {
                    Box(Modifier.width(180.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shade))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(110.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(shade))
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = Muted, style = MaterialTheme.typography.bodyLarge)
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun TrackRow(track: Track, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null, onClick: () -> Unit) {
    val playingNow = track.id == PlayerHolder.currentId
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track.thumb, Modifier.size(54.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (playingNow) OwlGlow else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (playingNow) {
            BoomaOwl(modifier = Modifier.size(30.dp), playing = PlayerHolder.playing)
        }
        DownloadMark(track, Modifier.padding(start = 6.dp))
        TrackMenuButton(track, onRemove)
    }
}

@Composable
private fun TrackCard(track: Track, onClick: () -> Unit) {
    val playingNow = track.id == PlayerHolder.currentId
    Column(
        Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Artwork(track.thumb?.let { bigArt(it) }, Modifier.size(140.dp), corner = 14.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            track.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (playingNow) OwlGlow else Color.Unspecified,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An album, playlist or mix as a card; tap to open it. */
@Composable
private fun ListCard(list: Playlist, onOpen: (Playlist) -> Unit) {
    Column(
        Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpen(list) }
            .padding(4.dp),
    ) {
        Artwork(list.thumb?.let { bigArt(it) }, Modifier.size(140.dp), corner = 14.dp)
        Spacer(Modifier.height(8.dp))
        Text(list.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (list.subtitle.isNotBlank()) {
            Text(list.subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One Home shelf: "Quick picks"-style song lists scroll sideways in columns of four; the rest as cards. */
@Composable
private fun ShelfContent(shelf: Shelf, onPlay: (List<Track>, Int) -> Unit, onOpen: (Playlist) -> Unit) {
    if (shelf.rows && shelf.songs.isNotEmpty() && shelf.lists.isEmpty()) {
        val rows = minOf(4, shelf.songs.size)
        LazyHorizontalGrid(
            rows = GridCells.Fixed(rows),
            modifier = Modifier.fillMaxWidth().height(68.dp * rows),
        ) {
            itemsIndexed(shelf.songs, key = { i, t -> "$i-${t.id}" }, contentType = { _, _ -> "row" }) { i, t ->
                TrackRow(t, Modifier.width(320.dp)) { onPlay(shelf.songs, i) }
            }
        }
    } else {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(shelf.songs, key = { i, t -> "s$i-${t.id}" }, contentType = { _, _ -> "card" }) { i, t ->
                TrackCard(t) { onPlay(shelf.songs, i) }
            }
            itemsIndexed(shelf.lists, key = { i, l -> "l$i-${l.id}" }, contentType = { _, _ -> "list" }) { _, l ->
                ListCard(l, onOpen)
            }
        }
    }
}

@Composable
private fun PlayButtons(tracks: List<Track>, onPlay: (List<Track>, Int) -> Unit, showDownload: Boolean = true) {
    val context = LocalContext.current
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { onPlay(tracks, 0) }) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Play")
        }
        OutlinedButton(onClick = { PlayerHolder.playShuffled(tracks) }) {
            Icon(Icons.Filled.Shuffle, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Shuffle")
        }
        Spacer(Modifier.weight(1f))
        Text("${tracks.size} songs", color = Muted, style = MaterialTheme.typography.bodyMedium)
        if (showDownload) {
            val missing = tracks.filter { !Downloads.byId.containsKey(it.id) }
            IconButton(onClick = {
                if (missing.isEmpty()) {
                    toast(context, "All of these are already on your phone")
                } else {
                    Downloads.download(missing)
                    toast(context, "Downloading ${missing.size} songs")
                }
            }) {
                Icon(
                    if (missing.isEmpty()) Icons.Filled.DownloadDone else Icons.Filled.Download,
                    contentDescription = "Download all",
                    tint = if (missing.isEmpty()) OwlGlow else Color.Unspecified,
                )
            }
        }
    }
}

/** A song list with its header, placeholder rows while loading, and a retry on failure. */
@Composable
private fun TrackList(
    load: Load<List<Track>>,
    emptyText: String,
    onPlay: (List<Track>, Int) -> Unit,
    onRetry: () -> Unit,
    showPlayButtons: Boolean = false,
    hide: (Track) -> Boolean = { false },
    onRemove: ((Track) -> Unit)? = null,
    header: @Composable () -> Unit = {},
) {
    val tracks = load.data.orEmpty().filterNot(hide)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { header() }
        when {
            load.loading && tracks.isEmpty() -> item { SkeletonRows() }
            load.error != null && tracks.isEmpty() -> item {
                Message("Couldn't load this list. Check your connection, or play your downloads from Library.", "Try again", onRetry)
            }
            tracks.isEmpty() -> item { Message(emptyText) }
            else -> {
                if (showPlayButtons) item { PlayButtons(tracks, onPlay) }
                itemsIndexed(tracks, key = { i, t -> "$i-${t.id}" }, contentType = { _, _ -> "track" }) { i, t ->
                    TrackRow(t, onRemove = onRemove?.let { remove -> { remove(t) } }) { onPlay(tracks, i) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- tabs

private fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 5 -> "Up late"
        h < 12 -> "Good morning"
        h < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun HomeScreen(
    repo: YtmRepository,
    signedIn: Boolean,
    onPlay: (List<Track>, Int) -> Unit,
    onOpen: (Playlist) -> Unit,
    onSignIn: () -> Unit,
    listState: LazyListState,
) {
    var retry by remember { mutableIntStateOf(0) }
    // Home as YouTube Music lays it out; kept on the phone, so it shows at once and refreshes quietly.
    val load = rememberLoad("home", Lists.shelves, retry) { repo.homeShelves(2) }
    val shelves = load.data.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header", contentType = "header") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
                Text(greeting(), style = MaterialTheme.typography.bodyLarge, color = Muted)
                Text("Booma", style = MaterialTheme.typography.headlineLarge)
            }
        }
        if (!signedIn) {
            item(key = "signin", contentType = "message") {
                Message("Sign in to YouTube Music to see your own mixes, likes and playlists.", "Sign in", onSignIn)
            }
        }
        when {
            load.loading && shelves.isEmpty() -> item(key = "skeleton") { SkeletonRows() }
            load.error != null && shelves.isEmpty() -> item(key = "error", contentType = "message") {
                Message("Couldn't load Home. Check your connection, or play your downloads from Library.", "Try again") { retry++ }
            }
            shelves.isEmpty() -> item(key = "empty", contentType = "message") { Message("Nothing here yet. Try Search.") }
            else -> shelves.forEachIndexed { i, shelf ->
                item(key = "title$i", contentType = "title") {
                    SectionTitle(shelf.title, Modifier.padding(top = if (i == 0) 0.dp else 10.dp))
                }
                item(key = "shelf$i", contentType = if (shelf.rows) "rows" else "cards") {
                    ShelfContent(shelf, onPlay, onOpen)
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(
    repo: YtmRepository,
    settings: SettingsStore,
    onPlay: (List<Track>, Int) -> Unit,
    onOpen: (Playlist) -> Unit,
    listState: LazyListState,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(settings.searchHistory()) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    val focus = LocalFocusManager.current
    val search: (String) -> Unit = { q ->
        val text = q.trim()
        if (text.isNotEmpty()) {
            query = text
            submitted = text
            settings.addSearch(text)
            history = settings.searchHistory()
            focus.clearFocus()
        }
    }
    // suggestions from YouTube Music while you type (after a short pause)
    val editing = query.isBlank() || query != submitted
    LaunchedEffect(query) {
        if (query.isBlank() || query == submitted) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        suggestions = try {
            repo.suggestions(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    val results = if (submitted.isNotBlank()) rememberLoad("search:$submitted", Lists.search, retry) { repo.searchAll(submitted) } else null

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "field", contentType = "field") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
                Text("Search", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Songs, albums and artists") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search(query) }),
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OwlGlow,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color.White.copy(alpha = 0.06f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        when {
            // empty field: your recent searches
            query.isBlank() -> {
                if (history.isEmpty()) {
                    item(key = "hint", contentType = "message") { Message("Type a song, an album or an artist.") }
                } else {
                    item(key = "recent", contentType = "title") {
                        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("Recent searches", Modifier.weight(1f))
                            TextButton(onClick = {
                                settings.clearSearchHistory()
                                history = emptyList()
                            }) { Text("Clear", color = OwlGlow) }
                        }
                    }
                    items(history, key = { "h:$it" }, contentType = { "history" }) { h ->
                        QueryRow(h, Icons.Filled.History, onClick = { search(h) }, onRemove = {
                            settings.removeSearch(h)
                            history = settings.searchHistory()
                        })
                    }
                }
            }
            // typing: what YouTube Music suggests
            editing -> {
                item(key = "typed", contentType = "query") { QueryRow(query, Icons.Filled.Search, onClick = { search(query) }) }
                items(suggestions.filterNot { it.equals(query, ignoreCase = true) }, key = { "s:$it" }, contentType = { "query" }) { sug ->
                    QueryRow(sug, Icons.Filled.Search, onClick = { search(sug) })
                }
            }
            // results: albums and playlists first, then songs
            results != null -> {
                val found = results.data
                when {
                    results.loading && found == null -> item(key = "skeleton") { SkeletonRows() }
                    results.error != null && found == null -> item(key = "error", contentType = "message") {
                        Message("Couldn't search. Check your connection.", "Try again") { retry++ }
                    }
                    found == null || (found.songs.isEmpty() && found.lists.isEmpty()) ->
                        item(key = "none", contentType = "message") { Message("No results for \"$submitted\".") }
                    else -> {
                        if (found.lists.isNotEmpty()) {
                            item(key = "listsTitle", contentType = "title") { SectionTitle("Albums and playlists") }
                            item(key = "lists", contentType = "cards") {
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    itemsIndexed(found.lists, key = { i, l -> "$i-${l.id}" }, contentType = { _, _ -> "list" }) { _, l -> ListCard(l, onOpen) }
                                }
                            }
                        }
                        if (found.songs.isNotEmpty()) {
                            item(key = "songsTitle", contentType = "title") { SectionTitle("Songs", Modifier.padding(top = 8.dp)) }
                            itemsIndexed(found.songs, key = { i, t -> "r$i-${t.id}" }, contentType = { _, _ -> "track" }) { i, t ->
                                TrackRow(t) { onPlay(found.songs, i) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A search suggestion or a recent search: tap to search it; recent ones can be removed. */
@Composable
private fun QueryRow(text: String, icon: ImageVector, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Muted, modifier = Modifier.size(18.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(
    repo: YtmRepository,
    signedIn: Boolean,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onSignIn: () -> Unit,
) {
    // 0 liked songs, 1 playlists, 2 downloaded
    var view by rememberSaveable { mutableIntStateOf(0) }
    val showPlaylists = view == 1
    var retry by remember { mutableIntStateOf(0) }
    val header: @Composable () -> Unit = {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            Text("Library", style = MaterialTheme.typography.headlineLarge)
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = view == 0, onClick = { view = 0 }, label = { Text("Liked songs") })
                FilterChip(selected = view == 1, onClick = { view = 1 }, label = { Text("Playlists") })
                FilterChip(selected = view == 2, onClick = { view = 2 }, label = { Text("Downloaded") })
            }
        }
    }
    when {
        view == 2 -> DownloadedList(onPlay, header)
        !signedIn -> Column {
            header()
            Message("Sign in to see your liked songs and playlists.", "Sign in", onSignIn)
        }
        showPlaylists -> Column {
            header()
            PlaylistGrid(repo, onOpenPlaylist)
        }
        else -> TrackList(
            load = rememberLoad("liked", Lists.tracks, retry) { repo.likedSongs().also { Liked.seed(it) } },
            hide = { Liked.unliked(it.id) },
            emptyText = "Songs you like on YouTube Music show up here.",
            onPlay = onPlay,
            onRetry = { retry++ },
            showPlayButtons = true,
            header = header,
        )
    }
}

/** Songs saved on this phone; they play without a connection. */
@Composable
private fun DownloadedList(onPlay: (List<Track>, Int) -> Unit, header: @Composable () -> Unit) {
    val tracks = Downloads.tracks()
    val active = Downloads.progress.size
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { header() }
        if (active > 0) {
            item {
                Text(
                    "Downloading $active ${if (active == 1) "song" else "songs"}…",
                    color = OwlGlow,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }
        if (tracks.isEmpty()) {
            item {
                Message("Downloaded songs play without a connection. Use the menu on a song, or the download button on a playlist.")
            }
        } else {
            item {
                Column {
                    PlayButtons(tracks, onPlay, showDownload = false)
                    Text(
                        "%.1f MB on this phone".format(Downloads.totalBytes() / 1_048_576.0),
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            itemsIndexed(tracks, key = { i, t -> "d$i-${t.id}" }, contentType = { _, _ -> "track" }) { i, t -> TrackRow(t) { onPlay(tracks, i) } }
        }
    }
}

@Composable
private fun PlaylistGrid(repo: YtmRepository, onOpen: (Playlist) -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    var naming by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val load = rememberLoad("library", Lists.playlists, retry + PlaylistsChanged.version * 1000) { repo.playlists() }
    val playlists = load.data.orEmpty()
    when {
        load.loading && playlists.isEmpty() -> SkeletonRows(5)
        load.error != null && playlists.isEmpty() ->
            Message("Couldn't load your playlists. Check your connection and try again.", "Try again") { retry++ }
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // make a new, empty playlist
            item(key = "new") {
                Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { naming = true }.padding(4.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.5.dp, OwlGlow.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = OwlGlow, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("New playlist", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = OwlGlow)
                }
            }
            items(playlists.size, key = { playlists[it].id }) { i ->
                val pl = playlists[i]
                Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { onOpen(pl) }.padding(4.dp)) {
                    Artwork(pl.thumb?.let { bigArt(it) }, Modifier.fillMaxWidth().aspectRatio(1f), corner = 14.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(pl.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (pl.subtitle.isNotBlank()) {
                        Text(pl.subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (naming) {
        NameDialog(title = "New playlist", initial = "", confirm = "Create", onDismiss = { naming = false }, onDone = { name ->
            naming = false
            scope.launch {
                try {
                    repo.createPlaylist(name, emptyList())
                    Lists.playlists.remove("library")
                    PlaylistsChanged.version++
                    toast(context, "Made \"$name\"")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toast(context, "Couldn't make the playlist. Check your connection.")
                }
            }
        })
    }
}

@Composable
private fun PlaylistScreen(repo: YtmRepository, playlist: Playlist, onPlay: (List<Track>, Int) -> Unit, onBack: () -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // your own playlists ("VLPL…") can be renamed, deleted and have songs removed; albums and mixes can't
    val own = playlist.id.startsWith("VLPL")
    var title by remember(playlist.id) { mutableStateOf(playlist.title) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val removed = remember(playlist.id) { mutableStateMapOf<String, Boolean>() }   // setVideoId -> removed
    // Album songs come without their own cover or artist: use the album's.
    val albumArtist = remember(playlist.subtitle) { artistFromSubtitle(playlist.subtitle) }
    val load = rememberLoad("playlist:${playlist.id}", Lists.tracks, retry) {
        repo.playlist(playlist.id).map { t ->
            if (t.thumb != null && t.artist.isNotBlank()) t
            else t.copy(thumb = t.thumb ?: playlist.thumb, artist = t.artist.ifBlank { albumArtist })
        }
    }
    val remove: (Track) -> Unit = { t ->
        val entry = t.setVideoId
        if (entry == null) {
            toast(context, "Couldn't remove this song. Pull the list again and retry.")
        } else {
            removed[entry] = true
            scope.launch {
                try {
                    repo.removeFromPlaylist(playlist.id, t.id, entry)
                    Lists.tracks["playlist:${playlist.id}"]?.let { list ->
                        Lists.tracks["playlist:${playlist.id}"] = list.filterNot { it.setVideoId == entry }
                    }
                    toast(context, "Removed from $title")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    removed.remove(entry)
                    toast(context, "Couldn't remove it. You can only change your own playlists.")
                }
            }
        }
    }
    TrackList(
        load,
        "This playlist is empty. Add songs from any song's menu > Add to playlist.",
        onPlay,
        onRetry = { retry++ },
        showPlayButtons = true,
        hide = { t -> t.setVideoId?.let { removed[it] } == true },
        onRemove = if (own) remove else null,
    ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Spacer(Modifier.weight(1f))
                if (own) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Playlist options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    renaming = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete playlist") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    deleting = true
                                },
                            )
                        }
                    }
                }
            }
            Row(Modifier.padding(start = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(playlist.thumb?.let { bigArt(it) }, Modifier.size(120.dp), corner = 14.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (playlist.subtitle.isNotBlank()) {
                        Text(playlist.subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                }
            }
        }
    }
    if (renaming) {
        NameDialog(title = "Rename playlist", initial = title, confirm = "Rename", onDismiss = { renaming = false }, onDone = { name ->
            renaming = false
            scope.launch {
                try {
                    repo.renamePlaylist(playlist.id, name)
                    title = name
                    Lists.playlists.remove("library")
                    PlaylistsChanged.version++
                    toast(context, "Renamed to \"$name\"")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toast(context, "Couldn't rename it. Check your connection.")
                }
            }
        })
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete \"$title\"?") },
            text = { Text("It's removed from your YouTube Music library, on every device.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        try {
                            repo.deletePlaylist(playlist.id)
                            Lists.playlists.remove("library")
                            Lists.tracks.remove("playlist:${playlist.id}")
                            PlaylistsChanged.version++
                            toast(context, "Deleted \"$title\"")
                            onBack()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            toast(context, "Couldn't delete it. Check your connection.")
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        trailing()
    }
}

/** A setting with an on / off switch that remembers itself. */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    initial: Boolean,
    colors: androidx.compose.material3.SwitchColors,
    onChange: (Boolean) -> Unit,
) {
    var on by remember { mutableStateOf(initial) }
    SettingRow(title, subtitle) {
        Switch(
            checked = on,
            onCheckedChange = {
                on = it
                onChange(it)
            },
            colors = colors,
        )
    }
}

/** You > Car. */
@Composable
private fun CarSettings(settings: SettingsStore, switchColors: androidx.compose.material3.SwitchColors) {
    Text("Car", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 2.dp))
    ToggleRow(
        "Lyrics in the car",
        "Shows the current lyric line where the artist name goes: on Android Auto, car screens over Bluetooth and the lock screen.",
        settings.carLyrics(),
        switchColors,
    ) { settings.setCarLyrics(it) }
}

/** A setting with a few choices shown as chips under its title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(title: String, subtitle: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun AccountScreen(
    background: Color,
    onBackgroundChange: (Color) -> Unit,
    gradient: Boolean,
    onGradientChange: (Boolean) -> Unit,
    snakeHead: Boolean,
    onSnakeHeadChange: (Boolean) -> Unit,
    dynamicTheme: Boolean,
    onDynamicThemeChange: (Boolean) -> Unit,
    settings: SettingsStore,
    onSignOut: () -> Unit,
) {
    val themes = listOf("Default" to BoomaDefaultBg) + BoomaPalette
    val currentName = themes.firstOrNull { it.second == background }?.first ?: "Custom"
    val switchColors = SwitchDefaults.colors(checkedTrackColor = OwlGlow, checkedThumbColor = MaterialTheme.colorScheme.background)
    var keepPlaying by remember { mutableStateOf(settings.keepPlayingAfterClose()) }
    var normalize by remember { mutableStateOf(settings.normalizeVolume()) }
    var crossfade by remember { mutableIntStateOf(settings.crossfadeSeconds()) }
    var quality by remember { mutableStateOf(settings.audioQuality()) }
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("You", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Signed in to YouTube Music. Your library matches Booma on PC.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
                TextButton(onClick = onSignOut) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
            }
        }
        item {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Theme", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(currentName, color = Muted)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                themes.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (name, color) ->
                            val selected = color == background
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        if (selected) 3.dp else 1.dp,
                                        if (selected) OwlCore else Color.White.copy(alpha = 0.25f),
                                        CircleShape,
                                    )
                                    .clickable(onClickLabel = name) { onBackgroundChange(color) },
                            )
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(top = 12.dp)) {
            SettingRow("Match the song's cover", "Colours follow the album art of the song playing. The theme above is used when nothing plays.") {
                Switch(checked = dynamicTheme, onCheckedChange = onDynamicThemeChange, colors = switchColors)
            }
            SettingRow("Gradient background", "Fades the theme colour from top to bottom, like Booma on PC.") {
                Switch(checked = gradient, onCheckedChange = onGradientChange, colors = switchColors)
            }
            SettingRow("Snake head", "Shows the head on the snake progress bar.") {
                Switch(checked = snakeHead, onCheckedChange = onSnakeHeadChange, colors = switchColors)
            }
            SettingRow("Keep playing after closing Booma", "When off, swiping Booma away from recent apps stops the music.") {
                Switch(
                    checked = keepPlaying,
                    onCheckedChange = {
                        keepPlaying = it
                        settings.setKeepPlayingAfterClose(it)
                    },
                    colors = switchColors,
                )
            }
            Text("Sound", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 2.dp))
            SettingRow("Even out volume", "Loud songs are turned down to the same level as the rest, like YouTube does.") {
                Switch(
                    checked = normalize,
                    onCheckedChange = {
                        normalize = it
                        settings.setNormalizeVolume(it)
                    },
                    colors = switchColors,
                )
            }
            ChoiceRow(
                title = "Crossfade",
                subtitle = "Songs fade into each other.",
                options = listOf(0 to "Off", 3 to "3 s", 6 to "6 s", 10 to "10 s"),
                selected = crossfade,
                onSelect = {
                    crossfade = it
                    settings.setCrossfadeSeconds(it)
                },
            )
            ChoiceRow(
                title = "Audio quality",
                subtitle = "Data saver uses about a third of the data. Downloads use this too.",
                options = listOf("high" to "High", "low" to "Data saver"),
                selected = quality,
                onSelect = {
                    quality = it
                    settings.setAudioQuality(it)
                },
            )
            CarSettings(settings, switchColors)
            CrashReport.text?.let { report ->
                SettingRow("Booma closed unexpectedly", "Copy the error report and send it so it can be fixed.") {
                    TextButton(onClick = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Booma crash report", report))
                        CrashReport.text = null
                        toast(context, "Report copied")
                    }) { Text("Copy", color = OwlGlow) }
                }
            }
            Box(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                TextButton(onClick = onSignOut) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
            }
            }
        }
    }
}
