package com.booma.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.booma.app.data.Streams
import com.booma.app.data.Track

/** The app's connection to the playback service, shared by every screen. */
@androidx.annotation.OptIn(UnstableApi::class)
object PlayerHolder {
    var controller: MediaController? by mutableStateOf(null)
        private set
    var title: String by mutableStateOf("Nothing playing")
        private set
    var artist: String by mutableStateOf("Pick a song to start")
        private set
    var artwork: String? by mutableStateOf(null)
        private set
    var currentId: String? by mutableStateOf(null)
        private set
    private var localPlaying: Boolean by mutableStateOf(false)
    private var localBuffering: Boolean by mutableStateOf(false)

    /** Playing (on the phone, or on the TV while casting). */
    val playing: Boolean get() = if (CastBridge.active) CastBridge.playing else localPlaying

    val buffering: Boolean get() = if (CastBridge.active) CastBridge.buffering else localBuffering

    /** Where the song is now, in ms (the TV's position while casting). */
    fun position(): Long = if (CastBridge.active) CastBridge.position() else controller?.currentPosition ?: 0L

    fun duration(): Long = if (CastBridge.active) CastBridge.duration() else controller?.duration?.coerceAtLeast(0L) ?: 0L
    var shuffle: Boolean by mutableStateOf(false)
        private set
    var repeatMode: Int by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var lastError: String? by mutableStateOf(null)
        private set

    /** The play queue, in play order of the list (not shuffle order). */
    var queue: List<Track> by mutableStateOf(emptyList())
        private set
    var queueIndex: Int by mutableIntStateOf(-1)
        private set

    /** Sleep timer: when it ends (elapsed-realtime ms), or "end of this song". */
    var sleepEndsAt: Long? by mutableStateOf(null)
        private set
    var sleepAtEndOfSong: Boolean by mutableStateOf(false)
        private set

    val currentTrack: Track? get() = queue.getOrNull(queueIndex)

    private val retried = HashSet<String>()
    private val handler = Handler(Looper.getMainLooper())
    private val sleepRunnable = Runnable {
        controller?.pause()
        sleepEndsAt = null
    }

