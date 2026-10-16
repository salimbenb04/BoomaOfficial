package com.booma.app.player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.booma.app.data.Streams
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Plays Booma's queue on a TV or speaker with Google Cast (Chromecast, Android TV / Google TV, Nest),
 * using Google's standard TV player (cover, title, progress).
 *
 * The phone keeps the real queue; the Cast device gets the song playing plus the next few, each with an
 * audio link it can fetch itself, and more are added as it goes. Skipping, picking a song or changing
 * the queue on the phone moves the Cast device along with it. When casting stops, the phone carries on
 * from where the TV was (paused).
 *
 * Nothing in here may crash the app: every step that talks to the TV is guarded, and a song the TV can't
 * play is skipped with a message.
 */
@OptIn(UnstableApi::class)
object CastBridge {
    /** True while a Cast device is connected. */
    var active by mutableStateOf(false)
        private set
    var deviceName by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set

    /** The TV's volume, 0..1. */
    var volume by mutableFloatStateOf(1f)
        private set

    private const val AHEAD = 3   // songs sent ahead of the one playing
    private var remote: RemoteCastPlayer? = null
    private var local: Player? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e -> Log.w("Booma", "cast step failed", e) }
    )
    private var loadJob: Job? = null
    private var extendJob: Job? = null
    private var syncing = false     // true while the phone follows the TV, so it doesn't send the song back

    /** Sets Cast up for the player service. Does nothing on phones without Google Play services. */
    fun init(context: Context, player: Player) {
        if (remote != null) return
        local = player
        appContext = context.applicationContext
        runCatching {
            val castContext = CastContext.getSharedInstance(context.applicationContext)
            remote = RemoteCastPlayer.Builder(context.applicationContext).build().also { r ->
                r.addListener(remoteListener)
                r.setSessionAvailabilityListener(sessionListener)
                if (r.isCastSessionAvailable) sessionListener.onCastSessionAvailable()
            }
            player.addListener(localListener)
            castContext.sessionManager.addSessionManagerListener(failures, CastSession::class.java)
        }.onFailure { Log.w("Booma", "Cast unavailable", it) }
    }

    fun release() {
        loadJob?.cancel()
        extendJob?.cancel()
        runCatching {
            local?.removeListener(localListener)
            remote?.removeListener(remoteListener)
            remote?.release()
        }
        remote = null
        local = null
        active = false
    }

    fun position(): Long = runCatching { remote?.currentPosition }.getOrNull() ?: 0L

    fun duration(): Long = runCatching { remote?.duration?.takeIf { it != C.TIME_UNSET } }.getOrNull() ?: 0L

    fun togglePlayPause() {
        guard {
            val r = remote ?: return@guard
            if (r.playWhenReady) r.pause() else r.play()
        }
    }

    fun seekTo(ms: Long) {
        guard { remote?.seekTo(ms) }
    }

    fun changeVolume(value: Float) {
        guard {
            val r = remote ?: return@guard
            val max = r.deviceInfo.maxVolume.takeIf { it > 0 } ?: return@guard
            volume = value.coerceIn(0f, 1f)
            r.setDeviceVolume((volume * max).roundToInt(), 0)
        }
    }

    /** Stop casting (the phone takes over, paused). */
    fun disconnect(context: Context) {
        runCatching { CastContext.getSharedInstance(context.applicationContext).sessionManager.endCurrentSession(true) }
    }

    /** Runs a step that talks to the TV; if it fails, it's logged instead of crashing Booma. */
    private fun guard(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w("Booma", "cast call failed", e)
        }
    }

    private fun say(text: String) {
        appContext?.let { Toast.makeText(it, text, Toast.LENGTH_LONG).show() }
    }

    /** Says why casting didn't start, instead of failing silently. */
    private val failures = object : SessionManagerListener<CastSession> {
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            say("Couldn't start casting (${CastStatusCodes.getStatusCodeString(error)}). Check the phone and TV are on the same Wi-Fi.")
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStarted(session: CastSession, sessionId: String) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionEnded(session: CastSession, error: Int) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    private val sessionListener = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() {
            guard {
                active = true
                deviceName = runCatching {
                    appContext?.let { CastContext.getSharedInstance(it).sessionManager.currentCastSession?.castDevice?.friendlyName }
                }.getOrNull()
                remote?.let { r ->
                    val max = r.deviceInfo.maxVolume
                    if (max > 0) volume = r.deviceVolume.toFloat() / max
                }
                val l = local
                val startAt = l?.currentPosition ?: 0L
                l?.pause()
                loadFromPhone(startPosition = startAt)
            }
        }

        override fun onCastSessionUnavailable() {
            guard {
                val r = remote
                val l = local
                if (r != null && l != null) {
                    // carry on from the TV's song and position on the phone (paused)
                    val index = l.indexOf(r.currentMediaItem?.mediaId)
                    val pos = r.currentPosition
                    syncing = true
                    try {
                        if (index != C.INDEX_UNSET && index != l.currentMediaItemIndex) {
                            l.seekTo(index, pos)
                        } else if (index != C.INDEX_UNSET) {
                            l.seekTo(pos)
                        }
                        l.pause()
                    } finally {
                        syncing = false
                    }
                }
            }
            loadJob?.cancel()
            extendJob?.cancel()
            active = false
            playing = false
            buffering = false
            deviceName = null
        }
    }

    private val remoteListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            guard {
                playing = player.playWhenReady && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
                buffering = player.playbackState == Player.STATE_BUFFERING
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // the TV moved on by itself: show the same song on the phone and keep songs coming
            guard {
                val l = local ?: return@guard
                val index = l.indexOf(mediaItem?.mediaId)
                if (index != C.INDEX_UNSET && index != l.currentMediaItemIndex) {
                    syncing = true
                    try {
                        l.seekTo(index, 0L)
                        l.pause()
                    } finally {
                        syncing = false
                    }
                }
                extendIfNeeded()
            }
        }

        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
            guard {
                val max = remote?.deviceInfo?.maxVolume ?: 0
                if (max > 0) this@CastBridge.volume = if (muted) 0f else volume.toFloat() / max
            }
        }

        // a song the TV can't play: say so and move on to the next one
        override fun onPlayerError(error: PlaybackException) {
            Log.w("Booma", "TV couldn't play a song: ${error.errorCodeName}", error)
            guard {
                val r = remote ?: return@guard
                if (r.hasNextMediaItem()) {
                    say("The TV couldn't play this song. Skipping to the next one.")
                    r.seekToNextMediaItem()
                    r.prepare()
                    r.play()
                } else {
                    say("The TV couldn't play this song.")
                }
            }
        }
    }

    private val localListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // a song picked on the phone (skip, tap in a list, new queue) plays on the TV
            if (!active || syncing) return
            guard {
                local?.pause()
                loadFromPhone(startPosition = 0L)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // play pressed on the notification, headphones or widget while casting: play on the TV instead
            if (active && playWhenReady && !syncing) {
                guard {
                    local?.pause()
                    remote?.play()
                }
            }
        }
    }

    /** Send the phone's current song (and the next few) to the Cast device. */
    private fun loadFromPhone(startPosition: Long) {
        val l = local ?: return
        if (l.mediaItemCount == 0) return
        val start = l.currentMediaItemIndex.coerceIn(0, l.mediaItemCount - 1)
        val items = (start until minOf(l.mediaItemCount, start + 1 + AHEAD)).map { l.getMediaItemAt(it) }
        loadJob?.cancel()
        extendJob?.cancel()
        loadJob = scope.launch {
            // the first song the TV can get, starting with the one playing
            var firstIndex = 0
            var first: MediaItem? = null
            while (firstIndex < items.size && first == null) {
                first = castItem(items[firstIndex])
                if (first == null) firstIndex++
            }
            val r = remote ?: return@launch
            val firstItem = first
            if (firstItem == null) {
                say("Couldn't get these songs for the TV. Try another song.")
                return@launch
            }
            if (firstIndex > 0) say("Skipped a song the TV can't play.")
            guard {
                r.setMediaItem(firstItem, if (firstIndex == 0) startPosition else 0L)
                r.prepare()
                r.play()
            }
            // the rest follow once their links are ready
            for (item in items.drop(firstIndex + 1)) {
                val next = castItem(item) ?: continue
                if (!active) return@launch
                guard { r.addMediaItem(next) }
            }
        }
    }

    private fun extendIfNeeded() {
        val r = remote ?: return
        val l = local ?: return
        if (extendJob?.isActive == true || loadJob?.isActive == true) return
        val count = r.mediaItemCount
        // nothing on the TV (it stopped or failed): nothing to extend
        if (count == 0 || count - r.currentMediaItemIndex > 2) return
        val from = l.indexOf(r.getMediaItemAt(count - 1).mediaId)
        if (from == C.INDEX_UNSET) return
        val next = ((from + 1) until minOf(l.mediaItemCount, from + 1 + AHEAD)).map { l.getMediaItemAt(it) }
        if (next.isEmpty()) return
        extendJob = scope.launch {
            for (item in next) {
                val castable = castItem(item) ?: continue
                if (!active) return@launch
                guard { r.addMediaItem(castable) }
            }
        }
    }

    /**
     * The queue item with an audio link the Cast device can open, plus title, artist and cover.
     * The audio type is read from the link itself (M4A or WebM), so the TV is told the right format.
     */
    private suspend fun castItem(item: MediaItem): MediaItem? {
        val url = runCatching { Streams.resolveForCast(item.mediaId) }.getOrNull() ?: return null
        val mime = runCatching { url.getQueryParameter("mime") }.getOrNull()
            ?.takeIf { it.startsWith("audio/") }
            ?: MimeTypes.AUDIO_MP4
        val md = item.mediaMetadata
        return MediaItem.Builder()
            .setMediaId(item.mediaId)
            .setUri(url)
            .setMimeType(mime)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(md.title)
                    .setArtist(md.realArtist())
                    .setArtworkUri(md.artworkUri?.let { Uri.parse(it.toString()) })
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    private fun Player.indexOf(mediaId: String?): Int {
        if (mediaId == null) return C.INDEX_UNSET
        for (i in 0 until mediaItemCount) if (getMediaItemAt(i).mediaId == mediaId) return i
        return C.INDEX_UNSET
    }
}
