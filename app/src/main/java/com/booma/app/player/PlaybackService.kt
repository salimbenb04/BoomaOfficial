package com.booma.app.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.booma.app.MainActivity
import com.booma.app.R
import com.booma.app.data.Downloads
import com.booma.app.data.Http
import com.booma.app.data.Loudness
import com.booma.app.data.SettingsStore
import com.booma.app.data.Streams
import com.booma.app.widget.BoomaWidget
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Plays audio in the background with lock-screen, notification, widget and Android Auto controls,
 * and on a TV or speaker through Google Cast.
 *
 * Queue items carry a "booma://stream/VIDEO_ID" address. The real audio is looked up only when that
 * song starts loading: the downloaded file if there is one, otherwise a YouTube audio link.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var settings: SettingsStore
    private val gain = GainProcessor()
    private var crossfader: Crossfader? = null
    private var carLyrics: CarLyrics? = null

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        // The shared HTTP client keeps connections to YouTube's servers open, so songs start sooner.
        val http = OkHttpDataSource.Factory(Http.client)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
        // DefaultDataSource reads both web links and files on the phone (downloads).
        val upstream = DefaultDataSource.Factory(this, http)
        val resolving = ResolvingDataSource.Factory(upstream, ResolvingDataSource.Resolver { dataSpec: DataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme == SCHEME) {
                val id = uri.lastPathSegment ?: throw IOException("No video id in $uri")
                val local = Downloads.fileFor(id)
                if (local != null) {
                    dataSpec.withUri(Uri.fromFile(local))   // downloaded: play offline
                } else {
                    val real = runBlocking { Streams.resolveCached(this@PlaybackService, id) }
                        ?: throw IOException("No playable audio for $id")
                    dataSpec.withUri(real)
                }
            } else {
                dataSpec
            }
        })
        val sources = DefaultMediaSourceFactory(resolving)
        // Start playing after 1 s of audio is buffered (the default waits for 2.5 s).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_000, 2_000)
            .build()
        val player = ExoPlayer.Builder(this, gainRenderersFactory(this, gain))
            .setLoadControl(loadControl)
            .setMediaSourceFactory(sources)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true, // pause for calls and other apps
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            // Look up the next song while this one plays, so skipping and auto-advance start instantly.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                prefetchNext(player)
                applyNormalization(mediaItem?.mediaId)
                ResumeStore.save(this@PlaybackService, player)
                updateWidget(player)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                prefetchNext(player)
                ResumeStore.save(this@PlaybackService, player)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) ResumeStore.save(this@PlaybackService, player)
                updateWidget(player)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updateWidget(player)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateButtons(player)
            }
        })

        crossfader = Crossfader(this, player, sources, { settings.crossfadeSeconds() }, gain).also { it.start() }
        carLyrics = CarLyrics(player, settings, scope).also { it.start() }
        CastBridge.init(this, player)

        // The owl in the status bar and on the media notification.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().also { it.setSmallIcon(R.drawable.ic_stat_booma) }
        )

        // Tapping the notification, lock-screen controls or the dynamic island opens Booma's player.
        val openPlayer = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // CarLibrary answers Android Auto's browsing and gives queued songs their booma:// address.
        mediaSession = MediaLibrarySession.Builder(this, player, CarLibrary(this, scope))
            .setSessionActivity(openPlayer)
            .build()
        updateButtons(player)
    }

    /** Like, shuffle, repeat and radio buttons in Android Auto, the notification and the lock screen. */
    @Suppress("DEPRECATION")
    private fun updateButtons(player: Player) {
        mediaSession?.setCustomLayout(CarButtons.layout(player.repeatMode))
    }

    /** Volume normalisation: turn this song down to the common level (when the setting is on). */
    private fun applyNormalization(id: String?) {
        if (id == null || !settings.normalizeVolume()) {
            gain.setGain(0)
            return
        }
        val known = Loudness.cached(id)
        gain.setGain(Loudness.gainMb(known))
        if (known == null) {
            scope.launch {
                val db = Loudness.of(this@PlaybackService, id)
                if (mediaSession?.player?.currentMediaItem?.mediaId == id) gain.setGain(Loudness.gainMb(db))
            }
        }
    }

    private fun prefetchNext(player: Player) {
        val next = player.nextMediaItemIndex
        if (next != C.INDEX_UNSET && next < player.mediaItemCount) {
            Streams.prefetch(this, player.getMediaItemAt(next).mediaId)
        }
    }

    private var lastWidget: List<Any?>? = null

    private fun updateWidget(player: Player) {
        val md = player.mediaMetadata
        val title = md.title?.toString() ?: "Booma"
        val artist = md.realArtist()
        val art = md.artworkUri?.toString()
        val playing = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        // only redraw the widget when something it shows changed
        val shown = listOf(title, artist, art, playing)
        if (shown == lastWidget) return
        lastWidget = shown
        scope.launch {
            runCatching { BoomaWidget.push(this@PlaybackService, title, artist, playing, art, hasQueue = true) }
        }
    }

    /** Swiping Booma away from recent apps stops the music, unless "Keep playing after closing" is on. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        val keep = settings.keepPlayingAfterClose()
        if (player != null && keep && player.playWhenReady && player.mediaItemCount > 0) return
        if (CastBridge.active) return   // the TV keeps playing; Cast's own notification controls it
        player?.let { ResumeStore.save(this, it) }
        player?.pause()
        player?.stop()
        stopSelf()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onDestroy() {
        carLyrics?.release()
        carLyrics = null
        crossfader?.release()
        crossfader = null
        CastBridge.release()
        mediaSession?.let {
            ResumeStore.save(this, it.player)
            it.player.release()
            it.release()
        }
        mediaSession = null
        // show the widget as paused; it can resume the saved queue
        val app = applicationContext
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { BoomaWidget.markStopped(app) }
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val SCHEME = "booma"
        fun streamUri(videoId: String): Uri = Uri.parse("$SCHEME://stream/$videoId")
    }
}
