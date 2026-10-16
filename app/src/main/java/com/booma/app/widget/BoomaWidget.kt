package com.booma.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil.imageLoader
import coil.request.ImageRequest
import com.booma.app.MainActivity
import com.booma.app.R
import com.booma.app.player.ResumeStore
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray

internal val TITLE = stringPreferencesKey("title")
internal val ARTIST = stringPreferencesKey("artist")
internal val ART = stringPreferencesKey("art")
internal val PLAYING = booleanPreferencesKey("playing")
internal val HAS_QUEUE = booleanPreferencesKey("hasQueue")
internal val PLAYLISTS = stringPreferencesKey("playlists")

/**
 * Home-screen widget: the song playing with previous / play-pause / next, and, when the widget is
 * tall enough, your recent playlists (tap one to play it). Tap the song to open Booma's player.
 */
class BoomaWidget : GlanceAppWidget() {

    // lay out for the widget's real size, so the playlists row shows only when there is room
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent(context, currentState<Preferences>())
        }
    }

    companion object {
        /** Called by the playback service whenever the song or play state changes. */
        suspend fun push(context: Context, title: String, artist: String, playing: Boolean, artUrl: String?, hasQueue: Boolean) {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(BoomaWidget::class.java)
            if (ids.isEmpty()) return
            val artPath = artUrl?.let { url ->
                val name = "widget_art_${url.hashCode()}.jpg"
                File(context.cacheDir, name).takeIf { it.exists() }?.path ?: run {
                    context.cacheDir.listFiles { f -> f.name.startsWith("widget_art_") }?.forEach { it.delete() }
                    saveImage(context, url, name, 300)
                }
            }
            for (id in ids) {
                updateAppWidgetState(context, id) { prefs ->
                    prefs[TITLE] = title
                    prefs[ARTIST] = artist
                    prefs[PLAYING] = playing
                    prefs[HAS_QUEUE] = hasQueue
                    if (artPath != null) prefs[ART] = artPath else prefs.remove(ART)
                }
                BoomaWidget().update(context, id)
            }
        }

        /** The player stopped (app closed): show play, which resumes the saved queue. */
        suspend fun markStopped(context: Context) {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(BoomaWidget::class.java)
            for (id in ids) {
                updateAppWidgetState(context, id) { prefs ->
                    prefs[PLAYING] = false
                    prefs[HAS_QUEUE] = ResumeStore.hasQueue(context)
                }
                BoomaWidget().update(context, id)
            }
        }

        /** Widgets can't download images, so covers are saved as small files the widget reads. */
        suspend fun saveImage(context: Context, url: String, name: String, size: Int): String? {
            val request = ImageRequest.Builder(context).data(url).size(size).allowHardware(false).build()
            val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap ?: return null
            val file = File(context.cacheDir, name)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            return file.path
        }
    }
}

private val WidgetBg = Color(0xFF0B0716)
private val WidgetText = Color.White
private val WidgetMuted = Color(0xFFD9D2F4)

private class WidgetPlaylist(val id: String, val title: String, val art: String?, val thumb: String?)

private fun readPlaylists(json: String?): List<WidgetPlaylist> {
    val arr = runCatching { JSONArray(json ?: return emptyList()) }.getOrNull() ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        WidgetPlaylist(o.optString("id"), o.optString("title"), o.optString("art").ifEmpty { null }, o.optString("thumb").ifEmpty { null })
    }.filter { it.id.isNotEmpty() }
}

@Composable
private fun WidgetContent(context: Context, prefs: Preferences) {
    val size = LocalSize.current
    val title = prefs[TITLE] ?: "Booma"
    val artist = prefs[ARTIST] ?: "Tap to open"
    val playing = prefs[PLAYING] ?: false
    val art = prefs[ART]?.let { BitmapFactory.decodeFile(it) }
    val playlists = readPlaylists(prefs[PLAYLISTS])
    val openPlayer = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .cornerRadius(22.dp)
            .padding(12.dp),
    ) {
        // now playing
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(openPlayer),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = if (art != null) ImageProvider(art) else ImageProvider(R.drawable.widget_owl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.size(54.dp).cornerRadius(12.dp),
            )
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = title,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(WidgetText), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
                Text(text = artist, maxLines = 1, style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 13.sp))
            }
            WidgetButton(R.drawable.ic_widget_prev, "Previous", actionRunCallback<PreviousAction>())
            WidgetButton(
                if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                if (playing) "Pause" else "Play",
                actionRunCallback<PlayPauseAction>(),
            )
            WidgetButton(R.drawable.ic_widget_next, "Next", actionRunCallback<NextAction>())
        }

        // recent playlists, when the widget is tall enough
        if (size.height >= 150.dp && playlists.isNotEmpty()) {
            Spacer(GlanceModifier.height(12.dp))
            Text(text = "Your playlists", style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 12.sp))
            Spacer(GlanceModifier.height(6.dp))
            val count = if (size.width >= 300.dp) 4 else 3
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                playlists.take(count).forEach { pl ->
                    val cover = pl.art?.let { BitmapFactory.decodeFile(it) }
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(horizontal = 3.dp)
                            .clickable(openPlaylist(context, pl)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            provider = if (cover != null) ImageProvider(cover) else ImageProvider(R.drawable.widget_owl),
                            contentDescription = pl.title,
                            contentScale = ContentScale.Crop,
                            modifier = GlanceModifier.size(60.dp).cornerRadius(10.dp),
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text(text = pl.title, maxLines = 1, style = TextStyle(color = ColorProvider(WidgetText), fontSize = 11.sp))
                    }
                }
            }
        }
    }
}

/** Opens the playlist in Booma (each tile gets its own address so the taps don't mix up). */
private fun openPlaylist(context: Context, pl: WidgetPlaylist): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .setData(Uri.parse("booma://playlist/${pl.id}"))
        .putExtra(MainActivity.EXTRA_PLAYLIST_ID, pl.id)
        .putExtra(MainActivity.EXTRA_PLAYLIST_TITLE, pl.title)
        .putExtra(MainActivity.EXTRA_PLAYLIST_THUMB, pl.thumb)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
)

@Composable
private fun WidgetButton(icon: Int, label: String, action: Action) {
    Box(
        modifier = GlanceModifier.size(44.dp).cornerRadius(22.dp).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(provider = ImageProvider(icon), contentDescription = label, modifier = GlanceModifier.size(28.dp))
    }
}
