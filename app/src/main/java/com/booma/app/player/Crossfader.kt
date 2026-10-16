package com.booma.app.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.MediaSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A renderers factory whose audio passes through [gain] (volume normalisation). */
@OptIn(UnstableApi::class)
fun gainRenderersFactory(context: Context, gain: GainProcessor): DefaultRenderersFactory =
    object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(gain))
            .build()
    }

/**
 * Crossfade: in the last seconds of a song, the end of it keeps playing on a second, temporary player
 * and fades out while the next song starts and fades in on the main player.
 *
 * The temporary player loads the end of the song a few seconds early, so the fade starts on time.
 * Nothing happens with crossfade off, on repeat-one, when casting, before "sleep at end of song",
 * or for songs shorter than three fades.
 */
@OptIn(UnstableApi::class)
class Crossfader(
    private val context: Context,
    private val main: ExoPlayer,
    private val sources: MediaSource.Factory,
    private val seconds: () -> Int,
    private val mainGain: GainProcessor,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var tail: ExoPlayer? = null
    private var tailGain: GainProcessor? = null
    private var tailFor: String? = null      // media id the tail player was loaded for
    private var fadeStart = 0L               // uptime when the fade began, 0 = not fading
    private var fadeMs = 0L

    private val tick = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, if (fadeStart > 0) 40L else 250L)
        }
    }

    fun start() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun release() {
        handler.removeCallbacks(tick)
        dropTail()
        main.volume = 1f
    }

    private fun step() {
        if (fadeStart > 0) {
            fade()
            return
        }
        val fadeSeconds = seconds()
        val id = main.currentMediaItem?.mediaId
        val allowed = fadeSeconds > 0 && main.isPlaying && main.hasNextMediaItem() &&
            main.repeatMode != Player.REPEAT_MODE_ONE && !CastBridge.active && !PlayerHolder.sleepAtEndOfSong
        val duration = main.duration
        if (!allowed || id == null || duration == C.TIME_UNSET || duration < fadeSeconds * 3000L) {
            if (tail != null) dropTail()
            return
        }
        if (tailFor != null && tailFor != id) dropTail()   // the song changed (skip, seek to another song)
        val window = fadeSeconds * 1000L
        val remaining = duration - main.currentPosition
        when {
            // load the end of this song a few seconds early, silent and paused
            remaining <= window + 5_000 && tail == null && remaining > window -> prepareTail(duration - window)
            // time to fade
            remaining <= window -> {
                val t = tail
                if (t != null && t.playbackState == Player.STATE_READY && remaining > window / 2) {
                    beginFade(t, remaining)
                } else if (remaining <= window / 2) {
                    dropTail()   // not ready in time: let the song end normally
                }
            }
        }
    }

    private fun prepareTail(from: Long) {
        val item = main.currentMediaItem ?: return
        val gain = GainProcessor().also { it.setGain(mainGain.gainMb) }
        val player = ExoPlayer.Builder(context, gainRenderersFactory(context, gain))
            .setMediaSourceFactory(sources)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                false, // the main player keeps audio focus
            )
            .build()
        player.volume = 0f
        player.setMediaItem(item, from)
        player.playWhenReady = false
        player.prepare()
        tail = player
        tailGain = gain
        tailFor = item.mediaId
    }

    private fun beginFade(t: ExoPlayer, remaining: Long) {
        // line the tail up with where the song is now, start it, and move the main player on
        val duration = main.duration
        t.seekTo((duration - remaining).coerceAtLeast(0L))
        t.volume = 1f
        t.play()
        fadeMs = remaining.coerceAtLeast(1_000L)
        fadeStart = android.os.SystemClock.uptimeMillis()
        main.volume = 0f
        main.seekToNextMediaItem()
    }

    private fun fade() {
        val t = tail
        // paused or skipped during the fade: stop it cleanly
        if (t == null || !main.playWhenReady) {
            finishFade()
            return
        }
        val p = ((android.os.SystemClock.uptimeMillis() - fadeStart).toFloat() / fadeMs).coerceIn(0f, 1f)
        // equal-power curves keep the overall loudness even through the fade
        main.volume = sin(p * PI.toFloat() / 2f)
        t.volume = cos(p * PI.toFloat() / 2f)
        if (p >= 1f) finishFade()
    }

    private fun finishFade() {
        fadeStart = 0L
        main.volume = 1f
        dropTail()
    }

    private fun dropTail() {
        tail?.release()
        tail = null
        tailGain = null
        tailFor = null
    }
}
