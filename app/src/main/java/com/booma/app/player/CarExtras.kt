package com.booma.app.player

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.booma.app.R
import com.booma.app.data.Liked
import com.booma.app.data.Lyrics
import com.booma.app.data.LyricsClient
import com.booma.app.data.SettingsStore
import com.booma.app.data.Track
import com.booma.app.data.Ytm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Extra buttons for Android Auto, the media notification and the lock screen:
 * like, shuffle what's up next, repeat (off / all / one) and start a radio from the song.
 */
@OptIn(UnstableApi::class)
object CarButtons {
    private const val LIKE = "booma.like"
    private const val SHUFFLE = "booma.shuffle"
    private const val REPEAT = "booma.repeat"
    private const val RADIO = "booma.radio"

    val commands = listOf(LIKE, SHUFFLE, REPEAT, RADIO).map { SessionCommand(it, Bundle.EMPTY) }

    fun layout(repeatMode: Int): List<CommandButton> = listOf(
        button("Like / remove like", R.drawable.ic_car_like, LIKE),
        button("Shuffle up next", R.drawable.ic_car_shuffle, SHUFFLE),
        when (repeatMode) {
            Player.REPEAT_MODE_ALL -> button("Repeat all", R.drawable.ic_car_repeat, REPEAT)
            Player.REPEAT_MODE_ONE -> button("Repeat this song", R.drawable.ic_car_repeat_one, REPEAT)
            else -> button("Repeat off", R.drawable.ic_car_repeat_off, REPEAT)
        },
        button("Start radio", R.drawable.ic_car_radio, RADIO),
    )

    private fun button(name: String, icon: Int, action: String): CommandButton =
        CommandButton.Builder()
            .setDisplayName(name)
            .setIconResId(icon)
            .setSessionCommand(SessionCommand(action, Bundle.EMPTY))
            .build()

    /** Runs a button's action on the session's player. Returns false for commands that aren't Booma's. */
    fun handle(context: Context, player: Player, action: String, scope: CoroutineScope): Boolean {
        val id = player.currentMediaItem?.mediaId
        when (action) {
            LIKE -> {
                val item = player.currentMediaItem ?: return true
                val md = item.mediaMetadata
                val track = Track(item.mediaId, md.title?.toString().orEmpty(), md.realArtist(), md.artworkUri?.toString())
                // like, or undo the like if the song is already liked
                scope.launch { runCatching { Liked.toggle(Ytm.client(context), track) } }
            }
            SHUFFLE -> {
                val cur = player.currentMediaItemIndex
                val n = player.mediaItemCount
                if (cur + 2 < n) {
                    val rest = (cur + 1 until n).map { player.getMediaItemAt(it) }.shuffled()
                    player.removeMediaItems(cur + 1, n)
                    player.addMediaItems(rest)
                }
            }
            REPEAT -> player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            RADIO -> {
                if (id == null) return true
                scope.launch {
                    val radio = withContext(Dispatchers.IO) {
                        runCatching { Ytm.client(context).radio(id) }.getOrDefault(emptyList())
                    }.filter { it.id != id }
                    if (radio.isEmpty() || player.currentMediaItem?.mediaId != id) return@launch
                    // the radio replaces what was up next
                    val cur = player.currentMediaItemIndex
                    if (cur + 1 < player.mediaItemCount) player.removeMediaItems(cur + 1, player.mediaItemCount)
                    player.addMediaItems(radio.map { it.toMediaItem() })
                }
            }
            else -> return false
        }
        return true
    }
}

/**
 * "Lyrics in the car": while a song with synced lyrics plays, its current line takes the artist's place
 * in the song's details, which Android Auto, car screens over Bluetooth and the lock screen show.
 * The real artist is kept in the item's extras (Booma's own screens keep showing it), and put back when
 * the song changes or the setting is turned off. The audio isn't touched (only the details change).
 */
@OptIn(UnstableApi::class)
class CarLyrics(
    private val player: ExoPlayer,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var songId: String? = null
    private var lyrics: Lyrics? = null
    private var changedId: String? = null     // the item whose artist line currently shows lyrics
    private var shown: String? = null

    private val tick = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, 300L)
        }
    }

    fun start() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun release() {
        handler.removeCallbacks(tick)
        restore()
    }

    private fun step() {
        val item = player.currentMediaItem
        if (!settings.carLyrics() || item == null || CastBridge.active) {
            restore()
            return
        }
        val id = item.mediaId
        if (id != songId) {
            restore()
            songId = id
            lyrics = null
            val md = item.mediaMetadata
            val title = md.title?.toString().orEmpty()
            val artist = md.realArtist()
            scope.launch {
                val found = runCatching { LyricsClient.fetch(id, title, artist) }.getOrNull()
                if (songId == id) lyrics = found
            }
            return
        }
        val ly = lyrics?.takeIf { it.synced } ?: return
        val pos = player.currentPosition
        val line = ly.lines.lastOrNull { it.timeMs <= pos + 300 }?.text?.ifBlank { "♪" } ?: "♪"
        if (line == shown && changedId == id) return
        if (setArtistLine(id, line)) {
            shown = line
            changedId = id
        }
    }

    /** Put the real artist back on the item that shows lyrics. */
    private fun restore() {
        val id = changedId ?: return
        setArtistLine(id, null)
        changedId = null
        shown = null
    }

    /** Show [line] as the artist of the item with [id] ([line] null: the real artist again). */
    private fun setArtistLine(id: String, line: String?): Boolean {
        val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == id } ?: return false
        val item = player.getMediaItemAt(index)
        val md = item.mediaMetadata
        val real = md.realArtist()
        val extras = Bundle(md.extras ?: Bundle.EMPTY)
        if (line == null) extras.remove(REAL_ARTIST_KEY) else extras.putString(REAL_ARTIST_KEY, real)
        val updated = md.buildUpon().setArtist(line ?: real).setExtras(extras).build()
        // same address, new details: the player keeps playing without a break
        player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(updated).build())
        return true
    }
}
