package com.booma.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp as ThumbUpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.booma.app.data.Downloads
import com.booma.app.data.Liked
import com.booma.app.data.Playlist
import com.booma.app.data.Track
import com.booma.app.data.YtmRepository
import com.booma.app.player.PlayerHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What the song menu can do. Provided once by the app, used by every list and the player. */
class TrackActions(
    val startRadio: (Track) -> Unit,
    val like: (Track) -> Unit,
    val addToPlaylist: (Track) -> Unit,
)

val LocalTrackActions = staticCompositionLocalOf<TrackActions?> { null }

fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

@Composable
fun rememberTrackActions(repo: YtmRepository, onPickPlaylist: (Track) -> Unit, onLiked: () -> Unit): TrackActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(repo) {
        TrackActions(
            startRadio = { t ->
                scope.launch {
                    toast(context, "Starting radio from ${t.title}")
                    try {
                        val list = repo.radio(t.id)
                        if (list.isEmpty()) toast(context, "No radio found for this song.") else PlayerHolder.playQueue(list, 0)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        toast(context, "Couldn't start radio. Check your connection.")
                    }
                }
            },
            like = { t ->
                scope.launch {
                    try {
                        val liked = Liked.toggle(repo, t)
                        onLiked()
                        toast(context, if (liked) "Added to your liked songs" else "Removed from your liked songs")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        toast(context, "Couldn't change the like. Check your connection.")
                    }
                }
            },
            addToPlaylist = onPickPlaylist,
        )
    }
}

/** Download state for a song: a tick when saved, a progress ring while downloading, nothing otherwise. */
@Composable
fun DownloadMark(track: Track, modifier: Modifier = Modifier) {
    // derived state: this row only redraws when its own song's download changes
    val progress by remember(track.id) { derivedStateOf { Downloads.progress[track.id] } }
    val saved by remember(track.id) { derivedStateOf { Downloads.byId.containsKey(track.id) } }
    val p = progress
    when {
        p != null -> CircularProgressIndicator(
            progress = { p },
            modifier = modifier.size(18.dp),
            color = OwlGlow,
            strokeWidth = 2.dp,
        )
        saved -> Icon(
            Icons.Filled.DownloadDone,
            contentDescription = "Downloaded",
            tint = OwlGlow,
            modifier = modifier.size(18.dp),
        )
    }
}

/** The ⋮ button on a song, with everything you can do to it. [onRemove]: the song is in your own playlist. */
@Composable
fun TrackMenuButton(track: Track, onRemove: (() -> Unit)? = null) {
    val actions = LocalTrackActions.current ?: return
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More for ${track.title}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // read download state only while the menu is open, so rows don't redraw on every progress tick
            val downloaded = Downloads.byId.containsKey(track.id)
            val downloading = Downloads.progress.containsKey(track.id)
            MenuItem("Add to queue", Icons.AutoMirrored.Filled.QueueMusic) {
                open = false
                PlayerHolder.addToQueue(track)
                toast(context, "Added to queue")
            }
            MenuItem("Start radio", Icons.Filled.Radio) {
                open = false
                actions.startRadio(track)
            }
            if (Liked.isLiked(track.id)) {
                MenuItem("Remove like", Icons.Outlined.ThumbUpOutline) {
                    open = false
                    actions.like(track)
                }
            } else {
                MenuItem("Like", Icons.Filled.ThumbUp) {
                    open = false
                    actions.like(track)
                }
            }
            MenuItem("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) {
                open = false
                actions.addToPlaylist(track)
            }
            if (onRemove != null) {
                MenuItem("Remove from this playlist", Icons.Filled.RemoveCircleOutline) {
                    open = false
                    onRemove()
                }
            }
            when {
                downloaded -> MenuItem("Remove download", Icons.Filled.Delete) {
                    open = false
                    Downloads.remove(track.id)
                    toast(context, "Removed from this phone")
                }
                downloading -> MenuItem("Downloading…", Icons.Filled.Download) {
                    open = false
                }
                else -> MenuItem("Download", Icons.Filled.Download) {
                    open = false
                    Downloads.download(track)
                    toast(context, "Downloading ${track.title}")
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/** Pick one of your own playlists to add a song to. */
@Composable
fun AddToPlaylistDialog(
    track: Track,
    loadPlaylists: suspend () -> List<Playlist>,
    onPick: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var lists by remember { mutableStateOf<List<Playlist>?>(null) }
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        NameDialog(
            title = "New playlist with \"${track.title}\"",
            initial = "",
            confirm = "Create",
            onDone = onCreate,
            onDismiss = { naming = false },
        )
        return
    }
    LaunchedEffect(Unit) {
        lists = try {
            loadPlaylists().filter { it.id.startsWith("VLPL") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add \"${track.title}\" to…", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            val l = lists
            when {
                l == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OwlGlow)
                }
                else -> LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { naming = true }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = OwlGlow)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("New playlist", style = MaterialTheme.typography.bodyLarge, color = OwlGlow)
                        }
                    }
                    items(l.size) { i ->
                        val pl = l[i]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(pl) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(pl.thumb, Modifier.size(44.dp), corner = 8.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(pl.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
    )
}

/** Name the playlist that the current queue is saved into. */
@Composable
fun SaveQueueDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("Booma queue") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save queue as a playlist") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Playlist name") })
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifEmpty { "Booma queue" }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Ask for a name (new playlist, rename). */
@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Playlist name") })
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onDone(name.trim()) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
