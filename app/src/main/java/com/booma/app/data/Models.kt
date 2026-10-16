package com.booma.app.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.booma.app.ui.BoomaDefaultBg

data class Track(
    val id: String,       // YouTube video id
    val title: String,
    val artist: String,
    val thumb: String? = null,
    val setVideoId: String? = null,   // this song's entry in a playlist (needed to remove it from that playlist)
)

data class Playlist(
    val id: String,       // browse id ("VLPL…" playlist, "MPREb_…" album) or "mix:RD…" for a mix
    val title: String,
    val subtitle: String,
    val thumb: String?,
)

/** One Home shelf ("Quick picks", "Listen again", "Mixed for you"…). [rows]: songs shown as a list. */
data class Shelf(
    val title: String,
    val songs: List<Track>,
    val lists: List<Playlist>,
    val rows: Boolean,
)

data class SearchResults(val songs: List<Track>, val lists: List<Playlist>)

/** Text from an album or playlist subtitle that names the artist ("Album • Daft Punk • 2013" -> "Daft Punk"). */
fun artistFromSubtitle(subtitle: String): String {
    val skip = setOf("Album", "Single", "EP", "Playlist", "Mix", "Song", "Video")
    return subtitle.split("•").map { it.trim() }
        .firstOrNull { it.isNotEmpty() && it !in skip && !Regex("""^\d{4}$""").matches(it) && !it.contains("views") && !it.contains("songs") }
        .orEmpty()
}

/** The signed-in YouTube Music session. Encrypted on the phone and never leaves it. */
class AuthStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        "booma_auth_secure",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun cookie(): String? = prefs.getString("cookie", null)

    fun isSignedIn(): Boolean = !cookie().isNullOrBlank()

    fun save(cookie: String) {
        prefs.edit().putString("cookie", cookie).apply()
    }

    fun clear() {
        prefs.edit().remove("cookie").apply()
    }
}

/**
 * App preferences that aren't secret: theme colour, and the client versions the Debug tab can change
 * without a rebuild (YouTube sometimes needs a newer version string).
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("booma_settings", Context.MODE_PRIVATE)

    fun background(): Color = Color(prefs.getInt("background", BoomaDefaultBg.toArgb()))

    fun setBackground(color: Color) {
        prefs.edit().putInt("background", color.toArgb()).apply()
    }

    fun gradient(): Boolean = prefs.getBoolean("gradient", true)

    fun snakeHead(): Boolean = prefs.getBoolean("snakeHead", true)

    /** Colours follow the cover of the song that is playing. */
    fun dynamicTheme(): Boolean = prefs.getBoolean("dynamicTheme", false)

    fun setDynamicTheme(on: Boolean) {
        prefs.edit().putBoolean("dynamicTheme", on).apply()
    }

    /** Off by default: swiping Booma away from recent apps stops the music. */
    fun keepPlayingAfterClose(): Boolean = prefs.getBoolean("keepPlaying", false)

    fun setKeepPlayingAfterClose(on: Boolean) {
        prefs.edit().putBoolean("keepPlaying", on).apply()
    }

    fun setSnakeHead(on: Boolean) {
        prefs.edit().putBoolean("snakeHead", on).apply()
    }

    fun setGradient(on: Boolean) {
        prefs.edit().putBoolean("gradient", on).apply()
    }

    fun webVersion(): String = prefs.getString("webVersion", "1.20241009.01.00") ?: "1.20241009.01.00"

    fun setWebVersion(v: String) {
        prefs.edit().putString("webVersion", v).apply()
    }

    // ---- search history (newest first)

    fun searchHistory(): List<String> {
        val arr = runCatching { org.json.JSONArray(prefs.getString("searchHistory", "[]")) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }

    fun addSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        saveHistory(listOf(q) + searchHistory().filterNot { it.equals(q, ignoreCase = true) })
    }

    fun removeSearch(query: String) {
        saveHistory(searchHistory().filterNot { it == query })
    }

    fun clearSearchHistory() {
        saveHistory(emptyList())
    }

    private fun saveHistory(list: List<String>) {
        prefs.edit().putString("searchHistory", org.json.JSONArray(list.take(20)).toString()).apply()
    }

    // ---- audio

    /** Evens out loudness so quiet and loud songs play at a similar level. */
    fun normalizeVolume(): Boolean = prefs.getBoolean("normalize", true)

    fun setNormalizeVolume(on: Boolean) {
        prefs.edit().putBoolean("normalize", on).apply()
    }

    /** Seconds songs fade into each other; 0 is off. */
    fun crossfadeSeconds(): Int = prefs.getInt("crossfade", 0)

    fun setCrossfadeSeconds(seconds: Int) {
        prefs.edit().putInt("crossfade", seconds).apply()
    }

    /** "high" picks the best audio YouTube offers; "low" saves mobile data. */
    fun audioQuality(): String = prefs.getString("audioQuality", "high") ?: "high"

    fun setAudioQuality(quality: String) {
        prefs.edit().putString("audioQuality", quality).apply()
    }

    // ---- car

    /** Show the current lyric line in place of the artist on Android Auto, car Bluetooth screens and the lock screen. */
    fun carLyrics(): Boolean = prefs.getBoolean("carLyrics", false)
    fun setCarLyrics(on: Boolean) = prefs.edit().putBoolean("carLyrics", on).apply()

    fun androidVersion(): String = prefs.getString("androidVersion", "7.11.50") ?: "7.11.50"

    fun setAndroidVersion(v: String) {
        prefs.edit().putString("androidVersion", v).apply()
    }
}
