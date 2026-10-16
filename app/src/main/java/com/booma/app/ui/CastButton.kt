package com.booma.app.ui

import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import androidx.mediarouter.app.MediaRouteControllerDialog
import androidx.mediarouter.app.MediaRouteControllerDialogFragment
import androidx.mediarouter.app.MediaRouteDialogFactory
import com.booma.app.R
import com.booma.app.player.CastBridge
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext

/**
 * The Cast button: lists the TVs and speakers on your Wi-Fi; pick one and Booma plays there.
 * Hidden on phones without Google Play services.
 */
@OptIn(UnstableApi::class)
@Composable
fun CastButton(tint: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val available = remember {
        runCatching { CastContext.getSharedInstance(context.applicationContext) }.isSuccess
    }
    if (!available) return
    val casting = CastBridge.active
    val color = if (casting) OwlGlow else tint
    AndroidView(
        factory = { viewContext ->
            val themed = viewContext.castThemed()
            MediaRouteButton(themed).apply {
                runCatching { CastButtonFactory.setUpMediaRouteButton(themed, this) }
                dialogFactory = BoomaCastDialogs
            }
        },
        update = { button ->
            button.contentDescription = if (casting) "Casting to ${CastBridge.deviceName ?: "a device"}" else "Cast to a TV or speaker"
            ContextCompat.getDrawable(context, if (casting) R.drawable.ic_cast_connected else R.drawable.ic_cast)
                ?.mutate()
                ?.let { d ->
                    val wrapped = DrawableCompat.wrap(d)
                    DrawableCompat.setTint(wrapped, color.toArgb())
                    button.setRemoteIndicatorDrawable(wrapped)
                }
        },
        modifier = modifier.size(48.dp),
    )
}

/** The device picker in a dark AppCompat theme, to sit well on Booma's dark screens. */
private fun Context.castThemed() = ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)

private object BoomaCastDialogs : MediaRouteDialogFactory() {
    override fun onCreateChooserDialogFragment() = BoomaCastChooser()

    override fun onCreateControllerDialogFragment() = BoomaCastController()
}

class BoomaCastChooser : MediaRouteChooserDialogFragment() {
    override fun onCreateChooserDialog(context: Context, savedInstanceState: Bundle?) =
        MediaRouteChooserDialog(context.castThemed())
}

class BoomaCastController : MediaRouteControllerDialogFragment() {
    override fun onCreateControllerDialog(context: Context, savedInstanceState: Bundle?) =
        MediaRouteControllerDialog(context.castThemed())
}

/** While casting: which TV, its volume, and a button to stop casting (the phone takes over, paused). */
@OptIn(UnstableApi::class)
@Composable
fun CastPanel(modifier: Modifier = Modifier) {
    if (!CastBridge.active) return
    val context = LocalContext.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_cast_connected), contentDescription = null, tint = OwlGlow, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            CastBridge.deviceName ?: "TV",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp),
        )
        Spacer(Modifier.width(8.dp))
        Slider(
            value = CastBridge.volume,
            onValueChange = { CastBridge.changeVolume(it) },
            colors = SliderDefaults.colors(thumbColor = OwlCore, activeTrackColor = OwlGlow),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { CastBridge.disconnect(context) }) { Text("Stop", color = OwlGlow) }
    }
}
