package com.booma.app.ui

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp as ThumbUpOutline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.booma.app.data.Downloads
import com.booma.app.data.Liked
import com.booma.app.data.Lyrics
import com.booma.app.data.LyricsClient
import com.booma.app.data.Track
import com.booma.app.data.YtmRepository
import com.booma.app.player.PlayerHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Panel { COVER, LYRICS, QUEUE }

/**
 * Full-screen player: the dimmed cover behind; the cover, lyrics or up-next list in the middle;
 * the Booma snake seek bar and controls below. Swipe down to close.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(repo: YtmRepository, startOnQueue: Boolean = false, onClose: () -> Unit) {
    // Position lives in state holders read only by the parts that show it (time labels, snake, lyrics),
    // so the timer never rebuilds the whole screen.
    val position = remember { mutableLongStateOf(0L) }
    val duration = remember { mutableLongStateOf(0L) }
    val scrub = remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            position.longValue = PlayerHolder.position()
            duration.longValue = PlayerHolder.duration()
            delay(250)
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = LocalTrackActions.current
    var panel by remember { mutableStateOf(if (startOnQueue) Panel.QUEUE else Panel.COVER) }
    var saveOpen by remember { mutableStateOf(false) }

    // swipe down to close
    val offset = remember { Animatable(0f) }
    val closeAt = with(LocalDensity.current) { 140.dp.toPx() }
    val dragState = rememberDraggableState { delta ->
        scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) }
    }

    // swipe sideways on the cover or the title: left for the next song, right for the previous one
    val swipe = remember { Animatable(0f) }
    val skipAt = with(LocalDensity.current) { 90.dp.toPx() }
    val swipeToSkip = Modifier
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val moved = swipe.value
                    when {
                        moved < -skipAt -> PlayerHolder.next()
                        moved > skipAt -> PlayerHolder.previousSong()
                    }
                    scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f)) }
                },
                onDragCancel = { scope.launch { swipe.animateTo(0f, spring()) } },
            ) { change, amount ->
                change.consume()
                scope.launch { swipe.snapTo(swipe.value + amount) }
            }
        }
        .graphicsLayer {
            translationX = swipe.value * 0.7f
            alpha = 1f - (kotlin.math.abs(swipe.value) / (skipAt * 4f)).coerceAtMost(0.4f)
        }

    val dimmed = Color.White.copy(alpha = 0.6f)
    val bg = MaterialTheme.colorScheme.background
    val current = PlayerHolder.currentTrack

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = offset.value }
            .boomaBackground()
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (offset.value > closeAt || velocity > 2500f) onClose()
                    else offset.animateTo(0f, spring(dampingRatio = 0.8f))
                },
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        // the cover, large and dimmed, behind everything (no blur: blurring the full screen costs frames)
        PlayerHolder.artwork?.let { url ->
            Artwork(url, Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f }, corner = 0.dp, fade = true)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg.copy(alpha = 0.6f), bg.copy(alpha = 0.9f), bg))))

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(top = 6.dp).size(width = 40.dp, height = 4.dp).background(Color.White.copy(alpha = 0.3f), CircleShape))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.weight(1f))
                CastButton(tint = dimmed)
                SleepTimerButton(dimmed)
            }

            // middle: cover, lyrics or up next
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Crossfade(targetState = panel, animationSpec = tween(250), label = "panel") { p ->
                    when (p) {
                        Panel.COVER -> Box(swipeToSkip, contentAlignment = Alignment.Center) { CoverPanel() }
                        Panel.LYRICS -> LyricsPanel { position.longValue }
                        Panel.QUEUE -> QueuePanel(onSave = { saveOpen = true })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).then(swipeToSkip)) {
                    Text(PlayerHolder.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1, modifier = Modifier.basicMarquee())
                    Text(PlayerHolder.artist, style = MaterialTheme.typography.titleMedium, color = dimmed, maxLines = 1, modifier = Modifier.basicMarquee())
                }
                if (current != null) {
                    val liked = Liked.isLiked(current.id)
                    IconButton(onClick = { actions?.like(current) }) {
                        if (liked) {
                            Icon(Icons.Filled.ThumbUp, contentDescription = "Remove like", tint = OwlGlow)
                        } else {
                            Icon(Icons.Outlined.ThumbUpOutline, contentDescription = "Like", tint = dimmed)
                        }
                    }
                    val progress = Downloads.progress[current.id]
                    IconButton(onClick = {
                        when {
                            Downloads.byId.containsKey(current.id) -> toast(context, "This song is on your phone")
                            progress == null -> {
                                Downloads.download(current)
                                toast(context, "Downloading ${current.title}")
                            }
                        }
                    }) {
                        when {
                            progress != null -> CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(22.dp),
                                color = OwlGlow,
                                strokeWidth = 2.5.dp,
                            )
                            Downloads.byId.containsKey(current.id) ->
                                Icon(Icons.Filled.DownloadDone, contentDescription = "Downloaded", tint = OwlGlow)
                            else -> Icon(Icons.Filled.Download, contentDescription = "Download", tint = dimmed)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            SnakeSeekBar(
                positionMs = { position.longValue },
                durationMs = { duration.longValue },
                playing = PlayerHolder.playing,
                onSeek = { PlayerHolder.seekTo(it) },
                onScrub = { scrub.value = it },
            )
            TimeLabels(position, duration, scrub, dimmed)
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { PlayerHolder.toggleShuffle() }) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (PlayerHolder.shuffle) OwlGlow else dimmed)
                }
                IconButton(onClick = { PlayerHolder.previous() }, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(38.dp))
                }
                Surface(
                    onClick = { PlayerHolder.togglePlayPause() },
                    shape = CircleShape,
                    color = OwlCore,
                    shadowElevation = 10.dp,
                    modifier = Modifier.size(74.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (PlayerHolder.buffering) {
                            CircularProgressIndicator(color = bg, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                        } else {
                            Crossfade(targetState = PlayerHolder.playing, animationSpec = tween(150), label = "playIcon") { playing ->
                                Icon(
                                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = bg,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = { PlayerHolder.next() }, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = { PlayerHolder.cycleRepeat() }) {
                    Icon(
                        if (PlayerHolder.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (PlayerHolder.repeatMode != Player.REPEAT_MODE_OFF) OwlGlow else dimmed,
                    )
                }
            }
            CastPanel(Modifier.padding(top = 4.dp))
            PlayerHolder.lastError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // bottom: switch the middle panel, add to playlist
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val chipColors = FilterChipDefaults.filterChipColors(
                    labelColor = Color.White.copy(alpha = 0.85f),
                    selectedContainerColor = OwlCore,
                    selectedLabelColor = bg,
                )

                FilterChip(
                    selected = panel == Panel.LYRICS,
                    onClick = { panel = if (panel == Panel.LYRICS) Panel.COVER else Panel.LYRICS },
                    label = { Text("Lyrics") },
                    colors = chipColors,
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = panel == Panel.LYRICS,
                        borderColor = Color.White.copy(alpha = 0.35f),
                    ),
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = panel == Panel.QUEUE,
                    onClick = { panel = if (panel == Panel.QUEUE) Panel.COVER else Panel.QUEUE },
                    label = { Text("Up next") },
                    colors = chipColors,
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = panel == Panel.QUEUE,
                        borderColor = Color.White.copy(alpha = 0.35f),
                    ),
                )
                Spacer(Modifier.weight(1f))
                if (current != null) {
                    IconButton(onClick = { actions?.addToPlaylist(current) }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to playlist", tint = dimmed)
                    }
                }
            }
        }
    }

    if (saveOpen) {
        SaveQueueDialog(
            onSave = { name ->
                saveOpen = false
                val ids = PlayerHolder.queue.map { it.id }
                scope.launch {
                    try {
                        repo.createPlaylist(name, ids)
                        toast(context, "Saved \"$name\" to your playlists")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        toast(context, "Couldn't save the playlist. Check your connection.")
                    }
                }
            },
            onDismiss = { saveOpen = false },
        )
    }
}

/** Elapsed / total time. Only this small row updates as the song plays. */
@Composable
private fun TimeLabels(position: State<Long>, duration: State<Long>, scrub: State<Float?>, color: Color) {
    val total = duration.value
    val shown = scrub.value?.let { (it * total).toLong() } ?: position.value
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Text(fmt(shown), color = color, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        Text(fmt(total), color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CoverPanel() {
    val coverScale by animateFloatAsState(
        targetValue = if (PlayerHolder.playing) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "coverScale",
    )
    Crossfade(targetState = PlayerHolder.artwork, animationSpec = tween(400), label = "cover") { url ->
        Artwork(
            url,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = coverScale
                    scaleY = coverScale
                }
                .shadow(12.dp, RoundedCornerShape(22.dp)),
            corner = 22.dp,
        )
    }
}

/** Lyrics that follow the song: the current line is bright and stays in view; tap a line to jump to it. */
@Composable
private fun LyricsPanel(positionMs: () -> Long) {
    val track = PlayerHolder.currentTrack
    val id = track?.id
    var lyrics by remember(id) { mutableStateOf<Lyrics?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    LaunchedEffect(id) {
        loading = true
        lyrics = if (track == null) null else try {
            LyricsClient.fetch(track.id, track.title, track.artist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        loading = false
    }
    val dim = Color.White.copy(alpha = 0.6f)
    val l = lyrics
    when {
        loading -> Text("Finding lyrics…", color = dim)
        l == null -> Text("No lyrics found for this song.", color = dim)
        else -> {
            // recomposes only when the current line changes, not on every position update
            val currentLine by remember(l) {
                derivedStateOf { if (l.synced) l.lines.indexOfLast { it.timeMs <= positionMs() + 250 } else -1 }
            }
            val listState = rememberLazyListState()
            LaunchedEffect(currentLine) {
                if (currentLine >= 0) {
                    val third = listState.layoutInfo.viewportSize.height / 3
                    listState.animateScrollToItem(currentLine, -third)
                }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 60.dp)) {
                itemsIndexed(l.lines) { i, line ->
                    val active = i == currentLine
                    val color by animateColorAsState(
                        if (active) OwlCore else Color.White.copy(alpha = if (l.synced) 0.4f else 0.85f),
                        label = "lyricColor",
                    )
                    Text(
                        line.text.ifBlank { "♪" },
                        style = if (l.synced) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        color = color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = l.synced) { PlayerHolder.seekTo(line.timeMs) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

private class QueueRow(val key: Int, val index: Int, val track: Track)

private fun <T> List<T>.swapped(a: Int, b: Int): List<T> = toMutableList().also {
    val t = it[a]
    it[a] = it[b]
    it[b] = t
}

/**
 * The play queue. Drag a song by its handle to move it, tap a song to jump to it, ✕ to remove it,
 * or save the whole queue as a playlist.
 */
@Composable
private fun QueuePanel(onSave: () -> Unit) {
    val queue = PlayerHolder.queue
    val now = PlayerHolder.queueIndex
    val dim = Color.White.copy(alpha = 0.6f)
    val rowHeight = 60.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }

    var rows by remember { mutableStateOf(queue.mapIndexed { i, t -> QueueRow(i, i, t) }) }
    var draggingKey by remember { mutableStateOf<Int?>(null) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(queue) {
        if (draggingKey == null) rows = queue.mapIndexed { i, t -> QueueRow(i, i, t) }
    }

    fun dragBy(dy: Float) {
        val key = draggingKey ?: return
        dragOffset += dy
        var i = rows.indexOfFirst { it.key == key }
        while (dragOffset > rowPx / 2 && i in 0 until rows.lastIndex) {
            rows = rows.swapped(i, i + 1)
            i++
            dragOffset -= rowPx
        }
        while (dragOffset < -rowPx / 2 && i > 0) {
            rows = rows.swapped(i, i - 1)
            i--
            dragOffset += rowPx
        }
    }

    fun drop() {
        val key = draggingKey ?: return
        val to = rows.indexOfFirst { it.key == key }
        if (dragFrom >= 0 && to >= 0 && to != dragFrom) PlayerHolder.move(dragFrom, to)
        draggingKey = null
        dragFrom = -1
        dragOffset = 0f
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (now - 1).coerceAtLeast(0))
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Up next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (queue.size > 1) TextButton(onClick = onSave) { Text("Save as playlist", color = OwlGlow) }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(rows, key = { _, r -> r.key }) { _, r ->
                val dragging = r.key == draggingKey
                val isNow = r.index == now && r.track.id == PlayerHolder.currentId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .then(
                            if (dragging) {
                                Modifier
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        translationY = dragOffset
                                        shadowElevation = 16f
                                    }
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            } else {
                                Modifier.animateItem()
                            },
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = draggingKey == null) { PlayerHolder.jumpTo(r.index) }
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(r.track.thumb, Modifier.size(44.dp), corner = 8.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.track.title,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isNow) OwlGlow else Color.Unspecified,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(r.track.artist, color = dim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (isNow) {
                        BoomaOwl(modifier = Modifier.size(30.dp), playing = PlayerHolder.playing)
                    } else {
                        IconButton(onClick = { PlayerHolder.removeAt(r.index) }, enabled = draggingKey == null) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove from queue", tint = dim)
                        }
                    }
                    // drag handle: hold and slide up or down
                    Box(
                        Modifier
                            .size(48.dp)
                            .pointerInput(r.key) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingKey = r.key
                                        dragFrom = rows.indexOfFirst { it.key == r.key }
                                        dragOffset = 0f
                                    },
                                    onDragEnd = { drop() },
                                    onDragCancel = { drop() },
                                ) { change, amount ->
                                    change.consume()
                                    dragBy(amount.y)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.DragHandle, contentDescription = "Drag to reorder", tint = if (dragging) OwlGlow else dim)
                    }
                }
            }
        }
    }
}

/** Pause after a while, or at the end of the current song. Shows the minutes left while it runs. */
@Composable
private fun SleepTimerButton(dimmed: Color) {
    var open by remember { mutableStateOf(false) }
    var minutesLeft by remember { mutableStateOf<Long?>(null) }
    val endsAt = PlayerHolder.sleepEndsAt
    LaunchedEffect(endsAt) {
        while (true) {
            minutesLeft = endsAt?.let { ((it - SystemClock.elapsedRealtime()) / 60_000 + 1).coerceAtLeast(0) }
            delay(10_000)
        }
    }
    val active = endsAt != null || PlayerHolder.sleepAtEndOfSong
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(18.dp)).clickable { open = true }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Timer, contentDescription = "Sleep timer", tint = if (active) OwlGlow else dimmed)
            val label = when {
                PlayerHolder.sleepAtEndOfSong -> "End of song"
                minutesLeft != null -> "$minutesLeft min"
                else -> null
            }
            if (label != null) {
                Spacer(Modifier.width(4.dp))
                Text(label, color = OwlGlow, style = MaterialTheme.typography.bodySmall)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour").forEach { (minutes, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    PlayerHolder.setSleepTimer(minutes)
                    open = false
                })
            }
            DropdownMenuItem(text = { Text("End of this song") }, onClick = {
                PlayerHolder.sleepAtEndOfThisSong()
                open = false
            })
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
