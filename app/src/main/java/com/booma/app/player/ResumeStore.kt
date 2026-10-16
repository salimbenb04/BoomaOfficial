package com.booma.app.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * Remembers the last queue, song and position, so playback can resume from the widget, the lock
 * screen or Android's media controls after Booma was closed.
 */
object ResumeStore {
    private fun prefs(context: Context) = context.getSharedPreferences("booma_resume", Context.MODE_PRIVATE)

    fun hasQueue(context: Context): Boolean = prefs(context).contains("queue")

    fun save(context: Context, player: Player) {
        val n = player.mediaItemCount
        if (n == 0) return
        val start = max(0, player.currentMediaItemIndex - 50)
        val end = min(n, start + 200)
        val arr = JSONArray()
        for (i in start until end) {
            val m = player.getMediaItemAt(i)
            arr.put(
                JSONObject()
                    .put("id", m.mediaId)
                    .put("title", m.mediaMetadata.title?.toString().orEmpty())
                    .put("artist", m.mediaMetadata.realArtist())
                    .put("art", m.mediaMetadata.artworkUri?.toString().orEmpty())
            )
        }
        prefs(context).edit()
            .putString("queue", arr.toString())
            .putInt("index", player.currentMediaItemIndex - start)
            .putLong("position", player.currentPosition.coerceAtLeast(0))
            .apply()
    }

    @OptIn(UnstableApi::class)
    fun load(context: Context): MediaSession.MediaItemsWithStartPosition? {
        val p = prefs(context)
        val arr = runCatching { JSONArray(p.getString("queue", null) ?: return null) }.getOrNull() ?: return null
        val items = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifEmpty { return@mapNotNull null }
            MediaItem.Builder()
                .setMediaId(id)
                .setUri(PlaybackService.streamUri(id))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(o.optString("title"))
                        .setArtist(o.optString("artist"))
                        .setArtworkUri(o.optString("art").ifEmpty { null }?.let { Uri.parse(it) })
                        .build()
                )
                .build()
        }
        if (items.isEmpty()) return null
        val index = p.getInt("index", 0).coerceIn(0, items.lastIndex)
        return MediaSession.MediaItemsWithStartPosition(items, index, p.getLong("position", 0))
    }
}
