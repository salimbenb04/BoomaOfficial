package com.booma.app.data

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How loud each song is, from YouTube ("loudnessDb": how many dB louder than YouTube's reference level).
 * Volume normalisation turns loud songs down by that much, like YouTube itself does.
 */
object Loudness {
    private val cache = ConcurrentHashMap<String, Double>()   // NaN: YouTube didn't say

    fun cached(videoId: String): Double? = cache[videoId]?.takeUnless { it.isNaN() }

    suspend fun of(context: Context, videoId: String): Double? {
        cache[videoId]?.let { return it.takeUnless { v -> v.isNaN() } }
        val value = withContext(Dispatchers.IO) {
            runCatching { Ytm.client(context).loudnessDb(videoId) }.getOrNull()
        }
        cache[videoId] = value ?: Double.NaN
        return value
    }

    /** Gain for the player in millibels: loud songs go down to the reference level; quiet ones stay as they are. */
    fun gainMb(loudnessDb: Double?): Int =
        if (loudnessDb == null) 0 else (-loudnessDb * 100).toInt().coerceIn(-1500, 0)
}

/** One YouTube Music client for the background parts of the app (player service, Android Auto, loudness). */
object Ytm {
    @Volatile
    private var shared: InnertubeClient? = null

    fun client(context: Context): InnertubeClient {
        shared?.let { return it }
        synchronized(this) {
            shared?.let { return it }
            val app = context.applicationContext
            return InnertubeClient(AuthStore(app), SettingsStore(app)).also { shared = it }
        }
    }
}
