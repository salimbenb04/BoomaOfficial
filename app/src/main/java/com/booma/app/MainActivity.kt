package com.booma.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.booma.app.data.AuthStore
import com.booma.app.data.Ytm
import com.booma.app.data.Playlist
import com.booma.app.data.SettingsStore
import com.booma.app.data.Streams
import com.booma.app.player.PlayerHolder
import com.booma.app.ui.BoomaApp
import com.booma.app.ui.BoomaTheme
import com.booma.app.ui.CoverColors
import com.booma.app.widget.WidgetData

// FragmentActivity (a ComponentActivity) so the Cast device picker can show its dialogs.
class MainActivity : FragmentActivity() {
    // Android 13+ asks before showing the playback notification.
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Goes up each time the notification, dynamic island or widget asks to show the player. */
    private var openPlayerSignal by mutableIntStateOf(0)

    /** A playlist the widget asked to open. */
    private var playlistRequest by mutableStateOf<Playlist?>(null)

    private fun readIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayerSignal++
        intent.getStringExtra(EXTRA_PLAYLIST_ID)?.let { id ->
            playlistRequest = Playlist(
                id = id,
                title = intent.getStringExtra(EXTRA_PLAYLIST_TITLE).orEmpty(),
                subtitle = "",
                thumb = intent.getStringExtra(EXTRA_PLAYLIST_THUMB)?.ifEmpty { null },
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Booma's screens are always dark, so the clock and icons in the status and navigation bars
        // stay light whatever the phone's own light / dark setting is.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        readIntent(intent)
        PlayerHolder.connect(this)
        // start looking for Cast devices (TVs, speakers) on the Wi-Fi; no-op without Google Play services
        runCatching { com.google.android.gms.cast.framework.CastContext.getSharedInstance(applicationContext) }
        val auth = AuthStore(this)
        val settings = SettingsStore(this)
        val client = Ytm.client(this)
        Streams.warmUp(this)   // load YouTube's player code now, so the first song starts faster
        if (auth.isSignedIn()) WidgetData.refreshPlaylists(this, client)   // keep the widget's playlists current
        setContent {
            var background by remember { mutableStateOf(settings.background()) }
            var gradient by remember { mutableStateOf(settings.gradient()) }
            var snakeHead by remember { mutableStateOf(settings.snakeHead()) }
            var dynamic by remember { mutableStateOf(settings.dynamicTheme()) }

            // "Match the song's cover": take the colour from the playing song's artwork, fading between songs.
            // The artwork is watched outside the screen tree, so a song change only re-themes when the
            // cover's colour actually differs (and never when this option is off).
            var coverColor by remember { mutableStateOf<Color?>(null) }
            LaunchedEffect(dynamic) {
                if (!dynamic) {
                    coverColor = null
                    return@LaunchedEffect
                }
                snapshotFlow { PlayerHolder.artwork }.distinctUntilChanged().collect { art ->
                    if (art != null) {
                        runCatching { CoverColors.extract(this@MainActivity, art) }.getOrNull()?.let { coverColor = it }
                    }
                }
            }
            // Switches at once; only the background fades (see boomaBackground), which keeps it smooth.
            BoomaTheme(coverColor ?: background, gradient, snakeHead) {
                BoomaApp(
                    auth = auth,
                    repo = client,
                    settings = settings,
                    background = background,
                    onBackgroundChange = { picked ->
                        settings.setBackground(picked)
                        background = picked
                    },
                    gradient = gradient,
                    onGradientChange = { on ->
                        settings.setGradient(on)
                        gradient = on
                    },
                    snakeHead = snakeHead,
                    onSnakeHeadChange = { on ->
                        settings.setSnakeHead(on)
                        snakeHead = on
                    },
                    dynamicTheme = dynamic,
                    onDynamicThemeChange = { on ->
                        settings.setDynamicTheme(on)
                        dynamic = on
                    },
                    openPlayerSignal = openPlayerSignal,
                    playlistRequest = playlistRequest,
                    onPlaylistRequestHandled = { playlistRequest = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        PlayerHolder.connect(this) // reconnects if the player service was stopped meanwhile
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "booma.openPlayer"
        const val EXTRA_PLAYLIST_ID = "booma.playlistId"
        const val EXTRA_PLAYLIST_TITLE = "booma.playlistTitle"
        const val EXTRA_PLAYLIST_THUMB = "booma.playlistThumb"
    }
}
