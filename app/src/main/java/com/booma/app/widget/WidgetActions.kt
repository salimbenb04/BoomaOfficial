package com.booma.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.booma.app.data.AuthStore
import com.booma.app.data.InnertubeClient
import com.booma.app.data.SettingsStore
import com.booma.app.data.YtmRepository
import com.booma.app.player.PlaybackService
import com.booma.app.player.ResumeStore
import com.booma.app.player.bigArt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/** Connects to the player for one widget tap, works whether Booma is open or closed. */
private object WidgetControl {
    suspend fun withPlayer(context: Context, block: (MediaController) -> Unit) = withContext(Dispatchers.Main) {
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val controller = MediaController.Builder(app, token).buildAsync().await()
        try {
            block(controller)
            // stay connected until playback has really started, so the player can take over
            withTimeoutOrNull(5_000) {
                while (controller.playWhenReady && !controller.isPlaying && controller.playbackState != Player.STATE_ENDED) delay(100)
            }
        } finally {
            controller.release()
        }
    }

    /** Nothing loaded (Booma was closed): continue the last queue where it stopped. */
    fun resumeOrPlay(context: Context, c: MediaController) {
        if (c.mediaItemCount == 0) {
            val saved = ResumeStore.load(context) ?: return
            c.setMediaItems(saved.mediaItems, saved.startIndex, saved.startPositionMs)
        }
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetControl.withPlayer(context) { c ->
            if (c.isPlaying) c.pause() else WidgetControl.resumeOrPlay(context, c)
        }
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetControl.withPlayer(context) { c ->
            if (c.mediaItemCount == 0) {
                WidgetControl.resumeOrPlay(context, c)   // Booma was closed: just continue
            } else {
                if (c.hasNextMediaItem()) c.seekToNextMediaItem()
                c.play()
            }
        }
    }
}

class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetControl.withPlayer(context) { c ->
            if (c.mediaItemCount == 0) {
                WidgetControl.resumeOrPlay(context, c)
            } else {
                c.seekToPrevious()
                c.play()
            }
        }
    }
}

/** Keeps the widget's playlists row filled with your recent playlists. */
object WidgetData {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshPlaylists(context: Context, repo: YtmRepository) {
        val app = context.applicationContext
        scope.launch {
            val ids = GlanceAppWidgetManager(app).getGlanceIds(BoomaWidget::class.java)
            if (ids.isEmpty()) return@launch
            val lists = runCatching { repo.playlists() }.getOrNull()
                ?.filter { it.id.startsWith("VL") }
                ?.take(4) ?: return@launch
            val arr = JSONArray()
            lists.forEachIndexed { i, pl ->
                val art = pl.thumb?.let { BoomaWidget.saveImage(app, bigArt(it), "widget_pl_$i.jpg", 180) }
                arr.put(JSONObject().put("id", pl.id).put("title", pl.title).put("art", art ?: "").put("thumb", pl.thumb ?: ""))
            }
            for (id in ids) {
                updateAppWidgetState(app, id) { it[PLAYLISTS] = arr.toString() }
                BoomaWidget().update(app, id)
            }
        }
    }
}

class BoomaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BoomaWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // a widget was added or refreshed: fill in the playlists
        if (AuthStore(context).isSignedIn()) {
            WidgetData.refreshPlaylists(context, InnertubeClient(AuthStore(context), SettingsStore(context)))
        }
    }
}