    fun connect(context: Context) {
        if (controller?.isConnected == true) return
        controller = null
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    this@PlayerHolder.controller = null
                    localPlaying = false
                }
            })
            .buildAsync()
        future.addListener({
            val c = future.get()
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    localPlaying = isPlaying
                    if (isPlaying) c.currentMediaItem?.mediaId?.let { retried.remove(it) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    localBuffering = playbackState == Player.STATE_BUFFERING
                }

                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    title = mediaMetadata.title?.toString() ?: "Nothing playing"
                    artist = mediaMetadata.realArtist()
                    artwork = mediaMetadata.artworkUri?.toString()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentId = mediaItem?.mediaId
                    queueIndex = c.currentMediaItemIndex
                    lastError = null
                    if (sleepAtEndOfSong && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        c.pause()
                        sleepAtEndOfSong = false
                    }
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    refreshQueue(c)
                }

                override fun onRepeatModeChanged(mode: Int) {
                    repeatMode = mode
                }

                override fun onPlayerError(error: PlaybackException) {
                    val id = c.currentMediaItem?.mediaId
                    Log.w("Booma", "playback error for $id: ${error.errorCodeName}", error)
                    if (id != null && retried.add(id)) {
                        // Stream links expire: fetch a fresh one once and try again.
                        Streams.forget(id)
                        c.prepare()
                        c.play()
                    } else {
                        lastError = "Couldn't play this song (${error.errorCodeName})."
                    }
                }
            })
            if (c.shuffleModeEnabled) c.shuffleModeEnabled = false
            localPlaying = c.isPlaying
            currentId = c.currentMediaItem?.mediaId
            repeatMode = c.repeatMode
            refreshQueue(c)
            controller = c
        }, ContextCompat.getMainExecutor(context))
    }

    private fun refreshQueue(c: Player) {
        queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toTrack() }
        queueIndex = c.currentMediaItemIndex
    }

    /** The list order to go back to when shuffle is turned off. */
    private var unshuffled: List<MediaItem>? = null

    /**
     * Replace the queue with [tracks], starting at [startIndex]. With shuffle on, that song plays first
     * and the rest follow in random order.
     */
    fun playQueue(tracks: List<Track>, startIndex: Int) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        val start = startIndex.coerceIn(0, tracks.lastIndex)
        val items = tracks.map { it.toMediaItem() }
        if (shuffle) {
            unshuffled = items
            val first = items[start]
            c.setMediaItems(listOf(first) + items.filterIndexed { i, _ -> i != start }.shuffled(), 0, 0L)
        } else {
            unshuffled = null
            c.setMediaItems(items, start, 0L)
        }
        c.prepare()
        c.play()
    }

    /** Play [tracks] in random order with shuffle on. */
    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        shuffle = true
        playQueue(tracks, tracks.indices.random())
    }

    /**
     * Add [track] right after the song playing now, like Spotify and Booma on PC. Several songs added
     * this way line up in the order you added them, before the rest of the queue.
     */
    fun addToQueue(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playQueue(listOf(track), 0)
            return
        }
        var pos = c.currentMediaItemIndex + 1
        while (pos < c.mediaItemCount && c.getMediaItemAt(pos).isHandAdded()) pos++
        c.addMediaItem(pos, track.toMediaItem(handAdded = true))
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun removeAt(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount && index != c.currentMediaItemIndex) c.removeMediaItem(index)
    }

    fun move(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun togglePlayPause() {
        if (CastBridge.active) {
            CastBridge.togglePlayPause()
            return
        }
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    /** Straight to the previous song (a swipe; the button restarts the song first when it's past 3 s). */
    fun previousSong() {
        controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0L) }
    }

    fun seekTo(ms: Long) {
        if (CastBridge.active) CastBridge.seekTo(ms) else controller?.seekTo(ms)
    }

    /**
     * Shuffle reorders the real queue, so Up next always shows what plays next: the current song stays,
     * everything else is shuffled after it. Turning it off brings back the original order (songs you
     * queued meanwhile stay right after the current one).
     */
    fun toggleShuffle() {
        val c = controller ?: return
        val n = c.mediaItemCount
        if (n == 0) {
            shuffle = !shuffle
            return
        }
        val cur = c.currentMediaItemIndex
        val items = (0 until n).map { c.getMediaItemAt(it) }
        val saved = unshuffled
        // keep only the current song, then rebuild around it (the current song keeps playing)
        if (cur + 1 < n) c.removeMediaItems(cur + 1, n)
        if (cur > 0) c.removeMediaItems(0, cur)
        if (saved == null) {
            unshuffled = items
            c.addMediaItems(items.filterIndexed { i, _ -> i != cur }.shuffled())
            shuffle = true
        } else {
            unshuffled = null
            val savedIds = saved.map { it.mediaId }.toSet()
            val added = items.filterIndexed { i, item -> i != cur && item.mediaId !in savedIds }
            val pos = saved.indexOfFirst { it.mediaId == items[cur].mediaId }
            if (pos < 0) {
                c.addMediaItems(added + saved)
            } else {
                if (pos > 0) c.addMediaItems(0, saved.subList(0, pos))
                c.addMediaItems(added + saved.subList(pos + 1, saved.size))
            }
            shuffle = false
        }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    /** Pause after [minutes]; 0 turns the timer off. */
    fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleepRunnable)
        sleepAtEndOfSong = false
        sleepEndsAt = null
        if (minutes > 0) {
            handler.postDelayed(sleepRunnable, minutes * 60_000L)
            sleepEndsAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        }
    }

    fun sleepAtEndOfThisSong() {
        handler.removeCallbacks(sleepRunnable)
        sleepEndsAt = null
        sleepAtEndOfSong = true
    }

    /** Songs added with "Add to queue" (they line up after the current song). */
    private fun MediaItem.isHandAdded(): Boolean = mediaMetadata.extras?.getBoolean(HAND_ADDED_KEY) == true


    private fun MediaItem.toTrack(): Track = Track(
        id = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.realArtist(),
        thumb = mediaMetadata.artworkUri?.toString(),
    )
}

/** A queue item for [this] song; the player looks the audio up from the media id when it plays. */
fun Track.toMediaItem(handAdded: Boolean = false): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(PlaybackService.streamUri(id))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(thumb?.let { Uri.parse(bigArt(it)) })
            .setExtras(Bundle().apply { putBoolean(HAND_ADDED_KEY, handAdded) })
            .build()
    )
    .build()

internal const val HAND_ADDED_KEY = "booma.handAdded"

/** Where the real artist is kept while "lyrics in the car" shows the current lyric line as the artist. */
internal const val REAL_ARTIST_KEY = "booma.realArtist"

/** The song's artist, even while the artist line shows lyrics. */
fun MediaMetadata.realArtist(): String = extras?.getString(REAL_ARTIST_KEY) ?: artist?.toString().orEmpty()

/** Ask Google's image server for a large square version of a thumbnail (local files are left as they are). */
fun bigArt(url: String): String =
    url.replace(Regex("=w\\d+-h\\d+"), "=w544-h544").replace(Regex("=s\\d+"), "=s544")
