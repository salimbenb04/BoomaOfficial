package com.booma.app.ui

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Picks a background colour from a song's cover, for "Match the song's cover". */
object CoverColors {
    private val cache = HashMap<String, Color>()

    suspend fun extract(context: Context, url: String): Color? {
        synchronized(cache) { cache[url]?.let { return it } }
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(128)
            .allowHardware(false) // Palette needs to read the pixels
            .build()
        val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap ?: return null
        val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }
        val swatch = palette.darkVibrantSwatch
            ?: palette.vibrantSwatch
            ?: palette.darkMutedSwatch
            ?: palette.dominantSwatch
            ?: return null
        val color = Color(swatch.rgb)
        synchronized(cache) { cache[url] = color }
        return color
    }
}
